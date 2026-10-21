package com.studysnap.backend.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class StudyPackGenerationTaskDispatcher {
    private final TaskExecutor taskExecutor;
    private final TaskExecutor challengePreparationTaskExecutor;

    @Autowired
    public StudyPackGenerationTaskDispatcher(
            @Qualifier("studyPackGenerationTaskExecutor") TaskExecutor taskExecutor,
            @Qualifier("challengePreparationTaskExecutor") TaskExecutor challengePreparationTaskExecutor
    ) {
        this.taskExecutor = taskExecutor;
        this.challengePreparationTaskExecutor = challengePreparationTaskExecutor;
    }

    /** Convenience constructor for callers that only exercise Study Pack dispatch. */
    public StudyPackGenerationTaskDispatcher(TaskExecutor taskExecutor) {
        this(taskExecutor, taskExecutor);
    }

    public void execute(Runnable task) {
        taskExecutor.execute(task);
    }

    public void executeChallengePreparation(Runnable task) {
        challengePreparationTaskExecutor.execute(task);
    }
}
