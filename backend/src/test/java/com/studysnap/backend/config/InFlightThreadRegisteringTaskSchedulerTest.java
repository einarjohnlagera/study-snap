package com.studysnap.backend.config;

import com.studysnap.backend.service.RetentionService;
import com.studysnap.backend.service.jobs.RetentionEmailScheduler;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.Task;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InFlightThreadRegisteringTaskSchedulerTest {

    @Test
    void instrumentationFailuresNeverReplaceTaskExecutionOrItsOwnFailure() {
        InFlightRequestRegistry registry = mock(InFlightRequestRegistry.class);
        doThrow(new IllegalStateException("register failed"))
                .when(registry).registerCurrentThread(anyString());
        doThrow(new IllegalStateException("remove failed"))
                .when(registry).removeCurrentThread();
        InFlightThreadRegisteringTaskDecorator decorator =
                new InFlightThreadRegisteringTaskDecorator(registry);
        AtomicBoolean ran = new AtomicBoolean();

        assertThatCode(() -> decorator.decorate(() -> ran.set(true)).run()).doesNotThrowAnyException();
        assertThat(ran).isTrue();
        verify(registry).removeCurrentThread();

        IllegalArgumentException taskFailure = new IllegalArgumentException("task failed");
        assertThatThrownBy(() -> decorator.decorate(() -> {
            throw taskFailure;
        }).run()).isSameAs(taskFailure);
    }

    @Test
    void scheduledMethodUsesItsClassAndMethodDescriptionAndCleansUpNormally() throws Exception {
        InFlightRequestRegistry registry = new InFlightRequestRegistry();
        InFlightThreadRegisteringTaskScheduler scheduler = scheduler(registry);
        BlockingScheduledJob job = new BlockingScheduledJob();
        Runnable runnable = scheduledMethod(job, "run");

        try {
            ScheduledFuture<?> future = scheduler.schedule(runnable, Instant.now());
            assertThat(job.started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.snapshot().values())
                    .singleElement()
                    .extracting(InFlightRequestRegistry.InFlightRequest::path)
                    .isEqualTo(BlockingScheduledJob.class.getName() + ".run");

            job.release.countDown();
            future.get(2, TimeUnit.SECONDS);
            assertThat(registry.snapshot()).isEmpty();
        } finally {
            job.release.countDown();
            scheduler.shutdown();
        }
    }

    @Test
    void scheduledMethodIsRemovedWhenItThrows() throws Exception {
        InFlightRequestRegistry registry = new InFlightRequestRegistry();
        InFlightThreadRegisteringTaskScheduler scheduler = scheduler(registry);
        ThrowingScheduledJob job = new ThrowingScheduledJob();

        try {
            ScheduledFuture<?> future = scheduler.schedule(
                    scheduledMethod(job, "run"),
                    Instant.now()
            );

            assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(registry.snapshot()).isEmpty();
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void poolSaturationPollNeverRegistersItselfDuringOrAfterItsCycle() throws Exception {
        InFlightRequestRegistry registry = new InFlightRequestRegistry();
        InFlightThreadRegisteringTaskScheduler scheduler = scheduler(registry);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
        CountDownLatch poolReadStarted = new CountDownLatch(1);
        CountDownLatch releasePoolRead = new CountDownLatch(1);
        when(dataSource.getHikariPoolMXBean()).thenReturn(pool);
        when(dataSource.getMaximumPoolSize()).thenReturn(20);
        when(pool.getActiveConnections()).thenAnswer(invocation -> {
            poolReadStarted.countDown();
            releasePoolRead.await(2, TimeUnit.SECONDS);
            return 0;
        });
        PoolSaturationDetector detector = new PoolSaturationDetector(dataSource, registry);

        try {
            ScheduledFuture<?> future = scheduler.schedule(
                    scheduledMethod(detector, "poll"),
                    Instant.now()
            );
            assertThat(poolReadStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.snapshot()).isEmpty();

            releasePoolRead.countDown();
            future.get(2, TimeUnit.SECONDS);
            assertThat(registry.snapshot()).isEmpty();
        } finally {
            releasePoolRead.countDown();
            scheduler.shutdown();
        }
    }

    @Test
    void retentionCronsAreRegisteredAgainstAsiaManilaWithTheCustomScheduler() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(SchedulingConfiguration.class)) {
            ScheduledTaskHolder taskHolder = context.getBean(ScheduledTaskHolder.class);
            Map<String, CronTrigger> retentionTriggers = taskHolder.getScheduledTasks().stream()
                    .map(ScheduledTask::getTask)
                    .filter(TriggerTask.class::isInstance)
                    .map(TriggerTask.class::cast)
                    .filter(task -> task.getRunnable().toString()
                            .startsWith(RetentionEmailScheduler.class.getName() + "."))
                    .collect(java.util.stream.Collectors.toMap(
                            task -> task.getRunnable().toString()
                                    .substring(RetentionEmailScheduler.class.getName().length() + 1),
                            task -> (CronTrigger) task.getTrigger()
                    ));

            assertThat(retentionTriggers).containsKeys("runDaily", "runWeekly", "runMonthly");
            Instant base = Instant.parse("2026-01-01T00:00:00Z");
            assertThat(nextExecution(retentionTriggers.get("runDaily"), base))
                    .isEqualTo(Instant.parse("2026-01-01T18:45:00Z"));
            assertThat(nextExecution(retentionTriggers.get("runWeekly"), base))
                    .isEqualTo(Instant.parse("2026-01-04T10:00:00Z"));
        }
    }

    private static Instant nextExecution(CronTrigger trigger, Instant base) {
        SimpleTriggerContext context = new SimpleTriggerContext();
        context.update(base, base, base);
        return trigger.nextExecution(context);
    }

    private static Runnable scheduledMethod(Object target, String methodName) throws NoSuchMethodException {
        return new Task(new ScheduledMethodRunnable(target, methodName)).getRunnable();
    }

    private static InFlightThreadRegisteringTaskScheduler scheduler(InFlightRequestRegistry registry) {
        InFlightThreadRegisteringTaskScheduler scheduler = new InFlightThreadRegisteringTaskScheduler(
                new InFlightThreadRegisteringTaskDecorator(registry)
        );
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.initialize();
        return scheduler;
    }

    static final class BlockingScheduledJob {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        public void run() throws InterruptedException {
            started.countDown();
            release.await();
        }
    }

    static final class ThrowingScheduledJob {
        public void run() {
            throw new IllegalStateException("scheduled job failed");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingConfiguration {
        @Bean
        InFlightRequestRegistry inFlightRequestRegistry() {
            return new InFlightRequestRegistry();
        }

        @Bean
        InFlightThreadRegisteringTaskDecorator taskDecorator(InFlightRequestRegistry registry) {
            return new AppConfig().inFlightThreadRegisteringTaskDecorator(registry);
        }

        @Bean
        org.springframework.scheduling.TaskScheduler taskScheduler(
                InFlightThreadRegisteringTaskDecorator taskDecorator
        ) {
            return new AppConfig().taskScheduler(taskDecorator);
        }

        @Bean
        RetentionEmailScheduler retentionEmailScheduler() {
            return new RetentionEmailScheduler(mock(RetentionService.class));
        }
    }
}
