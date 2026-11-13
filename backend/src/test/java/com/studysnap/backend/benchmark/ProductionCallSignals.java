package com.studysnap.backend.benchmark;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.studysnap.backend.service.impl.OpenAiLlmStudyPackService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.LoggerFactory;

/** Captures the production llm_call signal for preregistration section 7. */
final class ProductionCallSignals implements AutoCloseable {
    private static final String SIGNAL_MISMATCH = "INCONCLUSIVE_SIGNAL_MISMATCH";
    record Signal(String operation, String route, String model, int attempt, String outcome) {}
    private final Logger logger = (Logger) LoggerFactory.getLogger(OpenAiLlmStudyPackService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final String threadName = Thread.currentThread().getName();

    ProductionCallSignals() {
        appender.start();
        logger.addAppender(appender);
    }

    List<Signal> signals() {
        List<Signal> result = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            if (!threadName.equals(event.getThreadName())) continue;
            String line = event.getFormattedMessage();
            if (!line.startsWith("action=llm_call ")) continue;
            result.add(new Signal(between(line, " operation=", " route="), field(line, "route"),
                    field(line, "model"), Integer.parseInt(field(line, "attempt")),
                    field(line, "outcome")));
        }
        return List.copyOf(result);
    }

    StageRunner.CallResult result(String generationId, boolean delivered, String failureClass, int httpCalls) {
        List<Signal> signals = signals();
        if (signals.size() != httpCalls)
            return new StageRunner.CallResult(false, Set.of(), SIGNAL_MISMATCH);
        List<String> groupIds = new ArrayList<>();
        List<AttemptOutcome.Kind> kinds = new ArrayList<>();
        List<Integer> attempts = new ArrayList<>();
        Set<Integer> parseFailures = new HashSet<>();
        int group = 0;
        for (int index = 0; index < signals.size(); index++) {
            Signal signal = signals.get(index);
            if (signal.attempt() == 1) group++;
            if (group == 0) return new StageRunner.CallResult(false, Set.of(), SIGNAL_MISMATCH);
            if (!Set.of("SUCCESS", "SCHEMA_OR_PARSE", "HTTP_ERROR", "TIMEOUT").contains(signal.outcome()))
                return new StageRunner.CallResult(false, Set.of(), SIGNAL_MISMATCH);
            groupIds.add(generationId + "-call-" + group);
            attempts.add(signal.attempt());
            kinds.add(switch (signal.outcome()) {
                case "HTTP_ERROR" -> AttemptOutcome.Kind.HTTP_ERROR;
                case "TIMEOUT" -> AttemptOutcome.Kind.TIMEOUT;
                default -> AttemptOutcome.Kind.OK;
            });
            if ("SCHEMA_OR_PARSE".equals(signal.outcome())) parseFailures.add(index);
        }
        return new StageRunner.CallResult(delivered, Set.of(), failureClass,
                List.copyOf(groupIds), Set.copyOf(parseFailures), List.copyOf(kinds), List.copyOf(attempts));
    }

    private static String field(String line, String name) {
        String marker = " " + name + "=";
        int start = line.indexOf(marker);
        if (start < 0) throw new IllegalArgumentException("Missing production signal field: " + name);
        start += marker.length();
        int end = line.indexOf(' ', start);
        return line.substring(start, end < 0 ? line.length() : end);
    }

    private static String between(String line, String prefix, String suffix) {
        int start = line.indexOf(prefix);
        if (start < 0) throw new IllegalArgumentException("Missing production signal field: " + prefix);
        start += prefix.length();
        int end = line.indexOf(suffix, start);
        if (end < 0) throw new IllegalArgumentException("Missing production signal field: " + suffix);
        return line.substring(start, end);
    }

    @Override public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
