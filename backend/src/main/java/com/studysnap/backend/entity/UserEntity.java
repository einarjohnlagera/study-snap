package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "pending_email")
    private String pendingEmail;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "username", nullable = false, unique = true, length = 30)
    private String username;

    @Column(name = "bio", length = 200)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "learner_level", length = 32)
    private LearnerLevel learnerLevel;

    @Column(name = "course_program", length = 120)
    private String courseProgram;

    @Column(name = "study_goal", columnDefinition = "text")
    private String studyGoal;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "focus_subjects", columnDefinition = "text[]", nullable = false)
    private String[] focusSubjects = new String[0];

    @Column(name = "school_name", length = 120)
    private String schoolName;

    @Column(name = "public_profile_visible", nullable = false)
    private Boolean publicProfileVisible;

    @Column(name = "country_code", length = 8)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_type")
    private ProfileType profileType;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "engagement_mode", nullable = false, length = 32)
    private EngagementMode engagementMode;

    @Column(name = "inactivity_reminders_enabled", nullable = false)
    private Boolean inactivityRemindersEnabled;

    @Column(name = "weak_concept_reminders_enabled", nullable = false)
    private Boolean weakConceptRemindersEnabled;

    @Column(name = "weekly_summary_reminders_enabled", nullable = false)
    private Boolean weeklySummaryRemindersEnabled;

    @Column(name = "marketing_emails_enabled", nullable = false)
    private Boolean marketingEmailsEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_preference", nullable = false, length = 16)
    private ThemePreference themePreference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak;

    @Column(name = "longest_streak", nullable = false)
    private Integer longestStreak;

    @Column(name = "last_study_date")
    private LocalDate lastStudyDate;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_password_change_at")
    private OffsetDateTime lastPasswordChangeAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "onboarding_completed_at")
    private OffsetDateTime onboardingCompletedAt;

    @Column(name = "product_onboarding_completed_at")
    private OffsetDateTime productOnboardingCompletedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
