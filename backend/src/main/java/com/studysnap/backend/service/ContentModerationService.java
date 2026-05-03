package com.studysnap.backend.service;

import com.studysnap.backend.exception.AppException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lightweight, dictionary-based content moderation. No LLM, no external API.
 *
 * <p>Loads banned-word dictionaries from {@code classpath:/moderation/banned_words_*.txt}
 * at startup. Each file is plain text with `#` comments and `# section` headers
 * (e.g. {@code # explicit}, {@code # hate}, {@code # harmful}) that group
 * entries by reason category.
 *
 * <p>Matching is token-based: input is lowercased and punctuation-stripped, then
 * whitespace-tokenized. A token matches a banned entry if {@code token.equals(entry)}.
 * Substring matching is intentionally avoided to prevent false positives on
 * legitimate words ("classic", "scunthorpe", etc).
 *
 * <p>Extensibility:
 * <ul>
 *   <li>Add new categories simply by adding a new section header in the dictionary file.</li>
 *   <li>Add new languages by dropping {@code banned_words_xx.txt} into the same folder.</li>
 *   <li>Replace the loader with an admin-managed table or external moderation API
 *       without changing {@link #isValidContent(String)} / {@link #moderate(String)} callers.</li>
 * </ul>
 */
@Slf4j
@Service
public class ContentModerationService {

    public enum ModerationCategory {
        EXPLICIT,
        HATE,
        HARMFUL,
        OTHER;

        static ModerationCategory fromHeader(String rawHeader) {
            String normalized = rawHeader == null ? "" : rawHeader.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "explicit" -> EXPLICIT;
                case "hate" -> HATE;
                case "harmful" -> HARMFUL;
                default -> OTHER;
            };
        }
    }

    public record ModerationResult(boolean valid, String matchedTerm, ModerationCategory category) {
        public static ModerationResult pass() {
            return new ModerationResult(true, null, null);
        }

        public static ModerationResult fail(String term, ModerationCategory category) {
            return new ModerationResult(false, term, category);
        }
    }

    public static final String BLOCKED_MESSAGE = "This content is not allowed on NoteLib.";
    public static final String BLOCKED_CODE = "CONTENT_NOT_ALLOWED";

    private static final String DICTIONARY_LOCATION_PATTERN = "classpath:/moderation/banned_words_*.txt";

    private final Map<String, ModerationCategory> bannedTerms = new HashMap<>();

    @PostConstruct
    void loadDictionaries() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(DICTIONARY_LOCATION_PATTERN);
            for (Resource resource : resources) {
                loadDictionary(resource);
            }
            log.info("ContentModerationService loaded {} banned terms across {} dictionary files",
                    bannedTerms.size(), resources.length);
        } catch (Exception e) {
            log.error("Failed to load content moderation dictionaries; service will allow all content", e);
        }
    }

    private void loadDictionary(Resource resource) {
        ModerationCategory currentCategory = ModerationCategory.OTHER;
        try (InputStream in = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    String headerBody = trimmed.substring(1).trim();
                    if (!headerBody.isEmpty() && !headerBody.contains(" ")) {
                        currentCategory = ModerationCategory.fromHeader(headerBody);
                    }
                    continue;
                }
                String normalized = trimmed.toLowerCase(Locale.ROOT);
                bannedTerms.putIfAbsent(normalized, currentCategory);
            }
        } catch (Exception e) {
            log.error("Failed to read moderation dictionary {}", resource.getFilename(), e);
        }
    }

    /**
     * Quick yes/no check. Use this when the caller does not need the matched term or category.
     */
    public boolean isValidContent(String text) {
        return moderate(text).valid();
    }

    /**
     * Detailed result including the matched term and category, for logging or future
     * admin reporting. Returns {@link ModerationResult#valid()} for null/blank input
     * (length validation is the caller's responsibility).
     */
    public ModerationResult moderate(String text) {
        if (text == null || text.isBlank() || bannedTerms.isEmpty()) {
            return ModerationResult.pass();
        }
        Set<String> tokens = tokenize(text);
        for (String token : tokens) {
            ModerationCategory category = bannedTerms.get(token);
            if (category != null) {
                return ModerationResult.fail(token, category);
            }
        }
        return ModerationResult.pass();
    }

    /**
     * Throws an {@link AppException} with the standard blocked message when the input is invalid.
     * Use this at validation boundaries (note creation, study-pack input, topic generation).
     */
    public void validateOrThrow(String text) {
        ModerationResult result = moderate(text);
        if (!result.valid()) {
            log.info("Blocked content matched term=\"{}\" category={}", result.matchedTerm(), result.category());
            throw new AppException(BLOCKED_CODE, BLOCKED_MESSAGE, HttpStatus.BAD_REQUEST);
        }
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        StringBuilder current = new StringBuilder(32);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                current.append(Character.toLowerCase(c));
            } else if (!current.isEmpty()) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /**
     * Test-only hook to verify dictionary loading independent of integration tests.
     */
    Optional<ModerationCategory> categoryOf(String term) {
        if (term == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bannedTerms.get(term.toLowerCase(Locale.ROOT)));
    }
}
