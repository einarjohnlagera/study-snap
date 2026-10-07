package com.studysnap.backend.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Consumer;

@Component
public class GoalAdoptionDispatcher {
    private final AsyncTaskExecutor executor;

    public GoalAdoptionDispatcher(@Qualifier("goalAdoptionTaskExecutor") AsyncTaskExecutor executor) {
        this.executor = executor;
    }

    public void dispatch(UUID jobId, Consumer<UUID> worker) {
        executor.execute(() -> worker.accept(jobId));
    }
}
