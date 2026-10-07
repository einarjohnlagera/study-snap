package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.GoalAdoptionJobStatus;
import com.studysnap.backend.repository.GoalAdoptionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoalAdoptionRecoveryService {
    private final GoalAdoptionJobRepository repository;
    private final NoteCollectionService collectionService;
    private final StudySnapProperties properties;

    public void reenqueueRecoverableJobs() {
        StudySnapProperties.GoalAdoption settings = properties.getGoalAdoption();
        List<UUID> ids = repository.findRecoverableIds(
                List.of(GoalAdoptionJobStatus.PENDING),
                GoalAdoptionJobStatus.RUNNING,
                OffsetDateTime.now().minusMinutes(settings.getStaleMinutes()),
                PageRequest.of(0, settings.getRecoveryBatchSize()));
        ids.forEach(collectionService::enqueueGoalAdoption);
    }
}
