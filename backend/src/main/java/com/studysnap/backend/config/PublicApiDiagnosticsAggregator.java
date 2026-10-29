package com.studysnap.backend.config;

import com.studysnap.backend.service.NoteService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/** Bounded, lock-free request recording. All log writes happen on a dedicated single worker. */
@Component
public class PublicApiDiagnosticsAggregator {
    static final int MAX_CLIENTS = 5_000;
    static final int MAX_AGENTS = 500;
    static final int MAX_TAGS = 500;
    static final int MAX_SAMPLES = 20;
    static final int TOP_N = 10;
    static final int MAX_QUERY_LENGTH = 2_048;
    static final int MAX_QUERY_PAIRS = 32;
    static final int MAX_PARAMETER_NAME = 60;
    static final int MAX_AGENT_LENGTH = 120;
    static final int MAX_SLUG_LENGTH = 60;
    private static final int MAX_HEADER_LENGTH = 2_048;
    private static final String OTHER = "<other>";
    private static final String[] GROUPS = {"listing", "discovery-sections", "facets", "note-detail", "collections", "other-public"};
    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm'Z'").withZone(ZoneOffset.UTC);

    private final StudySnapProperties.PublicApiDiagnostics settings;
    private final PublicApiDiagnosticsLog log;
    private final Clock clock;
    private final AtomicReference<Bucket> current;
    private final ArrayBlockingQueue<Bucket> pending = new ArrayBlockingQueue<>(2);
    private final AtomicBoolean drainQueued = new AtomicBoolean();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "public-api-diagnostics-flush");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    @Autowired
    public PublicApiDiagnosticsAggregator(StudySnapProperties properties, PublicApiDiagnosticsLog log) {
        this(properties.getPublicApiDiagnostics(), log, Clock.systemUTC());
    }

    PublicApiDiagnosticsAggregator(StudySnapProperties.PublicApiDiagnostics settings, PublicApiDiagnosticsLog log, Clock clock) {
        this.settings = settings;
        this.log = log;
        this.clock = clock;
        this.current = new AtomicReference<>(new Bucket(minuteNow()));
    }

    @PostConstruct
    void announce() {
        try {
            log.startup("PUBLIC_API_DIAGNOSTICS_CONFIG enabled=" + settings.isEnabled()
                    + " reviewBy=" + settings.getReviewBy());
        } catch (Throwable failure) {
            recordErrorSafely();
        }
    }

    @PreDestroy
    void stop() {
        worker.shutdown();
    }

    public void recordErrorSafely() {
        try {
            current.get().errors.increment();
        } catch (Throwable ignored) {
            // Diagnostics cannot interrupt the request, including a failure in error counting.
        }
    }

    public void record(HttpServletRequest request, int status, boolean options) {
        Bucket bucket = enter();
        try {
            if (options) {
                bucket.options.increment();
                return;
            }
            int rate = bucket.rate.incrementAndGet();
            bucket.total.increment();
            if (status >= 0 && status < 1_000) bucket.statuses.incrementAndGet(status);
            boolean anonymous = request.getHeader("Authorization") == null;
            if (!anonymous) {
                bucket.authenticated.increment();
                return;
            }
            bucket.anonymous.increment();
            bucket.groups.incrementAndGet(group(request.getServletPath()));
            QueryFacts query = parseQuery(request.getQueryString());
            for (String name : query.names) count(bucket.parameters, bucket.parameterSlots, name, 100);
            for (String tag : query.tags) count(bucket.tags, bucket.tagSlots, tag, tagCap());
            String agent = sanitize(request.getHeader("User-Agent"), MAX_AGENT_LENGTH);
            count(bucket.agents, bucket.agentSlots, agent, agentCap());
            AddressFacts address = addresses(request);
            bucket.xffShape.incrementAndGet(address.entryCount);
            if (address.trueClientPresent) bucket.trueClient.increment();
            if (address.cfPresent) bucket.cfConnecting.increment();
            countClient(bucket, address.clientKey);
            if (rate > Math.clamp(settings.getSampleThresholdPerMinute(), 0, Integer.MAX_VALUE)
                    && claimSample(bucket)) {
                bucket.samples.add("PUBLIC_API_SAMPLE minute=" + MINUTE_FORMAT.format(Instant.ofEpochSecond(bucket.minute * 60))
                        + " path=" + GROUPS[group(request.getServletPath())]
                        + " params=" + query.names.stream().sorted().map(PublicApiDiagnosticsAggregator::quote)
                                .collect(Collectors.joining(",", "[", "]"))
                        + " ua=" + quote(agent) + " status=" + status
                        + " tci=" + address.trueClient + " xffFirst=" + address.xffFirst
                        + " xffLast=" + address.xffLast + " remote=" + address.remote);
            }
        } finally {
            bucket.active.decrementAndGet();
            scheduleDrain();
        }
    }

    @Scheduled(fixedDelay = 5_000)
    void flushStragglers() {
        try {
            if (!settings.isEnabled()) return;
            rotate();
            scheduleDrain();
        } catch (Throwable failure) {
            recordErrorSafely();
        }
    }

    private Bucket enter() {
        for (;;) {
            rotate();
            Bucket bucket = current.get();
            bucket.active.incrementAndGet();
            if (current.get() == bucket) return bucket;
            bucket.active.decrementAndGet();
        }
    }

    private void rotate() {
        long minute = minuteNow();
        for (;;) {
            Bucket old = current.get();
            if (minute <= old.minute) return;
            if (current.compareAndSet(old, new Bucket(minute))) {
                if (!pending.offer(old)) recordErrorSafely();
                scheduleDrain();
                return;
            }
        }
    }

    private void scheduleDrain() {
        if (pending.isEmpty() || !drainQueued.compareAndSet(false, true)) return;
        try {
            worker.execute(this::drain);
        } catch (Throwable failure) {
            drainQueued.set(false);
            recordErrorSafely();
        }
    }

    private void drain() {
        try {
            Bucket bucket;
            while ((bucket = pending.peek()) != null) {
                if (bucket.active.get() != 0) return;
                pending.poll();
                try {
                    if (bucket.total.sum() == 0 && bucket.options.sum() == 0 && bucket.errors.sum() == 0) continue;
                    log.aggregate(render(bucket));
                    for (String line : bucket.samples) log.sample(line);
                } catch (Throwable failure) {
                    recordErrorSafely();
                }
            }
        } finally {
            drainQueued.set(false);
            if (!pending.isEmpty() && pending.peek().active.get() == 0) scheduleDrain();
        }
    }

    private String render(Bucket bucket) {
        String groups = java.util.stream.IntStream.range(0, GROUPS.length)
                .mapToObj(i -> GROUPS[i] + ':' + bucket.groups.get(i)).collect(Collectors.joining(","));
        String statuses = java.util.stream.IntStream.range(0, 1_000).filter(i -> bucket.statuses.get(i) > 0)
                .mapToObj(i -> i + ":" + bucket.statuses.get(i)).collect(Collectors.joining(","));
        return "PUBLIC_API_DIAGNOSTICS minute=" + MINUTE_FORMAT.format(Instant.ofEpochSecond(bucket.minute * 60))
                + " total=" + bucket.total.sum() + " anonymous=" + bucket.anonymous.sum()
                + " authenticated=" + bucket.authenticated.sum() + " options=" + bucket.options.sum()
                + " distinctAnonymousClients=" + (bucket.clientOverflow.get() ? ">=cap" : bucket.clients.size())
                + " diagnosticsErrors=" + bucket.errors.sum() + " paths=[" + groups + "] statuses=[" + statuses + ']'
                + " parameters=" + top(bucket.parameters) + " agents=" + top(bucket.agents)
                + " tags=" + top(bucket.tags) + " xffEntries=[0:" + bucket.xffShape.get(0)
                + ",1:" + bucket.xffShape.get(1) + ",2:" + bucket.xffShape.get(2)
                + ",3+:" + bucket.xffShape.get(3) + "] trueClientIp=" + bucket.trueClient.sum()
                + " cfConnectingIp=" + bucket.cfConnecting.sum();
    }

    private String top(Map<String, LongAdder> map) {
        return map.entrySet().stream().sorted(Comparator.<Map.Entry<String, LongAdder>>comparingLong(e -> e.getValue().sum())
                        .reversed().thenComparing(Map.Entry::getKey))
                .limit(Math.clamp(settings.getTopN(), 1, TOP_N)).map(e -> quote(e.getKey()) + ':' + e.getValue().sum())
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static void count(ConcurrentHashMap<String, LongAdder> map, AtomicInteger slots, String key, int cap) {
        if (OTHER.equals(key)) key = "<literal-other>";
        LongAdder existing = map.get(key);
        if (existing != null) { existing.increment(); return; }
        for (;;) {
            int size = slots.get();
            if (size >= cap - 1) {
                map.computeIfAbsent(OTHER, ignored -> new LongAdder()).increment();
                return;
            }
            if (slots.compareAndSet(size, size + 1)) break;
        }
        LongAdder inserted = new LongAdder();
        LongAdder race = map.putIfAbsent(key, inserted);
        if (race != null) {
            slots.decrementAndGet();
            race.increment();
        } else inserted.increment();
    }

    private void countClient(Bucket bucket, String key) {
        LongAdder existing = bucket.clients.get(key);
        if (existing != null) { existing.increment(); return; }
        for (;;) {
            int size = bucket.clientSlots.get();
            if (size >= clientCap()) {
                bucket.clientOverflow.set(true);
                return;
            }
            if (bucket.clientSlots.compareAndSet(size, size + 1)) break;
        }
        LongAdder inserted = new LongAdder();
        LongAdder race = bucket.clients.putIfAbsent(key, inserted);
        if (race != null) {
            bucket.clientSlots.decrementAndGet();
            race.increment();
        } else inserted.increment();
    }

    private boolean claimSample(Bucket bucket) {
        for (;;) {
            int count = bucket.sampleCount.get();
            if (count >= sampleCap()) return false;
            if (bucket.sampleCount.compareAndSet(count, count + 1)) return true;
        }
    }

    private int sampleCap() { return Math.clamp(settings.getSampleMaxPerMinute(), 0, MAX_SAMPLES); }
    private int clientCap() { return Math.clamp(settings.getDistinctClientCap(), 1, MAX_CLIENTS); }
    private int agentCap() { return Math.clamp(settings.getUserAgentCap(), 1, MAX_AGENTS); }
    private int tagCap() { return Math.clamp(settings.getTagSlugCap(), 1, MAX_TAGS); }
    private long minuteNow() { return clock.instant().getEpochSecond() / 60; }

    Bucket currentBucket() { return current.get(); }

    static int group(String path) {
        if (PublicApiDiagnosticsFilter.NOTES_PUBLIC.equals(path)) return 0;
        if ((PublicApiDiagnosticsFilter.NOTES_PUBLIC + "/discovery-sections").equals(path)) return 1;
        if (PublicApiDiagnosticsFilter.TAGS.equals(path) || PublicApiDiagnosticsFilter.SUBJECTS.equals(path)
                || PublicApiDiagnosticsFilter.COURSE_PROGRAMS.equals(path)
                || (PublicApiDiagnosticsFilter.NOTES_PUBLIC + "/learner-levels").equals(path)) return 2;
        if (path != null && path.startsWith(PublicApiDiagnosticsFilter.NOTES_PUBLIC + "/")) return 3;
        if (path != null && path.startsWith(PublicApiDiagnosticsFilter.COLLECTIONS_PUBLIC)) return 4;
        return 5;
    }

    static String sanitize(String value, int limit) {
        if (value == null || value.isBlank()) return "<none>";
        StringBuilder out = new StringBuilder(Math.min(limit, value.length()));
        boolean previousSpace = false;
        for (int i = 0; i < value.length() && out.length() < limit; i++) {
            char c = value.charAt(i);
            boolean space = Character.isISOControl(c) || Character.isWhitespace(c)
                    || Character.getType(c) == Character.FORMAT;
            if (space && !previousSpace) out.append(' ');
            else if (!space) out.append(c);
            previousSpace = space;
        }
        String result = out.toString().strip();
        return result.isEmpty() ? "<none>" : result;
    }

    private QueryFacts parseQuery(String query) {
        if (query == null) return new QueryFacts(List.of(), List.of());
        List<String> names = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        int end = Math.min(query.length(), MAX_QUERY_LENGTH);
        int start = 0;
        for (int pairs = 0; start < end && pairs < MAX_QUERY_PAIRS; pairs++) {
            int amp = query.indexOf('&', start);
            if (amp < 0 || amp > end) amp = end;
            int equals = query.indexOf('=', start);
            if (equals < 0 || equals > amp) equals = amp;
            String name = decode(query.substring(start, Math.min(equals, start + MAX_PARAMETER_NAME * 3)));
            name = sanitize(name, MAX_PARAMETER_NAME);
            if (!"<none>".equals(name)) names.add(name);
            if ("tag".equals(name) && equals < amp) {
                String raw = query.substring(equals + 1, Math.min(amp, equals + 1 + 120));
                for (String part : raw.split(",", 16)) {
                    String slug = NoteService.normalizePublicLibraryFilterSlug(decode(part));
                    if (slug != null && !slug.isBlank()) tags.add(sanitize(slug, MAX_SLUG_LENGTH));
                }
            }
            start = amp + 1;
        }
        return new QueryFacts(names, tags);
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException failure) { return value; }
    }

    private AddressFacts addresses(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        String first = null;
        String last = null;
        int entries = 0;
        if (xff != null && !xff.isBlank()) {
            int end = Math.min(xff.length(), MAX_HEADER_LENGTH);
            int start = 0;
            while (start < end && entries < 64) {
                int comma = xff.indexOf(',', start);
                if (comma < 0 || comma > end) comma = end;
                String part = xff.substring(start, comma).trim();
                if (entries == 0) first = part;
                last = part;
                entries++;
                start = comma + 1;
            }
        }
        String tci = request.getHeader("True-Client-IP");
        String cf = request.getHeader("CF-Connecting-IP");
        String tciSubnet = subnet(tci);
        String firstSubnet = subnet(first);
        return new AddressFacts(Math.min(entries, 3), tci != null, cf != null,
                tciSubnet, firstSubnet, subnet(last), subnet(request.getRemoteAddr()),
                tci != null ? tciSubnet : firstSubnet);
    }

    /**
     * Truncates an address to /24 (IPv4) or /48 (IPv6) using string parsing ONLY. ⚠️ Never hand these headers to
     * {@code InetAddress.getByName}: a value such as {@code x.attacker.example:1} contains a colon yet is not an
     * IPv6 literal, so the JDK treats it as a host name and performs a blocking DNS lookup on the request thread
     * (and discloses the resolver to the caller). Every input here is attacker-controlled.
     */
    static String subnet(String input) {
        if (input == null || input.isBlank()) return "<none>";
        String value = input.trim();
        if (value.length() > 45) return "<invalid>";
        if (value.indexOf(':') >= 0) return ipv6Subnet(value);
        int[] octets = ipv4Octets(value);
        return octets == null ? "<invalid>" : octets[0] + "." + octets[1] + "." + octets[2] + ".0/24";
    }

    private static int[] ipv4Octets(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return null;
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) return null;
            for (int j = 0; j < part.length(); j++) {
                if (part.charAt(j) < '0' || part.charAt(j) > '9') return null;
            }
            octets[i] = Integer.parseInt(part);
            if (octets[i] > 255) return null;
        }
        return octets;
    }

    private static String ipv6Subnet(String value) {
        int[] groups = parseIpv6(value);
        if (groups == null) return "<invalid>";
        boolean ipv4Mapped = groups[0] == 0 && groups[1] == 0 && groups[2] == 0 && groups[3] == 0
                && groups[4] == 0 && groups[5] == 0xffff;
        if (ipv4Mapped) {
            return (groups[6] >> 8) + "." + (groups[6] & 255) + "." + (groups[7] >> 8) + ".0/24";
        }
        return String.format("%04x:%04x:%04x::/48", groups[0], groups[1], groups[2]);
    }

    /** Eight 16-bit groups, or null when the text is not a well-formed IPv6 literal (no zone ids, no names). */
    private static int[] parseIpv6(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex && c != ':' && c != '.') return null;
        }
        int compression = value.indexOf("::");
        if (compression >= 0 && value.indexOf("::", compression + 1) >= 0) return null;
        List<Integer> head = ipv6Groups(compression < 0 ? value : value.substring(0, compression));
        List<Integer> tail = compression < 0 ? List.of() : ipv6Groups(value.substring(compression + 2));
        if (head == null || tail == null) return null;
        if (compression < 0 ? head.size() != 8 : head.size() + tail.size() > 7) return null;
        int[] groups = new int[8];
        for (int i = 0; i < head.size(); i++) groups[i] = head.get(i);
        for (int i = 0; i < tail.size(); i++) groups[8 - tail.size() + i] = tail.get(i);
        return groups;
    }

    private static List<Integer> ipv6Groups(String part) {
        if (part.isEmpty()) return List.of();
        String[] pieces = part.split(":", -1);
        List<Integer> groups = new ArrayList<>();
        for (int i = 0; i < pieces.length; i++) {
            String piece = pieces[i];
            if (piece.isEmpty()) return null;
            if (piece.indexOf('.') >= 0) {
                int[] octets = i == pieces.length - 1 ? ipv4Octets(piece) : null;
                if (octets == null) return null;
                groups.add(octets[0] << 8 | octets[1]);
                groups.add(octets[2] << 8 | octets[3]);
            } else {
                if (piece.length() > 4) return null;
                groups.add(Integer.parseInt(piece, 16));
            }
        }
        return groups;
    }

    /**
     * Caller-controlled text is sanitized (no control characters) AND quoted, so a hostile value cannot add or
     * overwrite a field inside a log line by containing {@code ,} {@code ]} {@code =} or spaces.
     */
    static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private record QueryFacts(List<String> names, List<String> tags) { }
    private record AddressFacts(int entryCount, boolean trueClientPresent, boolean cfPresent,
                                String trueClient, String xffFirst, String xffLast, String remote, String clientKey) { }

    static final class Bucket {
        final long minute;
        final AtomicInteger active = new AtomicInteger();
        final LongAdder total = new LongAdder();
        final AtomicInteger rate = new AtomicInteger();
        final LongAdder anonymous = new LongAdder();
        final LongAdder authenticated = new LongAdder();
        final LongAdder options = new LongAdder();
        final LongAdder errors = new LongAdder();
        final LongAdder trueClient = new LongAdder();
        final LongAdder cfConnecting = new LongAdder();
        final AtomicLongArray statuses = new AtomicLongArray(1_000);
        final AtomicLongArray groups = new AtomicLongArray(6);
        final AtomicLongArray xffShape = new AtomicLongArray(4);
        final ConcurrentHashMap<String, LongAdder> clients = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, LongAdder> agents = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, LongAdder> tags = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, LongAdder> parameters = new ConcurrentHashMap<>();
        final AtomicInteger clientSlots = new AtomicInteger();
        final AtomicInteger agentSlots = new AtomicInteger();
        final AtomicInteger tagSlots = new AtomicInteger();
        final AtomicInteger parameterSlots = new AtomicInteger();
        final AtomicBoolean clientOverflow = new AtomicBoolean();
        final AtomicInteger sampleCount = new AtomicInteger();
        final ConcurrentLinkedQueue<String> samples = new ConcurrentLinkedQueue<>();
        Bucket(long minute) { this.minute = minute; }
    }
}
