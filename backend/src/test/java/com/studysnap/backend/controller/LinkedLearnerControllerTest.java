package com.studysnap.backend.controller;

import com.studysnap.backend.dto.RecordLinkedLearnerBirthYearRequest;
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
}
