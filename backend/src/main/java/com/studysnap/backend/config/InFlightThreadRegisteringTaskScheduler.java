package com.studysnap.backend.config;

import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/** Preserves the user task while Spring decorates its internal scheduled-execution callback. */
final class InFlightThreadRegisteringTaskScheduler extends ThreadPoolTaskScheduler {
    private final InFlightThreadRegisteringTaskDecorator taskDecorator;

    InFlightThreadRegisteringTaskScheduler(InFlightThreadRegisteringTaskDecorator taskDecorator) {
        this.taskDecorator = taskDecorator;
        setTaskDecorator(taskDecorator);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        return super.schedule(taskDecorator.decorateScheduledTask(task), trigger);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        return super.schedule(taskDecorator.decorateScheduledTask(task), startTime);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
        return super.scheduleAtFixedRate(taskDecorator.decorateScheduledTask(task), startTime, period);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        return super.scheduleAtFixedRate(taskDecorator.decorateScheduledTask(task), period);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        return super.scheduleWithFixedDelay(taskDecorator.decorateScheduledTask(task), startTime, delay);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        return super.scheduleWithFixedDelay(taskDecorator.decorateScheduledTask(task), delay);
    }
}
