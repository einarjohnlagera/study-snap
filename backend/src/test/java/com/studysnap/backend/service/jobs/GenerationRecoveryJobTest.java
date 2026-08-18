package com.studysnap.backend.service.jobs;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.service.GenerationRecoveryService;
import com.studysnap.backend.service.GenerationRecoveryService.SurfaceRecoveryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationRecoveryJobTest {
    @Mock
    private GenerationRecoveryService generationRecoveryService;

    private StudySnapProperties properties;
    private GenerationRecoveryJob job;

    @BeforeEach
    void setUp() {
        properties = new StudySnapProperties();
        job = new GenerationRecoveryJob(generationRecoveryService, properties);
    }

    @Test
    void run_delegatesToAllThreeSurfaces() {
        when(generationRecoveryService.recoverStaleExamQuestionPools()).thenReturn(SurfaceRecoveryResult.empty());
        when(generationRecoveryService.recoverStaleLongExamSessions()).thenReturn(SurfaceRecoveryResult.empty());
        when(generationRecoveryService.recoverStaleNotes()).thenReturn(SurfaceRecoveryResult.empty());

        job.run();

        verify(generationRecoveryService).recoverStaleExamQuestionPools();
        verify(generationRecoveryService).recoverStaleLongExamSessions();
        verify(generationRecoveryService).recoverStaleNotes();
    }

    @Test
    void run_oneSurfaceFailureDoesNotBlockOthersAndNeverPropagates() {
        when(generationRecoveryService.recoverStaleExamQuestionPools())
                .thenThrow(new IllegalStateException("database unavailable"));
        when(generationRecoveryService.recoverStaleLongExamSessions()).thenReturn(SurfaceRecoveryResult.empty());
        when(generationRecoveryService.recoverStaleNotes()).thenReturn(SurfaceRecoveryResult.empty());

        assertThatCode(job::run).doesNotThrowAnyException();

        verify(generationRecoveryService).recoverStaleLongExamSessions();
        verify(generationRecoveryService).recoverStaleNotes();
    }

    @Test
    void run_disabledKillSwitchSkipsEverySurface() {
        properties.getGeneration().setEnabled(false);

        job.run();

        verify(generationRecoveryService, never()).recoverStaleExamQuestionPools();
        verify(generationRecoveryService, never()).recoverStaleLongExamSessions();
        verify(generationRecoveryService, never()).recoverStaleNotes();
    }
}
