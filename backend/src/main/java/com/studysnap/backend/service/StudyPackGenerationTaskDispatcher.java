package com.studysnap.backend.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class StudyPackGenerationTaskDispatcher {
    private final TaskExecutor taskExecutor;

    public StudyPackGenerationTaskDispatcher(
            @Qualifier("studyPackGenerationTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.taskExecutor = taskExecutor;
    }

    public void execute(Runnable task) {
        taskExecutor.execute(task);
    }
}
