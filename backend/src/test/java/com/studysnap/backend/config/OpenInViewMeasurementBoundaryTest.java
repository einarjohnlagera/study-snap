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
 * ⚠️ THIS TEST EXISTS TO RECORD A LIMIT OF THE MEASUREMENT, NOT TO EVALUATE A REMEDY. It pins that
 * {@code spring.jpa.open-in-view: false} <b>cannot be assessed by this harness</b>, so nobody runs the
 * delta measurement against it, gets a number, and compares that number to the other two.
 *
 * <p><b>Why it cannot.</b> {@code open-in-view} decides only whether
 * {@code OpenEntityManagerInViewInterceptor} binds an {@code EntityManager} for the duration of an HTTP
 * request. It changes <b>request lifecycle</b>, not <b>connection handling</b>. The sibling tests
 * measure the connection by binding an {@code EntityManager} by hand — which is precisely what that
 * interceptor does — so they simulate the OSIV-enabled case <b>by construction</b> and are blind to the
 * flag. This test asserts that blindness directly: with {@code open-in-view: false} the delta is still
 * ONE, identical to {@link ConnectionHandlingModeContractTest}.
 *
 * <p>⚠️ SO THE THREE OPTIONS ARE NOT ON EQUAL EVIDENTIAL FOOTING, AND THAT ASYMMETRY IS REAL RATHER THAN
 * AN OVERSIGHT. {@code HOLD} and {@code RELEASE_AFTER_TRANSACTION} have measured deltas because they are
 * connection-handling settings. {@code open-in-view: false} has none, because what it changes only
 * happens inside a real servlet request — and its blast radius, {@code LazyInitializationException}
 * during response serialization, is by definition reachable only when something serializes a lazy
 * association after the transaction closed. <b>That is why the release routes it to a STAGING RUN and
 * never to a direct production edit.</b> An option having no number here must not be read as it being
 * the weaker option.
 */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
class OpenInViewMeasurementBoundaryTest {

    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void turningOpenInViewOffDoesNotChangeWhenTheConnectionIsReleased() {
        int baseline = checkedOutConnections();
        EntityManager em = entityManagerFactory.createEntityManager();
        TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(em));
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    em.createNativeQuery("select 1").getSingleResult());

            assertThat(checkedOutConnections() - baseline)
                    .as("identical to the open-in-view=true case: the connection is held because the "
                            + "handling mode is HOLD, NOT because the interceptor is registered. This "
                            + "harness measures connection handling and cannot see the request lifecycle.")
                    .isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            em.close();
        }
    }

    private int checkedOutConnections() {
        try {
            return dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
        } catch (SQLException ex) {
            throw new IllegalStateException("could not read the Hikari pool", ex);
        }
    }
}
