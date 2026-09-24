package com.studysnap.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

import java.util.concurrent.RunnableScheduledFuture;

/** Registers executor and scheduled-job threads while their tasks are running. */
public class InFlightThreadRegisteringTaskDecorator implements TaskDecorator {
    private static final Logger log = LoggerFactory.getLogger(InFlightThreadRegisteringTaskDecorator.class);

    private final InFlightRequestRegistry registry;

    public InFlightThreadRegisteringTaskDecorator(InFlightRequestRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Runnable decorate(Runnable runnable) {
        // ThreadPoolTaskScheduler decorates its internal future rather than the user Runnable.
        // The scheduler below decorates that user Runnable explicitly so recurring trigger tasks
        // retain their description on every reschedule; do not wrap the internal future twice.
        if (runnable instanceof RunnableScheduledFuture<?>) {
            return runnable;
        }
        return instrument(runnable, null);
    }

    Runnable decorateScheduledTask(Runnable scheduledTask) {
        String description = scheduledDescription(scheduledTask);
        if (isPoolSaturationPoll(scheduledTask, description)) {
            return scheduledTask;
        }
        return instrument(scheduledTask, description);
    }

    private Runnable instrument(Runnable runnable, String fixedDescription) {
        return () -> {
            String description = fixedDescription != null
                    ? fixedDescription
                    : Thread.currentThread().getName();
            try {
                registry.registerCurrentThread(description);
            } catch (RuntimeException exception) {
                log.warn("in-flight task registration failed; running task without instrumentation", exception);
            }

            try {
                runnable.run();
            } finally {
                try {
                    registry.removeCurrentThread();
                } catch (RuntimeException exception) {
                    log.warn("in-flight task cleanup failed after task completion", exception);
                }
            }
        };
    }

    private String scheduledDescription(Runnable task) {
        try {
            // Spring's Task wrapper delegates to ScheduledMethodRunnable.toString(), preserving the
            // exact declaring-class and method name.
            return task.toString();
        } catch (RuntimeException exception) {
            log.warn("in-flight scheduled-task description failed; using its thread name", exception);
            return null;
        }
    }

    private boolean isPoolSaturationPoll(Runnable task, String description) {
        if (task instanceof ScheduledMethodRunnable scheduledMethod) {
            return scheduledMethod.getMethod().getDeclaringClass() == PoolSaturationDetector.class;
        }
        return (PoolSaturationDetector.class.getName() + ".poll").equals(description);
    }
}
