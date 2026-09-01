package com.studysnap.backend.service;

import com.studysnap.backend.dto.AdminNoteApplicableProgramsItemResponse;
import com.studysnap.backend.dto.AdminNoteApplicableProgramsPageResponse;
import com.studysnap.backend.dto.ApplicableProgramResponse;
import com.studysnap.backend.dto.NoteApplicableProgramsResponse;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.DuplicateCourseProgramException;
import com.studysnap.backend.exception.CourseProgramSelectionRequiredException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.exception.UnknownCourseProgramException;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.util.UuidParsingUtils;
import com.studysnap.backend.util.NoteCourseProgramShadowing;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    public NoteApplicableProgramsResponse get(String noteIdRaw, UUID requesterUserId) {
        NoteEntity note = findReadableNote(noteIdRaw, requesterUserId);
        List<ApplicableProgramResponse> programs = noteCourseProgramRepository.findByNoteId(note.getId());
        boolean courseProgramShadowed = NoteCourseProgramShadowing.isShadowed(
                programs.size(),
                note.getDomainContext()
        );
        return new NoteApplicableProgramsResponse(programs, courseProgramShadowed);
    }

    @Transactional
    public List<ApplicableProgramResponse> replace(
            String noteIdRaw,
            List<UUID> requestedIds,
            UUID requesterUserId
    ) {
        NoteEntity note = findAuthorizedNote(noteIdRaw, requesterUserId);
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new CourseProgramSelectionRequiredException();
        }
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
    public AdminNoteApplicableProgramsPageResponse getAdminPage(int page, int size, UUID requesterUserId) {
        Page<NoteEntity> notes = noteRepository.findByOwnerUserId(requesterUserId, PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, UPDATED_AT_PROPERTY)
        ));
        List<UUID> noteIds = notes.getContent().stream().map(NoteEntity::getId).toList();
        Map<UUID, List<ApplicableProgramResponse>> programsByNoteId =
                noteCourseProgramRepository.findByNoteIds(noteIds);

        // No owner lookup: the page now returns only the requester's own notes, so an owner column
        // would be the viewer's own address on every row. Dropped with the column it fed (v0.71.1).
        List<AdminNoteApplicableProgramsItemResponse> items = new ArrayList<>();
        for (NoteEntity note : notes.getContent()) {
            items.add(new AdminNoteApplicableProgramsItemResponse(
                    note.getId(),
                    note.getTitle(),
                    note.getCourseProgram(),
                    note.getDomainContext() == null ? null : note.getDomainContext().name(),
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
        boolean isOwner = note.getOwnerUserId().equals(requesterUserId);
        boolean isCurator = CuratorAuthoringPredicate.isCurator(requester);
        if (!isOwner || !isCurator) {
            throw new NoteNotFoundException();
        }
        return note;
    }

    private NoteEntity findReadableNote(String noteIdRaw, UUID requesterUserId) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(noteIdRaw, NoteNotFoundException::new);
        NoteEntity note = noteRepository.findById(noteId).orElseThrow(NoteNotFoundException::new);
        UserEntity requester = userRepository.findById(requesterUserId).orElseThrow(NoteNotFoundException::new);
        if (requester.getRole() == UserRole.ADMIN || note.getOwnerUserId().equals(requesterUserId)) {
            return note;
        }
        throw new NoteNotFoundException();
    }
}
