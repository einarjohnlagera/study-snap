package com.studysnap.backend.service;

import com.studysnap.backend.dto.AdminNoteApplicableProgramsItemResponse;
import com.studysnap.backend.dto.AdminNoteApplicableProgramsPageResponse;
import com.studysnap.backend.dto.ApplicableProgramResponse;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.DuplicateCourseProgramException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.UnknownCourseProgramException;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteApplicableProgramsService {
    private static final String UPDATED_AT_PROPERTY = "updatedAt";

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final CourseProgramCatalogRepository courseProgramCatalogRepository;
    private final NoteCourseProgramRepository noteCourseProgramRepository;

    @Transactional(readOnly = true)
    public List<ApplicableProgramResponse> get(String noteIdRaw, UUID requesterUserId) {
        NoteEntity note = findAuthorizedNote(noteIdRaw, requesterUserId);
        return noteCourseProgramRepository.findByNoteId(note.getId());
    }

    @Transactional
    public List<ApplicableProgramResponse> replace(
            String noteIdRaw,
            List<UUID> requestedIds,
            UUID requesterUserId
    ) {
        NoteEntity note = findAuthorizedNote(noteIdRaw, requesterUserId);
        LinkedHashSet<UUID> desiredIds = new LinkedHashSet<>(requestedIds);
        if (desiredIds.size() != requestedIds.size()) {
            throw new DuplicateCourseProgramException();
        }
        List<UUID> existingIds = courseProgramCatalogRepository.findExistingIds(desiredIds);
        if (existingIds.size() != desiredIds.size()) {
            throw new UnknownCourseProgramException();
        }
        noteCourseProgramRepository.replace(note.getId(), desiredIds);
        return noteCourseProgramRepository.findByNoteId(note.getId());
    }

    @Transactional(readOnly = true)
    public AdminNoteApplicableProgramsPageResponse getAdminPage(int page, int size) {
        Page<NoteEntity> notes = noteRepository.findAll(PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, UPDATED_AT_PROPERTY)
        ));
        List<UUID> noteIds = notes.getContent().stream().map(NoteEntity::getId).toList();
        Map<UUID, List<ApplicableProgramResponse>> programsByNoteId =
                noteCourseProgramRepository.findByNoteIds(noteIds);

        Set<UUID> ownerIds = new HashSet<>();
        notes.getContent().forEach(note -> ownerIds.add(note.getOwnerUserId()));
        Map<UUID, String> ownerEmailById = new HashMap<>();
        userRepository.findAllById(ownerIds).forEach(user -> ownerEmailById.put(user.getId(), user.getEmail()));

        List<AdminNoteApplicableProgramsItemResponse> items = new ArrayList<>();
        for (NoteEntity note : notes.getContent()) {
            items.add(new AdminNoteApplicableProgramsItemResponse(
                    note.getId(),
                    note.getTitle(),
                    ownerEmailById.get(note.getOwnerUserId()),
                    note.getCourseProgram(),
                    programsByNoteId.getOrDefault(note.getId(), List.of())
            ));
        }
        return new AdminNoteApplicableProgramsPageResponse(
                items,
                notes.getNumber(),
                notes.getSize(),
                notes.getTotalElements()
        );
    }

    private NoteEntity findAuthorizedNote(String noteIdRaw, UUID requesterUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(noteIdRaw, NoteNotFoundException::new);
        NoteEntity note = noteRepository.findById(noteId).orElseThrow(NoteNotFoundException::new);
        UserEntity requester = userRepository.findById(requesterUserId).orElseThrow(NoteNotFoundException::new);
        if (requester.getRole() == UserRole.ADMIN) {
            return note;
        }
        boolean isTeacherOwner = note.getOwnerUserId().equals(requesterUserId)
                && requester.getProfileType() == ProfileType.TEACHER;
        if (!isTeacherOwner) {
            throw new NoteNotFoundException();
        }
        return note;
    }
}
