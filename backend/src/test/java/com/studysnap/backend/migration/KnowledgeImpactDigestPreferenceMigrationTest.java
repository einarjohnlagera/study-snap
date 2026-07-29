package com.studysnap.backend.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeImpactDigestPreferenceMigrationTest {
    private static final String COLUMN_NAME = "knowledge_impact_digest_reminders_enabled";

    @Test
    void migrationDefaultsExistingAndNewUsersToOptedOut() throws Exception {
        String databaseName = "knowledge-impact-migration-" + UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table users (id uuid primary key)");
                statement.execute("insert into users (id) values (random_uuid())");
                String migration = new ClassPathResource(
                        "db/migration/V99__add_knowledge_impact_digest_preference.sql"
                ).getContentAsString(StandardCharsets.UTF_8);
                statement.execute(migration);

                assertThat(readOnlyPreferenceValue(statement)).isFalse();
                statement.execute("insert into users (id) values (random_uuid())");
                assertThat(countOptedOutUsers(statement)).isEqualTo(2);
            }
        }
    }

    private boolean readOnlyPreferenceValue(Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("select " + COLUMN_NAME + " from users")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getBoolean(COLUMN_NAME);
        }
    }

    private int countOptedOutUsers(Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(
                "select count(*) from users where " + COLUMN_NAME + " = false"
        )) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }
}
