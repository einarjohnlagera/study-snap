package com.studysnap.backend.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the defect class that produced a live 500 on "Shared with you".
 *
 * <p>⚠️ In a NATIVE query, PostgreSQL types the parameters itself. A bare {@code :param is null} gives it
 * nothing to infer from and the whole statement fails at PARSE time — {@code could not determine data type of
 * parameter $n} — so the endpoint 500s on every call, not just on a paged one. The fix is an explicit
 * {@code cast(:param as <type>)}.
 *
 * <p>⚠️ SCOPE, stated so this is not mistaken for coverage. Verified empirically against PostgreSQL 16
 * (2026-08-27): of the hazard shapes considered, only TWO actually fail — a parameter whose SOLE type
 * context is {@code IS [NOT] NULL}, and arithmetic on a bare parameter. {@code coalesce}, {@code nullif},
 * {@code case}, {@code lower()}, {@code $1 = $2}, {@code order by $1} and {@code limit $1} all prepare
 * cleanly, because PG resolves unknown to text. **Do NOT lengthen this pattern to cover them** — that
 * restores the guess this repo argued against in `v0.85.0`.
 *
 * <p>⚠️ Typing is ORDER-SENSITIVE: {@code (col < :p or :p is null)} prepares, while
 * {@code (:p is null or col < :p)} does not. So "just reorder the OR" is a landmine that a later
 * readability edit silently re-arms. Keep the cast.
 *
 * <p>⚠️ WHAT THIS STILL MISSES: SQL assembled from concatenated constants, queries built via
 * {@code EntityManager.createNativeQuery} (both {@code *RepositoryImpl} files), a cast to the WRONG type
 * ({@code timestamp} rather than {@code timestamptz} parses fine and shifts the cursor), and anything
 * outside this directory. **The durable fix is executing native queries against real PostgreSQL** — see
 * {@link NativeQueryPostgresIntegrationTest}. This guard remains the Docker-free tripwire.
 *
 * <p>⚠️ This is a SOURCE-TEXT test on purpose, and it is the only form that works here. The suite runs on H2,
 * which accepts the uncast form, so no behavioural test in this repository can fail on this defect — that is
 * exactly how it reached production in `v0.91.0`. JPQL is unaffected, because Hibernate types those parameters,
 * so this deliberately checks native queries only.
 */
class NativeQueryParameterTypingTest {
    private static final Path REPOSITORY_SOURCE_DIR =
            Path.of("src/main/java/com/studysnap/backend/repository");

    /** A named parameter used directly in an IS NULL test, which PostgreSQL cannot type. */
    private static final Pattern UNTYPED_IS_NULL =
            Pattern.compile(":\\w+\\s+is\\s+(not\\s+)?null", Pattern.CASE_INSENSITIVE);

    @Test
    void noNativeQueryTestsANamedParameterForNullWithoutCastingIt() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(REPOSITORY_SOURCE_DIR)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = stripComments(Files.readString(source));
                if (!text.replace(" ", "").contains("nativeQuery=true")) {
                    continue;
                }
                Matcher matcher = UNTYPED_IS_NULL.matcher(text);
                while (matcher.find()) {
                    offenders.add(source.getFileName() + " -> " + matcher.group());
                }
            }
        }

        assertThat(offenders)
                .withFailMessage(
                        "Native query parameter used in IS NULL without a cast: %s. PostgreSQL cannot infer the "
                                + "type and fails the statement at parse time (could not determine data type of "
                                + "parameter $n), 500ing the endpoint. Wrap it as cast(:param as <type>). "
                                + "H2 accepts the uncast form, so no behavioural test here can catch this.",
                        offenders)
                .isEmpty();
    }

    /**
     * ⚠️ Comments are stripped before scanning. Without this, the javadoc on THIS class — which quotes the
     * offending shape in order to explain it — matches its own pattern and fails the build. Documentation
     * describing a defect must not be indistinguishable from the defect.
     */
    private static String stripComments(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
    }

    @Test
    void theSharedWithMeCursorParametersStayCast() throws IOException {
        // The specific query this guard was written for; pinned so a rewrite cannot silently drop the casts.
        // ⚠️ stripComments here too: this javadoc quotes the cast strings to explain them, so reading
        // raw source would keep passing after the query itself moved or lost its casts.
        String source = stripComments(Files.readString(REPOSITORY_SOURCE_DIR.resolve("NoteShareRepository.java")));

        assertThat(source).contains("cast(:cursorCreatedAt as timestamptz)");
        assertThat(source).contains("cast(:cursorId as uuid)");
    }
}
