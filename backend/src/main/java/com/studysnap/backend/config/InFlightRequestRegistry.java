package com.studysnap.backend.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cross-thread view of HTTP requests, executor work, and scheduled jobs currently running.
 *
 * <p>A {@link ThreadLocal} cannot serve this purpose: the saturation poller runs on a scheduler
 * thread and must be able to inspect other worker threads. Entries are keyed by the actual worker
 * thread and are removed by their registering filter or task decorator in a {@code finally} block.
 */
@Component
public class InFlightRequestRegistry {
    private final ConcurrentHashMap<Thread, InFlightRequest> requests = new ConcurrentHashMap<>();

    public void registerCurrentThread(String path) {
        requests.put(Thread.currentThread(), new InFlightRequest(path, Instant.now()));
    }

    public void removeCurrentThread() {
        requests.remove(Thread.currentThread());
    }

    public Map<Thread, InFlightRequest> snapshot() {
        return Map.copyOf(requests);
    }

    public record InFlightRequest(String path, Instant startedAt) {
    }
}
