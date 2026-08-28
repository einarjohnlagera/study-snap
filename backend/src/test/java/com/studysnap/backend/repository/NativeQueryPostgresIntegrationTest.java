package com.studysnap.backend.repository;

import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.LinkedLearnerGrantScope;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.model.NoteLibraryReadiness;
import com.studysnap.backend.model.NoteListItemProjection;
import com.studysnap.backend.model.NoteLibrarySort;
import com.studysnap.backend.model.PublicLibrarySort;
import com.studysnap.backend.model.PublicLibrarySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the repository's native SQL against the production database engine and the full Flyway schema.
 *
 * <p>⚠️ Flyway is enabled only for this isolated test context. The shared test {@code application.yaml}
 * deliberately keeps Flyway disabled and H2 configured for the rest of the suite.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(NativeQueryPostgresIntegrationTest.DockerRequiredUnlessOptedOut.class)
class NativeQueryPostgresIntegrationTest {
    private static final String SKIP_PROPERTY = "nativequery.pg.skip";
    private static final String SKIP_FLAG = "-D" + SKIP_PROPERTY + "=true";
    private static final String CLASS_SUFFIX = ".class";
    private static final String SEARCH_PATTERN = "%heart%";
    /**
     * The exact count present today. ADDING a native query fails this until the number is raised —
     * deliberately, so the addition is noticed. A looser bound was tried first and rejected at the
     * v0.93.0 pressure test: at 25 against an actual 31, the reflective scan could silently degrade by
     * six queries and stay green, which is the same false comfort the harness exists to remove.
     */
    private static final int EXPECTED_NATIVE_QUERIES = 31;
    private static final String REPOSITORY_CLASSES =
            "classpath*:com/studysnap/backend/repository/**/*.class";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NoteLibraryRepositoryImpl noteLibraryRepository;

    @Autowired
    private PublicLibraryRepositoryImpl publicLibraryRepository;

    @Autowired
    private LinkedLearnerGrantRepository grantRepository;

    @Test
    void everyAnnotatedNativeQueryPreparesAgainstPostgres16() throws Exception {
        List<NativeQueryMethod> queries = findNativeQueries();

        // ⚠️ A LOWER BOUND, not an exact count. `isNotEmpty()` alone would stay green if the reflective
        // scan silently broke and found two queries instead of every one — which is precisely the false
        // comfort this harness exists to remove. The bound sits below the 31 present when it was written,
        // so ADDING a native query never needs an edit here; only a scan that collapses fails.
        assertThat(queries)
                .as("reflective scan over repository @Query(nativeQuery = true) methods")
                .hasSize(EXPECTED_NATIVE_QUERIES);
        for (int index = 0; index < queries.size(); index++) {
            prepare(queries.get(index), index);
        }
        System.out.printf("PREPARED %d repository-native queries against PostgreSQL 16.%n", queries.size());
    }

    /**
     * ⚠️ The ONLY test that executes the live-grant insert against real data.
     *
     * <p>A cold-agent pressure test mutated this statement two ways at once — {@code 'ACCEPTED'} to
     * {@code 'PENDING'} AND the {@code relationship.id = :relationshipId} predicate deleted, so the
     * insert matches any row in the table — and <strong>all 1,760 tests passed</strong>, because every
     * {@code LinkedLearnerGrantRepository} reference in the test tree is a Mockito mock. The
     * {@code PREPARE} sweep above did not help: it validates syntax, types and {@code ON CONFLICT}
     * arbiter resolution, never predicate correctness.
     *
     * <p>The second relationship is not padding — it is what kills the deleted-id-predicate mutant.
     * Without another ACCEPTED row present while this one is PENDING, an insert that stopped filtering
     * on the relationship id would still return 0 and the mutation would survive.
     */
    @Test
    void liveGrantInsertIsScopedToItsOwnRelationshipAndRequiresAccepted() {
        UUID supporter = seedUser("grant-supporter");
        UUID learner = seedUser("grant-learner");
        UUID pausedRelationship = seedRelationship(supporter, learner, "PENDING");
        seedRelationship(seedUser("other-supporter"), seedUser("other-learner"), "ACCEPTED");

        assertThat(insertGrant(pausedRelationship, learner, supporter))
                .as("insert against a PENDING relationship while a DIFFERENT relationship is ACCEPTED")
                .isZero();
        assertThat(liveGrants(pausedRelationship)).isZero();

        jdbcTemplate.update(
                "update linked_learner_relationships set status = 'ACCEPTED' where id = ?", pausedRelationship);

        assertThat(insertGrant(pausedRelationship, learner, supporter))
                .as("insert once this relationship is ACCEPTED")
                .isEqualTo(1);
        assertThat(liveGrants(pausedRelationship)).isEqualTo(1);

        assertThat(insertGrant(pausedRelationship, learner, supporter))
                .as("repeat insert is idempotent via the live-row partial unique index")
                .isZero();
        assertThat(liveGrants(pausedRelationship)).isEqualTo(1);

        jdbcTemplate.update(
                "update linked_learner_grants set revoked_at = now() where relationship_id = ?",
                pausedRelationship);
        assertThat(insertGrant(pausedRelationship, learner, supporter))
                .as("a revoked row does not block re-granting")
                .isEqualTo(1);
        assertThat(liveGrants(pausedRelationship)).isEqualTo(1);
    }

