package com.studysnap.backend.config;

import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.ProfileType;
import java.math.RoundingMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "studysnap")
@Getter
public class StudySnapProperties {
    private final Settings settings = new Settings();
    private final Ocr ocr = new Ocr();
    private final Llm llm = new Llm();
    private final QuizGeneration quizGeneration = new QuizGeneration();
    private final QuickReview quickReview = new QuickReview();
    private final Pricing pricing = new Pricing();
    private final Limits limits = new Limits();
    private final Billing billing = new Billing();
    private final Email email = new Email();
    private final Retention retention = new Retention();

    @Setter
    private String appName = "NoteLib";

    @Getter
    @Setter
    public static class Settings {
        private int maxNotesChars = 12000;
        private long maxImageBytes = 5_000_000;
        private int quizQuestionsFree = 5;
        private String modelFree = "gpt-4.1-mini";
        private String modelPremium = "gpt-4.1";
        private String modelCritique = "gpt-4.1-mini";
        private String promptDir = "prompts/study-pack-v1";
    }

    @Getter
    @Setter
    public static class Ocr {
        private String provider = "stub";
        private double confidenceThreshold = 0.8;
        private int minDetectedChars = 30;
        private int minDetectedWords = 6;
        private double hardRejectConfidence = 0.45;
        private int freeMonthlyUploadLimit = 20;
        private int premiumMonthlyUploadLimit = 200;
        private long freeMaxImageBytes = 5_000_000;
        private long premiumMaxImageBytes = 10_000_000;
        private int maxPagesPerUpload = 1;
        private int freeRateLimitPerMinute = 6;
        private int premiumRateLimitPerMinute = 20;
        private String googleApplicationCredentials = "";
        private String googleApplicationCredentialsJson = "";
    }

    @Getter
    @Setter
    public static class Llm {
        private final Api api = new Api();
    }

    @Getter
    @Setter
    public static class Api {
        private String provider = "openai";
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";
    }

    @Getter
    @Setter
    public static class QuizGeneration {
        private String mode = "real";
        private int mockDelayMs = 0;
    }

    @Getter
    @Setter
    public static class QuickReview {
        private final StudyTip studyTip = new StudyTip();
    }

    @Getter
    @Setter
    public static class StudyTip {
        private boolean enabled = true;
        private int minIncorrectCount = 1;
        private int maxQuestions = 3;
    }

    @Getter
    @Setter
    public static class Pricing {
        private int freeMonthlyStudyPackLimit = 10;
        private int plusMonthlyStudyPackLimit = 50;
        private int proMonthlyStudyPackLimit = 100;
        private int freeMonthlyChallengeQuizLimit = 5;
        private int plusMonthlyChallengeQuizLimit = 25;
        private int proMonthlyChallengeQuizLimit = 50;
        private int plusMonthlyAdaptivePracticeLimit = 10;
        private int proMonthlyAdaptivePracticeLimit = 30;
        private int proMonthlyInterviewPracticeLimit = 10;
        private int proMonthlyLongExamLimit = 10;
        private int proMonthlyBoardExamLimit = 5;
        private int freeMonthlyOcrLimit = 20;
        private int plusMonthlyOcrLimit = 50;
        private int proMonthlyOcrLimit = 100;
        private int freeMonthlyNoteGenerationLimit = 5;
        private int plusMonthlyNoteGenerationLimit = 25;
        private int proMonthlyNoteGenerationLimit = 100;
        private int freeMonthlyDocxExportLimit = 2;
        private int plusMonthlyDocxExportLimit = 15;
        private int proMonthlyDocxExportLimit = -1;
        private int freeTeacherMonthlyDocxExportLimit = 10;
        private int plusTeacherMonthlyDocxExportLimit = -1;
        private int proTeacherMonthlyDocxExportLimit = -1;
        private int freeMonthlyQuizShareLinkLimit = 3;
        private int plusMonthlyQuizShareLinkLimit = 10;
        private int proMonthlyQuizShareLinkLimit = 0;
        private int freeMonthlyPdfExportLimit = 2;
        private int plusMonthlyPdfExportLimit = 15;
        private int proMonthlyPdfExportLimit = -1;
        private boolean adaptivePracticeProOnly = false;
        private boolean interviewPracticeProOnly = true;
        private boolean difficultySelectionProOnly = true;
        private boolean longExamAvailableForPro = true;
        private int longExamLowTierCount = 20;
        private int longExamMidTierCount = 25;
        private int longExamHighTierCount = 30;
        private int longExamPoolSize = 48;
        private int boardExamPoolSize = 24;

