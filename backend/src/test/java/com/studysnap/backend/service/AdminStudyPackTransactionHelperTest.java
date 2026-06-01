package com.studysnap.backend.service;

import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStudyPackTransactionHelperTest {

    @Mock
    private NoteRepository noteRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LlmStudyPackService llmStudyPackService;

    private AdminStudyPackTransactionHelper transactionHelper;

    @BeforeEach
    void setUp() {
        transactionHelper = new AdminStudyPackTransactionHelper(
                noteRepository,
                studyPackRepository,
                userRepository,
                llmStudyPackService
        );
    }

    @Test
    void regenerateOnePack_handlesNoteNotFound() {
        StudyPackEntity pack = new StudyPackEntity();
        pack.setId(UUID.randomUUID());
        pack.setNoteId(UUID.randomUUID());
        when(studyPackRepository.findById(pack.getId())).thenReturn(Optional.of(pack));
        when(noteRepository.findById(pack.getNoteId())).thenReturn(Optional.empty());

        assertThatCode(() -> transactionHelper.regenerateOnePack(pack))
                .doesNotThrowAnyException();

        verify(llmStudyPackService, never()).regenerateSummary(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(studyPackRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
