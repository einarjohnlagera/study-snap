package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailUnsubscribeLinkService {
    private static final String SUPPORT_UNSUBSCRIBE_MAILTO = "mailto:support@mail.notelib.app?subject=unsubscribe";
    private static final String LIST_UNSUBSCRIBE = "List-Unsubscribe";
    private static final String LIST_UNSUBSCRIBE_POST = "List-Unsubscribe-Post";
    private static final String ONE_CLICK_HEADER_VALUE = "List-Unsubscribe=One-Click";
    private static final String FOOTER_STYLE = "font-size: 12px; color: #6b7280;";

    private final StudySnapProperties properties;
    private final UnsubscribeTokenService unsubscribeTokenService;

    public OptionalEmailUnsubscribeContext buildContext(UUID userId, UnsubscribeCategory category) {
        String token = unsubscribeTokenService.sign(userId, category);
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String unsubscribeUrl = buildAbsoluteUrl("/unsubscribe?token=" + encodedToken);
        String oneClickUrl = buildAbsoluteUrl("/api/email/unsubscribe?token=" + encodedToken);
        return new OptionalEmailUnsubscribeContext(
                unsubscribeUrl,
                "<p style=\"" + FOOTER_STYLE + "\">Don’t want these emails? <a href=\"" + unsubscribeUrl
                        + "\">Unsubscribe from " + category.displayName() + " emails</a>.</p>",
                "Don’t want these emails? Unsubscribe from " + category.displayName() + " emails:\n" + unsubscribeUrl,
                Map.of(
                        LIST_UNSUBSCRIBE, "<" + oneClickUrl + ">, <" + SUPPORT_UNSUBSCRIBE_MAILTO + ">",
                        LIST_UNSUBSCRIBE_POST, ONE_CLICK_HEADER_VALUE
                )
        );
    }

    private String buildAbsoluteUrl(String path) {
        String baseUrl = properties.getEmail().getAppBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBase + path;
    }

    public record OptionalEmailUnsubscribeContext(
            String unsubscribeUrl,
            String htmlFooter,
            String textFooter,
            Map<String, String> headers
    ) {
        public static OptionalEmailUnsubscribeContext empty() {
            return new OptionalEmailUnsubscribeContext("", "", "", Map.of());
        }
    }
}
