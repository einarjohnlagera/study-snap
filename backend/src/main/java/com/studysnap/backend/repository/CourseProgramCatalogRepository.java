package com.studysnap.backend.repository;

import com.studysnap.backend.dto.CourseProgramCatalogItemResponse;
import com.studysnap.backend.dto.ProgramFamilyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CourseProgramCatalogRepository {
    private static final String BSED_ALIAS = "Bsed";
    private static final String BSED_CATALOG_NAME = "Education";
    private static final String CATALOG_SELECT = """
            SELECT course_programs.id, course_programs.name,
                   program_families.id AS program_family_id,
                   program_families.name AS program_family_name,
                   course_programs.is_active
            FROM course_programs
            LEFT JOIN course_program_family ON course_program_family.course_program_id = course_programs.id
            LEFT JOIN program_families ON program_families.id = course_program_family.program_family_id
            """;
    private static final String FIND_ALL = CATALOG_SELECT + " ORDER BY course_programs.name, program_families.name";
    private static final String FIND_BY_ID = CATALOG_SELECT
            + " WHERE course_programs.id = ? ORDER BY course_programs.name, program_families.name";
    private static final String FIND_BY_NORMALIZED_NAME = CATALOG_SELECT
            + " WHERE lower(regexp_replace(trim(course_programs.name), '\\s+', ' ', 'g')) = ?"
            + " ORDER BY course_programs.name, program_families.name";
    private static final String FIND_SIMILAR = """
            SELECT matched.id, matched.name, program_families.id AS program_family_id,
                   program_families.name AS program_family_name, matched.is_active
            FROM (
                SELECT id, name, is_active FROM course_programs
                WHERE lower(regexp_replace(trim(name), '\\s+', ' ', 'g')) LIKE ?
                   OR ? LIKE '%' || lower(regexp_replace(trim(name), '\\s+', ' ', 'g')) || '%'
                ORDER BY name LIMIT 8
            ) matched
            LEFT JOIN course_program_family ON course_program_family.course_program_id = matched.id
            LEFT JOIN program_families ON program_families.id = course_program_family.program_family_id
            ORDER BY matched.name, program_families.name
            """;
    private static final String FIND_PROGRAM_FAMILY_NAME = "SELECT name FROM program_families WHERE id = ?";
    private static final String INSERT = "INSERT INTO course_programs (id, name, exam_goal_slug) VALUES (?, ?, ?)";
    private static final String INSERT_MEMBERSHIP = "INSERT INTO course_program_family (id, course_program_id, program_family_id) VALUES (?, ?, ?)";
    private static final String DELETE_MEMBERSHIPS = "DELETE FROM course_program_family WHERE course_program_id = ?";
    private static final String FIND_ALL_PROGRAM_FAMILIES = "SELECT id, name FROM program_families ORDER BY name";
    private static final String FIND_PROGRAM_FAMILY_BY_NORMALIZED_NAME = "SELECT id, name FROM program_families WHERE lower(trim(name)) = ?";
    private static final String INSERT_PROGRAM_FAMILY = "INSERT INTO program_families (id, name) VALUES (?, ?)";
    private static final String FIND_ID_BY_NAME = "SELECT id FROM course_programs WHERE name = ?";
    private static final String FIND_NAMES_BY_EXAM_GOAL_SLUG = "SELECT name FROM course_programs WHERE exam_goal_slug = ? ORDER BY name";

    private final JdbcTemplate jdbcTemplate;

    public List<String> findNamesByExamGoalSlug(String slug) { return jdbcTemplate.queryForList(FIND_NAMES_BY_EXAM_GOAL_SLUG, String.class, slug); }
    public List<CourseProgramCatalogItemResponse> findAll() { return queryCatalog(FIND_ALL); }
    public Optional<CourseProgramCatalogItemResponse> findById(UUID id) { return queryCatalog(FIND_BY_ID, id).stream().findFirst(); }
    public Optional<CourseProgramCatalogItemResponse> findByNormalizedName(String name) { return queryCatalog(FIND_BY_NORMALIZED_NAME, name).stream().findFirst(); }
    public List<CourseProgramCatalogItemResponse> findSimilar(String name) { return queryCatalog(FIND_SIMILAR, "%" + name + "%", name); }
    public Optional<String> findProgramFamilyName(UUID id) {
        return jdbcTemplate.query(FIND_PROGRAM_FAMILY_NAME, (rs, row) -> rs.getString("name"), id).stream().findFirst();
    }
    public List<ProgramFamilyResponse> findAllProgramFamilies() { return jdbcTemplate.query(FIND_ALL_PROGRAM_FAMILIES, this::mapProgramFamily); }
    public Optional<ProgramFamilyResponse> findProgramFamilyByNormalizedName(String name) {
        return jdbcTemplate.query(FIND_PROGRAM_FAMILY_BY_NORMALIZED_NAME, this::mapProgramFamily, name).stream().findFirst();
    }
    public ProgramFamilyResponse insertProgramFamily(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(INSERT_PROGRAM_FAMILY, id, name);
        return new ProgramFamilyResponse(id, name);
    }
    public UUID insert(String name, String examGoalSlug) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(INSERT, id, name, examGoalSlug);
        return id;
    }
    public void insertProgramFamilies(UUID programId, Collection<UUID> familyIds) {
        for (UUID familyId : familyIds) jdbcTemplate.update(INSERT_MEMBERSHIP, UUID.randomUUID(), programId, familyId);
    }
    public void replaceProgramFamilies(UUID programId, Collection<UUID> familyIds) {
        jdbcTemplate.update(DELETE_MEMBERSHIPS, programId);
        insertProgramFamilies(programId, familyIds);
    }
    public Optional<UUID> resolveIdForLegacyName(String value) {
        if (value == null) return Optional.empty();
        String name = BSED_ALIAS.equals(value) ? BSED_CATALOG_NAME : value;
        return jdbcTemplate.query(FIND_ID_BY_NAME, (rs, row) -> rs.getObject("id", UUID.class), name).stream().findFirst();
    }
    public List<UUID> findExistingIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.queryForList("SELECT id FROM course_programs WHERE id IN (" + placeholders + ")", UUID.class, ids.toArray());
    }
    public List<String> findNamesByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.queryForList("SELECT name FROM course_programs WHERE id IN (" + placeholders + ") ORDER BY name", String.class, ids.toArray());
    }

    private List<CourseProgramCatalogItemResponse> queryCatalog(String sql, Object... args) {
        Map<UUID, CatalogAccumulator> grouped = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            UUID id = rs.getObject("id", UUID.class);
            String name = rs.getString("name");
            boolean active = rs.getBoolean("is_active");
            CatalogAccumulator item = grouped.computeIfAbsent(id,
                    ignored -> new CatalogAccumulator(id, name, active));
            UUID familyId = rs.getObject("program_family_id", UUID.class);
            if (familyId != null) item.families.add(new ProgramFamilyResponse(familyId, rs.getString("program_family_name")));
        }, args);
        return grouped.values().stream().map(CatalogAccumulator::toResponse).toList();
    }
    private ProgramFamilyResponse mapProgramFamily(ResultSet rs, int row) throws SQLException {
        return new ProgramFamilyResponse(rs.getObject("id", UUID.class), rs.getString("name"));
    }
    private static final class CatalogAccumulator {
        private final UUID id;
        private final String name;
        private final boolean active;
        private final List<ProgramFamilyResponse> families = new ArrayList<>();
        private CatalogAccumulator(UUID id, String name, boolean active) { this.id = id; this.name = name; this.active = active; }
        private CourseProgramCatalogItemResponse toResponse() {
            ProgramFamilyResponse first = families.isEmpty() ? null : families.getFirst();
            return new CourseProgramCatalogItemResponse(id, name, families,
                    first == null ? null : first.id(), first == null ? null : first.name(), active);
        }
    }
}
