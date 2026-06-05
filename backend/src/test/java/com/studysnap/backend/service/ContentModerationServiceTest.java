package com.studysnap.backend.service;

import com.studysnap.backend.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentModerationServiceTest {

    private ContentModerationService service;

    @BeforeEach
    void setUp() {
        service = new ContentModerationService();
        service.loadDictionaries();
    }

    @Test
    void allowsLegitimateAcademicContent() {
        assertThat(service.isValidContent("Newton's Laws of Motion explain how forces act on objects."))
                .isTrue();
        assertThat(service.isValidContent("The mitochondria is the powerhouse of the cell."))
                .isTrue();
        assertThat(service.isValidContent("Sex education and biological reproduction are core biology topics."))
                .isTrue();
        assertThat(service.isValidContent("Ang photosynthesis ay proseso kung saan ang halaman ay gumagawa ng pagkain."))
                .isTrue();
    }

    @Test
    void blocksExplicitEnglishProfanity() {
        ContentModerationService.ModerationResult result = service.moderate("This is fucking bullshit.");
        assertThat(result.valid()).isFalse();
        assertThat(result.matchedTerm()).isEqualTo("fucking");
        assertThat(result.category()).isEqualTo(ContentModerationService.ModerationCategory.EXPLICIT);
    }

    @Test
    void blocksFilipinoProfanity() {
        ContentModerationService.ModerationResult result = service.moderate("putang ina mo");
        assertThat(result.valid()).isFalse();
        assertThat(result.category()).isEqualTo(ContentModerationService.ModerationCategory.EXPLICIT);
    }

    @Test
    void doesNotMatchSubstringsToAvoidFalsePositives() {
        // "classic", "scunthorpe", and "passage" all contain substrings of banned terms in
        // naive substring matching. Token-based matching keeps them safe.
        assertThat(service.isValidContent("This is a classic problem in passage analysis."))
                .isTrue();
        // "Damascus" should not be blocked even though it begins with "dam".
        assertThat(service.isValidContent("Damascus steel is studied in metallurgy class."))
                .isTrue();
    }

    @Test
    void stripsPunctuationBeforeMatching() {
        assertThat(service.isValidContent("F*U*C*K is censored, but plain fuck is not."))
                .isFalse();
    }

    @Test
    void validateOrThrow_throwsAppExceptionWithStandardCode() {
        assertThatThrownBy(() -> service.validateOrThrow("you fucking idiot"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> {
                    AppException ex = (AppException) e;
                    assertThat(ex.getCode()).isEqualTo(ContentModerationService.BLOCKED_CODE);
                    assertThat(ex.getMessage()).isEqualTo(ContentModerationService.BLOCKED_MESSAGE);
                });
    }

    @Test
    void validateOrThrow_passesForCleanInput() {
        service.validateOrThrow("Quadratic equations are solved using the quadratic formula.");
    }

    @Test
    void emptyOrNullInputPasses() {
        assertThat(service.isValidContent(null)).isTrue();
        assertThat(service.isValidContent("")).isTrue();
        assertThat(service.isValidContent("   ")).isTrue();
    }

    @Test
    void dictionariesLoadCategoriesCorrectly() {
        assertThat(service.categoryOf("fuck")).contains(ContentModerationService.ModerationCategory.EXPLICIT);
        assertThat(service.categoryOf("nigger")).contains(ContentModerationService.ModerationCategory.HATE);
        assertThat(service.categoryOf("kys")).contains(ContentModerationService.ModerationCategory.HARMFUL);
        assertThat(service.categoryOf("puta")).contains(ContentModerationService.ModerationCategory.EXPLICIT);
        assertThat(service.categoryOf("photosynthesis")).isEmpty();
    }
}
