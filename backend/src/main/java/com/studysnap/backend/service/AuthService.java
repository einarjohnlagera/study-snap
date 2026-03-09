package com.studysnap.backend.service;

import com.studysnap.backend.dto.AuthResponse;
import com.studysnap.backend.dto.LoginRequest;
import com.studysnap.backend.dto.MeResponse;
import com.studysnap.backend.dto.SignupRequest;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException("EMAIL_ALREADY_EXISTS", "This email is already registered.", HttpStatus.CONFLICT);
        }

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setDisplayName(resolveDisplayName(request.displayName(), request.firstName()));
        user.setCountryCode(normalizeCountryCode(request.countryCode()));
        user.setProfileType(request.profileType() == null ? ProfileType.STUDENT : request.profileType());
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        UserEntity saved = userRepository.save(user);
        subscriptionService.createDefaultFreeSubscription(saved);
        return buildAuthResponse(saved, PlanType.FREE);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AppException("INVALID_CREDENTIALS", "Invalid email or password.", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException("INVALID_CREDENTIALS", "Invalid email or password.", HttpStatus.UNAUTHORIZED);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException("ACCOUNT_INACTIVE", "This account is not active.", HttpStatus.FORBIDDEN);
        }

        user.setLastLoginAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        PlanType planType = subscriptionService.resolvePlan(user.getId());
        return buildAuthResponse(user, planType);
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));

        return new MeResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getCountryCode(),
                user.getProfileType(),
                user.getStatus(),
                subscriptionService.resolvePlan(user.getId())
        );
    }

    private AuthResponse buildAuthResponse(UserEntity user, PlanType planType) {
        String token = UUID.randomUUID().toString();
        return new AuthResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getProfileType(),
                planType,
                token
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveDisplayName(String displayName, String firstName) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return firstName == null ? null : firstName.trim();
    }
}
