package com.studysnap.backend.service;

import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicResponse;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.CourseProgramSelectionRequiredException;
import com.studysnap.backend.exception.DuplicateCourseProgramException;
import com.studysnap.backend.exception.MultiProgramDomainContextRequiredException;
import com.studysnap.backend.exception.UnknownCourseProgramException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.util.CourseProgramNormalizationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteGenerationService {
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;
    private final NoteGenerationUsageProtectionService noteGenerationUsageProtectionService;
    private final LlmStudyPackService llmStudyPackService;
    private final ContentModerationService contentModerationService;
    private final OnboardingGuardService onboardingGuardService;
    private final StudyPackGenerationContextResolver generationContextResolver;
    private final CourseProgramCatalogRepository courseProgramCatalogRepository;

    private static String firstNonBlank(String primary, String fallback) {
        return (primary != null && !primary.isBlank()) ? primary : fallback;
    }

    public GenerateNoteFromTopicResponse generateFromTopic(GenerateNoteFromTopicRequest request, UUID userId) {
        return generateFromTopic(request, userId, null);
    }

    public GenerateNoteFromTopicResponse generateFromTopic(
            GenerateNoteFromTopicRequest request,
            UUID userId,
            StudyPackGenerationContext resolvedContext
    ) {
        onboardingGuardService.assertProfileComplete(userId);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        noteGenerationUsageProtectionService.assertQuotaAvailable(userId, subscriptionService.resolvePlan(userId));
        String normalizedTopic = request.topic().trim();
        contentModerationService.validateOrThrow(normalizedTopic);
        StudyPackGenerationContext context = resolvedContext == null
                ? resolveAuthoringContext(request, user)
                : resolvedContext;
        String generatedContent = llmStudyPackService.generateNoteFromTopic(normalizedTopic, context);
        noteGenerationUsageProtectionService.recordUsage(userId, OffsetDateTime.now(ZoneOffset.UTC));
        return new GenerateNoteFromTopicResponse(generatedContent);
    }

    private StudyPackGenerationContext resolveAuthoringContext(
            GenerateNoteFromTopicRequest request,
            UserEntity user
    ) {
        DomainContext domainContext = NoteAuthoringMetadataParser.parseDomainContextOrThrow(request.domainContext());
        if (CuratorAuthoringPredicate.isCurator(user)) {
            Set<UUID> courseProgramIds = validateCuratedProgramIds(request.courseProgramIds());
            if (courseProgramIds.size() > 1 && domainContext == null) {
                throw new MultiProgramDomainContextRequiredException();
            }
            return generationContextResolver.resolveForBulkGeneration(
                    user.getId(),
                    List.copyOf(courseProgramIds),
                    null,
                    null,
                    domainContext,
                    null
            );
        }

        String courseProgramText = CourseProgramNormalizationUtils.normalizeForStorage(
                firstNonBlank(request.courseProgramText(), user.getCourseProgram()));
        if (courseProgramText == null) {
            throw new CourseProgramSelectionRequiredException();
        }
        return generationContextResolver.resolveForBulkGeneration(
                user.getId(),
                List.of(),
                courseProgramText,
                null,
                domainContext,
                null
        );
    }

    private Set<UUID> validateCuratedProgramIds(List<UUID> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new CourseProgramSelectionRequiredException();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new DuplicateCourseProgramException();
        }
        if (courseProgramCatalogRepository.findExistingIds(uniqueIds).size() != uniqueIds.size()) {
            throw new UnknownCourseProgramException();
        }
        return uniqueIds;
    }

}
