package com.studysnap.backend.repository;

import com.studysnap.backend.dto.ProgramFamilyResponse;
import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
import com.studysnap.backend.dto.CreateProgramFamilyRequest;
import com.studysnap.backend.dto.UpdateProgramFamilyRequest;
import com.studysnap.backend.exception.CourseProgramNotFoundException;
import com.studysnap.backend.service.CourseProgramCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@Testcontainers
class CourseProgramCatalogRepositoryProgramFamilyIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final String EDUCATION = "Education";
    private static final String ENGINEERING = "Engineering";

    @Test
    void updateIsActiveRunsAgainstPostgresAndPersistsFalse() throws Exception {
        UUID programId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("create table course_programs (id uuid primary key, is_active boolean not null default true)");
            statement.executeUpdate("insert into course_programs (id) values ('" + programId + "')");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            CourseProgramCatalogRepository repository = new CourseProgramCatalogRepository(jdbcTemplate);

            repository.updateIsActive(programId, false);

            assertThat(jdbcTemplate.queryForObject(
                    "select is_active from course_programs where id = ?", Boolean.class, programId)).isFalse();
        }
    }

    @Test
    void theProgramFamilyStatementsRunAgainstARealDatabaseAndRoundTrip() throws Exception {
        String url = "jdbc:h2:mem:program-family-repo-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {

            statement.execute("create table notes (id uuid primary key, course_program varchar(120))");
            statement.execute("create table users (id uuid primary key, course_program varchar(120))");
            applyMigration(statement, "V106__course_program_catalog.sql");
            applyMigration(statement, "V142__education_program_family.sql");
            applyMigration(statement, "V145__course_program_is_active.sql");
            applyMigration(statement, "V146__course_program_family_membership.sql");

            // H2 cannot execute V146's PL/pgSQL DO block. This independently executes the same
            // discriminating NOT EXISTS predicate and proves its relationship-parity SQL, not that
            // DO $$ ... RAISE EXCEPTION itself executes. Flyway/PostgreSQL supplies that deploy guard.
            String parityQuery = """
                    SELECT count(*) FROM course_programs
                    WHERE course_programs.program_family_id IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM course_program_family
                          WHERE course_program_family.course_program_id = course_programs.id
                            AND course_program_family.program_family_id = course_programs.program_family_id
                      )
                    """;
            assertThat(count(statement, parityQuery)).isZero();
            assertThat(count(statement, """
                    select count(*) from course_program_family
                    join program_families on program_families.id = course_program_family.program_family_id
                    where program_families.name = 'Education'
                    """)).isEqualTo(8);
            UUID corruptedProgramId = UUID.fromString("20000000-0000-0000-0000-000000000001");
            UUID engineeringId = UUID.fromString("10000000-0000-0000-0000-000000000001");
            statement.executeUpdate("update course_program_family set program_family_id = '" + engineeringId
                    + "' where course_program_id = '" + corruptedProgramId + "'");
            assertThat(count(statement, parityQuery)).isEqualTo(1);
            statement.executeUpdate("update course_program_family set program_family_id = '" + UUID.fromString("10000000-0000-0000-0000-000000000002")
                    + "' where course_program_id = '" + corruptedProgramId + "'");

            assertThatThrownBy(() -> statement.executeUpdate("""
                    insert into course_program_family (id, course_program_id, program_family_id)
                    values (random_uuid(), '20000000-0000-0000-0000-000000000001',
                            '10000000-0000-0000-0000-000000000002')
                    """)).isInstanceOf(Exception.class);

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

            // The Java lookup key collapses internal whitespace. The SQL predicate must do the same,
            // or a doubled-space stored name escapes the duplicate check that calls this finder.
            ProgramFamilyResponse doubledSpace = repository.insertProgramFamily("Allied  Health");
            assertThat(repository.findProgramFamilyByNormalizedName("allied health"))
                    .isPresent()
                    .get()
                    .extracting(ProgramFamilyResponse::id)
                    .isEqualTo(doubledSpace.id());

            // 5. FIND_BY_ID executes the same joined mapper used by the catalog list, including the
            // lifecycle column. A missing WHERE predicate would make the second assertion fail.
            UUID programId = repository.findAll().stream()
                    .filter(program -> program.name().equals("Civil Engineering"))
                    .map(CourseProgramCatalogItemResponse::id)
                    .findFirst()
                    .orElseThrow();
            assertThat(repository.findById(programId))
                    .isPresent()
                    .get()
                    .satisfies(program -> assertThat(program.isActive()).isTrue());
            assertThat(repository.findById(UUID.randomUUID())).isEmpty();

            // The service's @Transactional create path uses these two repository operations. Prove
            // the database boundary removes the program when the membership FK write fails.
            UUID invalidFamilyId = UUID.randomUUID();
            assertThatThrownBy(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                    .executeWithoutResult(status -> {
                        UUID inserted = repository.insert("Rollback Program", null);
                        repository.insertProgramFamilies(inserted, List.of(invalidFamilyId));
                    })).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(repository.findByNormalizedName("rollback program")).isEmpty();

            // The shared catalog list deliberately includes retired rows. Persist a real false value
            // so this fails if FIND_ALL starts filtering or mapCatalogItem hard-codes the default.
            statement.executeUpdate("update course_programs set is_active = false where id = '" + programId + "'");
            assertThat(repository.findAll())
                    .filteredOn(program -> program.id().equals(programId))
                    .singleElement()
                    .satisfies(program -> assertThat(program.isActive()).isFalse());
            assertThat(repository.findById(programId))
                    .isPresent()
                    .get()
                    .satisfies(program -> assertThat(program.isActive()).isFalse());

            // 6. Authoritative replacement persists multiple memberships and clearing.
            repository.replaceProgramFamilies(programId, List.of(educationId, engineeringId));
            assertThat(repository.findById(programId))
                    .isPresent()
                    .get()
                    .satisfies(program -> {
                        assertThat(program.programFamilies()).extracting(ProgramFamilyResponse::name)
                                .containsExactly(EDUCATION, ENGINEERING);
                        assertThat(program.programFamilyId()).isEqualTo(educationId);
                    });
            repository.replaceProgramFamilies(programId, List.of());
            assertThat(repository.findById(programId))
                    .isPresent()
                    .get()
                    .satisfies(program -> assertThat(program.programFamilyId()).isNull());

            // 7. Family-side replacement deletes by family id. Civil already belongs to Engineering;
            // adding it to Education must retain that first membership.
            repository.replaceProgramMemberships(educationId, List.of(programId));
            assertThat(repository.findById(programId)).isPresent().get().satisfies(program ->
                    assertThat(program.programFamilies()).extracting(ProgramFamilyResponse::name)
                            .containsExactly(EDUCATION));
            repository.replaceProgramMemberships(engineeringId, List.of(programId));
            repository.replaceProgramMemberships(educationId, List.of(programId));
            assertThat(repository.findById(programId)).isPresent().get().satisfies(program ->
                    assertThat(program.programFamilies()).extracting(ProgramFamilyResponse::name)
                            .containsExactly(EDUCATION, ENGINEERING));
            repository.replaceProgramMemberships(educationId, List.of());
            assertThat(repository.findById(programId)).isPresent().get().satisfies(program ->
                    assertThat(program.programFamilies()).extracting(ProgramFamilyResponse::name)
                            .containsExactly(ENGINEERING));

            // 8. Create-with-members rolls the family row back when a program id is unknown.
            CourseProgramCatalogService service = new CourseProgramCatalogService(repository);
            assertThatThrownBy(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                    .executeWithoutResult(status -> service.createProgramFamily(
                            new CreateProgramFamilyRequest("Rollback Family", List.of(UUID.randomUUID())))))
                    .isInstanceOf(CourseProgramNotFoundException.class);
            assertThat(repository.findProgramFamilyByNormalizedName("rollback family")).isEmpty();

            // 9. Rename preserves UUID and memberships and cannot touch note applicability: Notes
            // persist program ids only, with no family identifier or name.
            statement.execute("create table note_course_program (id uuid primary key, note_id uuid not null, course_program_id uuid not null)");
            UUID noteId = UUID.randomUUID();
            statement.executeUpdate("insert into notes (id, course_program) values ('" + noteId + "', null)");
            UUID applicabilityId = UUID.randomUUID();
            statement.executeUpdate("insert into note_course_program (id, note_id, course_program_id) values ('"
                    + applicabilityId + "', '" + noteId + "', '" + programId + "')");
            ProgramFamilyResponse renamed = service.updateProgramFamily(engineeringId,
                    new UpdateProgramFamilyRequest("Engineering Sciences", null));
            assertThat(renamed.id()).isEqualTo(engineeringId);
            assertThat(repository.findProgramFamilyById(engineeringId)).get()
                    .extracting(ProgramFamilyResponse::name).isEqualTo("Engineering Sciences");
            assertThat(repository.findById(programId)).get().satisfies(program ->
                    assertThat(program.programFamilies()).extracting(ProgramFamilyResponse::name)
                            .containsExactly("Engineering Sciences"));
            assertThat(single(statement, "select id from note_course_program where note_id = '" + noteId + "'"))
                    .isEqualTo(applicabilityId.toString());
        }
    }

    private void applyMigration(Statement statement, String file) throws Exception {
        String migration = new ClassPathResource("db/migration/" + file)
                .getContentAsString(StandardCharsets.UTF_8);
        if (file.startsWith("V146__")) {
            // H2 2.4 accepts gen_random_uuid() in PostgreSQL mode but not INSERT ... ON CONFLICT.
            // Keep the shipped PostgreSQL migration unchanged; only this clean, local test copy drops
            // the idempotency clause that H2 cannot parse.
            migration = migration.replace(
                    "ON CONFLICT (course_program_id, program_family_id) DO NOTHING;", ";");
        }
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

    private int count(Statement statement, String sql) throws Exception {
        try (var rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
