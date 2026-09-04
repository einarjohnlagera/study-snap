package com.studysnap.backend.config;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.studysnap.backend.config.ConnectionLifetimeStartupLogger.HANDLING_MODE_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⚠️ THE SIBLING UNIT TEST PROVES THE LOGGER DERIVES ITS VALUES; THIS PROVES IT IS ACTUALLY WIRED.
 * Both are needed and neither substitutes for the other — a component that reads the factory perfectly
 * but is never registered emits nothing in production, and a registered component that prints a constant
 * emits something worse than nothing.
 *
 * <p>⚠️ IT ASSERTS AGAINST THE FACTORY, NOT AGAINST A LITERAL. Writing the expected mode into this file
 * would restate {@link ConnectionHandlingModeContractTest}'s pin in a second place, so a future change to
 * the real value would have to be made in two files and could be made in one.
 *
 * @see ConnectionLifetimeStartupLoggerTest the discriminating guard, with sentinel values
 */
@SpringBootTest
class ConnectionLifetimeStartupLoggerWiringTest {

    @Autowired private ConnectionLifetimeStartupLogger logger;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Test
    void theStartupLineCarriesTheRunningContextsOwnHandlingMode() {
        Object effectiveMode = entityManagerFactory.getProperties().get(HANDLING_MODE_PROPERTY);

        assertThat(logger.describeConnectionLifetime())
                .as("the component must be registered AND reading this context's factory — production is "
                        + "the only place the prod profile and datasource are ever exercised, so this "
                        + "line is the only read of the mode that is not taken against H2")
                .contains(HANDLING_MODE_PROPERTY + "=" + effectiveMode);
    }
}
