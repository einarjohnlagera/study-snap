package com.studysnap.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.dto.CreateSavedLibraryFilterRequest;
import com.studysnap.backend.dto.SavedLibraryFilterResponse;
import com.studysnap.backend.entity.UserLibraryFilterEntity;
import com.studysnap.backend.exception.InvalidSavedFilterRequestException;
import com.studysnap.backend.exception.SavedFilterNotFoundException;
import com.studysnap.backend.repository.UserLibraryFilterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserLibraryFilterService {

    private static final int SAVED_FILTER_NAME_MAX_LENGTH = 100;
    private static final String SAVED_FILTER_NAME_REQUIRED_MESSAGE = "Name is required.";
    private static final String SAVED_FILTER_NAME_TOO_LONG_MESSAGE = "Name must be 100 characters or fewer.";
    private static final String SAVED_FILTER_STATE_REQUIRED_MESSAGE = "Filter state is required.";
    private static final String INVALID_FILTER_STATE_MESSAGE = "Filter state could not be saved.";
    private static final String INVALID_STATUS_MESSAGE = "Saved filter status is invalid.";
    private static final String SEARCH_KEY = "search";
    private static final String SUBJECT_KEY = "subject";
    private static final String COURSE_PROGRAM_KEY = "courseProgram";
    private static final String TAGS_KEY = "tags";
    private static final String STATUS_KEY = "status";
    private static final String SORT_KEY = "sort";
    private static final Set<String> READINESS_FILTER_VALUES = Set.of(
            "ALL",
            "DRAFT",
            "QUIZ_READY",
            "STUDY_PACK_READY"
    );

    private static final TypeReference<Map<String, Object>> FILTER_STATE_TYPE = new TypeReference<>() {
    };

    private final UserLibraryFilterRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<SavedLibraryFilterResponse> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SavedLibraryFilterResponse create(UUID userId, CreateSavedLibraryFilterRequest request) {
        String name = validateName(request == null ? null : request.name());
        Map<String, Object> filterState = validateFilterState(request == null ? null : request.filterState());

        UserLibraryFilterEntity entity = new UserLibraryFilterEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setName(name);
        entity.setFilterState(writeFilterState(filterState));
        entity.setCreatedAt(Instant.now());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(UUID filterId, UUID userId) {
        UserLibraryFilterEntity entity = repository.findByIdAndUserId(filterId, userId)
                .orElseThrow(SavedFilterNotFoundException::new);
        repository.delete(entity);
    }

    private String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            throw new InvalidSavedFilterRequestException(SAVED_FILTER_NAME_REQUIRED_MESSAGE);
        }
        if (name.length() > SAVED_FILTER_NAME_MAX_LENGTH) {
            throw new InvalidSavedFilterRequestException(SAVED_FILTER_NAME_TOO_LONG_MESSAGE);
        }
        return name;
    }

    private Map<String, Object> validateFilterState(Map<String, Object> filterState) {
        if (filterState == null) {
            throw new InvalidSavedFilterRequestException(SAVED_FILTER_STATE_REQUIRED_MESSAGE);
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        addStringValue(sanitized, SEARCH_KEY, filterState.get(SEARCH_KEY));
        addStringValue(sanitized, SUBJECT_KEY, filterState.get(SUBJECT_KEY));
        addStringValue(sanitized, COURSE_PROGRAM_KEY, filterState.get(COURSE_PROGRAM_KEY));
        addTagsValue(sanitized, filterState.get(TAGS_KEY));
        addStatusValue(sanitized, filterState.get(STATUS_KEY));
        addStringValue(sanitized, SORT_KEY, filterState.get(SORT_KEY));
        return sanitized;
    }

    private void addStringValue(Map<String, Object> sanitized, String key, Object value) {
        if (!(value instanceof String stringValue)) {
            return;
        }
        String trimmedValue = stringValue.trim();
        if (!trimmedValue.isBlank()) {
            sanitized.put(key, trimmedValue);
        }
    }

    private void addTagsValue(Map<String, Object> sanitized, Object value) {
        if (!(value instanceof List<?> rawTags)) {
            return;
        }
        List<String> tags = new ArrayList<>();
        for (Object rawTag : rawTags) {
            if (rawTag instanceof String tag) {
                String trimmedTag = tag.trim();
                if (!trimmedTag.isBlank()) {
                    tags.add(trimmedTag);
                }
            }
        }
        if (!tags.isEmpty()) {
            sanitized.put(TAGS_KEY, tags);
        }
    }

    private void addStatusValue(Map<String, Object> sanitized, Object value) {
        if (!(value instanceof String status)) {
            return;
        }
        String trimmedStatus = status.trim();
        if (trimmedStatus.isBlank()) {
            return;
        }
        if (!READINESS_FILTER_VALUES.contains(trimmedStatus)) {
            throw new InvalidSavedFilterRequestException(INVALID_STATUS_MESSAGE);
        }
        sanitized.put(STATUS_KEY, trimmedStatus);
    }

    private String writeFilterState(Map<String, Object> filterState) {
        try {
            return objectMapper.writeValueAsString(filterState);
        } catch (JsonProcessingException ex) {
            throw new InvalidSavedFilterRequestException(INVALID_FILTER_STATE_MESSAGE);
        }
    }

    private Map<String, Object> readFilterState(String filterState) {
        try {
            return objectMapper.readValue(filterState, FILTER_STATE_TYPE);
        } catch (JsonProcessingException ex) {
            throw new InvalidSavedFilterRequestException(INVALID_FILTER_STATE_MESSAGE);
        }
    }

    private SavedLibraryFilterResponse toResponse(UserLibraryFilterEntity entity) {
        return new SavedLibraryFilterResponse(
                entity.getId(),
                entity.getName(),
                readFilterState(entity.getFilterState()),
                entity.getCreatedAt()
        );
    }
}
