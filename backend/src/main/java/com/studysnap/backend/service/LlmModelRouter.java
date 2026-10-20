package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.ModelTier;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.LlmRoute;
import com.studysnap.backend.service.model.LlmRouting;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmModelRouter {
    private static final Logger log = LoggerFactory.getLogger(LlmModelRouter.class);
    private static final String TIER_FREE = "FREE";
    private static final String TIER_PAID = "PAID";
    private static final String TIER_ADMIN = "ADMIN";
    public static final String TIER_NOT_APPLICABLE = "n/a";

    private final StudySnapProperties properties;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    public Selection resolve(LlmRouting routing) {
        if (routing == null || routing.route() == null) {
            throw new IllegalArgumentException("An explicit LLM route is required");
        }
        return switch (routing.route()) {
            case INTERACTIVE_QUIZ -> new Selection(routing.route(),
                    properties.getSettings().getModelInteractiveQuiz(), TIER_NOT_APPLICABLE, null);
            case PAID_INTERACTIVE -> new Selection(routing.route(),
                    properties.getSettings().getModelPaidInteractive(), TIER_NOT_APPLICABLE, null);
            case CRITIQUE -> throw new IllegalArgumentException("Critique uses its existing model resolver");
            case TIERED -> resolveTiered(routing);
        };
    }

    private Selection resolveTiered(LlmRouting routing) {
        if (routing.tierUserId() == null) {
            log.warn("LLM tier user is missing; routing to FREE userId={}", routing.tierUserId());
            return freeSelection();
        }
        try {
            UserRole role = userRepository.findById(routing.tierUserId())
                    .orElseThrow(UserNotFoundException::new).getRole();
            boolean paid = subscriptionService.getPlanSnapshot(routing.tierUserId()).planType().isPaid();
            if (role == UserRole.ADMIN) {
                return new Selection(LlmRoute.TIERED, properties.getSettings().getModelPremium(),
                        TIER_ADMIN, paid ? ModelTier.PREMIUM : ModelTier.FREE);
            }
            return paid
                    ? new Selection(LlmRoute.TIERED, properties.getSettings().getModelPremium(), TIER_PAID, ModelTier.PREMIUM)
                    : freeSelection();
        } catch (UserNotFoundException ex) {
            log.warn("LLM tier user is missing; routing to FREE userId={}", routing.tierUserId());
            return freeSelection();
        }
    }

    private Selection freeSelection() {
        return new Selection(LlmRoute.TIERED, properties.getSettings().getModelFree(), TIER_FREE, ModelTier.FREE);
    }

    public record Selection(LlmRoute route, String model, String tier, ModelTier modelTier) {
    }
}
