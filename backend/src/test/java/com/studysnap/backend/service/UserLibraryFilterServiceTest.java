package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.dto.CreateSavedLibraryFilterRequest;
import com.studysnap.backend.dto.SavedLibraryFilterResponse;
import com.studysnap.backend.entity.UserLibraryFilterEntity;
import com.studysnap.backend.exception.InvalidSavedFilterRequestException;
import com.studysnap.backend.exception.SavedFilterNotFoundException;
import com.studysnap.backend.repository.UserLibraryFilterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLibraryFilterServiceTest {

    private static final String PNLE_FILTER_NAME = "PNLE Notes";
    private static final String COURSE_PROGRAM_KEY = "courseProgram";
    private static final String SEARCH_KEY = "search";
    private static final String SUBJECT_KEY = "subject";
    private static final String TAGS_KEY = "tags";
    private static final String STATUS_KEY = "status";
    private static final String UNKNOWN_KEY = "unknown";
    private static final String DRAFT_STATUS = "DRAFT";
    private static final String PNLE_COURSE_PROGRAM = "PNLE";

    @Mock
    private UserLibraryFilterRepository repository;

    private UserLibraryFilterService service;

    @BeforeEach
    void setUp() {
        service = new UserLibraryFilterService(repository, new ObjectMapper());
    }

    @Test
    void list_returnsFiltersForUserInRepositoryOrder() {
        UUID userId = UUID.randomUUID();
        UserLibraryFilterEntity newer = buildEntity(userId, PNLE_FILTER_NAME, Instant.parse("2026-03-22T00:00:00Z"));
        UserLibraryFilterEntity older = buildEntity(userId, "Drafts", Instant.parse("2026-03-21T00:00:00Z"));
        when(repository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(newer, older));

        List<SavedLibraryFilterResponse> result = service.list(userId);

        assertThat(result).extracting(SavedLibraryFilterResponse::name).containsExactly(PNLE_FILTER_NAME, "Drafts");
        verify(repository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void create_savesTrimmedNameAndSerializedFilterState() {
        UUID userId = UUID.randomUUID();
        when(repository.save(any(UserLibraryFilterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SavedLibraryFilterResponse result = service.create(userId, new CreateSavedLibraryFilterRequest(
                "  " + PNLE_FILTER_NAME + "  ",
                Map.of(COURSE_PROGRAM_KEY, PNLE_COURSE_PROGRAM, "tags", List.of("review"))
        ));

        assertThat(result.name()).isEqualTo(PNLE_FILTER_NAME);
        assertThat(result.filterState()).containsEntry(COURSE_PROGRAM_KEY, PNLE_COURSE_PROGRAM);
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    void create_sanitizesUnsetFilterStateValues() {
        UUID userId = UUID.randomUUID();
        when(repository.save(any(UserLibraryFilterEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SavedLibraryFilterResponse result = service.create(userId, new CreateSavedLibraryFilterRequest(
                PNLE_FILTER_NAME,
                Map.of(
                        SEARCH_KEY, "  anatomy  ",
                        SUBJECT_KEY, "",
                        TAGS_KEY, List.of(" review ", "", "exam"),
                        STATUS_KEY, DRAFT_STATUS,
                        UNKNOWN_KEY, "ignored"
                )
        ));

        assertThat(result.filterState())
                .containsEntry(SEARCH_KEY, "anatomy")
                .containsEntry(TAGS_KEY, List.of("review", "exam"))
                .containsEntry(STATUS_KEY, DRAFT_STATUS)
                .doesNotContainKeys(SUBJECT_KEY, UNKNOWN_KEY);
    }

    @Test
    void create_rejectsInvalidReadinessStatus() {
        CreateSavedLibraryFilterRequest request = new CreateSavedLibraryFilterRequest(
                PNLE_FILTER_NAME,
                Map.of(STATUS_KEY, "ready")
        );

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(InvalidSavedFilterRequestException.class)
                .hasMessage("Saved filter status is invalid.");
    }

    @Test
    void create_rejectsBlankName() {
        CreateSavedLibraryFilterRequest request = new CreateSavedLibraryFilterRequest(
                "  ",
                Map.of(STATUS_KEY, DRAFT_STATUS)
        );

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(InvalidSavedFilterRequestException.class)
                .hasMessage("Name is required.");
    }

    @Test
    void create_rejectsNameOverOneHundredCharacters() {
        String tooLongName = "a".repeat(101);
        CreateSavedLibraryFilterRequest request = new CreateSavedLibraryFilterRequest(
                tooLongName,
                Map.of(STATUS_KEY, DRAFT_STATUS)
        );

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(InvalidSavedFilterRequestException.class)
                .hasMessage("Name must be 100 characters or fewer.");
    }

    @Test
    void create_rejectsNullFilterState() {
        CreateSavedLibraryFilterRequest request = new CreateSavedLibraryFilterRequest(PNLE_FILTER_NAME, null);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(InvalidSavedFilterRequestException.class)
                .hasMessage("Filter state is required.");
    }

    @Test
    void delete_removesOwnedFilter() {
        UUID userId = UUID.randomUUID();
        UUID filterId = UUID.randomUUID();
        UserLibraryFilterEntity entity = buildEntity(userId, PNLE_FILTER_NAME, Instant.now());
        entity.setId(filterId);
        when(repository.findByIdAndUserId(filterId, userId)).thenReturn(Optional.of(entity));

        service.delete(filterId, userId);

        verify(repository).delete(entity);
    }

    @Test
    void delete_throwsWhenFilterDoesNotBelongToUser() {
        UUID userId = UUID.randomUUID();
        UUID filterId = UUID.randomUUID();
        when(repository.findByIdAndUserId(filterId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(filterId, userId))
                .isInstanceOf(SavedFilterNotFoundException.class);
    }

    @Test
    void delete_isTransactional() throws NoSuchMethodException {
        Method deleteMethod = UserLibraryFilterService.class.getMethod("delete", UUID.class, UUID.class);

        assertThat(deleteMethod.getAnnotation(Transactional.class)).isNotNull();
    }

    private UserLibraryFilterEntity buildEntity(UUID userId, String name, Instant createdAt) {
        UserLibraryFilterEntity entity = new UserLibraryFilterEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setName(name);
        entity.setFilterState("{\"courseProgram\":\"PNLE\"}");
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
