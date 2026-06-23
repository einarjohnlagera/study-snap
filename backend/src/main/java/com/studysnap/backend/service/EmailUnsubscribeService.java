package com.studysnap.backend.service;

import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailUnsubscribeService {
    private final UnsubscribeTokenService unsubscribeTokenService;
    private final UserRepository userRepository;

    @Transactional
    public UnsubscribeResult unsubscribe(String token) {
        UnsubscribeTokenService.VerifiedUnsubscribeToken verified = unsubscribeTokenService.verify(token);
        userRepository.findById(verified.userId()).ifPresent(user -> applyPreference(user, verified.category()));
        return new UnsubscribeResult(verified.category(), verified.category().displayName());
    }

    private void applyPreference(UserEntity user, UnsubscribeCategory category) {
        switch (category) {
            case MARKETING -> user.setMarketingEmailsEnabled(false);
            case WEEKLY_SUMMARY -> user.setWeeklySummaryRemindersEnabled(false);
            case STUDY_REMINDERS -> user.setInactivityRemindersEnabled(false);
            case WEAK_CONCEPT -> user.setWeakConceptRemindersEnabled(false);
        }
    }

    public record UnsubscribeResult(UnsubscribeCategory category, String displayName) {
    }
}