        public int resolveMonthlyStudyPackLimit(PlanType planType) {
            return switch (normalizePlanType(planType)) {
                case PLUS -> plusMonthlyStudyPackLimit;
                case PRO -> proMonthlyStudyPackLimit;
                case FREE -> freeMonthlyStudyPackLimit;
            };
        }

        public int resolveMonthlyChallengeQuizLimit(PlanType planType) {
            return switch (normalizePlanType(planType)) {
                case PLUS -> plusMonthlyChallengeQuizLimit;
                case PRO -> proMonthlyChallengeQuizLimit;
                case FREE -> freeMonthlyChallengeQuizLimit;
            };
        }

        public int resolveMonthlyAdaptivePracticeLimit(PlanType planType) {
            PlanType normalizedPlanType = normalizePlanType(planType);
            if (adaptivePracticeProOnly && normalizedPlanType != PlanType.PRO) {
                return 0;
            }
            return switch (normalizedPlanType) {
                case PRO -> proMonthlyAdaptivePracticeLimit;
                case PLUS -> plusMonthlyAdaptivePracticeLimit;
                case FREE -> 0;
            };
        }

        public int resolveMonthlyInterviewPracticeLimit(PlanType planType) {
            PlanType normalizedPlanType = normalizePlanType(planType);
            if (interviewPracticeProOnly && normalizedPlanType != PlanType.PRO) {
                return 0;
            }
            return normalizedPlanType == PlanType.PRO ? proMonthlyInterviewPracticeLimit : 0;
        }

        public int resolveMonthlyLongExamLimit(PlanType planType) {
            if (!longExamAvailableForPro || normalizePlanType(planType) != PlanType.PRO) {
                return 0;
            }
            return proMonthlyLongExamLimit;
        }

        public int resolveMonthlyBoardExamLimit(PlanType planType) {
            if (normalizePlanType(planType) != PlanType.PRO) {
                return 0;
            }
            return proMonthlyBoardExamLimit;
        }

        public int resolveMonthlyOcrLimit(PlanType planType) {
            return switch (normalizePlanType(planType)) {
                case PLUS -> plusMonthlyOcrLimit;
                case PRO -> proMonthlyOcrLimit;
                case FREE -> freeMonthlyOcrLimit;
            };
        }

        public int resolveMonthlyNoteGenerationLimit(PlanType planType) {
            return switch (normalizePlanType(planType)) {
                case PLUS -> plusMonthlyNoteGenerationLimit;
                case PRO -> proMonthlyNoteGenerationLimit;
                case FREE -> freeMonthlyNoteGenerationLimit;
            };
        }

        public Integer resolveMonthlyDocxExportLimit(PlanType planType, ProfileType profileType) {
            boolean isTeacher = profileType == ProfileType.TEACHER;
            int limit = switch (normalizePlanType(planType)) {
                case PLUS -> isTeacher ? plusTeacherMonthlyDocxExportLimit : plusMonthlyDocxExportLimit;
                case PRO -> isTeacher ? proTeacherMonthlyDocxExportLimit : proMonthlyDocxExportLimit;
                case FREE -> isTeacher ? freeTeacherMonthlyDocxExportLimit : freeMonthlyDocxExportLimit;
            };
            return unlimitedToNull(limit);
        }

        public Integer resolveMonthlyPdfExportLimit(PlanType planType) {
            int limit = switch (normalizePlanType(planType)) {
                case PLUS -> plusMonthlyPdfExportLimit;
                case PRO -> proMonthlyPdfExportLimit;
                case FREE -> freeMonthlyPdfExportLimit;
            };
            return unlimitedToNull(limit);
        }

        public Integer resolveMonthlyQuizShareLinkLimit(PlanType planType) {
            int limit = switch (normalizePlanType(planType)) {
                case PLUS -> plusMonthlyQuizShareLinkLimit;
                case PRO -> proMonthlyQuizShareLinkLimit;
                case FREE -> freeMonthlyQuizShareLinkLimit;
            };
            return unlimitedToNull(limit);
        }

        public boolean isAdaptivePracticeAvailable(PlanType planType) {
            PlanType normalizedPlanType = normalizePlanType(planType);
            if (!adaptivePracticeProOnly) {
                return normalizedPlanType != PlanType.FREE;
            }
            return normalizedPlanType == PlanType.PRO;
        }

