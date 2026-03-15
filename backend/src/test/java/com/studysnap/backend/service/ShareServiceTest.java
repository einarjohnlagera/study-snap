package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.ShareRemixResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.ShareLinkRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.testutil.builders.StudyPackEntityBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private ShareLinkRepository shareLinkRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private ActivityTrackingService activityTrackingService;

    private ShareService shareService;

    @BeforeEach
    void setUp() {
        shareService = new ShareService(
                shareLinkRepository,
                studyPackRepository,
                activityTrackingService
        );
    }

    @Test
    void remixSharedStudyPack_keepsOriginalTitleWhenNoTitleConflictExists() {
        UUID currentUserId = UUID.randomUUID();
        StudyPackEntity source = buildSharedPack("Photosynthesis", "token-1");
        when(studyPackRepository.findByShareToken("token-1")).thenReturn(Optional.of(source));
        when(studyPackRepository.findOwnedTitlesForCopyConflict(
                currentUserId,
                "Photosynthesis",
                "Photosynthesis (Copy%"
        )).thenReturn(List.of());
        when(studyPackRepository.save(any(StudyPackEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareRemixResponse response = shareService.remixSharedStudyPack("token-1", currentUserId);

        ArgumentCaptor<StudyPackEntity> savedCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(savedCaptor.capture());
        StudyPackEntity saved = savedCaptor.getValue();

        assertThat(saved.getTitle()).isEqualTo("Photosynthesis");
        assertThat(saved.getSummary()).isEqualTo(source.getSummary());
        assertThat(saved.getSubject()).isEqualTo(source.getSubject());
        assertThat(saved.getKeyConcepts()).containsExactlyElementsOf(source.getKeyConcepts());
        assertThat(saved.getQuiz()).containsExactlyElementsOf(source.getQuiz());
        assertThat(saved.getTags()).containsExactly(source.getTags());
        assertThat(response.studyPackId()).isEqualTo(saved.getId().toString());
        verify(activityTrackingService).recordActivity(currentUserId, ActivityType.CREATED_STUDY_PACK, saved.getId());
    }

    @Test
    void remixSharedStudyPack_addsCopySuffixWhenBaseTitleAlreadyExists() {
        UUID currentUserId = UUID.randomUUID();
        StudyPackEntity source = buildSharedPack("Photosynthesis", "token-2");
        when(studyPackRepository.findByShareToken("token-2")).thenReturn(Optional.of(source));
        when(studyPackRepository.findOwnedTitlesForCopyConflict(
                currentUserId,
                "Photosynthesis",
                "Photosynthesis (Copy%"
        )).thenReturn(List.of("Photosynthesis"));
        when(studyPackRepository.save(any(StudyPackEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shareService.remixSharedStudyPack("token-2", currentUserId);

        ArgumentCaptor<StudyPackEntity> savedCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getTitle()).isEqualTo("Photosynthesis (Copy)");
    }

    @Test
    void remixSharedStudyPack_incrementsCopyNumberWhenPriorCopiesExist() {
        UUID currentUserId = UUID.randomUUID();
        StudyPackEntity source = buildSharedPack("Photosynthesis", "token-3");
        when(studyPackRepository.findByShareToken("token-3")).thenReturn(Optional.of(source));
        when(studyPackRepository.findOwnedTitlesForCopyConflict(
                currentUserId,
                "Photosynthesis",
                "Photosynthesis (Copy%"
        )).thenReturn(List.of(
                "Photosynthesis",
                "Photosynthesis (Copy)",
                "Photosynthesis (Copy 2)"
        ));
        when(studyPackRepository.save(any(StudyPackEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shareService.remixSharedStudyPack("token-3", currentUserId);

        ArgumentCaptor<StudyPackEntity> savedCaptor = ArgumentCaptor.forClass(StudyPackEntity.class);
        verify(studyPackRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getTitle()).isEqualTo("Photosynthesis (Copy 3)");
    }

    private StudyPackEntity buildSharedPack(String title, String token) {
        StudyPackEntity source = StudyPackEntityBuilder.aStudyPack()
                .withTitle(title)
                .withSummary("Summary for " + title)
                .build();
        source.setShareToken(token);
        source.setSubject("Biology");
        source.setKeyConcepts(List.of("Light reactions", "Calvin cycle"));
        source.setQuiz(List.of(
                new QuizItem(
                        "What does chlorophyll absorb?",
                        List.of("Light", "Water", "Carbon dioxide", "Oxygen"),
                        "Light",
                        "Photosynthesis",
                        "Chlorophyll captures light energy."
                )
        ));
        source.setTags(new String[]{"biology", "plants"});
        return source;
    }
}
