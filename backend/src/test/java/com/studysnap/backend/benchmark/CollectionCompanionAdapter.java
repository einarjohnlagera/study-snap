package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.dto.GenerateCompanionRequest;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.service.NoteCollectionService;

/** Collection draft class-B entry point for preregistration sections 3, 6 and 7, Revision 2.2. */
final class CollectionCompanionAdapter extends AbstractClassBAdapter {
    private static final ObjectMapper JSON = new ObjectMapper();
    CollectionCompanionAdapter(ClassBCaseManifest.Case item) {
        super(item, ClassBCaseManifest.Workflow.COLLECTION_COMPANION);
    }

    @Override protected void validate() {
        if (item.requestedSections() == null || item.requestedSections().isEmpty()
                || item.companionContext() == null || item.companionContext().routing() != null
                || !item.companionContext().subjectPlans().isEmpty()
                    && !item.companionContext().notes().isEmpty())
            throw new InputDefect("Invalid collection companion fixture");
    }

    @Override protected void prepare(ClassBServiceHarness harness) {
        requireAdmin(harness.user);
        harness.collection(item.companionContext());
    }

    static void requireAdmin(UserEntity user) {
        if (user.getRole() != UserRole.ADMIN || user.getOnboardingCompletedAt() == null)
            throw new InputDefect("Collection companion fixture requires an onboarded admin");
    }

    @Override protected String invoke(ClassBServiceHarness harness) {
        var result = harness.service(NoteCollectionService.class).generateCompanion(
                harness.sourceId, harness.userId, new GenerateCompanionRequest(item.requestedSections()));
        try {
            return JSON.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new InvalidDeliveredOutput(ex);
        }
    }

    static final class InvalidDeliveredOutput extends RuntimeException {
        InvalidDeliveredOutput(Throwable cause) { super(cause); }
    }
}
