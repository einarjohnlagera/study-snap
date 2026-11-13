package com.studysnap.backend.benchmark;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.studysnap.backend.service.impl.OpenAiLlmStudyPackService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.LoggerFactory;

/** Captures and aligns all production calls by batch for preregistration sections 5 and 7. */
final class ProductionCallSignals implements AutoCloseable {
    static final String SIGNAL_MISMATCH = "INCONCLUSIVE_SIGNAL_MISMATCH";
    private static final String SCHEMA_OR_PARSE = "SCHEMA_OR_PARSE";
    private static final String HTTP_ERROR = "HTTP_ERROR";
    private static final String TIMEOUT = "TIMEOUT";
    private static final String MISSING_FIELD = "Missing production signal field: ";
    record Signal(String operation, String route, String model, int attempt, String outcome, String batchId) {}
    private final Logger logger = (Logger) LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
    private final Queue<ILoggingEvent> events = new ConcurrentLinkedQueue<>();
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
        @Override protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            events.add(event);
        }
    };

    ProductionCallSignals() {
        appender.start();
        logger.addAppender(appender);
    }

    List<Signal> signals() {
        List<Signal> result = new ArrayList<>();
        for (ILoggingEvent event : events) {
            String line = event.getFormattedMessage();
            if (!line.startsWith("action=llm_call ")) continue;
            result.add(new Signal(between(line, " operation=", " route="), field(line, "route"),
                    field(line, "model"), Integer.parseInt(field(line, "attempt")),
                    field(line, "outcome"), event.getMDCPropertyMap().get(BatchTaggingExecutor.MDC_KEY)));
        }
        return List.copyOf(result);
    }

    StageRunner.CallResult result(String caseId, boolean delivered, String failureClass,
            List<ArmAssertingInterceptor.CallReceipt> receipts) {
        List<Signal> signals = signals();
        if (signals.size() != receipts.size()) return mismatch();
        Map<String, Queue<Signal>> tagged = new HashMap<>();
        Queue<Signal> sequential = new ArrayDeque<>();
        for (Signal signal : signals) {
            if (signal.batchId() == null) sequential.add(signal);
            else tagged.computeIfAbsent(signal.batchId(), ignored -> new ArrayDeque<>()).add(signal);
        }
        List<String> groupIds = new ArrayList<>();
        List<AttemptOutcome.Kind> kinds = new ArrayList<>();
        List<Integer> attempts = new ArrayList<>();
        Set<Integer> parseFailures = new HashSet<>();
        Map<String, Integer> lastAttempt = new HashMap<>();
        int sequence = 0;
        String sequentialGroup = null;
        for (int index = 0; index < receipts.size(); index++) {
            MeteringInterceptor.Call call = receipts.get(index).call();
            String batch = call.generationId().startsWith(caseId + "-batch-") ? call.generationId() : null;
            Queue<Signal> matching = batch == null ? sequential : tagged.get(batch);
            Signal signal = matching == null ? null : matching.poll();
            if (signal == null || !Set.of("SUCCESS", SCHEMA_OR_PARSE, HTTP_ERROR, TIMEOUT)
                    .contains(signal.outcome())) return mismatch();
            String group;
            if (batch == null) {
                if (signal.attempt() == 1) sequentialGroup = caseId + "-seq-" + ++sequence;
                group = sequentialGroup;
            } else {
                group = batch;
            }
            if (group == null || signal.attempt() != lastAttempt.getOrDefault(group, 0) + 1)
                return mismatch();
            lastAttempt.put(group, signal.attempt());
            groupIds.add(group);
            attempts.add(signal.attempt());
            kinds.add(switch (signal.outcome()) {
                case HTTP_ERROR -> AttemptOutcome.Kind.HTTP_ERROR;
                case TIMEOUT -> AttemptOutcome.Kind.TIMEOUT;
                default -> AttemptOutcome.Kind.OK;
            });
            if (SCHEMA_OR_PARSE.equals(signal.outcome())) parseFailures.add(index);
        }
        if (!sequential.isEmpty() || tagged.values().stream().anyMatch(queue -> !queue.isEmpty()))
            return mismatch();
        return new StageRunner.CallResult(delivered, Set.of(), failureClass,
                List.copyOf(groupIds), Set.copyOf(parseFailures), List.copyOf(kinds), List.copyOf(attempts));
    }

    private static StageRunner.CallResult mismatch() {
        return new StageRunner.CallResult(false, Set.of(), SIGNAL_MISMATCH);
    }

    private static String field(String line, String name) {
        String marker = " " + name + "=";
        int start = line.indexOf(marker);
        if (start < 0) throw new IllegalArgumentException(MISSING_FIELD + name);
        start += marker.length();
        int end = line.indexOf(' ', start);
        return line.substring(start, end < 0 ? line.length() : end);
    }

    private static String between(String line, String prefix, String suffix) {
        int start = line.indexOf(prefix);
        if (start < 0) throw new IllegalArgumentException(MISSING_FIELD + prefix);
        start += prefix.length();
        int end = line.indexOf(suffix, start);
        if (end < 0) throw new IllegalArgumentException(MISSING_FIELD + suffix);
        return line.substring(start, end);
    }

    @Override public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