        public boolean isDifficultySelectionAvailable(PlanType planType) {
            PlanType normalizedPlanType = normalizePlanType(planType);
            if (!difficultySelectionProOnly) {
                return normalizedPlanType != PlanType.FREE;
            }
            return normalizedPlanType == PlanType.PRO;
        }

        public boolean isLongExamAvailable(PlanType planType) {
            return longExamAvailableForPro && normalizePlanType(planType) == PlanType.PRO;
        }

        public boolean isInterviewPracticeAvailable(PlanType planType) {
            PlanType normalizedPlanType = normalizePlanType(planType);
            if (!interviewPracticeProOnly) {
                return normalizedPlanType != PlanType.FREE;
            }
            return normalizedPlanType == PlanType.PRO;
        }

        private PlanType normalizePlanType(PlanType planType) {
            return planType == null ? PlanType.FREE : planType;
        }

        private Integer unlimitedToNull(int limit) {
            return limit <= 0 ? null : limit;
        }
    }

    @Getter
    @Setter
    public static class Limits {
        private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

        private long fileUploadMaxSize = 10;
        private long txtUploadMaxSize = 1;
        private long pdfUploadMaxSize = 10;
        private long docxUploadMaxSize = 10;
        private int pdfMaxPages = 30;
        private int extractedTextMaxLength = 200_000;
        private int freeAiRateLimitPerMinute = 5;
        private int premiumAiRateLimitPerMinute = 20;
        private int freeOcrRateLimitPerMinute = 6;
        private int premiumOcrRateLimitPerMinute = 20;

        public long getFileUploadMaxSizeBytes() {
            return toBytes(fileUploadMaxSize);
        }

        public long getTxtUploadMaxSizeBytes() {
            return toBytes(txtUploadMaxSize);
        }

        public long getPdfUploadMaxSizeBytes() {
            return toBytes(pdfUploadMaxSize);
        }

        public long getDocxUploadMaxSizeBytes() {
            return toBytes(docxUploadMaxSize);
        }

        private long toBytes(long megabytes) {
            return Math.max(1L, megabytes) * BYTES_PER_MEGABYTE;
        }
    }

    @Getter
    @Setter
    public static class Billing {
        private String frontendBaseUrl = "http://localhost:3000";
        private String backendBaseUrl = "http://localhost:8080";
        private String priceIdPlaceholder = "not_used_for_now";
        private final Xendit xendit = new Xendit();
        private Map<String, RegionPricing> pricingRegions = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class Xendit {
        private String baseUrl = "https://api.xendit.co";
        private String secretKey = "";
        private String webhookToken = "";
    }

    @Getter
    @Setter
    public static class RegionPricing {
        private String currency = "USD";
        private boolean active = true;
        private final PaidPlanPricing plus = new PaidPlanPricing();
        private final PaidPlanPricing pro = new PaidPlanPricing();

        public PaidPlanPricing resolvePlanPricing(PlanType planType) {
            return switch (planType) {
                case PLUS -> plus;
                case PRO -> pro;
                default -> throw new IllegalArgumentException("Pricing is only configured for paid plans.");
            };
        }
    }

    @Getter
    @Setter
    public static class PaidPlanPricing {
        private final BillingCyclePricing monthly = new BillingCyclePricing();
        private final BillingCyclePricing yearly = new BillingCyclePricing();
    }

    @Getter
    @Setter
    public static class BillingCyclePricing {
        private BigDecimal amount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private int durationDays = 30;
        private boolean active = true;
    }

    @Getter
    @Setter
    public static class Email {
        private String resendApiKey = "";
        private String resendApiBaseUrl = "https://api.resend.com";
        private String from = "";
        private String support = "support@mail.notelib.app";
        private String appBaseUrl = "http://localhost:3000";
        private int verificationTokenHours = 24;
        private int resendCooldownSeconds = 60;
    }

    @Getter
    @Setter
    public static class Retention {
        private String dailyCron = "0 45 2 * * *";
        private String weeklyCron = "0 0 18 * * SUN";
        private int inactivityDays = 3;
        private int inactivityCooldownDays = 3;
        private int weakConceptInactivityDays = 3;
        private int weakConceptCooldownDays = 5;
        private int unfinishedNoteDays = 2;
        private int unfinishedNoteCooldownDays = 3;
        private int weeklyCooldownDays = 7;
        private int weakConceptThresholdPercent = 60;
    }
}
