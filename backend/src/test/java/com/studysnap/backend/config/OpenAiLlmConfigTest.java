package com.studysnap.backend.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The LLM read timeout is the ceiling on how long a JDBC connection can be held across an OpenAI call,
 * so it is pool configuration as much as HTTP configuration — pinned here for that reason.
 *
 * <p>⚠️ WHY IT IS BEHAVIOURAL RATHER THAN A PROPERTY ASSERTION. v0.112.0 extracted the timeout from a
 * hardcoded {@code Duration.ofSeconds(180)} into {@code studysnap.llm.api.read-timeout-seconds} so
 * Phase 2 can dial it from the environment once leak detection reports real hold durations. A mutation
 * run at that change found that **hardcoding the value back still compiles and still leaves the yaml
 * key declared** — {@link DataSourcePoolContractTest} would stay green while the property became dead
 * config and every environment override silently did nothing. Asserting the number in the properties
 * class would not have caught that either. Only exercising the built {@link RestClient} does.
 *
 * <p>This mirrors the reasoning {@code AppConfigTest} records for the analytics executor: where the
 * question is "does this setting actually take effect", a property assertion proves the setting exists,
 * not that anything reads it.
 */
class OpenAiLlmConfigTest {
    private static final int SLOW_HANDLER_MILLIS = 3_000;
    private static final int IMPATIENT_READ_TIMEOUT_SECONDS = 1;
    private static final int PATIENT_READ_TIMEOUT_SECONDS = 30;

    private HttpServer server;

    @BeforeEach
    void startSlowServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            sleepQuietly();
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopSlowServer() {
        server.stop(0);
    }

    /**
     * ⚠️ THE DISCRIMINATING HALF IS THE CONTROL BELOW, NOT THIS ONE. A timeout on its own also fires if
     * the stub server is simply broken, so on its own it would pass for the wrong reason.
     */
    @Test
    void readTimeoutIsHonouredFromConfigurationRatherThanHardcoded() {
        RestClient client = clientWithReadTimeout(IMPATIENT_READ_TIMEOUT_SECONDS);

        assertThatThrownBy(() -> client.get().uri("/slow").retrieve().body(String.class))
                .isInstanceOf(ResourceAccessException.class)
                .hasRootCauseInstanceOf(SocketTimeoutException.class);
    }

    /**
     * The control: the SAME request against the SAME server succeeds once the configured timeout is
     * generous. Together with the test above this proves the timeout came from the property — a
     * hardcoded {@code Duration.ofSeconds(180)} passes this test and FAILS the one above.
     */
    @Test
    void aGenerousConfiguredTimeoutLetsTheSameSlowCallSucceed() {
        RestClient client = clientWithReadTimeout(PATIENT_READ_TIMEOUT_SECONDS);

        assertThat(client.get().uri("/slow").retrieve().body(String.class)).isEqualTo("{}");
    }

    /**
     * ⚠️ 180 s IS DELIBERATELY UNCHANGED — the value was made overridable, not shortened (owner
     * decision, 2026-09-04). The outage finding suggests cutting it "toward ~90 s" but argues no
     * specific number, and one {@code RestClient} serves every LLM call including the largest Study
     * Pack generation, so an unevidenced cut would convert working generations into failures. Pinned so
     * that lowering it is a decision taken against Phase 2's evidence rather than a quiet edit.
     */
    @Test
    void theShippedDefaultStaysAtOneHundredEightySecondsUntilPhaseTwoProducesEvidence() {
        StudySnapProperties.Api api = new StudySnapProperties().getLlm().getApi();

        assertThat(api.getReadTimeoutSeconds()).isEqualTo(180);
        assertThat(api.getConnectTimeoutSeconds()).isEqualTo(10);
    }

    private RestClient clientWithReadTimeout(int seconds) {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getLlm().getApi().setReadTimeoutSeconds(seconds);
        properties.getLlm().getApi().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return new OpenAiLlmConfig().openAiRestClient(properties);
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(SLOW_HANDLER_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
