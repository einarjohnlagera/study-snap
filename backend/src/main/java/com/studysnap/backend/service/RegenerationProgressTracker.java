package com.studysnap.backend.service;

import com.studysnap.backend.dto.AdminRegenerationStatusResponse;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RegenerationProgressTracker {

    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public void startRun(int packCount) {
        total.set(packCount);
        processed.set(0);
        failed.set(0);
        running.set(packCount > 0);
    }

    public void recordSuccess() {
        processed.incrementAndGet();
        checkCompletion();
    }

    public void recordFailure() {
        failed.incrementAndGet();
        checkCompletion();
    }

    private void checkCompletion() {
        if (processed.get() + failed.get() >= total.get()) {
            running.set(false);
        }
    }

    public AdminRegenerationStatusResponse getStatus() {
        return new AdminRegenerationStatusResponse(
                total.get(),
                processed.get(),
                failed.get(),
                !running.get()
        );
    }
}
