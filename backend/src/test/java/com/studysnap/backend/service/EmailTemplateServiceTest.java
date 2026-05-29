package com.studysnap.backend.service;

import com.studysnap.backend.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTemplateServiceTest {

    private final EmailTemplateService service = new EmailTemplateService(new DefaultResourceLoader());

    @Test
    void render_replacesTemplateParameters() {
        EmailTemplateService.RenderedEmailTemplate rendered = service.render(
                "verification-email",
                Map.of(
                        "firstName", "Note",
                        "app_name", "NoteLib",
                        "verification_url", "https://notelib.test/verify-email?token=abc",
                        "email_from", "noreply@notelib.test",
                        "expiration_text", "This verification link expires in 24 hours."
                )
        );

        assertThat(rendered.subject()).isEqualTo("Verify your email for NoteLib");
        assertThat(rendered.textBody()).contains("Hi Note,");
        assertThat(rendered.htmlBody()).contains("https://notelib.test/verify-email?token=abc");
        assertThat(rendered.textBody()).contains("This verification link expires in 24 hours.");
    }

    @Test
    void render_welcomeTemplateReflectsCurrentPlanMessaging() {
        EmailTemplateService.RenderedEmailTemplate rendered = service.render(
                "welcome-email",
                Map.of(
                        "firstName", "Note",
                        "dashboard_url", "https://notelib.test/dashboard"
                )
        );

        assertThat(rendered.subject()).isEqualTo("Welcome to NoteLib — Turn Notes Into Quizzes");
        assertThat(rendered.textBody()).contains("10 Study Packs / month");
        assertThat(rendered.textBody()).contains("Challenge Quiz");
        assertThat(rendered.textBody()).contains("Difficulty selection");
        assertThat(rendered.textBody()).doesNotContain("Challenge Quiz (Premium — coming soon)");
    }

    @Test
    void render_throwsWhenTemplateParameterIsMissing() {
        assertThatThrownBy(() -> service.render(
                "verification-email",
                Map.of(
                        "firstName", "Note",
                        "app_name", "NoteLib",
                        "verification_url", "https://notelib.test/verify-email?token=abc"
                )
        ))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Could not render email template parameter")
                .extracting(ex -> ((AppException) ex).getCode())
                .isEqualTo("EMAIL_TEMPLATE_PARAM_MISSING");
    }
}
