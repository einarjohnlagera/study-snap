package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Cumulative pre-dispatch spend accounting for preregistration section 5. */
final class StageCapLedger implements AutoCloseable {
    enum Stage { STAGE_1, STAGE_2, STAGE_3 }
    static final class SpendStop extends RuntimeException {
        SpendStop(String message) { super(message); }
    }
    static final class LedgerFailure extends RuntimeException {
        LedgerFailure(String message) { super(message); }
        LedgerFailure(String message, Throwable cause) { super(message, cause); }
    }
    private static final int SCALE = 9;
    private static final String BAD_PATH = "Ledger path must be outside repository and backend/target: ";
    private static final String UNMETERED_REASON = "UNMETERED response: ";
    private static final String MISSING_FIELD = "Missing ";
    private static final String TYPE = "type";
    private static final String RESERVATION_ID = "reservationId";
    private static final String INIT = "INIT";
    private static final String RESERVE = "RESERVE";
    private static final String SETTLE = "SETTLE";
    private static final String UNMETERED = "UNMETERED";
    private static final String ABANDON = "ABANDON";
    private static final String STOP = "STOP";
    private static final String AMEND = "AMEND";
    private static final String RECONCILE = "RECONCILE";
    private static final String RESUME = "RESUME";
    private static final String STAGE_FIELD = "stage";
    private static final String REASON = "reason";
    private static final String OLD = "old";
    private static final String NEW = "new";
    private static final String FORECAST = "forecast";
    private static final String CREATED_AT = "createdAt";
    private static final String CONSERVATIVE = "conservative";
    private static final String CELL = "cell";
    private static final String CAPS = "caps";
    private static final String BILLED = "billed";
    private static final String ACTUAL_USD = "actualUsd";
    private static final String LOCK_HELD = "Ledger lock held";
    private static final String LINE_END = "\n";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Map<Stage, BigDecimal> caps = new EnumMap<>(Stage.class);
    private final Map<Stage, BigDecimal> spent = new EnumMap<>(Stage.class);
    private final Map<Stage, BigDecimal> billed = new EnumMap<>(Stage.class);
    private final Map<Stage, BigDecimal> inFlight = new EnumMap<>(Stage.class);
    private final Map<Stage, Integer> attempts = new EnumMap<>(Stage.class);
    private final Map<String, BigDecimal> cellSpent = new HashMap<>();
    private final Map<String, Pending> pending = new HashMap<>();
    private final Set<String> usedIds = new HashSet<>();
    private BigDecimal largestOvershoot = money(BigDecimal.ZERO);
    private String stopReason;
    private FileChannel channel;
    private FileLock lock;
    private Path file;
    private Instant createdAt;
    private int recordCount;
    private boolean closed;
    private record Pending(Stage stage, String cell, BigDecimal forecast) {}

    StageCapLedger(Map<Stage, BigDecimal> approvedCaps) {
        caps.putAll(checkedCaps(approvedCaps));
        for (Stage stage : Stage.values()) {
            spent.put(stage, money(BigDecimal.ZERO));
            billed.put(stage, money(BigDecimal.ZERO));
            inFlight.put(stage, money(BigDecimal.ZERO));
            attempts.put(stage, 0);
        }
    }

    private static Map<Stage, BigDecimal> checkedCaps(Map<Stage, BigDecimal> approvedCaps) {
        Objects.requireNonNull(approvedCaps);
        Map<Stage, BigDecimal> result = new EnumMap<>(Stage.class);
        approvedCaps.forEach((stage, cap) -> {
            if (stage == null || cap == null || cap.signum() <= 0)
                throw new IllegalArgumentException("Approved stage caps must be positive and explicit");
            result.put(stage, money(cap));
        });
        return result;
    }

    static StageCapLedger open(Path ledgerFile, Map<Stage, BigDecimal> approvedCaps, boolean init) {
        Objects.requireNonNull(ledgerFile);
        Map<Stage, BigDecimal> expected = checkedCaps(approvedCaps);
        rejectLedgerPath(ledgerFile);
        boolean exists = Files.exists(ledgerFile);
        if (init == exists) throw new LedgerFailure(init ? "Ledger already exists" : "Ledger missing; explicit init required");
        StageCapLedger ledger = new StageCapLedger(expected);
        ledger.file = ledgerFile;
        try {
            ledger.channel = init
                    ? FileChannel.open(ledgerFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE)
                    : FileChannel.open(ledgerFile, StandardOpenOption.READ, StandardOpenOption.WRITE);
            ledger.lock = ledger.channel.tryLock();
            if (ledger.lock == null) throw new LedgerFailure(LOCK_HELD);
            if (init) {
                ledger.createdAt = Instant.now();
                ledger.append(Map.of(TYPE, INIT, CREATED_AT, ledger.createdAt.toString(), CAPS, expected));
            }
            else ledger.replay();
            if (!ledger.caps.equals(expected)) throw new LedgerFailure("Ledger caps differ from run-time caps; amend explicitly");
            return ledger;
        } catch (IOException | RuntimeException ex) {
            ledger.close();
            if (ex instanceof OverlappingFileLockException) throw new LedgerFailure(LOCK_HELD, ex);
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new LedgerFailure("Cannot open ledger", ex);
        }
    }

