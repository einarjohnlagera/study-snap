package com.studysnap.backend.service;

import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.BulkRegenerationNotPermittedException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Curator gate for bulk regeneration.
 *
 * <p>⚠️ THE ENDPOINTS' {@code @PreAuthorize} CANNOT EXPRESS THIS. It reads
 * {@code hasAnyRole('USER','ADMIN')}, which every authenticated account satisfies, and curator status
 * here is TEACHER-by-profile or ADMIN-by-role — a distinction Spring's role expressions do not carry.
 * Without this guard bulk regeneration would be reachable by every learner, which is wider than the
 * capability was scoped to.
 *
 * <p>⚠️ Reuses {@link CuratorAuthoringPredicate} rather than restating {@code role == ADMIN ||
 * profileType == TEACHER}. That predicate also refuses an account mid-onboarding, and a bare role
 * comparison substituted for it has previously survived an entire test suite.
 *
 * <p>⚠️ This gates WHO MAY RUN A BATCH. It is not a quota bypass and grants no entitlement: a TEACHER
 * curator is metered normally under block-and-reduce.
 */
@Service
@RequiredArgsConstructor
public class BulkRegenerationAccessGuard {
    private final UserRepository userRepository;

    public void assertCurator(UUID userId) {
        UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (!CuratorAuthoringPredicate.isCurator(user)) {
            throw new BulkRegenerationNotPermittedException();
        }
    }
}
