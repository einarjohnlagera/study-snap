package com.studysnap.backend.repository;

import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.model.NoteListItemProjection;
import com.studysnap.backend.model.PublicLibrarySort;
import com.studysnap.backend.model.PublicLibrarySource;
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
import java.util.Objects;
import java.util.UUID;

@Repository
public class PublicLibraryRepositoryImpl implements PublicLibraryRepository {
    private static final String POSTGRES_DATABASE_PRODUCT_NAME = "PostgreSQL";
    private static final String OFFICIAL_AUTHOR_EMAIL = "einar.lagera@gmail.com";
    private static final String ID_ALIAS = "id";
    private static final String OWNER_USER_ID_ALIAS = "ownerUserId";
    private static final String TITLE_ALIAS = "title";
    private static final String COURSE_PROGRAM_ALIAS = "courseProgram";
    private static final String APPLICABLE_PROGRAMS_ALIAS = "applicablePrograms";
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
    private static final String HAS_STUDY_PACK_ALIAS = "hasStudyPack";
    private static final String HAS_CONTENT_ALIAS = "hasContent";
    private static final String HAS_SUMMARY_ALIAS = "hasSummary";
    private static final String QUIZ_COUNT_ALIAS = "quizCount";
    private static final String RANKED_SORT_PATH_REQUIRED_MESSAGE =
            "Ranked public-library sorts must use the materialized path.";
    private static final String NOTES_FROM = " from notes n ";
    private static final String NOTE_LIST_ITEMS_FROM = """
             from notes n
             left join (
                 select ncp.note_id, array_agg(cp.name order by cp.name) as program_names
                 from note_course_program ncp
                 join course_programs cp on cp.id = ncp.course_program_id
                 join notes program_note on program_note.id = ncp.note_id
                 where program_note.visibility = 'PUBLIC'
                 group by ncp.note_id
             ) note_programs on note_programs.note_id = n.id
            """;
    private static final String NOTE_LIST_ITEM_SELECT = """
            select n.id as id,
                   n.owner_user_id as "ownerUserId",
                   n.title as title,
                   n.course_program as "courseProgram",
                   note_programs.program_names as "applicablePrograms",
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

    private final EntityManager entityManager;
    private final SpelAwareProxyProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
    private Boolean postgres;

    public PublicLibraryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<String> findDistinctPublicTags() {
        if (isPostgres()) {
            @SuppressWarnings("unchecked")
            List<Object> values = entityManager.createNativeQuery("""
                    select distinct public_tag.value
                    from notes n
                    cross join lateral unnest(n.tags) as public_tag(value)
                    where n.visibility = 'PUBLIC'
                    """).getResultList();
            return values.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .toList();
        }

        @SuppressWarnings("unchecked")
        List<Object> tagArrays = entityManager.createNativeQuery("""
                select n.tags
                from notes n
                where n.visibility = 'PUBLIC'
                """).getResultList();
        return tagArrays.stream()
                .flatMap(value -> java.util.Arrays.stream(stringArray(value)))
                .toList();
    }

    @Override
    public List<NoteListItemProjection> findPublicLibraryPage(
            PublicLibraryFilterCriteria criteria,
            PublicLibrarySort sort,
            int offset,
            int limit
    ) {
        if (!sort.isSqlOrderable()) {
            throw new IllegalArgumentException(RANKED_SORT_PATH_REQUIRED_MESSAGE);
        }
        FilterSql filter = buildFilter(criteria);
        Query query = createNativeQuery(
                NOTE_LIST_ITEM_SELECT + NOTE_LIST_ITEMS_FROM + filter.whereClause() + orderBy(sort)
        );
        bind(query, filter.parameters());
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return tuples(query).stream().map(this::toListItemProjection).toList();
    }

    @Override
    public long countPublicLibraryMatches(PublicLibraryFilterCriteria criteria) {
        FilterSql filter = buildFilter(criteria);
        Query query = entityManager.createNativeQuery("select count(*)" + NOTES_FROM + filter.whereClause());
        bind(query, filter.parameters());
        return ((Number) query.getSingleResult()).longValue();
    }

    @Override
    public List<PublicLibraryCandidateProjection> findPublicLibraryCandidates(
            PublicLibraryFilterCriteria criteria
    ) {
        FilterSql filter = buildFilter(criteria);
        String quizCount = StudyPackQuizSqlExpressions.quizCount("sp", isPostgres());
        String candidateSelect = """
                select n.id as id,
                       n.owner_user_id as "ownerUserId",
                       n.created_at as "createdAt",
                       n.updated_at as "updatedAt",
                       n.status as status,
                       case when sp.id is null then false else true end as "hasStudyPack",
                       case when n.content is not null and trim(n.content) <> '' then true else false end as "hasContent",
                       case when sp.summary is not null and trim(sp.summary) <> '' then true else false end as "hasSummary",
                       %s as "quizCount"
                from notes n
                left join study_packs sp on sp.note_id = n.id
                """.formatted(quizCount);
        Query query = createNativeQuery(candidateSelect + filter.whereClause() + " order by n.id");
        bind(query, filter.parameters());
        return tuples(query).stream().map(this::toCandidate).toList();
    }

    @Override
    public List<NoteListItemProjection> findPublicLibraryListItemProjectionsByIdIn(Collection<UUID> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return List.of();
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        String placeholders = bindValues("publicPageNoteId", noteIds, parameters);
        Query query = createNativeQuery(NOTE_LIST_ITEM_SELECT + NOTE_LIST_ITEMS_FROM
                + " where n.visibility = 'PUBLIC' and n.id in (" + placeholders + ")");
        bind(query, parameters);
        return tuples(query).stream().map(this::toListItemProjection).toList();
    }

    private FilterSql buildFilter(PublicLibraryFilterCriteria criteria) {
        StringBuilder where = new StringBuilder(" where n.visibility = 'PUBLIC'");
        Map<String, Object> parameters = new LinkedHashMap<>();

        if (criteria.creator() != null) {
            where.append("""
                     and exists (
                         select 1 from users creator_user
                         where creator_user.id = n.owner_user_id
                           and lower(creator_user.username) = lower(:creator)
                     )
                    """);
            parameters.put("creator", criteria.creator());
        }
        if (criteria.searchPattern() != null) {
            appendSearchFilter(where);
            parameters.put("searchPattern", criteria.searchPattern());
        }
        if (criteria.subjectSlug() != null) {
            where.append(" and ").append(normalizedSlugSql("n.subject")).append(" = :subjectSlug");
            parameters.put("subjectSlug", criteria.subjectSlug());
        }
        if (criteria.courseProgramSlug() != null) {
            where.append("\n and (exists (select 1 from note_course_program ncp ")
                    .append("join course_programs cp on cp.id = ncp.course_program_id ")
                    .append("where ncp.note_id = n.id and ")
                    .append(normalizedSlugSql("cp.name"))
                    .append(" = :courseProgramSlug) or (")
                    .append("not exists (select 1 from note_course_program ncp where ncp.note_id = n.id) ")
                    .append("and ")
                    .append(normalizedSlugSql("n.course_program"))
                    .append(" = :courseProgramSlug))");
            parameters.put("courseProgramSlug", criteria.courseProgramSlug());
        }
        if (criteria.tagSlugs() != null && !criteria.tagSlugs().isEmpty()) {
            appendTagFilter(where, criteria.tagSlugs(), parameters);
        }
        if (criteria.readyOnly()) {
            where.append("""
                     and (
                         n.status = 'GENERATED'
                         or (
                             (n.status is null or n.status = 'DRAFT')
                             and exists (select 1 from study_packs ready_sp where ready_sp.note_id = n.id)
                         )
                     )
                    """);
        }
        appendSourceFilter(where, criteria, parameters);
        return new FilterSql(where.toString(), parameters);
    }

    private void appendSearchFilter(StringBuilder where) {
        String tagsSearch = isPostgres()
                ? """
                    exists (
                        select 1 from unnest(n.tags) as public_search_tag(value)
                        where lower(public_search_tag.value) like :searchPattern escape '\\'
                    )
                    """
                : "lower(array_to_string(n.tags, '|||PUBLIC_TAG_BOUNDARY|||')) like :searchPattern escape '\\'";
        String contentPreview = contentPreviewSearchSql("n.content");
        String summaryPreview = summaryPreviewSearchSql("search_sp.summary");
        where.append("""
                 and (
                     lower(coalesce(n.title, '')) like :searchPattern escape '\\'
                     or lower(coalesce(n.subject, '')) like :searchPattern escape '\\'
                     or exists (
                         select 1
                         from note_course_program search_ncp
                         join course_programs search_cp on search_cp.id = search_ncp.course_program_id
                         where search_ncp.note_id = n.id
                           and lower(search_cp.name) like :searchPattern escape '\\'
                     )
                     or (
                         not exists (
                             select 1 from note_course_program search_ncp where search_ncp.note_id = n.id
                         )
                         and lower(coalesce(n.course_program, '')) like :searchPattern escape '\\'
                     )
                     or %s like :searchPattern escape '\\'
                     or exists (
                         select 1 from study_packs search_sp
                         where search_sp.note_id = n.id
                           and %s like :searchPattern escape '\\'
                     )
                     or """.formatted(contentPreview, summaryPreview))
                .append(" ").append(tagsSearch).append("\n)");
    }

    private String contentPreviewSearchSql(String expression) {
        String normalized = isPostgres()
                ? "trim(regexp_replace(coalesce(" + expression + ", ''), '\\s+', ' ', 'g'))"
                : "trim(regexp_replace(coalesce(" + expression + ", ''), '\\s+', ' '))";
        return "lower(case when char_length(" + normalized + ") <= 180 then " + normalized
                + " else substring(" + normalized + ", 1, 177) || '...' end)";
    }

    private String summaryPreviewSearchSql(String expression) {
        return "lower(trim(substring(coalesce(" + expression + ", ''), 1, 180)))";
    }

    private void appendTagFilter(
            StringBuilder where,
            List<String> tagSlugs,
            Map<String, Object> parameters
    ) {
        if (isPostgres()) {
            String placeholders = bindValues("publicTagSlug", tagSlugs, parameters);
            where.append("""
                     and exists (
                         select 1 from unnest(n.tags) as public_filter_tag(value)
                         where """)
                    .append(normalizedSlugSql("public_filter_tag.value"))
                    .append(" in (").append(placeholders).append(")\n)");
            return;
        }
        where.append(" and (");
        for (int index = 0; index < tagSlugs.size(); index++) {
            if (index > 0) {
                where.append(" or ");
            }
            String parameter = "publicTagSlug" + index;
            where.append("array_contains(n.tags, :").append(parameter).append(")");
            parameters.put(parameter, tagSlugs.get(index));
        }
        where.append(")");
    }

    private void appendSourceFilter(
            StringBuilder where,
            PublicLibraryFilterCriteria criteria,
            Map<String, Object> parameters
    ) {
        if (criteria.sources() == null || criteria.sources().isEmpty()) {
            return;
        }
        boolean needsOfficialPredicate = criteria.sources().stream()
                .anyMatch(source -> source == PublicLibrarySource.OFFICIAL || source == PublicLibrarySource.COMMUNITY);
        boolean needsViewerPredicate = criteria.viewerUserId() != null && criteria.sources().stream()
                .anyMatch(source -> source == PublicLibrarySource.BY_YOU || source == PublicLibrarySource.COMMUNITY);
        if (needsOfficialPredicate) {
            parameters.put("deletedUserId", criteria.deletedUserId());
            parameters.put("officialAuthorEmail", OFFICIAL_AUTHOR_EMAIL);
        }
        if (needsViewerPredicate) {
            parameters.put("viewerUserId", criteria.viewerUserId());
        }
        List<String> predicates = new ArrayList<>();
        for (PublicLibrarySource source : criteria.sources()) {
            switch (source) {
                case BY_YOU -> predicates.add(criteria.viewerUserId() == null
                        ? "1 = 0"
                        : "n.owner_user_id = :viewerUserId");
                case OFFICIAL -> predicates.add(officialAuthorPredicate());
                case COMMUNITY -> {
                    String notViewer = criteria.viewerUserId() == null
                            ? "1 = 1"
                            : "n.owner_user_id <> :viewerUserId";
                    predicates.add("(" + notViewer + " and not " + officialAuthorPredicate() + ")");
                }
            }
        }
        where.append(" and (").append(String.join(" or ", predicates)).append(")");
    }

    private String officialAuthorPredicate() {
        return """
                exists (
                    select 1 from users official_user
                    where official_user.id = n.owner_user_id
                      and (official_user.role = 'ADMIN' or lower(official_user.email) = lower(:officialAuthorEmail))
                      and official_user.id <> :deletedUserId
                )
                """;
    }

    private String normalizedSlugSql(String expression) {
        String base = "lower(trim(coalesce(" + expression + ", '')))";
        if (isPostgres()) {
            return "regexp_replace(regexp_replace(" + base
                    + ", '[^a-z0-9]+', '-', 'g'), '(^-+|-+$)', '', 'g')";
        }
        return "regexp_replace(regexp_replace(" + base
                + ", '[^a-z0-9]+', '-'), '(^-+|-+$)', '')";
    }

    private String orderBy(PublicLibrarySort sort) {
        return switch (sort) {
            case RECENT -> " order by n.created_at desc, n.id asc";
            case TITLE -> " order by lower(coalesce(n.title, 'Untitled note')) asc, n.created_at desc, n.id asc";
            default -> throw new IllegalArgumentException(RANKED_SORT_PATH_REQUIRED_MESSAGE);
        };
    }

    private NoteListItemProjection toListItemProjection(Tuple tuple) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(ID_ALIAS, toUuid(tuple.get(ID_ALIAS)));
        values.put(OWNER_USER_ID_ALIAS, toUuid(tuple.get(OWNER_USER_ID_ALIAS)));
        values.put(TITLE_ALIAS, stringValue(tuple, TITLE_ALIAS));
        values.put(COURSE_PROGRAM_ALIAS, stringValue(tuple, COURSE_PROGRAM_ALIAS));
        values.put(APPLICABLE_PROGRAMS_ALIAS, stringArray(tuple.get(APPLICABLE_PROGRAMS_ALIAS)));
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

    private PublicLibraryCandidateProjection toCandidate(Tuple tuple) {
        return new PublicLibraryCandidateProjection(
                toUuid(tuple.get(ID_ALIAS)),
                toUuid(tuple.get(OWNER_USER_ID_ALIAS)),
                offsetDateTime(tuple.get(CREATED_AT_ALIAS)),
                offsetDateTime(tuple.get(UPDATED_AT_ALIAS)),
                enumValue(NoteStatus.class, stringValue(tuple, STATUS_ALIAS)),
                booleanValue(tuple.get(HAS_STUDY_PACK_ALIAS)),
                booleanValue(tuple.get(HAS_CONTENT_ALIAS)),
                booleanValue(tuple.get(HAS_SUMMARY_ALIAS)),
                ((Number) tuple.get(QUIZ_COUNT_ALIAS)).intValue()
        );
    }

    private Query createNativeQuery(String sql) {
        return entityManager.createNativeQuery(sql, Tuple.class);
    }

    @SuppressWarnings("unchecked")
    private List<Tuple> tuples(Query query) {
        return (List<Tuple>) query.getResultList();
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

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String stringValue(Tuple tuple, String alias) {
        Object value = tuple.get(alias);
        return value == null ? null : String.valueOf(value);
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
                throw new IllegalStateException("Could not read projected public-note tags.", exception);
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
