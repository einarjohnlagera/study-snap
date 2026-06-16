package com.studysnap.backend.service;

import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.ProfileSetupRequiredException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingGuardService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public void assertProfileComplete(UUID userId) {
        UserEntity user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (user.getProfileType() == null) {
            throw new ProfileSetupRequiredException();
        }
    }
}
