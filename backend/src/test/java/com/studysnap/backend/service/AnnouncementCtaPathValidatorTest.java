package com.studysnap.backend.service;

import com.studysnap.backend.exception.InvalidAnnouncementRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ⚠️ THIS IS A SECURITY TEST. An admin-authored link rendered inside every user's inbox is a phishing
 * surface if it can point anywhere, so the rule is pinned rather than left to the form.
 */
class AnnouncementCtaPathValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example",
            "http://evil.example/path",
            "//evil.example",
            "//evil.example/dashboard",
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "mailto:someone@evil.example",
            "dashboard",
            "\\\\evil.example",
            "/dashboard\\@evil.example",
            "/dashboard#fragment",
            "/dash board",
            "/dashboard?next=https://evil.example<script>"
    })
    void rejectsAnythingThatIsNotASameOriginRelativePath(String ctaPath) {
        assertThatThrownBy(() -> AnnouncementCtaPathValidator.validate(ctaPath))
                .isInstanceOf(InvalidAnnouncementRequestException.class)
                .hasMessageStartingWith("ctaPath:");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/",
            "/dashboard",
            "/valid/path",
            "/valid/path?x=1",
            "/notes/9f3c-1234?tab=quiz&mode=board",
            "/collections/my-set_2.0~final"
    })
    void acceptsSameOriginRelativePaths(String ctaPath) {
        assertThat(AnnouncementCtaPathValidator.validate(ctaPath)).isEqualTo(ctaPath);
    }

    @Test
    void trimsSurroundingWhitespaceRatherThanStrippingCharactersFromTheMiddle() {
        assertThat(AnnouncementCtaPathValidator.validate("  /valid/path?x=1  ")).isEqualTo("/valid/path?x=1");
    }

    @Test
    void treatsAbsentAndBlankAsNoCallToAction() {
        assertThat(AnnouncementCtaPathValidator.validate(null)).isNull();
        assertThat(AnnouncementCtaPathValidator.validate("   ")).isNull();
    }

    /**
     * ⚠️ A LEADING-SLASH CHECK ALONE IS NOT ENOUGH. {@code //evil.example} starts with "/" and is a
     * protocol-relative URL that leaves the origin, so the explicit prefix check is load-bearing —
     * deleting it while keeping the regex leaves the whole control passing.
     */
    @Test
    void theProtocolRelativePrefixIsRejectedEvenThoughItStartsWithASlash() {
        assertThat("//evil.example".startsWith("/")).isTrue();
        assertThatThrownBy(() -> AnnouncementCtaPathValidator.validate("//evil.example"))
                .isInstanceOf(InvalidAnnouncementRequestException.class);
    }
}
