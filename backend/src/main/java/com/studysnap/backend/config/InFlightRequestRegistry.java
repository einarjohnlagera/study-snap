package com.studysnap.backend.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cross-thread view of requests currently executing inside the servlet filter chain.
 *
 * <p>A {@link ThreadLocal} cannot serve this purpose: the saturation poller runs on a scheduler
 * thread and must be able to inspect the request threads. Entries are keyed by the actual worker
 * thread and are removed by {@link InFlightRequestTrackingFilter} in a {@code finally} block.
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
