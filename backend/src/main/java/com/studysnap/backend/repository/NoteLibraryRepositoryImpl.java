package com.studysnap.backend.repository;

import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.model.NoteLibraryReadiness;
import com.studysnap.backend.model.NoteLibrarySort;
import com.studysnap.backend.model.NoteListItemProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import org.hibernate.Session;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NoteLibraryRepositoryImpl implements NoteLibraryRepository {
    private static final String POSTGRES_DATABASE_PRODUCT_NAME = "PostgreSQL";
    private static final String ID_ALIAS = "id";
    private static final String OWNER_USER_ID_ALIAS = "ownerUserId";
    private static final String TITLE_ALIAS = "title";
    private static final String COURSE_PROGRAM_ALIAS = "courseProgram";
    private static final String DOMAIN_CONTEXT_ALIAS = "domainContext";
    private static final String LEARNER_LEVEL_ALIAS = "learnerLevel";
    private static final String TARGET_PROFILE_TYPE_ALIAS = "targetProfileType";
    private static final String SUBJECT_ALIAS = "subject";
    private static final String TAGS_ALIAS = "tags";
    private static final String CONTENT_ALIAS = "content";
    private static final String STATUS_ALIAS = "status";
    private static final String VISIBILITY_ALIAS = "visibility";
    private static final String CREATED_AT_ALIAS = "createdAt";
    private static final String UPDATED_AT_ALIAS = "updatedAt";
    private static final String COPIED_FROM_NOTE_ID_ALIAS = "copiedFromNoteId";
    private static final String COPIED_FROM_PUBLIC_ALIAS = "copiedFromPublic";
    private static final String LIBRARY_VALUE_ALIAS = "libraryValue";
    private static final String LIBRARY_COUNT_ALIAS = "libraryCount";
    private static final String NOTE_LIST_ITEM_SELECT = """
            select n.id as id,
                   n.owner_user_id as "ownerUserId",
                   n.title as title,
                   n.course_program as "courseProgram",
                   n.domain_context as "domainContext",
                   n.learner_level as "learnerLevel",
                   n.target_profile_type as "targetProfileType",
                   n.subject as subject,
                   n.tags as tags,
                   substring(n.content, 1, 2000) as content,
                   n.status as status,
                   n.visibility as visibility,
                   n.created_at as "createdAt",
                   n.updated_at as "updatedAt",
                   n.copied_from_note_id as "copiedFromNoteId",
                   n.copied_from_public as "copiedFromPublic"
            """;
    private static final String CANDIDATE_SELECT = """
            select n.id as id,
                   n.title as title,
                   n.subject as subject,
                   n.course_program as "courseProgram",
                   n.domain_context as "domainContext",
                   n.learner_level as "learnerLevel",
                   n.created_at as "createdAt",
                   n.updated_at as "updatedAt"
            """;
    private static final String SUBJECT_SELECT = """
            select n.subject as subject,
                   n.course_program as "courseProgram"
            """;
    private static final String SUBJECT_ID_SELECT = """
            select n.id as id,
                   n.subject as subject,
                   n.course_program as "courseProgram"
            """;
    private static final String NOTES_FROM = " from notes n ";
    private static final String STUDY_PACK_READY_PREDICATE = """
            (
                n.status = 'GENERATED'
                or (
                    (n.status is null or n.status = 'DRAFT')
                    and exists (select 1 from study_packs sp where sp.note_id = n.id)
                )
            )
            """;

    private final EntityManager entityManager;
    private final SpelAwareProxyProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
    private Boolean postgres;

    public NoteLibraryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<NoteListItemProjection> findLibraryPage(
            NoteLibraryFilterCriteria criteria,
            NoteLibrarySort sort,
            int offset,
            int limit
    ) {
        FilterSql filter = buildFilter(criteria);
        Query query = createNativeQuery(NOTE_LIST_ITEM_SELECT + NOTES_FROM + filter.whereClause() + orderBy(sort));
        bind(query, filter.parameters());
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return tuples(query).stream().map(this::toListItemProjection).toList();
    }

    @Override
    public long countLibraryMatches(NoteLibraryFilterCriteria criteria) {
        FilterSql filter = buildFilter(criteria);
        Query query = entityManager.createNativeQuery("select count(*)" + NOTES_FROM + filter.whereClause());
        bind(query, filter.parameters());
        return ((Number) query.getSingleResult()).longValue();
    }

    @Override
    public List<NoteLibraryCandidateProjection> findLibraryCandidates(NoteLibraryFilterCriteria criteria) {
        FilterSql filter = buildFilter(criteria);
        Query query = createNativeQuery(CANDIDATE_SELECT + NOTES_FROM + filter.whereClause());
        bind(query, filter.parameters());
        return tuples(query).stream().map(this::toCandidate).toList();
    }

    @Override
    public List<NoteLibrarySubjectProjection> findLibrarySubjectCandidates(NoteLibraryFilterCriteria criteria) {
        FilterSql filter = buildFilter(criteria);
        Query query = createNativeQuery(SUBJECT_SELECT + NOTES_FROM + filter.whereClause());
        bind(query, filter.parameters());
        return tuples(query).stream().map(this::toSubjectProjection).toList();
    }

    @Override
    public List<NoteLibrarySubjectIdProjection> findLibrarySubjectIdCandidates(NoteLibraryFilterCriteria criteria) {
        FilterSql filter = buildFilter(criteria);
        Query query = createNativeQuery(SUBJECT_ID_SELECT + NOTES_FROM + filter.whereClause());
        bind(query, filter.parameters());
        return tuples(query).stream().map(this::toSubjectIdProjection).toList();
    }

    @Override
    public List<UUID> findLibraryMatchingIds(NoteLibraryFilterCriteria criteria, int limit) {
        FilterSql filter = buildFilter(criteria);
        Query query = entityManager.createNativeQuery(
                "select n.id" + NOTES_FROM + filter.whereClause() + " order by n.id"
        );
        bind(query, filter.parameters());
        query.setMaxResults(limit);
        return rawResults(query).stream().map(this::toUuid).toList();
    }

    @Override
    public List<NoteListItemProjection> findLibraryListItemProjectionsByOwnerUserIdAndIdIn(
            UUID ownerUserId,
            Collection<UUID> noteIds
    ) {
        if (noteIds == null || noteIds.isEmpty()) {
            return List.of();
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(OWNER_USER_ID_ALIAS, ownerUserId);
        String placeholders = bindValues("pageNoteId", noteIds, parameters);
        Query query = createNativeQuery(NOTE_LIST_ITEM_SELECT + NOTES_FROM + """
                 where n.owner_user_id = :ownerUserId
                   and n.id in (""" + placeholders + ")");
        bind(query, parameters);
        return tuples(query).stream().map(this::toListItemProjection).toList();
    }

    @Override
    public List<NoteLibrarySubjectProjection> findAllLibrarySubjectCandidates(UUID ownerUserId) {
        Query query = createNativeQuery(SUBJECT_SELECT + NOTES_FROM + " where n.owner_user_id = :ownerUserId");
        query.setParameter(OWNER_USER_ID_ALIAS, ownerUserId);
        return tuples(query).stream().map(this::toSubjectProjection).toList();
    }

    @Override
    public List<NoteLibraryValueCountProjection> countLibraryCoursePrograms(UUID ownerUserId) {
        Query query = createNativeQuery("""
                select n.course_program as "libraryValue", count(*) as "libraryCount"
                from notes n
                where n.owner_user_id = :ownerUserId
                  and n.course_program is not null
                  and trim(n.course_program) <> ''
                group by n.course_program
                order by count(*) desc, n.course_program asc
                """);
        query.setParameter(OWNER_USER_ID_ALIAS, ownerUserId);
        return tuples(query).stream()
                .map(tuple -> new NoteLibraryValueCountProjection(
                        stringValue(tuple, LIBRARY_VALUE_ALIAS),
                        numberValue(tuple, LIBRARY_COUNT_ALIAS).longValue()
                ))
                .toList();
    }

    @Override
    public List<NoteLibraryValueCountProjection> countLibraryTags(UUID ownerUserId) {
        if (!isPostgres()) {
            Query query = createNativeQuery("select n.tags as tags from notes n where n.owner_user_id = :ownerUserId");
            query.setParameter(OWNER_USER_ID_ALIAS, ownerUserId);
            Map<String, Long> counts = new LinkedHashMap<>();
            for (Tuple tuple : tuples(query)) {
                for (String tag : stringArray(tuple.get(TAGS_ALIAS))) {
                    if (tag != null && !tag.isBlank()) {
                        counts.merge(tag, 1L, Long::sum);
                    }
                }
            }
            return counts.entrySet().stream()
                    .map(entry -> new NoteLibraryValueCountProjection(entry.getKey(), entry.getValue()))
                    .toList();
        }
        Query query = createNativeQuery("""
                select library_tag.value as "libraryValue", count(*) as "libraryCount"
                from notes n
                cross join lateral unnest(n.tags) as library_tag(value)
                where n.owner_user_id = :ownerUserId
                  and library_tag.value is not null
                  and trim(library_tag.value) <> ''
                group by library_tag.value
                order by count(*) desc, library_tag.value asc
                """);
        query.setParameter(OWNER_USER_ID_ALIAS, ownerUserId);
        return tuples(query).stream()
                .map(tuple -> new NoteLibraryValueCountProjection(
                        stringValue(tuple, LIBRARY_VALUE_ALIAS),
                        numberValue(tuple, LIBRARY_COUNT_ALIAS).longValue()
                ))
                .toList();
    }

    @Override
    public boolean existsOwnedNoteWithQuizQuestions(UUID ownerUserId) {
        String quizCount = StudyPackQuizSqlExpressions.quizCount("sp", isPostgres());
        Query query = entityManager.createNativeQuery("""
                select n.id
                from notes n
                join study_packs sp on sp.note_id = n.id
                where n.owner_user_id = :ownerUserId
                  and (""" + quizCount + ") > 0");
        query.setParameter(OWNER_USER_ID_ALIAS, ownerUserId);
        query.setMaxResults(1);
        return !rawResults(query).isEmpty();
    }

    @Override
    public Optional<UUID> findMostRecentlyUpdatedStudyPackReadyNoteId(UUID ownerUserId) {
        Query query = entityManager.createNativeQuery("""
                select n.id
                from notes n
                where n.owner_user_id = :ownerUserId
                  and """ + STUDY_PACK_READY_PREDICATE + " order by n.updated_at desc");
        query.setParameter(OWNER_USER_ID_ALIAS, ownerUserId);
        query.setMaxResults(1);
        return rawResults(query).stream().findFirst().map(this::toUuid);
    }

    private FilterSql buildFilter(NoteLibraryFilterCriteria criteria) {
        StringBuilder where = new StringBuilder(" where n.owner_user_id = :ownerUserId");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(OWNER_USER_ID_ALIAS, criteria.ownerUserId());

        if (criteria.searchPattern() != null) {
            appendSearchFilter(where);
            parameters.put("searchPattern", criteria.searchPattern());
        }
        if (criteria.courseProgram() != null) {
            where.append(" and n.course_program = :courseProgram");
            parameters.put(COURSE_PROGRAM_ALIAS, criteria.courseProgram());
        }
        if (criteria.visibility() != null) {
            where.append(" and n.visibility = :visibility");
            parameters.put("visibility", criteria.visibility().name());
        }
        appendReadinessFilter(where, criteria.readiness());
        if (criteria.tags() != null && !criteria.tags().isEmpty()) {
            appendTagFilter(where, criteria.tags(), parameters);
        }
        return new FilterSql(where.toString(), parameters);
    }

    private void appendSearchFilter(StringBuilder where) {
        if (isPostgres()) {
            where.append("""
                     and (
                         lower(coalesce(n.title, '')) like :searchPattern escape '\\'
                         or exists (
                             select 1
                             from unnest(n.tags) as search_tag(value)
                             where lower(search_tag.value) like :searchPattern escape '\\'
                         )
                     )
                    """);
            return;
        }
        where.append("""
                 and (
                     lower(coalesce(n.title, '')) like :searchPattern escape '\\'
                     or lower(array_to_string(n.tags, '|||LIBRARY_TAG_BOUNDARY|||'))
                         like :searchPattern escape '\\'
                 )
                """);
    }

    private void appendReadinessFilter(StringBuilder where, NoteLibraryReadiness readiness) {
        switch (readiness) {
            case ALL -> {
                // No readiness predicate.
            }
            case DRAFT -> where.append("""
                     and (n.status is null or n.status = 'DRAFT')
                     and not exists (select 1 from study_packs sp where sp.note_id = n.id)
                    """);
            case STUDY_PACK_READY -> where.append(" and ").append(STUDY_PACK_READY_PREDICATE);
            case QUIZ_READY -> where.append("""
                     and exists (
                         select 1
                         from generated_quizzes gq
                         where gq.note_id = n.id
                           and gq.owner_user_id = :ownerUserId
                     )
                    """);
        }
    }

    private void appendTagFilter(
            StringBuilder where,
            List<String> tags,
            Map<String, Object> parameters
    ) {
        String placeholders = bindValues("filterTag", tags, parameters);
        if (isPostgres()) {
            where.append(" and n.tags && cast(array[").append(placeholders).append("] as text[])");
            return;
        }
        where.append(" and (");
        for (int index = 0; index < tags.size(); index++) {
            if (index > 0) {
                where.append(" or ");
            }
            where.append("array_contains(n.tags, :filterTag").append(index).append(")");
        }
        where.append(")");
    }

    private String orderBy(NoteLibrarySort sort) {
        return switch (sort) {
            case TITLE_ASC -> " order by coalesce(n.title, 'Untitled note') asc, n.updated_at desc, n.id asc";
            case TITLE_DESC -> " order by coalesce(n.title, 'Untitled note') desc, n.updated_at desc, n.id asc";
            case OLDEST -> " order by n.created_at asc, n.updated_at desc, n.id asc";
            case NEWEST -> " order by n.created_at desc, n.updated_at desc, n.id asc";
            case RECENTLY_UPDATED -> " order by n.updated_at desc, n.id asc";
            case RECENTLY_REVIEWED -> throw new IllegalArgumentException(
                    "RECENTLY_REVIEWED must use the materialized library path."
            );
        };
    }

    private NoteListItemProjection toListItemProjection(Tuple tuple) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(ID_ALIAS, toUuid(tuple.get(ID_ALIAS)));
        values.put(OWNER_USER_ID_ALIAS, toUuid(tuple.get(OWNER_USER_ID_ALIAS)));
        values.put(TITLE_ALIAS, stringValue(tuple, TITLE_ALIAS));
        values.put(COURSE_PROGRAM_ALIAS, stringValue(tuple, COURSE_PROGRAM_ALIAS));
        values.put(DOMAIN_CONTEXT_ALIAS, enumValue(
                DomainContext.class, stringValue(tuple, DOMAIN_CONTEXT_ALIAS)
        ));
        values.put(LEARNER_LEVEL_ALIAS, enumValue(
                LearnerLevel.class, stringValue(tuple, LEARNER_LEVEL_ALIAS)
        ));
        values.put(TARGET_PROFILE_TYPE_ALIAS, enumValue(
                NoteTargetProfileType.class, stringValue(tuple, TARGET_PROFILE_TYPE_ALIAS)
        ));
        values.put(SUBJECT_ALIAS, stringValue(tuple, SUBJECT_ALIAS));
        values.put(TAGS_ALIAS, stringArray(tuple.get(TAGS_ALIAS)));
        values.put(CONTENT_ALIAS, stringValue(tuple, CONTENT_ALIAS));
        values.put(STATUS_ALIAS, enumValue(NoteStatus.class, stringValue(tuple, STATUS_ALIAS)));
        values.put(VISIBILITY_ALIAS, enumValue(NoteVisibility.class, stringValue(tuple, VISIBILITY_ALIAS)));
        values.put(CREATED_AT_ALIAS, offsetDateTime(tuple.get(CREATED_AT_ALIAS)));
        values.put(UPDATED_AT_ALIAS, offsetDateTime(tuple.get(UPDATED_AT_ALIAS)));
        values.put(COPIED_FROM_NOTE_ID_ALIAS, nullableUuid(tuple.get(COPIED_FROM_NOTE_ID_ALIAS)));
        values.put(COPIED_FROM_PUBLIC_ALIAS, tuple.get(COPIED_FROM_PUBLIC_ALIAS));
        return projectionFactory.createProjection(NoteListItemProjection.class, values);
    }

    private NoteLibraryCandidateProjection toCandidate(Tuple tuple) {
        return new NoteLibraryCandidateProjection(
                toUuid(tuple.get(ID_ALIAS)),
                stringValue(tuple, TITLE_ALIAS),
                stringValue(tuple, SUBJECT_ALIAS),
                stringValue(tuple, COURSE_PROGRAM_ALIAS),
                enumValue(DomainContext.class, stringValue(tuple, DOMAIN_CONTEXT_ALIAS)),
                enumValue(LearnerLevel.class, stringValue(tuple, LEARNER_LEVEL_ALIAS)),
                offsetDateTime(tuple.get(CREATED_AT_ALIAS)),
                offsetDateTime(tuple.get(UPDATED_AT_ALIAS))
        );
    }

    private NoteLibrarySubjectProjection toSubjectProjection(Tuple tuple) {
        return new NoteLibrarySubjectProjection(
                stringValue(tuple, SUBJECT_ALIAS),
                stringValue(tuple, COURSE_PROGRAM_ALIAS)
        );
    }

    private NoteLibrarySubjectIdProjection toSubjectIdProjection(Tuple tuple) {
        return new NoteLibrarySubjectIdProjection(
                toUuid(tuple.get(ID_ALIAS)),
                stringValue(tuple, SUBJECT_ALIAS),
                stringValue(tuple, COURSE_PROGRAM_ALIAS)
        );
    }

    private Query createNativeQuery(String sql) {
        return entityManager.createNativeQuery(sql, Tuple.class);
    }

    @SuppressWarnings("unchecked")
    private List<Tuple> tuples(Query query) {
        return (List<Tuple>) query.getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Object> rawResults(Query query) {
        return (List<Object>) query.getResultList();
    }

    private void bind(Query query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
    }

    private String bindValues(String prefix, Collection<?> values, Map<String, Object> parameters) {
        List<String> placeholders = new ArrayList<>();
        int index = 0;
        for (Object value : values) {
            String parameterName = prefix + index++;
            placeholders.add(":" + parameterName);
            parameters.put(parameterName, value);
        }
        return String.join(", ", placeholders);
    }

    private boolean isPostgres() {
        if (postgres == null) {
            postgres = entityManager.unwrap(Session.class).doReturningWork(connection ->
                    POSTGRES_DATABASE_PRODUCT_NAME.equals(connection.getMetaData().getDatabaseProductName())
            );
        }
        return postgres;
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        return UUID.fromString(String.valueOf(value));
    }

    private UUID nullableUuid(Object value) {
        return value == null ? null : toUuid(value);
    }

    private String stringValue(Tuple tuple, String alias) {
        Object value = tuple.get(alias);
        return value == null ? null : String.valueOf(value);
    }

    private Number numberValue(Tuple tuple, String alias) {
        return (Number) tuple.get(alias);
    }

    private String[] stringArray(Object value) {
        if (value == null) {
            return new String[0];
        }
        if (value instanceof String[] strings) {
            return strings;
        }
        if (value instanceof Object[] values) {
            String[] strings = new String[values.length];
            for (int index = 0; index < values.length; index++) {
                strings[index] = String.valueOf(values[index]);
            }
            return strings;
        }
        if (value instanceof Array sqlArray) {
            try {
                return stringArray(sqlArray.getArray());
            } catch (SQLException exception) {
                throw new IllegalStateException("Could not read projected note tags.", exception);
            }
        }
        return new String[]{String.valueOf(value)};
    }

    private OffsetDateTime offsetDateTime(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private <T extends Enum<T>> T enumValue(Class<T> enumType, String value) {
        return value == null ? null : Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
    }

    private record FilterSql(String whereClause, Map<String, Object> parameters) {
    }
}
