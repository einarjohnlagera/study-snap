package com.studysnap.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "studysnap")
@Getter
public class StudySnapProperties {
    private final Settings settings = new Settings();
    private final Ocr ocr = new Ocr();
    private final Llm llm = new Llm();
    private final QuickReview quickReview = new QuickReview();
    private final Pricing pricing = new Pricing();
    private final Billing billing = new Billing();
    private final Email email = new Email();

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
        private int freeMonthlyStudyPackLimit = 5;
        private int premiumMonthlyStudyPackLimit = 100;
        private int premiumMonthlyChallengeQuizLimit = 50;
        private int premiumMonthlyAdaptivePracticeLimit = 50;
    }

    @Getter
    @Setter
    public static class Billing {
        private final Stripe stripe = new Stripe();
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
    public static class Email {
        private String resendApiKey = "";
        private String resendApiBaseUrl = "https://api.resend.com";
        private String from = "";
        private String appBaseUrl = "http://localhost:3000";
        private int verificationTokenHours = 24;
        private int resendCooldownSeconds = 60;
    }
}
