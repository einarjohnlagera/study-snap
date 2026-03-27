package com.studysnap.backend.service;

import com.studysnap.backend.entity.PremiumWaitlistEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.PremiumWaitlistRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PremiumWaitlistService {
    private static final String SUCCESS_MESSAGE = "You're on the list! We'll notify you when Premium launches.";

    private final PremiumWaitlistRepository premiumWaitlistRepository;
    private final UserRepository userRepository;
    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;

    @Transactional
    public String joinWaitlist(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        requireVerifiedEmail(user);

        if (premiumWaitlistRepository.existsByUserId(userId)) {
            return SUCCESS_MESSAGE;
        }

        OffsetDateTime now = OffsetDateTime.now();
        PremiumWaitlistEntity entity = new PremiumWaitlistEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setEmail(user.getEmail());
        entity.setCreatedAt(now);
        premiumWaitlistRepository.save(entity);

        sendConfirmationEmail(user);
        return SUCCESS_MESSAGE;
    }

    private void sendConfirmationEmail(UserEntity user) {
        try {
            EmailTemplateService.RenderedEmailTemplate rendered = emailTemplateService.render(
                    "premium-waitlist-confirmation",
                    Map.of(
                            "name", resolveFirstName(user),
                            "dashboardUrl", "https://www.notelib.app/dashboard"
                    )
            );
            emailService.sendEmail(new EmailMessage(user.getEmail(), rendered.subject(), rendered.htmlBody(), rendered.textBody()));
        } catch (RuntimeException ex) {
            log.warn("premium.waitlist.email failed userId={} message={}", user.getId(), ex.getMessage());
        }
    }

    private String resolveFirstName(UserEntity user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return user.getFirstName().trim();
        }
        return "there";
    }

    private void requireVerifiedEmail(UserEntity user) {
        if (user.getEmailVerifiedAt() != null) {
            return;
        }
        throw new AppException(
                "EMAIL_VERIFICATION_REQUIRED",
                "Verify your email before joining the Premium waitlist.",
                null,
                "RESEND_VERIFICATION",
                HttpStatus.FORBIDDEN
        );
    }
}
