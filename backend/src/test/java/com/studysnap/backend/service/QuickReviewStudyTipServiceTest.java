package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.dto.QuickReviewStudyTipRequest;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.service.model.LlmRouting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuickReviewStudyTipServiceTest {
    @Mock private StudyPackRepository studyPackRepository;
    @Mock private LlmStudyPackService llmStudyPackService;
    @Mock private AuthService authService;

    @Test
    void generateStudyTipPassesInteractiveQuizRoute() {
        UUID packId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(studyPackRepository.findByIdAndOwnerUserId(packId, userId))
                .thenReturn(Optional.of(new StudyPackEntity()));
        when(llmStudyPackService.generateQuickReviewStudyTip(anyList(),
                org.mockito.ArgumentMatchers.eq(LlmRouting.interactiveQuiz())))
                .thenReturn("Review the concept.");
        QuickReviewStudyTipService service = new QuickReviewStudyTipService(
                new StudySnapProperties(), studyPackRepository, llmStudyPackService, authService);

        var response = service.generateStudyTip(packId.toString(), userId,
                new QuickReviewStudyTipRequest(List.of(new QuickReviewIncorrectQuestionRequest(
                        "What is ATP?", "Energy currency", "ATP transfers energy."))));

        assertThat(response.studyTip()).isEqualTo("Review the concept.");
        verify(llmStudyPackService).generateQuickReviewStudyTip(anyList(),
                org.mockito.ArgumentMatchers.eq(LlmRouting.interactiveQuiz()));
    }
}
