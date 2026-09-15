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

class CourseProgramActiveMigrationTest {

    @Test
    void v145AddsTheLifecycleColumnWithoutChangingOrRetiringExistingRows() throws Exception {
        String url = "jdbc:h2:mem:course-program-active-" + UUID.randomUUID() + ";MODE=PostgreSQL";
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.execute("create table notes (id uuid primary key, course_program varchar(120))");
            statement.execute("create table users (id uuid primary key, course_program varchar(120))");
            applyMigration(statement, "V106__course_program_catalog.sql");
            statement.execute("""
                    insert into course_programs (id, name)
                    values (random_uuid(), 'Nursing · Medicine'),
                           (random_uuid(), 'Nursing · Pharmacy')
                    """);
            int rowCountBefore = count(statement, "select count(*) from course_programs");

            applyMigration(statement, "V145__course_program_is_active.sql");

            assertThat(count(statement, "select count(*) from course_programs")).isEqualTo(rowCountBefore);
            assertThat(count(statement, "select count(*) from course_programs where is_active = true"))
                    .isEqualTo(rowCountBefore);
            assertThat(count(statement, "select count(*) from course_programs where is_active = false"))
                    .isZero();
            assertThat(count(statement, """
                    select count(*) from course_programs
                    where name in ('Nursing · Medicine', 'Nursing · Pharmacy')
                      and is_active = true
                    """))
                    .as("the fused rows remain active until the separately gated production step")
                    .isEqualTo(2);
        }
    }

    private void applyMigration(Statement statement, String file) throws Exception {
        String migration = new ClassPathResource("db/migration/" + file)
                .getContentAsString(StandardCharsets.UTF_8);
        int blockStart = migration.indexOf("DO $$");
        statement.execute(blockStart < 0 ? migration : migration.substring(0, blockStart));
    }

    private int count(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
