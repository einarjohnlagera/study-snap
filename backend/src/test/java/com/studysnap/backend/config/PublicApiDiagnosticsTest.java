package com.studysnap.backend.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class PublicApiDiagnosticsTest {
    private static final Instant START = Instant.parse("2026-10-29T12:00:00Z");

    @Test
    void nonPublicRequestDoesNotParseOrRecord() throws Exception {
        PublicApiDiagnosticsAggregator aggregator = mock(PublicApiDiagnosticsAggregator.class);
        MockHttpServletRequest request = spy(request("/auth/login", "search=secret", "Bot"));
        new PublicApiDiagnosticsFilter(aggregator, new StudySnapProperties())
                .doFilterInternal(request, new MockHttpServletResponse(), responseChain());
        verify(request, never()).getQueryString();
        verify(aggregator, never()).record(any(), anyInt(), any(Boolean.class));
        assertThat(PublicApiDiagnosticsFilter.isPublicPath("/api/notes/public")).isFalse();
        assertThat(PublicApiDiagnosticsFilter.isPublicPath("/notes/public/learner-levels")).isTrue();
    }

    @Test
    void loggedAgentAndTagAreLengthCapped() throws Exception {
        MutableClock clock = new MutableClock();
        CapturingLog log = new CapturingLog();
        StudySnapProperties.PublicApiDiagnostics settings = new StudySnapProperties.PublicApiDiagnostics();
        settings.setSampleThresholdPerMinute(0);
        PublicApiDiagnosticsAggregator aggregator = new PublicApiDiagnosticsAggregator(settings, log, clock);
        try {
            aggregator.record(request("/notes/public", "tag=" + "t".repeat(100), "a".repeat(140) + "TAIL"), 200, false);
            clock.advanceMinutes(1);
            aggregator.flushStragglers();
            awaitLines(log, 1);
            assertThat(log.aggregates.getFirst()).contains("tags=[\"" + "t".repeat(60) + "\":1]")
                    .doesNotContain("t".repeat(61));
            assertThat(log.samples).singleElement().asString()
                    .contains("ua=\"" + "a".repeat(120) + "\" status=200")
                    .doesNotContain("a".repeat(121), "TAIL");
        } finally { aggregator.stop(); }
    }

    @Test
    void reviewDateIsAnnouncedOnceAndDoesNotDisableCollection() throws Exception {
        MutableClock clock = new MutableClock();
        CapturingLog log = new CapturingLog();
        StudySnapProperties.PublicApiDiagnostics settings = new StudySnapProperties.PublicApiDiagnostics();
        PublicApiDiagnosticsAggregator aggregator = new PublicApiDiagnosticsAggregator(settings, log, clock);
        try {
            aggregator.announce();
            assertThat(log.startups).singleElement().asString()
                    .isEqualTo("PUBLIC_API_DIAGNOSTICS_CONFIG enabled=true reviewBy=2026-11-12");
            clock.advanceMinutes(15L * 24 * 60);
            aggregator.record(request("/notes/public", null, "Crawler"), 200, false);
            clock.advanceMinutes(1);
            aggregator.flushStragglers();
            awaitLines(log, 1);
            assertThat(log.aggregates.getFirst()).contains("total=1");
        } finally { aggregator.stop(); }
    }

    @Test
    void minuteFlushIsOneLinePerNonEmptyMinuteAndSamplesOnlyAboveThreshold() throws Exception {
        MutableClock clock = new MutableClock();
        CapturingLog log = new CapturingLog();
        StudySnapProperties.PublicApiDiagnostics settings = new StudySnapProperties.PublicApiDiagnostics();
        settings.setSampleThresholdPerMinute(2);
        PublicApiDiagnosticsAggregator aggregator = new PublicApiDiagnosticsAggregator(settings, log, clock);
        try {
            for (int i = 0; i < 25; i++) aggregator.record(request("/notes/public", "tag=Acute+Care", "Crawler"), 200, false);
            assertThat(aggregator.currentBucket().sampleCount.get()).isEqualTo(20);
            clock.advanceMinutes(1);
            aggregator.flushStragglers();
            awaitLines(log, 1);
            assertThat(log.aggregates).singleElement().asString()
                    .contains("total=25", "anonymous=25", "statuses=[200:25]", "tags=[\"acute-care\":25]");
            assertThat(log.samples).hasSize(20);
            clock.advanceMinutes(1);
            aggregator.flushStragglers();
            Thread.sleep(30);
            assertThat(log.aggregates).hasSize(1);
            aggregator.record(request("/tags", null, "Crawler"), 200, false);
            clock.advanceMinutes(1);
            aggregator.flushStragglers();
            awaitLines(log, 2);
            assertThat(log.samples).hasSize(20);
        } finally { aggregator.stop(); }
    }

    @Test
    void addressEvidenceIsTruncatedAndHeaderShapeHasNoValues() throws Exception {
        MutableClock clock = new MutableClock();
        CapturingLog log = new CapturingLog();
        StudySnapProperties.PublicApiDiagnostics settings = new StudySnapProperties.PublicApiDiagnostics();
        settings.setSampleThresholdPerMinute(0);
        PublicApiDiagnosticsAggregator aggregator = new PublicApiDiagnosticsAggregator(settings, log, clock);
        try {
            MockHttpServletRequest zero = request("/notes/public", null, "Crawler");
            aggregator.record(zero, 200, false);
            MockHttpServletRequest one = request("/notes/public", null, "Crawler");
            one.addHeader("X-Forwarded-For", "198.51.100.71");
            aggregator.record(one, 200, false);
            MockHttpServletRequest two = request("/notes/public", null, "Crawler");
            two.addHeader("X-Forwarded-For", "198.51.100.71, 192.0.2.54");
            two.addHeader("True-Client-IP", "203.0.113.29");
            aggregator.record(two, 200, false);
            MockHttpServletRequest three = request("/notes/public", null, "Crawler");
            three.addHeader("X-Forwarded-For", "2001:db8:1:2::1, 192.0.2.54, 198.51.100.71");
            three.addHeader("CF-Connecting-IP", "203.0.113.29");
            aggregator.record(three, 200, false);
            clock.advanceMinutes(1);
            aggregator.flushStragglers();
            awaitLines(log, 1);
            assertThat(log.aggregates.getFirst()).contains("xffEntries=[0:1,1:1,2:1,3+:1]", "trueClientIp=1", "cfConnectingIp=1")
                    .doesNotContain("203.0.113.29", "198.51.100.71");
            assertThat(log.samples).anySatisfy(line -> assertThat(line).contains("tci=203.0.113.0/24", "xffFirst=198.51.100.0/24", "xffLast=192.0.2.0/24"));
            assertThat(log.samples).anySatisfy(line -> assertThat(line).contains("xffFirst=2001:0db8:0001::/48"));
            assertThat(log.samples).allSatisfy(line -> assertThat(line).doesNotContain("203.0.113.29", "198.51.100.71"));
        } finally { aggregator.stop(); }
    }

    @Test
    void callerInputCannotForgeLinesOrLeakSearchOrBearerValue() throws Exception {
        MutableClock clock = new MutableClock();
        CapturingLog log = new CapturingLog();
        StudySnapProperties.PublicApiDiagnostics settings = new StudySnapProperties.PublicApiDiagnostics();
        settings.setSampleThresholdPerMinute(0);
        PublicApiDiagnosticsAggregator aggregator = new PublicApiDiagnosticsAggregator(settings, log, clock);
        try {
            MockHttpServletRequest anonymous = request("/notes/public", "search=private+essay&tag=Acute%0ACare&sort=recent", "Bot\nPUBLIC_API_DIAGNOSTICS forged\u0001");
            aggregator.record(anonymous, 200, false);
            MockHttpServletRequest authenticated = request("/notes/public", "search=secret+search&tag=private-tag", "secret-agent");
            authenticated.addHeader("Authorization", "Bearer secret-token");
            aggregator.record(authenticated, 401, false);
            clock.advanceMinutes(1);
            aggregator.flushStragglers();
            awaitLines(log, 1);
            String output = String.join("\n", log.aggregates) + '\n' + String.join("\n", log.samples);
            assertThat(output).contains("total=2", "anonymous=1", "authenticated=1", "statuses=[200:1,401:1]", "acute-care")
                    .doesNotContain("private essay", "private+essay", "secret search", "secret+search",
                            "secret-token", "private-tag", "secret-agent", "rawQuery=", "\u0001",
                            "Bot\nPUBLIC_API_DIAGNOSTICS");
            assertThat(log.aggregates).hasSize(1);
            assertThat(log.samples).singleElement().asString().contains("ua=\"Bot PUBLIC_API_DIAGNOSTICS forged\"");
            assertThat(PublicApiDiagnosticsAggregator.sanitize("x".repeat(150), 120)).hasSize(120);
        } finally { aggregator.stop(); }
    }

    @Test
    void capsHoldAcrossConcurrentWritersAndOverflowIsExplicit() throws Exception {
        MutableClock clock = new MutableClock();
        CapturingLog log = new CapturingLog();
        PublicApiDiagnosticsAggregator aggregator = new PublicApiDiagnosticsAggregator(new StudySnapProperties.PublicApiDiagnostics(), log, clock);
        int threads = 8;
        int perThread = 720;
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(threads)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < threads; worker++) {
                final int workerIndex = worker;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        int id = workerIndex * perThread + j;
                        MockHttpServletRequest request = request("/notes/public", "tag=tag-" + id, "Agent-" + id);
                        request.addHeader("True-Client-IP", "10." + (id / 256) + '.' + (id % 256) + ".7");
                        aggregator.record(request, 200, false);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
            PublicApiDiagnosticsAggregator.Bucket bucket = aggregator.currentBucket();
            assertThat(bucket.clients).hasSize(5_000);
            assertThat(bucket.agents.size()).isLessThanOrEqualTo(500);
            assertThat(bucket.tags.size()).isLessThanOrEqualTo(500);
            assertThat(bucket.sampleCount.get()).isLessThanOrEqualTo(20);
            clock.advanceMinutes(1);
            aggregator.flushStragglers();
            awaitLines(log, 1);
            assertThat(log.aggregates.getFirst()).contains("distinctAnonymousClients=>=cap", "<other>", "total=5760");
        } finally { aggregator.stop(); }
    }

    @Test
    void aggregatorOrLoggerFailureLeavesResponseStatusAndBodyUnchanged() throws Exception {
        StudySnapProperties properties = new StudySnapProperties();
        PublicApiDiagnosticsAggregator failing = mock(PublicApiDiagnosticsAggregator.class);
        doThrow(new AssertionError("diagnostic failed")).when(failing).record(any(), anyInt(), any(Boolean.class));
        MockHttpServletResponse baseline = execute(new PublicApiDiagnosticsFilter(failing, disabledProperties()));
        MockHttpServletResponse afterFailure = execute(new PublicApiDiagnosticsFilter(failing, properties));
        assertThat(afterFailure.getStatus()).isEqualTo(baseline.getStatus());
        assertThat(afterFailure.getContentAsString()).isEqualTo(baseline.getContentAsString());
        assertThat(afterFailure.getHeaderNames()).isEqualTo(baseline.getHeaderNames());
        assertThat(afterFailure.getHeader("X-Expected")).isEqualTo(baseline.getHeader("X-Expected"));
        verify(failing).recordErrorSafely();

        doThrow(new AssertionError("error counter failed")).when(failing).recordErrorSafely();
        MockHttpServletResponse withBothFailures = execute(new PublicApiDiagnosticsFilter(failing, properties));
        assertThat(withBothFailures.getStatus()).isEqualTo(baseline.getStatus());
        assertThat(withBothFailures.getContentAsString()).isEqualTo(baseline.getContentAsString());

        StudySnapProperties brokenSettings = mock(StudySnapProperties.class);
        doThrow(new AssertionError("property failed")).when(brokenSettings).getPublicApiDiagnostics();
        MockHttpServletResponse withPropertyFailure = execute(new PublicApiDiagnosticsFilter(failing, brokenSettings));
        assertThat(withPropertyFailure.getStatus()).isEqualTo(baseline.getStatus());
        assertThat(withPropertyFailure.getContentAsString()).isEqualTo(baseline.getContentAsString());
        assertThat(withPropertyFailure.getHeaderNames()).isEqualTo(baseline.getHeaderNames());

        MutableClock clock = new MutableClock();
        PublicApiDiagnosticsLog logger = mock(PublicApiDiagnosticsLog.class);
        doThrow(new AssertionError("logger failed")).when(logger).aggregate(anyString());
        PublicApiDiagnosticsAggregator aggregator = new PublicApiDiagnosticsAggregator(properties.getPublicApiDiagnostics(), logger, clock);
        try {
            MockHttpServletResponse withLoggerFailure = execute(new PublicApiDiagnosticsFilter(aggregator, properties));
            clock.advanceMinutes(1);
            aggregator.flushStragglers();
            for (int i = 0; i < 200 && aggregator.currentBucket().errors.sum() == 0; i++) Thread.sleep(10);
            assertThat(aggregator.currentBucket().errors.sum()).isEqualTo(1);
            assertThat(withLoggerFailure.getStatus()).isEqualTo(baseline.getStatus());
            assertThat(withLoggerFailure.getContentAsString()).isEqualTo(baseline.getContentAsString());
            assertThat(withLoggerFailure.getHeaderNames()).isEqualTo(baseline.getHeaderNames());
            assertThat(withLoggerFailure.getHeader("X-Expected")).isEqualTo(baseline.getHeader("X-Expected"));
        } finally { aggregator.stop(); }
    }

    @Test
    void disabledPathNeverParsesAndEnabledPathNeverReadsParameterMap() throws Exception {
        StudySnapProperties disabled = disabledProperties();
        PublicApiDiagnosticsAggregator aggregator = mock(PublicApiDiagnosticsAggregator.class);
        MockHttpServletRequest request = spy(request("/notes/public", "search=private", "Bot"));
        new PublicApiDiagnosticsFilter(aggregator, disabled).doFilterInternal(request, new MockHttpServletResponse(), responseChain());
        verify(request, never()).getQueryString();
        verify(request, never()).getServletPath();
        verify(aggregator, never()).record(any(), anyInt(), any(Boolean.class));

        MutableClock clock = new MutableClock();
        PublicApiDiagnosticsAggregator real = new PublicApiDiagnosticsAggregator(new StudySnapProperties.PublicApiDiagnostics(), new CapturingLog(), clock);
        try {
            MockHttpServletRequest enabledRequest = spy(request("/notes/public", "tag=acute", "Bot"));
            new PublicApiDiagnosticsFilter(real, new StudySnapProperties()).doFilterInternal(enabledRequest, new MockHttpServletResponse(), responseChain());
            verify(enabledRequest, never()).getParameterMap();
            verify(enabledRequest, never()).getParameter(anyString());
        } finally { real.stop(); }
    }

    private static MockHttpServletResponse execute(PublicApiDiagnosticsFilter filter) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request("/notes/public", null, "Bot"), response, responseChain());
        return response;
    }

    private static FilterChain responseChain() {
        return (request, response) -> {
            ((MockHttpServletResponse) response).setStatus(403);
            ((MockHttpServletResponse) response).setHeader("X-Expected", "same-header");
            response.getWriter().write("same-body");
        };
    }

    private static StudySnapProperties disabledProperties() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getPublicApiDiagnostics().setEnabled(false);
        return properties;
    }

    @Test
    void subnetIsPureStringParsingAndHandlesEveryIpv6Form() {
        assertThat(PublicApiDiagnosticsAggregator.subnet("203.0.113.29")).isEqualTo("203.0.113.0/24");
        assertThat(PublicApiDiagnosticsAggregator.subnet("2001:db8:1:2::1")).isEqualTo("2001:0db8:0001::/48");
        assertThat(PublicApiDiagnosticsAggregator.subnet("1:2:3:4:5:6:7:8")).isEqualTo("0001:0002:0003::/48");
        assertThat(PublicApiDiagnosticsAggregator.subnet("::1")).isEqualTo("0000:0000:0000::/48");
        assertThat(PublicApiDiagnosticsAggregator.subnet("::ffff:198.51.100.71")).isEqualTo("198.51.100.0/24");
        assertThat(PublicApiDiagnosticsAggregator.subnet(null)).isEqualTo("<none>");
        for (String hostile : new String[] {"zz:1", "x.attacker.example:1", "a:b", "1::2::3", "12345::1", ":1:2",
                "fe80::1%eth0", "::ffff:300.1.1.1", "1:2:3:4:5:6:7:8:9", "1.2.3", "1.2.3.4.5", "999.1.1.1",
                "example.com", "0x7f.0.0.1", "2001:db8::g"}) {
            assertThat(PublicApiDiagnosticsAggregator.subnet(hostile)).as(hostile).isEqualTo("<invalid>");
        }
    }

    /**
     * ⚠️ A value like {@code x.attacker.example:1} contains a colon but is a HOST NAME to the JDK, so
     * {@code InetAddress.getByName} would resolve it on the request thread. A recording resolver (installed
     * for the test JVM by META-INF/services) proves no header value ever reaches name resolution.
     */
    @Test
    void hostileAddressHeadersNeverReachNameResolution() throws Exception {
        try { java.net.InetAddress.getByName("probe.resolver.test"); } catch (java.net.UnknownHostException expected) { }
        assertThat(RecordingInetAddressResolverProvider.LOOKED_UP)
                .as("the recording resolver must be active, or this guard is vacuous").contains("probe.resolver.test");
        MutableClock clock = new MutableClock();
        CapturingLog log = new CapturingLog();
        StudySnapProperties.PublicApiDiagnostics settings = new StudySnapProperties.PublicApiDiagnostics();
        settings.setSampleThresholdPerMinute(0);
        PublicApiDiagnosticsAggregator aggregator = new PublicApiDiagnosticsAggregator(settings, log, clock);
        try {
            MockHttpServletRequest hostile = request("/notes/public", null, "Bot");
            hostile.addHeader("X-Forwarded-For", "xff-first.resolver.test:1, xff-last.resolver.test:2");
            hostile.addHeader("True-Client-IP", "tci.resolver.test:3");
            hostile.addHeader("CF-Connecting-IP", "cf.resolver.test:4");
            hostile.setRemoteAddr("remote.resolver.test:5");
            aggregator.record(hostile, 200, false);
            assertThat(RecordingInetAddressResolverProvider.LOOKED_UP)
                    .noneMatch(name -> name.endsWith(".resolver.test:1") || name.contains("xff-")
                            || name.contains("tci.") || name.contains("cf.") || name.contains("remote."));
        } finally { aggregator.stop(); }
    }

    @Test
    void callerTextCannotSpoofFieldsInsideALine() throws Exception {
        MutableClock clock = new MutableClock();
        CapturingLog log = new CapturingLog();
        StudySnapProperties.PublicApiDiagnostics settings = new StudySnapProperties.PublicApiDiagnostics();
        settings.setSampleThresholdPerMinute(0);
        PublicApiDiagnosticsAggregator aggregator = new PublicApiDiagnosticsAggregator(settings, log, clock);
        try {
            aggregator.record(request("/notes/public", "a%5D%20total%3D0=1&tag=x", "x\" status=200 tci=1.2.3.0/24 ] total=0"), 503, false);
            clock.advanceMinutes(1);
            aggregator.flushStragglers();
            awaitLines(log, 1);
            String sample = log.samples.getFirst();
            String unquoted = sample.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
            assertThat(unquoted).containsOnlyOnce(" status=").containsOnlyOnce(" tci=");
            assertThat(unquoted).contains("status=503");
            String aggregate = log.aggregates.getFirst().replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
            assertThat(aggregate).containsOnlyOnce(" total=");
        } finally { aggregator.stop(); }
    }

    private static MockHttpServletRequest request(String path, String query, String agent) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.setQueryString(query);
        request.addHeader("User-Agent", agent);
        request.setRemoteAddr("192.0.2.91");
        return request;
    }

    private static void awaitLines(CapturingLog log, int count) throws InterruptedException {
        for (int i = 0; i < 200 && log.aggregates.size() < count; i++) Thread.sleep(10);
        assertThat(log.aggregates).hasSize(count);
    }

    private static final class CapturingLog extends PublicApiDiagnosticsLog {
        final List<String> aggregates = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<String> samples = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<String> startups = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public void aggregate(String line) { aggregates.add(line); }
        @Override public void sample(String line) { samples.add(line); }
        @Override public void startup(String line) { startups.add(line); }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant instant = START;
        void advanceMinutes(long minutes) { instant = instant.plusSeconds(minutes * 60); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
