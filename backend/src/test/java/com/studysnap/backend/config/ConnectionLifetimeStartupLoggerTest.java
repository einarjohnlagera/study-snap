package com.studysnap.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

import java.util.Map;

import static com.studysnap.backend.config.ConnectionLifetimeStartupLogger.HANDLING_MODE_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ⚠️ THE PRE-DECLARED GUARD FOR v0.114.0, AND ITS SHAPE IS THE WHOLE POINT.
 *
 * <p>The release pre-declared that <b>the logged value must be the EFFECTIVE one read back from the
 * {@code EntityManagerFactory}, not the configured or defaulted one</b>, because a test asserting the
 * constant we passed in <b>passes under both the defect and the fix and proves nothing</b> — the same
 * shape as v0.105.0's schema test with hardcoded booleans instead of the derivation.
 *
 * <p>⚠️ SO EVERY FIXTURE HERE SUPPLIES A VALUE THAT IS DELIBERATELY <b>NOT</b> THE PRODUCTION ONE.
 * Asserting {@code DELAYED_ACQUISITION_AND_HOLD} would pass against a logger that hardcoded that string
 * and never touched the factory at all. A sentinel cannot: only a logger that actually reads the factory
 * can emit it. {@link ConnectionHandlingModeContractTest} is where the real value is pinned; this class
 * pins that the logger <b>derives</b> rather than <b>declares</b>.
 */
@ExtendWith(MockitoExtension.class)
class ConnectionLifetimeStartupLoggerTest {

    /** Deliberately not a real Hibernate mode — a hardcoded logger cannot produce it. */
    private static final String SENTINEL_HOLD_MODE = "SENTINEL_ACQUISITION_AND_HOLD";
    private static final String SENTINEL_RELEASE_MODE = "SENTINEL_RELEASE_AFTER_TRANSACTION";

    @Mock private EntityManagerFactory entityManagerFactory;
    @Mock private ObjectProvider<OpenEntityManagerInViewInterceptor> interceptorProvider;

    @Test
    void theHandlingModeIsReadFromTheEntityManagerFactoryRatherThanDeclaredInCode() {
        givenHandlingMode(SENTINEL_HOLD_MODE);
        givenOpenInView(true);

        assertThat(describe())
                .as("the mode must come from EntityManagerFactory.getProperties(). A logger that printed "
                        + "a constant would still read correctly in production today and would keep "
                        + "printing it after the real value moved.")
                .contains(HANDLING_MODE_PROPERTY + "=" + SENTINEL_HOLD_MODE);
    }

    /**
     * ⚠️ THE CONSEQUENCE SENTENCE IS THE HALF MOST LIKELY TO BE HARDCODED, because today only one branch
     * of it is ever true. Pinning both branches is what stops the line becoming a slogan.
     */
    @Test
    void theConsequenceFollowsTheModeThatWasActuallyRead() {
        givenHandlingMode(SENTINEL_RELEASE_MODE);
        givenOpenInView(true);

        assertThat(describe())
                .as("a non-holding mode returns the connection at transaction end even under "
                        + "open-in-view — the consequence must track the value read, not the fact that "
                        + "production currently holds")
                .contains("returned to the pool when its transaction ends")
                .doesNotContain("NOT until the transaction commits");
    }

    /**
     * ⚠️ OPEN-IN-VIEW IS READ FROM BEAN REGISTRATION, NOT FROM {@code spring.jpa.open-in-view}. The
     * property is unset in this application, so reading it would report a default nobody chose — the
     * substitution v0.112.0's anti-drift forbids. Bean presence is the request lifecycle itself.
     */
    @Test
    void openInViewIsReadFromInterceptorRegistrationRatherThanFromADefaultedProperty() {
        givenHandlingMode(SENTINEL_HOLD_MODE);
        givenOpenInView(false);

        assertThat(describe())
                .contains("open-in-view=OFF")
                .contains("OpenEntityManagerInViewInterceptor absent")
                .as("with the interceptor absent the connection still outlives the transaction under a "
                        + "holding mode — it is the EntityManager close that returns it, which is a "
                        + "different scope, not a different outcome")
                .contains("held until the EntityManager closes");
    }

    /**
     * ⚠️ AN ABSENT PROPERTY MUST READ AS ABSENT, NEVER AS A GUESS. If Hibernate ever stops exposing the
     * key, a logger that filled in a plausible default would print a confident falsehood into the one
     * line an operator would trust during an incident.
     */
    @Test
    void anUnreadableHandlingModeIsReportedAsAbsentRatherThanGuessed() {
        when(entityManagerFactory.getProperties()).thenReturn(Map.of());
        givenOpenInView(true);

        assertThat(describe()).contains(HANDLING_MODE_PROPERTY + "=<absent>");
    }

    private void givenHandlingMode(String mode) {
        when(entityManagerFactory.getProperties()).thenReturn(Map.of(HANDLING_MODE_PROPERTY, mode));
    }

    private void givenOpenInView(boolean registered) {
        when(interceptorProvider.getIfAvailable())
                .thenReturn(registered ? new OpenEntityManagerInViewInterceptor() : null);
    }

    private String describe() {
        return new ConnectionLifetimeStartupLogger(entityManagerFactory, interceptorProvider)
                .describeConnectionLifetime();
    }
}
