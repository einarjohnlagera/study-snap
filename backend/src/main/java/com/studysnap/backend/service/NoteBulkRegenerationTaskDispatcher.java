package com.studysnap.backend.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Dispatches the bulk regeneration DRIVER onto its own pool.
 *
 * <p>⚠️ Deliberately a separate component from {@code StudyPackGenerationTaskDispatcher} rather than a
 * second constructor on it: the two are qualified to different executors on purpose, and collapsing
 * them would reintroduce the starvation this pool exists to remove. The per-item generation work still
 * runs on the generation pool — only the loop that drives it lives here.
 */
@Component
public class NoteBulkRegenerationTaskDispatcher {
    private final TaskExecutor taskExecutor;

    public NoteBulkRegenerationTaskDispatcher(
            @Qualifier("bulkRegenerationTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.taskExecutor = taskExecutor;
    }

    public void execute(Runnable task) {
        taskExecutor.execute(task);
    }
}
