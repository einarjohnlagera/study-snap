package com.studysnap.backend.controller;

import com.studysnap.backend.dto.RecordLinkedLearnerBirthYearRequest;
import com.studysnap.backend.dto.LinkedLearnerActivityGrantRequest;
import com.studysnap.backend.dto.LinkedLearnerActivityGrantResponse;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.LinkedLearnerProgressService;
import com.studysnap.backend.service.LinkedLearnerService;
import com.studysnap.backend.service.LinkedLearnerActivityService;
import com.studysnap.backend.service.LinkedLearnerGrantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.web.bind.annotation.PutMapping;

@ExtendWith(MockitoExtension.class)
class LinkedLearnerControllerTest {
    @Mock
    private LinkedLearnerService linkedLearnerService;

    @Mock
    private LinkedLearnerProgressService linkedLearnerProgressService;
    @Mock
    private LinkedLearnerGrantService linkedLearnerGrantService;
    @Mock
    private LinkedLearnerActivityService linkedLearnerActivityService;

    private LinkedLearnerController controller;

    @BeforeEach
    void setUp() {
        controller = new LinkedLearnerController(
                linkedLearnerService,
                linkedLearnerProgressService,
                linkedLearnerGrantService,
                linkedLearnerActivityService);
    }

    @Test
    void birthYearCorrectionTargetsOnlyTheAuthenticatedCaller() {
        UUID learnerUserId = UUID.randomUUID();
        AuthenticatedUser learner = new AuthenticatedUser(learnerUserId, UserRole.USER, true, 1);
        RecordLinkedLearnerBirthYearRequest request = new RecordLinkedLearnerBirthYearRequest(2012);
        when(linkedLearnerService.correctBirthYear(learnerUserId, 2012)).thenReturn(List.of());

        controller.correctBirthYear(learner, request);

        verify(linkedLearnerService).correctBirthYear(learnerUserId, 2012);
    }

    @Test
    void activityAndProgressGrantRoutesKeepTheSameRequestAndResponseShape() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        LinkedLearnerActivityGrantRequest request = new LinkedLearnerActivityGrantRequest(true);
        when(linkedLearnerGrantService.setActivityGrant(userId, relationshipId, true))
                .thenReturn(new LinkedLearnerActivityGrantResponse(true));
        when(linkedLearnerGrantService.setProgressGrant(userId, relationshipId, true))
                .thenReturn(new LinkedLearnerActivityGrantResponse(true));

        assertThat(controller.setActivityGrant(relationshipId, user, request).granted()).isTrue();
        assertThat(controller.setProgressGrant(relationshipId, user, request).granted()).isTrue();
        verify(linkedLearnerGrantService).setActivityGrant(userId, relationshipId, true);
        verify(linkedLearnerGrantService).setProgressGrant(userId, relationshipId, true);
        assertThat(LinkedLearnerController.class
                .getMethod("setActivityGrant", UUID.class, AuthenticatedUser.class,
                        LinkedLearnerActivityGrantRequest.class)
                .getAnnotation(PutMapping.class).value())
                .containsExactly("/{relationshipId}/grants/activity");
        assertThat(LinkedLearnerController.class
                .getMethod("setProgressGrant", UUID.class, AuthenticatedUser.class,
                        LinkedLearnerActivityGrantRequest.class)
                .getAnnotation(PutMapping.class).value())
                .containsExactly("/{relationshipId}/grants/progress");
    }
}