    private int insertGrant(UUID relationshipId, UUID fromUserId, UUID toUserId) {
        return grantRepository.insertLiveIfAbsent(
                UUID.randomUUID(),
                relationshipId,
                fromUserId,
                toUserId,
                LinkedLearnerGrantScope.PROGRESS.name(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private int liveGrants(UUID relationshipId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from linked_learner_grants where relationship_id = ? and revoked_at is null",
                Integer.class,
                relationshipId
        );
        return count == null ? 0 : count;
    }

    private UUID seedUser(String handle) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, email, username, password_hash, role, first_name, last_name,"
                        + " created_at, updated_at)"
                        + " values (?, ?, ?, 'x', 'USER', 'Test', 'User', now(), now())",
                id, handle + "-" + id + "@example.test", handle + "-" + id.toString().substring(0, 8)
        );
        return id;
    }

    private UUID seedRelationship(UUID supporterUserId, UUID learnerUserId, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into linked_learner_relationships"
                        + " (id, supporter_user_id, learner_user_id, status, initiated_by, created_at)"
                        + " values (?, ?, ?, ?, 'SUPPORTER', now())",
                id, supporterUserId, learnerUserId, status
        );
        return id;
    }

    @Test
    void postgresLibraryBranchesExecuteEveryFilter() {
        UUID ownerUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        List<String> tags = List.of("cardiology", "review");

        for (NoteLibraryReadiness readiness : NoteLibraryReadiness.values()) {
            NoteLibraryFilterCriteria criteria = new NoteLibraryFilterCriteria(
                    ownerUserId,
                    SEARCH_PATTERN,
                    readiness,
                    "Nursing",
                    tags,
                    NoteVisibility.PRIVATE
            );
            noteLibraryRepository.findLibraryPage(criteria, NoteLibrarySort.RECENTLY_UPDATED, 0, 10);
            noteLibraryRepository.countLibraryMatches(criteria);
            noteLibraryRepository.findLibraryCandidates(criteria);
            noteLibraryRepository.findLibrarySubjectCandidates(criteria);
            noteLibraryRepository.findLibrarySubjectIdCandidates(criteria);
            noteLibraryRepository.findLibraryMatchingIds(criteria, 10);
        }
        NoteLibraryFilterCriteria allOwned = new NoteLibraryFilterCriteria(
                ownerUserId, null, NoteLibraryReadiness.ALL, null, List.of(), null
        );
        for (NoteLibrarySort sort : NoteLibrarySort.values()) {
            if (sort != NoteLibrarySort.RECENTLY_REVIEWED) {
                noteLibraryRepository.findLibraryPage(allOwned, sort, 0, 10);
            }
        }
        noteLibraryRepository.findListItemProjectionsByOwnerUserId(ownerUserId, 10);
        noteLibraryRepository.findLibraryListItemProjectionsByOwnerUserIdAndIdIn(ownerUserId, List.of(otherUserId));
        noteLibraryRepository.findAllLibrarySubjectCandidates(ownerUserId);
        noteLibraryRepository.countLibraryCoursePrograms(ownerUserId);
        noteLibraryRepository.countLibraryTags(ownerUserId);
        noteLibraryRepository.existsOwnedNoteWithQuizQuestions(ownerUserId);
        noteLibraryRepository.findMostRecentlyUpdatedStudyPackReadyNoteId(ownerUserId);

        PublicLibraryFilterCriteria publicCriteria = new PublicLibraryFilterCriteria(
                ownerUserId,
                otherUserId,
                SEARCH_PATTERN,
                "cardiology",
                tags,
                "nursing",
                "supporter",
                LearnerLevel.JUNIOR_HIGH,
                true,
                List.of(PublicLibrarySource.BY_YOU, PublicLibrarySource.OFFICIAL, PublicLibrarySource.COMMUNITY)
        );
        publicLibraryRepository.findDistinctPublicTags();
        publicLibraryRepository.findPublicLibraryPage(publicCriteria, PublicLibrarySort.RECENT, 0, 10);
        publicLibraryRepository.findPublicLibraryPage(publicCriteria, PublicLibrarySort.TITLE, 0, 10);
        publicLibraryRepository.countPublicLibraryMatches(publicCriteria);
        publicLibraryRepository.findPublicLibraryCandidates(publicCriteria);
        publicLibraryRepository.findPublicLibraryListItemProjectionsByIdIn(List.of(ownerUserId));
    }

    /**
     * ⚠️ Assertions on the PostgreSQL-only tag filter, which is unreachable from the H2 suite.
     *
     * <p>{@code postgresLibraryBranchesExecuteEveryFilter} above is a SMOKE test: it proves the PG
     * branches parse and run, and asserts nothing about what they return. A cold-agent pressure test
     * showed that gap concretely — flipping {@code " in ("} to {@code " not in ("} in
     * {@code PublicLibraryRepositoryImpl.appendTagFilter}'s PostgreSQL branch inverts tag filtering on
     * the anonymous Explore surface, and the whole suite stayed green. This test seeds real rows so
     * that inversion fails.
     */
    @Test
    void postgresTagFilterSelectsMatchingPublicNotesOnly() {
        UUID author = seedUser("tag-author");
        UUID matching = seedPublicNote(author, "Cardiology basics", new String[] {"cardiology", "review"});
        seedPublicNote(author, "Unrelated pharmacology", new String[] {"pharmacology"});

        List<NoteListItemProjection> cardiology = publicLibraryRepository.findPublicLibraryPage(
                publicTagCriteria(List.of("cardiology")), PublicLibrarySort.RECENT, 0, 10);

        assertThat(cardiology).extracting(NoteListItemProjection::getId).containsExactly(matching);
        assertThat(publicLibraryRepository.countPublicLibraryMatches(publicTagCriteria(List.of("cardiology"))))
                .isEqualTo(1);
        assertThat(publicLibraryRepository.findPublicLibraryPage(
                publicTagCriteria(List.of("nephrology")), PublicLibrarySort.RECENT, 0, 10))
                .as("a tag no public note carries must match nothing")
                .isEmpty();
    }

    private PublicLibraryFilterCriteria publicTagCriteria(List<String> tagSlugs) {
        return new PublicLibraryFilterCriteria(
                null, null, null, null, tagSlugs, null, null, null, false,
                List.of(PublicLibrarySource.BY_YOU, PublicLibrarySource.OFFICIAL, PublicLibrarySource.COMMUNITY)
        );
    }

    private UUID seedPublicNote(UUID ownerUserId, String title, String[] tags) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into notes (id, owner_user_id, title, content, visibility, tags,"
                        + " target_profile_type, created_at, updated_at)"
                        + " values (?, ?, ?, 'body', ?, ?, 'STUDENT', now(), now())",
                id, ownerUserId, title, NoteVisibility.PUBLIC.name(), tags
        );
        return id;
    }

    private void prepare(NativeQueryMethod query, int index) {
        String statementName = "notelib_native_query_" + index;
        String sql = positionalParameters(query.sql());
        try {
            jdbcTemplate.execute("PREPARE " + statementName + " AS " + sql);
            jdbcTemplate.execute("DEALLOCATE " + statementName);
        } catch (RuntimeException failure) {
            throw new AssertionError(
                    "PostgreSQL could not PREPARE " + query.repository() + "." + query.method()
                            + ": " + rootMessage(failure),
                    failure
            );
        }
    }

    private List<NativeQueryMethod> findNativeQueries() throws IOException, ClassNotFoundException {
        List<NativeQueryMethod> queries = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        for (Resource resource : resolver.getResources(REPOSITORY_CLASSES)) {
            String className = className(resource);
            if (className.contains("$") || !className.startsWith("com.studysnap.backend.repository.")) {
                continue;
            }
            Class<?> repositoryType = Class.forName(className);
            for (Method method : repositoryType.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query != null && query.nativeQuery()) {
                    queries.add(new NativeQueryMethod(
                            repositoryType.getSimpleName(), method.getName(), query.value()
                    ));
                }
            }
        }
        queries.sort(Comparator.comparing(NativeQueryMethod::repository).thenComparing(NativeQueryMethod::method));
        return queries;
    }

    private String className(Resource resource) throws IOException {
        URI uri = resource.getURI();
        String path = uri.toString();
        int packageStart = path.lastIndexOf("com/studysnap/backend/repository/");
        if (packageStart < 0 || !path.endsWith(CLASS_SUFFIX)) {
            throw new IOException("Cannot resolve repository class name from " + path);
        }
        return path.substring(packageStart, path.length() - CLASS_SUFFIX.length()).replace('/', '.');
    }

    private String positionalParameters(String sql) {
        Map<String, Integer> positions = new LinkedHashMap<>();
        int occurrence = 0;
        StringBuilder translated = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            if (inLineComment) {
                translated.append(current);
                if (current == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                translated.append(current);
                if (current == '*' && next == '/') {
                    translated.append(next);
                    index++;
                    inBlockComment = false;
                }
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote && current == '-' && next == '-') {
                translated.append(current).append(next);
                index++;
                inLineComment = true;
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote && current == '/' && next == '*') {
                translated.append(current).append(next);
                index++;
                inBlockComment = true;
                continue;
            }
            if (!inDoubleQuote && current == '\'') {
                translated.append(current);
                if (inSingleQuote && next == '\'') {
                    translated.append(next);
                    index++;
                } else {
                    inSingleQuote = !inSingleQuote;
                }
                continue;
            }
            if (!inSingleQuote && current == '"') {
                translated.append(current);
                if (inDoubleQuote && next == '"') {
                    translated.append(next);
                    index++;
                } else {
                    inDoubleQuote = !inDoubleQuote;
                }
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote && current == ':' && next != ':'
                    && (index == 0 || sql.charAt(index - 1) != ':') && Character.isJavaIdentifierStart(next)) {
                int end = index + 2;
                while (end < sql.length() && Character.isJavaIdentifierPart(sql.charAt(end))) {
                    end++;
                }
                String name = sql.substring(index + 1, end);
                // ⚠️ ONE PLACEHOLDER PER OCCURRENCE, not per distinct name — this must mirror what
                // Hibernate actually emits, or the harness is weaker than the shape it is checking.
                // Verified against PostgreSQL 16: `... where c < $1 or $1 is null` PREPAREs, while the
                // production shape `... where c < $1 or $2 is null` fails with `could not determine data
                // type of parameter $2`. Collapsing repeats to a single `$n` therefore made an uncast
                // `(col < :p or :p is null)` pass here and 500 in production — the exact defect class
                // this harness exists to close. `NoteShareRepository.findSharedWithMe` repeats
                // `:cursorCreatedAt` three times and `LinkedLearnerGrantRepository.insertLiveIfAbsent`
                // repeats `:relationshipId`, so this is live, not theoretical.
                positions.merge(name, 1, Integer::sum);
                translated.append('$').append(++occurrence);
                index = end - 1;
                continue;
            }
            translated.append(current);
        }
        return translated.toString();
    }

    private String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    private record NativeQueryMethod(String repository, String method, String sql) {
    }

    static final class DockerRequiredUnlessOptedOut implements ExecutionCondition {
        private static final ConditionEvaluationResult ENABLED =
                ConditionEvaluationResult.enabled("PostgreSQL native-query verification is enabled");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (Boolean.getBoolean(SKIP_PROPERTY)) {
                System.err.printf(
                        "WARNING: %s was supplied; PostgreSQL 16 native queries and the full Flyway schema "
                                + "were NOT verified.%n",
                        SKIP_FLAG
                );
                return ConditionEvaluationResult.disabled("Explicitly opted out with " + SKIP_FLAG);
            }
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                throw new ExtensionConfigurationException(
                        "Docker is required for PostgreSQL 16 native-query verification. Start Docker or "
                                + "explicitly opt out with " + SKIP_FLAG + "."
                );
            }
            return ENABLED;
        }
    }
}
