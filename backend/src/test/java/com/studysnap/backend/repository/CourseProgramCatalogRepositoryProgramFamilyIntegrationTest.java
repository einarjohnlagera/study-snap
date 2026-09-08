package com.studysnap.backend.repository;

import com.studysnap.backend.dto.ProgramFamilyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Executes the three Program Family SQL statements {@code v0.133.0} added against a REAL database.
 *
 * <p>⚠️ THIS EXISTS BECAUSE NOTHING ELSE IN THE SUITE RUNS THIS SQL. Found by a cold agent at the
 * {@code v0.133.0} signoff: {@link CourseProgramCatalogRepository} is a plain {@code JdbcTemplate}
 * repository with hand-written SQL constants and NO {@code @Query(nativeQuery = true)} methods, and
 * {@code NativeQueryPostgresIntegrationTest} discovers its subjects by reflecting over {@code @Query}
 * annotations — so this entire class is structurally invisible to the PostgreSQL harness, despite
 * {@code CLAUDE.md} describing that harness as covering "every native query."
 *
 * <p>The service test mocks this repository and the controller test mocks the service, so before this
 * file the new statements were verified by INSPECTION ONLY. "A guard is only worth what it actually
 * executes."
 *
 * <p>⚠️ IT ALSO COVERS {@code mapProgramFamily}'s alias→getter mapping, which is the specific gap the
 * {@code v0.132.0} pressure test named: {@code PREPARE} validates syntax, types and arbiter
 * resolution, but never that {@code SELECT id, name} actually feeds {@code getObject("id", ...)}.
 * A renamed column or a swapped constructor argument is caught here and nowhere else.
 */
class CourseProgramCatalogRepositoryProgramFamilyIntegrationTest {

    private static final String EDUCATION = "Education";
    private static final String ENGINEERING = "Engineering";

    @Test
    void theProgramFamilyStatementsRunAgainstARealDatabaseAndRoundTrip() throws Exception {
        String url = "jdbc:h2:mem:program-family-repo-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {

            statement.execute("create table notes (id uuid primary key, course_program varchar(120))");
            statement.execute("create table users (id uuid primary key, course_program varchar(120))");
            applyMigration(statement, "V106__course_program_catalog.sql");
            applyMigration(statement, "V142__education_program_family.sql");

            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
            CourseProgramCatalogRepository repository =
                    new CourseProgramCatalogRepository(new JdbcTemplate(dataSource));

            // 1. FIND_ALL_PROGRAM_FAMILIES executes, orders by name, and maps BOTH columns.
            //    ⚠️ The id assertion is the point: it proves getObject("id", UUID.class) resolves the
            //    alias, which no PREPARE-style check can establish.
            List<ProgramFamilyResponse> families = repository.findAllProgramFamilies();
            assertThat(families).extracting(ProgramFamilyResponse::name)
                    .containsExactly(EDUCATION, ENGINEERING);
            assertThat(families).allSatisfy(family -> assertThat(family.id()).isNotNull());

            UUID educationId = families.getFirst().id();
            assertThat(single(statement, "select name from program_families where id = '" + educationId + "'"))
                    .as("the mapped id must address the row it was read from, not merely be non-null")
                    .isEqualTo(EDUCATION);

            // 2. FIND_PROGRAM_FAMILY_BY_NORMALIZED_NAME executes its lower(trim(...)) predicate.
            assertThat(repository.findProgramFamilyByNormalizedName("education"))
                    .isPresent()
                    .get()
                    .extracting(ProgramFamilyResponse::name)
                    .isEqualTo(EDUCATION);
            assertThat(repository.findProgramFamilyByNormalizedName("nursing")).isEmpty();

            // 3. INSERT_PROGRAM_FAMILY actually writes, and the row is readable afterwards.
            //    ⚠️ Read it back through the FINDER rather than trusting the returned object — the
            //    method builds its response in Java, so returning a correct value proves nothing
            //    about whether the INSERT reached the database.
            ProgramFamilyResponse created = repository.insertProgramFamily("Health Sciences");
            Optional<ProgramFamilyResponse> reloaded =
                    repository.findProgramFamilyByNormalizedName("health sciences");
            assertThat(reloaded).isPresent();
            assertThat(reloaded.get().id()).isEqualTo(created.id());
            assertThat(repository.findAllProgramFamilies()).hasSize(3);

            // 4. ⚠️ THE RACE BACKSTOP IS REAL, NOT ASSUMED. CourseProgramCatalogService catches
            //    DataIntegrityViolationException and resolves to the winner; that recovery is dead
            //    code unless uk_program_families_name actually rejects the duplicate.
            assertThatThrownBy(() -> repository.insertProgramFamily(EDUCATION))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    private void applyMigration(Statement statement, String file) throws Exception {
        String migration = new ClassPathResource("db/migration/" + file)
                .getContentAsString(StandardCharsets.UTF_8);
        statement.execute(stripInformationalPlPgSqlBlock(migration));
    }

    /**
     * V106 ends with an informational {@code DO $$} block that H2 cannot parse. It only emits
     * {@code RAISE NOTICE} deploy-log counts, so dropping it costs this test no coverage.
     */
    private String stripInformationalPlPgSqlBlock(String migration) {
        int blockStart = migration.indexOf("DO $$");
        return blockStart < 0 ? migration : migration.substring(0, blockStart);
    }

    private String single(Statement statement, String sql) throws Exception {
        try (var rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