    private static void rejectLedgerPath(Path path) {
        try {
            Path repo = StageRunner.findRepo(Path.of("").toAbsolutePath().normalize()).toRealPath();
            Path absolute = path.toAbsolutePath().normalize();
            if (absolute.startsWith(repo))
                throw new IllegalArgumentException(BAD_PATH + path);
            Path ancestor = absolute;
            while (!Files.exists(ancestor)) ancestor = ancestor.getParent();
            Path candidate = ancestor.toRealPath().resolve(ancestor.relativize(absolute));
            if (candidate.startsWith(repo) || candidate.startsWith(repo.resolve("backend/target")))
                throw new IllegalArgumentException(BAD_PATH + path);
        } catch (IOException ex) { throw new LedgerFailure("Cannot validate ledger path", ex); }
    }

    private void replay() throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length == 0 || bytes[bytes.length - 1] != '\n') throw new LedgerFailure("Corrupt or truncated ledger");
        String[] lines = new String(bytes, StandardCharsets.UTF_8).split(LINE_END, -1);
        for (int index = 0; index < lines.length - 1; index++) {
            try {
                JsonNode row = JSON.readTree(lines[index]);
                if (row == null || !row.isObject()) throw new IllegalArgumentException("Not an object");
                String type = requiredText(row, TYPE);
                if (index == 0 && !INIT.equals(type)) throw new IllegalArgumentException("INIT missing");
                if (index > 0 && INIT.equals(type)) throw new IllegalArgumentException("Duplicate INIT");
                replayRecord(type, row);
                recordCount++;
            } catch (Exception ex) { throw new LedgerFailure("Corrupt ledger line " + (index + 1), ex); }
        }
    }

    private void replayRecord(String type, JsonNode row) {
        switch (type) {
            case INIT -> {
                createdAt = Instant.parse(requiredText(row, CREATED_AT));
                caps.clear(); caps.putAll(readCaps(row.get(CAPS)));
            }
            case AMEND -> {
                if (!caps.equals(readCaps(row.get(OLD)))) throw new IllegalArgumentException("AMEND old caps mismatch");
                caps.clear(); caps.putAll(readCaps(row.get(NEW)));
            }
            case RESERVE -> {
                String id = requiredText(row, RESERVATION_ID);
                if (!usedIds.add(id)) throw new IllegalArgumentException("Duplicate reservation");
                Stage stage = Stage.valueOf(requiredText(row, STAGE_FIELD));
                if (!caps.containsKey(stage)) throw new IllegalArgumentException("Unapproved stage");
                String cell = requiredText(row, CELL);
                BigDecimal forecast = requiredMoney(row, FORECAST);
                if (forecast.signum() <= 0) throw new IllegalArgumentException("Invalid forecast");
                pending.put(id, new Pending(stage, cell, forecast));
                spent.put(stage, spent.get(stage).add(forecast));
                cellSpent.merge(cell, forecast, BigDecimal::add);
                attempts.put(stage, attempts.get(stage) + 1);
            }
            case SETTLE, UNMETERED, ABANDON, RECONCILE -> {
                Pending entry = pending.remove(requiredText(row, RESERVATION_ID));
                if (entry == null) throw new IllegalArgumentException("Unknown or closed reservation");
                BigDecimal actual = switch (type) {
                    case SETTLE -> requiredMoney(row, CONSERVATIVE);
                    case RECONCILE -> requiredMoney(row, ACTUAL_USD);
                    default -> money(BigDecimal.ZERO);
                };
                if (actual.signum() < 0) throw new IllegalArgumentException("Negative actual");
                spent.put(entry.stage, spent.get(entry.stage).subtract(entry.forecast).add(actual));
                cellSpent.merge(entry.cell, actual.subtract(entry.forecast), BigDecimal::add);
                if (SETTLE.equals(type)) {
                    BigDecimal billedActual = requiredMoney(row, BILLED);
                    if (billedActual.signum() < 0) throw new IllegalArgumentException("Negative billed amount");
                    billed.put(entry.stage, billed.get(entry.stage).add(billedActual));
                }
                if (UNMETERED.equals(type)) stopReason = UNMETERED_REASON + entry.stage;
                BigDecimal overshoot = actual.subtract(entry.forecast);
                if (overshoot.compareTo(largestOvershoot) > 0) largestOvershoot = overshoot;
            }
            case STOP -> { if (stopReason == null) stopReason = requiredText(row, REASON); }
            case RESUME -> stopReason = null;
            default -> throw new IllegalArgumentException("Unknown ledger record");
        }
    }

    private static String requiredText(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank())
            throw new IllegalArgumentException(MISSING_FIELD + field);
        return value.asText();
    }
    private static BigDecimal requiredMoney(JsonNode row, String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isNumber()) throw new IllegalArgumentException(MISSING_FIELD + field);
        return money(value.decimalValue());
    }
    private static Map<Stage, BigDecimal> readCaps(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("Missing caps");
        Map<Stage, BigDecimal> result = new EnumMap<>(Stage.class);
        node.fields().forEachRemaining(entry -> result.put(Stage.valueOf(entry.getKey()),
                money(entry.getValue().decimalValue())));
        return checkedCaps(result);
    }

    static BigDecimal money(BigDecimal value) { return value.setScale(SCALE, RoundingMode.HALF_UP); }

    synchronized Reservation reserve(Stage stage, String cellId, BigDecimal forecastUsd) {
        requireOpen();
        Objects.requireNonNull(stage);
        Objects.requireNonNull(cellId);
        if (stopReason != null) throw new SpendStop(stopReason);
        BigDecimal cap = caps.get(stage);
        if (cap == null) throw refuse("No approved cap for " + stage);
        if (forecastUsd == null || forecastUsd.signum() <= 0) throw new IllegalArgumentException("Forecast must be positive");
        BigDecimal forecast = money(forecastUsd);
        if (spent.get(stage).add(inFlight.get(stage)).add(forecast).compareTo(cap) > 0)
            throw refuse("Stage cap exceeded: " + stage);
        BigDecimal committed = money(BigDecimal.ZERO);
        BigDecimal totalCap = money(BigDecimal.ZERO);
        for (Stage item : Stage.values()) {
            committed = committed.add(spent.get(item)).add(inFlight.get(item));
            if (caps.containsKey(item)) totalCap = totalCap.add(caps.get(item));
        }
        if (committed.add(forecast).compareTo(totalCap) > 0)
            throw refuse("Sum of approved stage caps exceeded: " + stage);
        String id = UUID.randomUUID().toString();
        append(Map.of(TYPE, RESERVE, RESERVATION_ID, id, STAGE_FIELD, stage.name(),
                CELL, cellId, FORECAST, forecast));
        usedIds.add(id);
        inFlight.put(stage, inFlight.get(stage).add(forecast));
        attempts.put(stage, attempts.get(stage) + 1);
        return new Reservation(id, stage, cellId, forecast);
    }

    synchronized void stop(String reason) {
        if (stopReason == null) { append(Map.of(TYPE, STOP, REASON, reason)); stopReason = reason; }
    }
    /** In-memory stop for this run only. A cap refusal, an arm mismatch or a provider error streak is not
     * written to the ledger: a later run reopens the same spend and may still fit a smaller forecast, and a
     * persisted refusal would brick the file (a fresh file would forget cumulative spend). Only an
     * UNMETERED response, an explicit owner {@link #stop} or a write failure is a persistent stop. */
    synchronized void stopRunOnly(String reason) { if (stopReason == null) stopReason = reason; }
    private SpendStop refuse(String reason) { stopRunOnly(reason); return new SpendStop(reason); }
    /** Owner-explicit: clears a persisted stop by appending a RESUME record. */
    synchronized void resume() {
        requireOpen();
        if (stopReason == null) throw new IllegalStateException("Ledger is not stopped");
        append(Map.of(TYPE, RESUME));
        stopReason = null;
    }
    synchronized boolean stopped() { return stopReason != null; }
    synchronized String stopReason() { return stopReason; }
    synchronized BigDecimal spent(Stage stage) { return spent.get(stage); }
    synchronized BigDecimal cap(Stage stage) { return caps.get(stage); }
    synchronized Map<Stage, BigDecimal> approvedCaps() { return Map.copyOf(caps); }
    synchronized BigDecimal billed(Stage stage) { return billed.get(stage); }
    synchronized BigDecimal inFlight(Stage stage) { return inFlight.get(stage); }
    synchronized int attempts(Stage stage) { return attempts.get(stage); }
    synchronized BigDecimal remaining(Stage stage) {
        BigDecimal cap = caps.get(stage);
        return cap == null ? money(BigDecimal.ZERO) : cap.subtract(spent.get(stage)).subtract(inFlight.get(stage));
    }
    synchronized BigDecimal cellSpent(String cellId) { return cellSpent.getOrDefault(cellId, money(BigDecimal.ZERO)); }
    synchronized Map<String, BigDecimal> cellSpending() { return Map.copyOf(cellSpent); }
    synchronized BigDecimal largestOvershoot() { return largestOvershoot; }
    synchronized int recordCount() { return recordCount; }
    synchronized boolean unreconciled() { return !pending.isEmpty(); }
    synchronized Instant createdAt() { return createdAt; }

    synchronized void amendCaps(Map<Stage, BigDecimal> newCaps) {
        requireOpen();
        Map<Stage, BigDecimal> checked = checkedCaps(newCaps);
        if (checked.equals(caps)) throw new IllegalArgumentException("Caps unchanged");
        append(Map.of(TYPE, AMEND, OLD, Map.copyOf(caps), NEW, checked));
        caps.clear(); caps.putAll(checked);
    }

    synchronized void reconcile(String reservationId, BigDecimal actualUsd) {
        requireOpen();
        Pending entry = pending.get(reservationId);
        if (entry == null) throw new IllegalArgumentException("No unreconciled reservation: " + reservationId);
        if (actualUsd == null || actualUsd.signum() < 0) throw new IllegalArgumentException("Actual must be nonnegative");
        BigDecimal actual = money(actualUsd);
        append(Map.of(TYPE, RECONCILE, RESERVATION_ID, reservationId, ACTUAL_USD, actual));
        pending.remove(reservationId);
        spent.put(entry.stage, spent.get(entry.stage).subtract(entry.forecast).add(actual));
        cellSpent.merge(entry.cell, actual.subtract(entry.forecast), BigDecimal::add);
        BigDecimal overshoot = actual.subtract(entry.forecast);
        if (overshoot.compareTo(largestOvershoot) > 0) largestOvershoot = overshoot;
    }

    private void append(Map<String, ?> record) {
        if (channel == null) return;
        requireOpen();
        try {
            byte[] bytes = (JSON.writeValueAsString(record) + LINE_END).getBytes(StandardCharsets.UTF_8);
            channel.position(channel.size());
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
            recordCount++;
        } catch (IOException ex) {
            stopReason = "Ledger write failed; no dispatch allowed";
            throw new LedgerFailure(stopReason, ex);
        }
    }
    private void requireOpen() { if (closed) throw new LedgerFailure("Ledger closed"); }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try { if (lock != null) lock.release(); } catch (IOException ignored) { /* close channel below */ }
        try { if (channel != null) channel.close(); } catch (IOException ignored) { /* closed */ }
    }

    final class Reservation {
        private final String id;
        private final Stage stage;
        private final String cellId;
        private final BigDecimal forecast;
        private boolean closed;
        private Reservation(String id, Stage stage, String cellId, BigDecimal forecast) {
            this.id = id; this.stage = stage; this.cellId = cellId; this.forecast = forecast;
        }
        String id() { return id; }
        boolean closed() { return closed; }
        void settle(BigDecimal actualUsd) { settle(actualUsd, actualUsd); }
        /** Books the interceptor's conservative output-plus-reasoning figure for the cap; billed is stored separately. */
        void settle(BigDecimal conservativeUsd, BigDecimal nonAdditiveUsd) {
            synchronized (StageCapLedger.this) {
                ensureOpen();
                boolean unmetered = conservativeUsd == null || nonAdditiveUsd == null
                        || conservativeUsd.signum() < 0 || nonAdditiveUsd.signum() < 0;
                if (unmetered) append(Map.of(TYPE, UNMETERED, RESERVATION_ID, id));
                else append(Map.of(TYPE, SETTLE, RESERVATION_ID, id,
                        CONSERVATIVE, money(conservativeUsd), BILLED, money(nonAdditiveUsd)));
                inFlight.put(stage, inFlight.get(stage).subtract(forecast));
                closed = true;
                if (unmetered) {
                    stop(UNMETERED_REASON + stage);
                    return;
                }
                BigDecimal conservative = money(conservativeUsd);
                spent.put(stage, spent.get(stage).add(conservative));
                billed.put(stage, billed.get(stage).add(money(nonAdditiveUsd)));
                cellSpent.merge(cellId, conservative, BigDecimal::add);
                BigDecimal overshoot = conservative.subtract(forecast);
                if (overshoot.compareTo(largestOvershoot) > 0) largestOvershoot = overshoot;
            }
        }
        void abandon() {
            synchronized (StageCapLedger.this) {
                ensureOpen();
                append(Map.of(TYPE, ABANDON, RESERVATION_ID, id));
                inFlight.put(stage, inFlight.get(stage).subtract(forecast));
                closed = true;
            }
        }
        private void ensureOpen() { if (closed) throw new IllegalStateException("Reservation already closed"); }
    }
}
