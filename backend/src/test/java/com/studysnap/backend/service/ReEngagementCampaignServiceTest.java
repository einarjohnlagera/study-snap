package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.ReEngagementCampaignService.ReEngagementSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReEngagementCampaignServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ActivityEventRepository activityEventRepository;
    @Mock private EmailLogRepository emailLogRepository;
    @Mock private EmailTemplateService emailTemplateService;
    @Mock private EmailService emailService;
    @Mock private StudySnapProperties properties;
    @Mock private StudySnapProperties.Email emailProperties;

    private ReEngagementCampaignService service;

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new ReEngagementCampaignService(
                userRepository, activityEventRepository, emailLogRepository,
                emailTemplateService, emailService, properties
        );
        lenient().when(properties.getEmail()).thenReturn(emailProperties);
        lenient().when(emailProperties.getAppBaseUrl()).thenReturn("https://notelib.app");
        lenient().when(emailTemplateService.render(anyString(), any()))
                .thenReturn(new EmailTemplateService.RenderedEmailTemplate("subject", "<p>body</p>", "body"));
    }

    @Test
    void send_usesStudentTemplateForStudentProfileType() {
        UserEntity user = activeUser(ProfileType.STUDENT);
        stubEligible(user);

        service.send(NOW);

        ArgumentCaptor<String> templateCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailTemplateService).render(templateCaptor.capture(), any());
        assertThat(templateCaptor.getValue()).isEqualTo("re-engagement-student");
    }

    @Test
    void send_usesStudentTemplateForBoardExamProfileType() {
        UserEntity user = activeUser(ProfileType.BOARD_EXAM);
        stubEligible(user);

        service.send(NOW);

        ArgumentCaptor<String> templateCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailTemplateService).render(templateCaptor.capture(), any());
        assertThat(templateCaptor.getValue()).isEqualTo("re-engagement-student");
    }

    @Test
    void send_usesStudentTemplateForNullProfileType() {
        UserEntity user = activeUser(null);
        stubEligible(user);

        service.send(NOW);

        ArgumentCaptor<String> templateCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailTemplateService).render(templateCaptor.capture(), any());
        assertThat(templateCaptor.getValue()).isEqualTo("re-engagement-student");
    }

    @Test
    void send_usesProfessionalTemplate() {
        UserEntity user = activeUser(ProfileType.PROFESSIONAL);
        stubEligible(user);

        service.send(NOW);

        ArgumentCaptor<String> templateCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailTemplateService).render(templateCaptor.capture(), any());
        assertThat(templateCaptor.getValue()).isEqualTo("re-engagement-professional");
    }

    @Test
    void send_usesTeacherTemplate() {
        UserEntity user = activeUser(ProfileType.TEACHER);
        stubEligible(user);

        service.send(NOW);

        ArgumentCaptor<String> templateCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailTemplateService).render(templateCaptor.capture(), any());
        assertThat(templateCaptor.getValue()).isEqualTo("re-engagement-teacher");
    }

    @Test
    void send_skipsUserAlreadySent() {
        UserEntity user = activeUser(ProfileType.STUDENT);
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNull(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(emailLogRepository.existsByUserIdAndEmailType(user.getId(), RetentionEmailType.RE_ENGAGEMENT_2025))
                .thenReturn(true);

        ReEngagementSendResult result = service.send(NOW);

        verify(emailService, never()).sendEmail(any());
        assertThat(result.sent()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(0);
    }

    @Test
    void send_skipsRecentlyActiveUser() {
        UserEntity user = activeUser(ProfileType.STUDENT);
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNull(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(emailLogRepository.existsByUserIdAndEmailType(user.getId(), RetentionEmailType.RE_ENGAGEMENT_2025))
                .thenReturn(false);
        // has activity within the last 30 days → not inactive
        when(activityEventRepository.existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                eq(user.getId()), any(Collection.class), any(OffsetDateTime.class)))
                .thenReturn(true);

        ReEngagementSendResult result = service.send(NOW);

        verify(emailService, never()).sendEmail(any());
        assertThat(result.sent()).isEqualTo(0);
    }

    @Test
    void send_countsFailedSendAsSkipped() {
        UserEntity user = activeUser(ProfileType.STUDENT);
        stubEligible(user);
        when(emailTemplateService.render(anyString(), any())).thenThrow(new RuntimeException("template error"));

        ReEngagementSendResult result = service.send(NOW);

        assertThat(result.sent()).isEqualTo(0);
        assertThat(result.skipped()).isEqualTo(1);
        verify(emailLogRepository, never()).save(any());
    }

    @Test
    void send_logsEmailAfterSuccessfulSend() {
        UserEntity user = activeUser(ProfileType.STUDENT);
        stubEligible(user);

        service.send(NOW);

        verify(emailLogRepository).save(any());
    }

    @Test
    void send_returnsCorrectSentAndSkippedCounts() {
        UserEntity user1 = activeUser(ProfileType.STUDENT);
        UserEntity user2 = activeUser(ProfileType.PROFESSIONAL);
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNull(UserStatus.ACTIVE))
                .thenReturn(List.of(user1, user2));
        when(emailLogRepository.existsByUserIdAndEmailType(any(), eq(RetentionEmailType.RE_ENGAGEMENT_2025)))
                .thenReturn(false);
        when(activityEventRepository.existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                any(), any(Collection.class), any(OffsetDateTime.class)))
                .thenReturn(false);
        when(emailTemplateService.render(eq("re-engagement-professional"), any()))
                .thenThrow(new RuntimeException("template error"));

        ReEngagementSendResult result = service.send(NOW);

        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void countEligible_returnsCorrectCount() {
        UserEntity user1 = activeUser(ProfileType.STUDENT);
        UserEntity user2 = activeUser(ProfileType.TEACHER);
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNull(UserStatus.ACTIVE))
                .thenReturn(List.of(user1, user2));
        when(emailLogRepository.existsByUserIdAndEmailType(any(), eq(RetentionEmailType.RE_ENGAGEMENT_2025)))
                .thenReturn(false);
        when(activityEventRepository.existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                any(), any(Collection.class), any(OffsetDateTime.class)))
                .thenReturn(false);

        int count = service.countEligible(NOW);

        assertThat(count).isEqualTo(2);
        verify(emailService, never()).sendEmail(any());
    }

    // --- helpers ---

    private UserEntity activeUser(ProfileType profileType) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("user-" + UUID.randomUUID() + "@example.com");
        user.setFirstName("Test");
        user.setStatus(UserStatus.ACTIVE);
        user.setProfileType(profileType);
        return user;
    }

    private void stubEligible(UserEntity user) {
        when(userRepository.findByStatusAndEmailVerifiedAtIsNotNull(UserStatus.ACTIVE))
                .thenReturn(List.of(user));
        when(emailLogRepository.existsByUserIdAndEmailType(user.getId(), RetentionEmailType.RE_ENGAGEMENT_2025))
                .thenReturn(false);
        when(activityEventRepository.existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                eq(user.getId()), any(Collection.class), any(OffsetDateTime.class)))
                .thenReturn(false);
    }
}
