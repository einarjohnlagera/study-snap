package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.entity.VoucherRedemptionEntity;
import com.studysnap.backend.exception.DeletedUserSentinelNotFoundException;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.BulkGenerationResultRepository;
import com.studysnap.backend.repository.ConceptHealthRepository;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.EmailVerificationTokenRepository;
import com.studysnap.backend.repository.FeedbackRepository;
import com.studysnap.backend.repository.GeneratedQuizRepository;
import com.studysnap.backend.repository.MemorizationCardRepository;
import com.studysnap.backend.repository.NoteBulkRegenerationItemRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountPurgeService {
    public static final UUID DELETED_USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000d1ed");
    private static final List<UUID> EMPTY_RETAINED_NOTE_IDS_SENTINEL = List.of(
            UUID.fromString("00000000-0000-0000-0000-000000000000")
    );

    private final StudySnapProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final StudyPackDraftRepository studyPackDraftRepository;
    private final GeneratedQuizRepository generatedQuizRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final ConceptHealthRepository conceptHealthRepository;
    private final MemorizationCardRepository memorizationCardRepository;
    private final ActivityEventRepository activityEventRepository;
    private final BulkGenerationResultRepository bulkGenerationResultRepository;
    private final NoteBulkRegenerationItemRepository noteBulkRegenerationItemRepository;
    private final QuizShareLinkRepository quizShareLinkRepository;
    private final PublicNoteLikeRepository publicNoteLikeRepository;
    private final UserLibraryFilterRepository userLibraryFilterRepository;
    private final UserUsageRepository userUsageRepository;
    private final NoteCollectionRepository noteCollectionRepository;
    private final NoteCollectionItemRepository noteCollectionItemRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailLogRepository emailLogRepository;
    private final FeedbackRepository feedbackRepository;
    private final PremiumWaitlistRepository premiumWaitlistRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final VoucherRedemptionRepository voucherRedemptionRepository;

    public AccountPurgeSummary purgeEligibleAccounts(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minusDays(properties.getAccount().getDeletionGraceDays());
        List<UserEntity> candidates = userRepository.findByStatusAndDeletedAtLessThanEqual(
                UserStatus.PENDING_DELETION,
                cutoff
        );
        int purged = 0;
        int failed = 0;
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        for (UserEntity candidate : candidates) {
            try {
                Boolean didPurge = transactionTemplate.execute(status -> purgeUser(candidate.getId(), cutoff, now));
                if (Boolean.TRUE.equals(didPurge)) {
                    purged += 1;
                    log.info("account.purge userId={} outcome=purged", candidate.getId());
                }
            } catch (RuntimeException ex) {
                failed += 1;
                log.error("account.purge userId={} outcome=failed message={}", candidate.getId(), ex.getMessage(), ex);
            }
        }
        return new AccountPurgeSummary(purged, failed);
    }

    boolean purgeUser(UUID userId, OffsetDateTime cutoff, OffsetDateTime now) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.PENDING_DELETION || user.getDeletedAt() == null
                || user.getDeletedAt().isAfter(cutoff)) {
            return false;
        }

        UserEntity deletedUser = userRepository.findById(DELETED_USER_ID)
                .orElseThrow(DeletedUserSentinelNotFoundException::new);

        List<UUID> retainedPublicNoteIds = noteRepository
                .findByOwnerUserIdAndVisibility(userId, NoteVisibility.PUBLIC)
                .stream()
                .map(NoteEntity::getId)
                .toList();

        reassignFinancialRecords(userId, deletedUser, now);
        deletePersonalRows(userId);
        reassignPublicArtifacts(userId, retainedPublicNoteIds, now);
        deletePrivateArtifacts(userId, retainedPublicNoteIds);
        userRepository.delete(user);
        return true;
    }

    private void reassignFinancialRecords(UUID userId, UserEntity deletedUser, OffsetDateTime now) {
        List<SubscriptionEntity> subscriptions = subscriptionRepository.findByUser_Id(userId);
        for (SubscriptionEntity subscription : subscriptions) {
            subscription.setUser(deletedUser);
            if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
                subscription.setStatus(SubscriptionStatus.CANCELED);
                subscription.setEndAt(now);
                subscription.setCancelAtPeriodEnd(false);
                subscription.setCancelledAt(now);
            }
            subscription.setUpdatedAt(now);
        }
        subscriptionRepository.saveAll(subscriptions);

        List<PaymentTransactionEntity> transactions = paymentTransactionRepository.findByUser_Id(userId);
        for (PaymentTransactionEntity transaction : transactions) {
            transaction.setUser(deletedUser);
        }
        paymentTransactionRepository.saveAll(transactions);

        List<VoucherRedemptionEntity> redemptions = voucherRedemptionRepository.findByUser_Id(userId);
        for (VoucherRedemptionEntity redemption : redemptions) {
            redemption.setUser(deletedUser);
        }
        voucherRedemptionRepository.saveAll(redemptions);
    }

    private void deletePersonalRows(UUID userId) {
        quizShareLinkRepository.deleteByOwnerUserId(userId);
        generatedQuizRepository.deleteByOwnerUserId(userId);
        quickReviewSessionRepository.deleteByUserId(userId);
        conceptHealthRepository.deleteByUserId(userId);
        memorizationCardRepository.deleteByUserId(userId);
        activityEventRepository.deleteByUserId(userId);
        bulkGenerationResultRepository.deleteByOwnerUserId(userId);
        // Mirrors the line above: the bulk regeneration receipt is personal data with the same
        // lifetime obligations as the bulk generation one.
        noteBulkRegenerationItemRepository.deleteByOwnerUserId(userId);
        publicNoteLikeRepository.deleteByUserId(userId);
        userLibraryFilterRepository.deleteByUserId(userId);
        userUsageRepository.deleteByUserId(userId);
        studyPackDraftRepository.deleteByOwnerUserId(userId);

        List<UUID> collectionIds = noteCollectionRepository.findByOwnerUserId(userId)
                .stream()
                .map(collection -> collection.getId())
                .toList();
        if (!collectionIds.isEmpty()) {
            noteCollectionItemRepository.deleteByCollectionIdIn(collectionIds);
        }
        noteCollectionRepository.deleteByOwnerUserId(userId);

        userAuthProviderRepository.deleteByUserId(userId);
        refreshTokenRepository.deleteByUserId(userId);
        emailVerificationTokenRepository.deleteByUserId(userId);
        passwordResetTokenRepository.deleteByUserId(userId);
        emailLogRepository.deleteByUserId(userId);
        feedbackRepository.deleteByUserId(userId);
        premiumWaitlistRepository.deleteByUserId(userId);
    }

    private void reassignPublicArtifacts(UUID userId, List<UUID> retainedPublicNoteIds, OffsetDateTime now) {
        if (retainedPublicNoteIds.isEmpty()) {
            return;
        }
        studyPackRepository.reassignOwnerByOwnerUserIdAndNoteIdIn(
                userId,
                DELETED_USER_ID,
                retainedPublicNoteIds,
                now
        );
        noteRepository.reassignOwnerByOwnerUserIdAndVisibility(
                userId,
                DELETED_USER_ID,
                NoteVisibility.PUBLIC,
                now
        );
    }

    private void deletePrivateArtifacts(UUID userId, List<UUID> retainedPublicNoteIds) {
        boolean retainedPublicNoteIdsEmpty = retainedPublicNoteIds.isEmpty();
        List<UUID> retainedIdsForQuery = retainedPublicNoteIdsEmpty
                ? EMPTY_RETAINED_NOTE_IDS_SENTINEL
                : retainedPublicNoteIds;
        studyPackRepository.deleteByOwnerUserIdExcludingNoteIds(
                userId,
                retainedIdsForQuery,
                retainedPublicNoteIdsEmpty
        );
        noteRepository.deleteByOwnerUserIdAndVisibility(userId, NoteVisibility.PRIVATE);
    }

    public record AccountPurgeSummary(int purgedCount, int failedCount) {
    }
}
