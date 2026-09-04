package com.studysnap.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
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
 * The other half of Phase 2's evidence: what the candidate remedy actually does, measured rather than
 * argued.
 *
 * <p>⚠️ THIS CLASS DOES NOT SHIP THE OVERRIDE — it declares it locally, as a MEASUREMENT. Nothing in
 * {@code src/main/resources} sets {@code hibernate.connection.handling_mode}, and
 * {@link ConnectionHandlingModeContractTest} pins that the production default is still
 * {@code DELAYED_ACQUISITION_AND_HOLD}. Which remedy ships is an owner decision, and the release routes
 * an {@code open-in-view} change to a staging run rather than a direct production edit.
 *
 * <p><b>What it establishes.</b> A user-supplied {@code spring.jpa.properties.*} value DOES win over the
 * mode Spring's {@code HibernateJpaVendorAdapter:190-192} forces — the javadoc at {@code :101-103} claims
 * this and it is verified here rather than trusted — and under
 * {@code DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION} the connection is returned at COMMIT while the
 * {@code EntityManager} stays open. That is the property Phase 3 needs and does not have today.
 *
 * <p>⚠️ ESTABLISHING THAT IT WORKS IS NOT ESTABLISHING THAT IT IS SAFE, AND THE DIFFERENCE IS THE WHOLE
 * POINT OF §7. {@code open-in-view: false} has a KNOWN, named blast radius that the release already priced
 * in ({@code LazyInitializationException} during serialization). This override's blast radius is UNKNOWN,
 * and Spring's own javadoc advises pairing a mode override with {@code prepareConnection=false} — which is
 * NOT free here, because <b>124 methods use {@code @Transactional(readOnly = true)}</b>.
 * {@link #readOnlyTransactionsStillWorkUnderTheOverride()} is the narrow part of that question this test
 * can answer; it does not answer the rest. <b>Cheaper-looking is not the same as cheaper.</b>
 */
@SpringBootTest(properties =
        "spring.jpa.properties.hibernate.connection.handling_mode="
                + "DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION")
class ConnectionHandlingModeReleaseOverrideTest {

    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager sharedEntityManager;

    @Test
    void aUserSuppliedPropertyOverridesTheModeSpringForces() {
        assertThat(entityManagerFactory.getProperties().get("hibernate.connection.handling_mode"))
                .as("Spring's javadoc at HibernateJpaVendorAdapter:101-103 says a user-specified mode "
                        + "wins over the enforced HOLD — verified, not trusted")
                .hasToString("DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION");
    }

    /**
     * ⚠️ THE DISCRIMINATING COMPARISON. This is byte-for-byte the scenario
     * {@code ConnectionHandlingModeContractTest} measures at a delta of ONE; here it is ZERO. Two numbers
     * that differ is the confirmation — had they matched, the model behind Phase 3's remedy would be wrong
     * and its shape would still be open.
     */
    @Test
    void theConnectionIsReturnedAtCommitEvenThoughTheEntityManagerStaysOpen() {
        int baseline = checkedOutConnections();
        EntityManager em = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(em));
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    em.createNativeQuery("select 1").getSingleResult());

            assertThat(checkedOutConnections() - baseline)
                    .as("under RELEASE_AFTER_TRANSACTION the commit returns the connection, so slow work "
                            + "after it — an LLM call, or response serialization — no longer holds one")
                    .isZero();
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            em.close();
        }
    }

    /**
     * ⚠️ A NARROW SAFETY PROBE, AND ITS LIMITS ARE STATED SO IT IS NOT READ AS CLEARANCE. Spring applies
     * the read-only flag through {@code prepareConnection}, which its javadoc suggests disabling alongside
     * a mode override. This proves the 124 {@code readOnly = true} paths still execute under the override
     * with {@code prepareConnection} left ALONE. It does NOT prove the flag still reaches the JDBC
     * connection, and it does not exercise lazy loading during serialization.
     */
    @Test
    void readOnlyTransactionsStillWorkUnderTheOverride() {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        // The SHARED, transaction-aware EntityManager — a freshly created one would not join the
        // transaction under test and the assertion would pass without exercising it.
        Object result = readOnly.execute(status ->
                sharedEntityManager.createNativeQuery("select 1").getSingleResult());

        assertThat(result).isNotNull();
    }

    private int checkedOutConnections() {
        try {
            return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
        } catch (SQLException ex) {
            throw new IllegalStateException("could not read the Hikari pool", ex);
        }
    }
}
