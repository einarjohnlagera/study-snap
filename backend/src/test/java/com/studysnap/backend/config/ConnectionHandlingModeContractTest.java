package com.studysnap.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⚠️ PHASE 2's EVIDENCE, AND IT CONFIRMS §7's HYPOTHESIS RATHER THAN REFUTING IT. This is the test that
 * decides whether Phase 3's transaction-boundary refactor can work at all.
 *
 * <p><b>What was measured.</b> Two independent reads, in the order the release specifies — the setting
 * first, the behaviour second, because reasoning from the first to the second is exactly the step §7
 * warns produces a wrong fix:
 *
 * <ol>
 *   <li><b>PRIMARY — the setting.</b> The effective {@code hibernate.connection.handling_mode} is
 *       {@code DELAYED_ACQUISITION_AND_HOLD}. It is NOT a Hibernate default: Spring's
 *       {@code HibernateJpaVendorAdapter} sets it unconditionally at {@code :190-192} whenever
 *       {@code prepareConnection} is true and the persistence unit is non-JTA, which is this
 *       application. It is therefore not dialect-specific and not an artefact of the H2 test profile.</li>
 *   <li><b>CONFIRMING — the behaviour.</b> {@link #connectionStaysCheckedOutAfterTheTransactionCommits()}
 *       measures Hikari's checked-out count directly, and the connection is still held after commit.</li>
 * </ol>
 *
 * <p><b>Why it matters.</b> {@code spring.jpa.open-in-view} is unset and defaults to {@code true}, so the
 * {@code EntityManager} is bound for the whole HTTP request. Combined with {@code HOLD}, the connection is
 * acquired on first database access and held until the request ends — <b>not</b> until the transaction
 * ends. <b>So moving the LLM call outside {@code @Transactional} releases the TRANSACTION while the
 * CONNECTION stays bound, and Phase 3 could land, look correct, and not fix the exhaustion.</b>
 *
 * <p>⚠️ THE ASSERTION IS ON A DELTA, NOT AN ABSOLUTE COUNT. {@code getActiveConnections()} is pool-wide,
 * so an absolute assertion would pass or fail on whatever else the shared context happens to be holding —
 * a fixture that cannot discriminate, which is the failure mode this repo has recorded repeatedly.
 *
 * @see ConnectionHandlingModeReleaseOverrideTest the same measurement with the mode overridden
 */
@SpringBootTest
class ConnectionHandlingModeContractTest {

    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;

    /**
     * ⚠️ PINS THE SETTING SO A SPRING UPGRADE CANNOT CHANGE IT SILENTLY. This value comes from Spring, not
     * from Hibernate, so a future {@code HibernateJpaVendorAdapter} that stops forcing it would flip the
     * behaviour of every request in the application with nothing in the build to notice.
     */
    @Test
    void theEffectiveConnectionHandlingModeIsHoldUntilTheSessionCloses() {
        assertThat(entityManagerFactory.getProperties().get("hibernate.connection.handling_mode"))
                .as("Spring's HibernateJpaVendorAdapter:190-192 forces this for non-JTA units with "
                        + "prepareConnection=true. If this value changes, Phase 3's premises change with it.")
                .hasToString("DELAYED_ACQUISITION_AND_HOLD");
    }

    /**
     * ⚠️ THE MEASUREMENT THAT SETTLES §7 — and it is deliberately NOT an argument from Spring Boot
     * defaults, which the release forbids substituting for either read.
     *
     * <p>It reproduces what {@code OpenEntityManagerInViewInterceptor} does on every request: bind an
     * {@code EntityManager} for a scope wider than the transaction. The transaction then commits, and the
     * connection is STILL checked out — which is the whole finding.
     */
    @Test
    void connectionStaysCheckedOutAfterTheTransactionCommits() {
        int baseline = checkedOutConnections();
        EntityManager em = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(em));
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    em.createNativeQuery("select 1").getSingleResult());

            assertThat(checkedOutConnections() - baseline)
                    .as("the transaction has COMMITTED and the EntityManager is still open, exactly as it "
                            + "would be mid-request under open-in-view. A connection still held here is "
                            + "why relocating the LLM call cannot fix the exhaustion on its own.")
                    .isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            em.close();
        }

        assertThat(checkedOutConnections())
                .as("closing the EntityManager — the end of the request under open-in-view — is what "
                        + "actually returns the connection")
                .isEqualTo(baseline);
    }

    private int checkedOutConnections() {
        try {
            return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
        } catch (SQLException ex) {
            throw new IllegalStateException("could not read the Hikari pool", ex);
        }
    }
}
