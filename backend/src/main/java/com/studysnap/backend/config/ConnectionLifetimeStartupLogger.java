package com.studysnap.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.stereotype.Component;

/**
 * Logs, once at startup, the two settings that together decide <b>how long a JDBC connection is held</b>.
 *
 * <p>⚠️ WHY THIS EXISTS, AND WHY IT IS NOT REDUNDANT WITH THE TESTS THAT ALREADY MEASURE THIS.
 * {@code ConnectionHandlingModeContractTest} settled the question in v0.112.0 and pins the value — but it
 * runs against the H2 <b>test</b> profile, which {@code src/test/resources/application.yaml} shadows in
 * entirely. It therefore proves what the mode is <b>in a test context</b>. This logger is the same read
 * taken <b>in production</b>, on the profile and datasource that actually serve requests.
 *
 * <p>⚠️ THAT GAP IS NOT THEORETICAL. {@code DataSourcePoolContractTest} guards against
 * {@code application-prod.yaml} shadowing the pool settings, but its overlay assertion covers
 * {@code spring.datasource.} only. A {@code spring.jpa.properties.hibernate.connection.handling_mode} key
 * added to the overlay — or supplied as an environment variable — would win in production while every
 * existing assertion in the suite stayed green. {@code ScheduledJobCronContractTest} records that exact
 * shadowing shape being missed twice. This line is what would show it.
 *
 * <p>⚠️ IT READS EFFECTIVE STATE, NEVER CONFIGURED STATE, AND THAT IS THE WHOLE POINT. The mode comes from
 * {@link EntityManagerFactory#getProperties()} — what Hibernate actually built with, after Spring's
 * {@code HibernateJpaVendorAdapter} has forced its own value. The open-in-view state comes from whether
 * {@link OpenEntityManagerInViewInterceptor} is <b>registered as a bean</b>, which is a direct read of the
 * request lifecycle rather than a property with a default filled in. Printing
 * {@code open-in-view=true (Spring Boot default)} would be an argument from defaults, which v0.112.0's
 * anti-drift forbids substituting for either observation.
 *
 * <p><b>What the reading means.</b> {@code DELAYED_ACQUISITION_AND_HOLD} plus a registered interceptor is
 * the combination that makes a connection outlive its transaction: it is acquired on first database access
 * and returned when the {@code EntityManager} closes at the end of the HTTP request. Under that pairing,
 * relocating a slow external call out of {@code @Transactional} releases the <b>transaction</b> while the
 * <b>connection</b> stays bound — so a transaction-boundary refactor can land, look correct, and not
 * relieve pool exhaustion.
 *
 * @see ConnectionHandlingModeContractTest the same two reads taken as assertions
 * @see <a href="file:../../../../../../../docs/claude-findings/2026-09-04-prod-outage-hikari-pool-exhaustion.md">the outage finding, §7</a>
 */
@Component
public class ConnectionLifetimeStartupLogger {
    private static final Logger log = LoggerFactory.getLogger(ConnectionLifetimeStartupLogger.class);

    /** The Hibernate property that decides when an acquired connection is given back. */
    static final String HANDLING_MODE_PROPERTY = "hibernate.connection.handling_mode";

    /**
     * The substring shared by every Hibernate mode that keeps the connection until the session closes.
     * Matching on the substring rather than on one enum constant keeps the reading honest if Hibernate
     * renames or adds a HOLD variant — the consequence follows the value read, never a constant here.
     */
    private static final String HOLDS_UNTIL_SESSION_CLOSE = "HOLD";

    private static final String UNREADABLE = "<absent>";

    private final EntityManagerFactory entityManagerFactory;
    private final ObjectProvider<OpenEntityManagerInViewInterceptor> openInViewInterceptor;

    public ConnectionLifetimeStartupLogger(
            EntityManagerFactory entityManagerFactory,
            ObjectProvider<OpenEntityManagerInViewInterceptor> openInViewInterceptor) {
        this.entityManagerFactory = entityManagerFactory;
        this.openInViewInterceptor = openInViewInterceptor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logConnectionLifetime() {
        log.info("{}", describeConnectionLifetime());
    }

    /**
     * The emitted line, composed from live state so a test can read it without capturing an appender.
     *
     * <p>⚠️ EVERY CLAUSE IS DERIVED. A version of this that hardcoded the mode, the open-in-view state or
     * the consequence sentence would print something true today and stay printing it after the underlying
     * value changed — which is worse than printing nothing, because it would be believed.
     */
    String describeConnectionLifetime() {
        String handlingMode = readEffectiveHandlingMode();
        boolean openInView = openInViewInterceptor.getIfAvailable() != null;
        return "connection lifetime: " + HANDLING_MODE_PROPERTY + "=" + handlingMode
                + " (effective, read from EntityManagerFactory), open-in-view="
                + (openInView ? "ON" : "OFF")
                + " (OpenEntityManagerInViewInterceptor "
                + (openInView ? "registered" : "absent") + ") — "
                + describeConsequence(handlingMode, openInView);
    }

    private String readEffectiveHandlingMode() {
        Object mode = entityManagerFactory.getProperties().get(HANDLING_MODE_PROPERTY);
        return mode == null ? UNREADABLE : mode.toString();
    }

    private String describeConsequence(String handlingMode, boolean openInView) {
        if (!handlingMode.contains(HOLDS_UNTIL_SESSION_CLOSE)) {
            return "a connection is returned to the pool when its transaction ends";
        }
        return openInView
                ? "a connection is held until the HTTP request ends, NOT until the transaction commits"
                : "a connection is held until the EntityManager closes, NOT until the transaction commits";
    }
}
