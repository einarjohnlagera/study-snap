package com.studysnap.backend.config;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.PlanType;
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
        private int premiumMonthlyStudyPackLimit = 100;
        private int freeMonthlyChallengeQuizLimit = 5;
        private int premiumMonthlyChallengeQuizLimit = 50;
        private int premiumMonthlyAdaptivePracticeLimit = 30;
        private int freeMonthlyOcrLimit = 20;
        private int premiumMonthlyOcrLimit = 100;
        private boolean adaptivePracticePremiumOnly = true;
        private boolean difficultySelectionPremiumOnly = true;

        public int resolveMonthlyStudyPackLimit(PlanType planType) {
            return planType == PlanType.PREMIUM
                    ? premiumMonthlyStudyPackLimit
                    : freeMonthlyStudyPackLimit;
        }

        public int resolveMonthlyChallengeQuizLimit(PlanType planType) {
            return planType == PlanType.PREMIUM
                    ? premiumMonthlyChallengeQuizLimit
                    : freeMonthlyChallengeQuizLimit;
        }

        public int resolveMonthlyAdaptivePracticeLimit(PlanType planType) {
            if (planType != PlanType.PREMIUM && adaptivePracticePremiumOnly) {
                return 0;
            }
            return premiumMonthlyAdaptivePracticeLimit;
        }

        public int resolveMonthlyOcrLimit(PlanType planType) {
            return planType == PlanType.PREMIUM
                    ? premiumMonthlyOcrLimit
                    : freeMonthlyOcrLimit;
        }

        public boolean isAdaptivePracticeAvailable(PlanType planType) {
            return !adaptivePracticePremiumOnly || planType == PlanType.PREMIUM;
        }

        public boolean isDifficultySelectionAvailable(PlanType planType) {
            return !difficultySelectionPremiumOnly || planType == PlanType.PREMIUM;
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
        private BillingProvider provider = BillingProvider.PAYMONGO;
        private final Stripe stripe = new Stripe();
        private final Paymongo paymongo = new Paymongo();
        private Map<String, RegionPricing> pricingRegions = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class Stripe {
        private String apiBaseUrl = "https://api.stripe.com/v1";
        private String secretKey = "";
        private String webhookSecret = "";
        private String premiumPriceId = "";
        private String checkoutSuccessUrl = "http://localhost:3000/settings?checkout=success";
        private String checkoutCancelUrl = "http://localhost:3000/settings?checkout=cancel";
    }

    @Getter
    @Setter
    public static class Paymongo {
        private String apiBaseUrl = "https://api.paymongo.com/v1";
        private String secretKey = "";
        private String webhookSecret = "";
        private String monthlyPlanId = "";
        private String yearlyPlanId = "";
        private BigDecimal monthlyAmount = new BigDecimal("4.99");
        private BigDecimal yearlyAmount = new BigDecimal("39.99");
        private String checkoutSuccessUrl = "http://localhost:3000/settings?checkout=success";
        private String checkoutCancelUrl = "http://localhost:3000/settings?checkout=cancel";
    }

    @Getter
    @Setter
    public static class RegionPricing {
        private String currency = "USD";
        private BigDecimal monthlyPrice = new BigDecimal("4.99");
        private BigDecimal yearlyPrice = new BigDecimal("39.99");
        private String paymongoMonthlyPlanId = "";
        private String paymongoYearlyPlanId = "";
        private String paymongoIntroMonthlyPlanId = "";
        private String paymongoIntroYearlyPlanId = "";
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
