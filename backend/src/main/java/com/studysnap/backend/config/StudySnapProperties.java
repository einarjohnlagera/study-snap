package com.studysnap.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "studysnap")
@Getter
public class StudySnapProperties {
    private final Limits limits = new Limits();
    private final Ocr ocr = new Ocr();
    private final Llm llm = new Llm();

    @Getter
    @Setter
    public static class Limits {
        private int maxNotesChars = 12000;
        private long maxImageBytes = 5_000_000;
    }

    @Getter
    @Setter
    public static class Ocr {
        private double confidenceThreshold = 0.8;
    }

    @Getter
    @Setter
    public static class Llm {
        private String provider = "openai";
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";
        private String modelFree = "gpt-4.1-mini";
        private String modelPremium = "gpt-4.1";
        private int quizQuestionsFree = 5;
    }
}
