package com.studysnap.backend.benchmark;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.MDC;
import org.springframework.core.task.AsyncTaskExecutor;

/** Tags production Long Exam submissions for preregistration sections 3, 5 and 7. */
final class BatchTaggingExecutor implements AsyncTaskExecutor, AutoCloseable {
    static final String MDC_KEY = "benchBatch";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private final String caseId;
    private final ExecutorService delegate;
    private final AtomicInteger submissions = new AtomicInteger();
    private final AtomicInteger outstanding = new AtomicInteger();
    private final Object idle = new Object();

    BatchTaggingExecutor(String caseId, ExecutorService delegate) {
        this.caseId = caseId;
        this.delegate = delegate;
    }

    static String currentBatch() { return CURRENT.get(); }
    int submissions() { return submissions.get(); }

    @Override public void execute(Runnable task) {
        String batch = caseId + "-batch-" + submissions.incrementAndGet();
        outstanding.incrementAndGet();
        try {
            delegate.execute(() -> {
                CURRENT.set(batch);
                MDC.put(MDC_KEY, batch);
                try {
                    task.run();
                } finally {
                    CURRENT.remove();
                    MDC.remove(MDC_KEY);
                    synchronized (idle) {
                        outstanding.decrementAndGet();
                        idle.notifyAll();
                    }
                }
            });
        } catch (RuntimeException exception) {
            synchronized (idle) {
                outstanding.decrementAndGet();
                idle.notifyAll();
            }
            throw exception;
        }
    }

    boolean awaitQuiescence(Duration bound) {
        long deadline = System.nanoTime() + bound.toNanos();
        synchronized (idle) {
            while (outstanding.get() != 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                try {
                    idle.wait(Math.max(1, remaining / 1_000_000), (int) (remaining % 1_000_000));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    @Override public void close() { delegate.shutdownNow(); }
}
