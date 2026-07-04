package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.entity.VoucherRedemptionEntity;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.BulkGenerationResultRepository;
import com.studysnap.backend.repository.ConceptHealthRepository;
import com.studysnap.backend.repository.MemorizationCardRepository;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.EmailVerificationTokenRepository;
import com.studysnap.backend.repository.FeedbackRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PasswordResetTokenRepository;
import com.studysnap.backend.repository.PaymentTransactionRepository;
import com.studysnap.backend.repository.PremiumWaitlistRepository;
import com.studysnap.backend.repository.PublicNoteLikeRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.QuizShareLinkRepository;
import com.studysnap.backend.repository.RefreshTokenRepository;
import com.studysnap.backend.repository.StudyPackDraftRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserAuthProviderRepository;
import com.studysnap.backend.repository.UserLibraryFilterRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.UserUsageRepository;
import com.studysnap.backend.repository.VoucherRedemptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPurgeServiceTest {
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private StudyPackDraftRepository studyPackDraftRepository;
    @Mock
    private GeneratedQuizRepository generatedQuizRepository;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private ConceptHealthRepository conceptHealthRepository;
    @Mock
    private MemorizationCardRepository memorizationCardRepository;
    @Mock
    private ActivityEventRepository activityEventRepository;
    @Mock
    private BulkGenerationResultRepository bulkGenerationResultRepository;
    @Mock
    private QuizShareLinkRepository quizShareLinkRepository;
    @Mock
    private PublicNoteLikeRepository publicNoteLikeRepository;
    @Mock
    private UserLibraryFilterRepository userLibraryFilterRepository;
    @Mock
    private UserUsageRepository userUsageRepository;
    @Mock
    private NoteCollectionRepository noteCollectionRepository;
    @Mock
    private NoteCollectionItemRepository noteCollectionItemRepository;
    @Mock
    private UserAuthProviderRepository userAuthProviderRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private EmailLogRepository emailLogRepository;
    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private PremiumWaitlistRepository premiumWaitlistRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private VoucherRedemptionRepository voucherRedemptionRepository;

    private StudySnapProperties properties;
    private AccountPurgeService accountPurgeService;

    @BeforeEach
    void setUp() {
        properties = new StudySnapProperties();
        accountPurgeService = new AccountPurgeService(
                properties,
                transactionManager,
                userRepository,
                noteRepository,
                studyPackRepository,
                studyPackDraftRepository,
                generatedQuizRepository,
                quickReviewSessionRepository,
                conceptHealthRepository,
                memorizationCardRepository,
                activityEventRepository,
                bulkGenerationResultRepository,
                quizShareLinkRepository,
                publicNoteLikeRepository,
                userLibraryFilterRepository,
                userUsageRepository,
                noteCollectionRepository,
                noteCollectionItemRepository,
                userAuthProviderRepository,
                refreshTokenRepository,
                emailVerificationTokenRepository,
                passwordResetTokenRepository,
                emailLogRepository,
                feedbackRepository,
                premiumWaitlistRepository,
                paymentTransactionRepository,
                subscriptionRepository,
                voucherRedemptionRepository
        );
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void purgeEligibleAccounts_reassignsPublicArtifactsAndFinancialRowsThenDeletesPersonalData() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-31T00:00:00Z");
        OffsetDateTime cutoff = now.minusDays(30);
        UUID userId = UUID.randomUUID();
        UUID publicNoteId = UUID.randomUUID();
        UserEntity user = pendingDeletionUser(userId, now.minusDays(31));
        UserEntity deletedUser = deletedUser();
        NoteEntity publicNote = note(publicNoteId);
        SubscriptionEntity subscription = activeSubscription(user);
        PaymentTransactionEntity payment = payment(user);
        VoucherRedemptionEntity redemption = redemption(user);

        when(userRepository.findByStatusAndDeletedAtLessThanEqual(UserStatus.PENDING_DELETION, cutoff))
                .thenReturn(List.of(user));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(AccountPurgeService.DELETED_USER_ID)).thenReturn(Optional.of(deletedUser));
        when(noteRepository.findByOwnerUserIdAndVisibility(userId, NoteVisibility.PUBLIC)).thenReturn(List.of(publicNote));
        when(subscriptionRepository.findByUser_Id(userId)).thenReturn(List.of(subscription));
        when(paymentTransactionRepository.findByUser_Id(userId)).thenReturn(List.of(payment));
        when(voucherRedemptionRepository.findByUser_Id(userId)).thenReturn(List.of(redemption));
        when(noteCollectionRepository.findByOwnerUserId(userId)).thenReturn(List.of());

        AccountPurgeService.AccountPurgeSummary summary = accountPurgeService.purgeEligibleAccounts(now);

        assertThat(summary.purgedCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isZero();
        assertThat(subscription.getUser()).isSameAs(deletedUser);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(subscription.getEndAt()).isEqualTo(now);
        assertThat(subscription.isCancelAtPeriodEnd()).isFalse();
        assertThat(subscription.getCancelledAt()).isEqualTo(now);
        assertThat(payment.getUser()).isSameAs(deletedUser);
        assertThat(redemption.getUser()).isSameAs(deletedUser);

        verify(quizShareLinkRepository).deleteByOwnerUserId(userId);
        verify(generatedQuizRepository).deleteByOwnerUserId(userId);
        verify(quickReviewSessionRepository).deleteByUserId(userId);
        verify(conceptHealthRepository).deleteByUserId(userId);
        verify(memorizationCardRepository).deleteByUserId(userId);
        verify(activityEventRepository).deleteByUserId(userId);
        verify(bulkGenerationResultRepository).deleteByOwnerUserId(userId);
        verify(publicNoteLikeRepository).deleteByUserId(userId);
        verify(userLibraryFilterRepository).deleteByUserId(userId);
        verify(userUsageRepository).deleteByUserId(userId);
        verify(studyPackDraftRepository).deleteByOwnerUserId(userId);
        verify(userAuthProviderRepository).deleteByUserId(userId);
        verify(refreshTokenRepository).deleteByUserId(userId);
        verify(emailVerificationTokenRepository).deleteByUserId(userId);
        verify(passwordResetTokenRepository).deleteByUserId(userId);
        verify(emailLogRepository).deleteByUserId(userId);
        verify(feedbackRepository).deleteByUserId(userId);
        verify(premiumWaitlistRepository).deleteByUserId(userId);
        verify(studyPackRepository).reassignOwnerByOwnerUserIdAndNoteIdIn(
                userId,
                AccountPurgeService.DELETED_USER_ID,
                List.of(publicNoteId),
                now
        );
        verify(noteRepository).reassignOwnerByOwnerUserIdAndVisibility(
                userId,
                AccountPurgeService.DELETED_USER_ID,
                NoteVisibility.PUBLIC,
                now
        );
        verify(studyPackRepository).deleteByOwnerUserIdExcludingNoteIds(userId, List.of(publicNoteId), false);
        verify(noteRepository).deleteByOwnerUserIdAndVisibility(userId, NoteVisibility.PRIVATE);
        verify(userRepository).delete(user);
    }

    @Test
    void purgeEligibleAccounts_usesConfiguredGraceBoundary() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-31T00:00:00Z");
        properties.getAccount().setDeletionGraceDays(30);
        when(userRepository.findByStatusAndDeletedAtLessThanEqual(UserStatus.PENDING_DELETION, now.minusDays(30)))
                .thenReturn(List.of());

        AccountPurgeService.AccountPurgeSummary summary = accountPurgeService.purgeEligibleAccounts(now);

        assertThat(summary.purgedCount()).isZero();
        assertThat(summary.failedCount()).isZero();
        verify(userRepository).findByStatusAndDeletedAtLessThanEqual(UserStatus.PENDING_DELETION, now.minusDays(30));
    }

    @Test
    void purgeEligibleAccounts_skipsReactivatedAccountEvenIfSelectedBeforeTransaction() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-31T00:00:00Z");
        UUID userId = UUID.randomUUID();
        UserEntity selected = pendingDeletionUser(userId, now.minusDays(31));
        UserEntity reactivated = activeUser(userId);
        when(userRepository.findByStatusAndDeletedAtLessThanEqual(UserStatus.PENDING_DELETION, now.minusDays(30)))
                .thenReturn(List.of(selected));
        when(userRepository.findById(userId)).thenReturn(Optional.of(reactivated));

        AccountPurgeService.AccountPurgeSummary summary = accountPurgeService.purgeEligibleAccounts(now);

        assertThat(summary.purgedCount()).isZero();
        assertThat(summary.failedCount()).isZero();
        verify(userRepository, never()).delete(any(UserEntity.class));
    }

    @Test
    void purgeEligibleAccounts_isolatesCandidateFailures() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-31T00:00:00Z");
        UUID failedUserId = UUID.randomUUID();
        UUID purgedUserId = UUID.randomUUID();
        UserEntity failedUser = pendingDeletionUser(failedUserId, now.minusDays(31));
        UserEntity purgedUser = pendingDeletionUser(purgedUserId, now.minusDays(31));
        when(userRepository.findByStatusAndDeletedAtLessThanEqual(UserStatus.PENDING_DELETION, now.minusDays(30)))
                .thenReturn(List.of(failedUser, purgedUser));
        when(userRepository.findById(failedUserId)).thenThrow(new IllegalStateException("boom"));
        when(userRepository.findById(purgedUserId)).thenReturn(Optional.of(purgedUser));
        when(userRepository.findById(AccountPurgeService.DELETED_USER_ID)).thenReturn(Optional.of(deletedUser()));
        when(noteRepository.findByOwnerUserIdAndVisibility(purgedUserId, NoteVisibility.PUBLIC)).thenReturn(List.of());
        when(subscriptionRepository.findByUser_Id(purgedUserId)).thenReturn(List.of());
        when(paymentTransactionRepository.findByUser_Id(purgedUserId)).thenReturn(List.of());
        when(voucherRedemptionRepository.findByUser_Id(purgedUserId)).thenReturn(List.of());
        when(noteCollectionRepository.findByOwnerUserId(purgedUserId)).thenReturn(List.of());

        AccountPurgeService.AccountPurgeSummary summary = accountPurgeService.purgeEligibleAccounts(now);

        assertThat(summary.purgedCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isEqualTo(1);
        verify(userRepository).delete(purgedUser);
    }

    private UserEntity pendingDeletionUser(UUID userId, OffsetDateTime deletedAt) {
        UserEntity user = activeUser(userId);
        user.setStatus(UserStatus.PENDING_DELETION);
        user.setDeletedAt(deletedAt);
        return user;
    }

    private UserEntity activeUser(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("user-" + userId + "@example.com");
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        return user;
    }

    private UserEntity deletedUser() {
        UserEntity user = activeUser(AccountPurgeService.DELETED_USER_ID);
        user.setEmail("deleted-user@notelib.internal");
        user.setStatus(UserStatus.SUSPENDED);
        return user;
    }

    private NoteEntity note(UUID noteId) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        return note;
    }

    private SubscriptionEntity activeSubscription(UserEntity user) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setPlanType(PlanType.PRO);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        return subscription;
    }

    private PaymentTransactionEntity payment(UserEntity user) {
        PaymentTransactionEntity payment = new PaymentTransactionEntity();
        payment.setId(UUID.randomUUID());
        payment.setUser(user);
        payment.setProvider(BillingProvider.XENDIT);
        payment.setBillingType(BillingType.SUBSCRIPTION);
        payment.setPlanType(PlanType.PRO);
        payment.setStatus(PaymentTransactionStatus.SUCCESS);
        payment.setAmount(BigDecimal.TEN);
        return payment;
    }

    private VoucherRedemptionEntity redemption(UserEntity user) {
        VoucherRedemptionEntity redemption = new VoucherRedemptionEntity();
        redemption.setId(UUID.randomUUID());
        redemption.setUser(user);
        return redemption;
    }
}
