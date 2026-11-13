package com.studysnap.backend.benchmark;

import com.studysnap.backend.config.StudySnapProperties;

/** Section 5 route-complete arm injection, including the critique route. */
final class ArmRouting {
    private ArmRouting() {}
    static StudySnapProperties forModel(String model, String key) {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("Arm model is required");
        StudySnapProperties properties = new StudySnapProperties();
        properties.getLlm().getApi().setApiKey(LlmModelBenchmark.Config.requireKeyForTest(key));
        properties.getSettings().setModelFree(model);
        properties.getSettings().setModelPremium(model);
        properties.getSettings().setModelInteractiveQuiz(model);
        properties.getSettings().setModelPaidInteractive(model);
        properties.getSettings().setModelCritique(model);
        return properties;
    }
}
