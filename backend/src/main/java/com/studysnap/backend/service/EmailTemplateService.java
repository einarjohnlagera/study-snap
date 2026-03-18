package com.studysnap.backend.service;

import com.studysnap.backend.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final String TEMPLATE_BASE_PATH = "classpath:templates/email/";

    private final ResourceLoader resourceLoader;

    public RenderedEmailTemplate render(String templateName, Map<String, String> parameters) {
        String subjectTemplate = loadRequiredTemplate(templateName + ".subject.txt");
        String htmlTemplate = loadRequiredTemplate(templateName + ".html");
        String textTemplate = loadOptionalTemplate(templateName + ".txt");

        String subject = substitute(subjectTemplate, parameters, templateName + ".subject.txt").trim();
        String htmlBody = substitute(htmlTemplate, parameters, templateName + ".html");
        String textBody = textTemplate == null ? null : substitute(textTemplate, parameters, templateName + ".txt");

        return new RenderedEmailTemplate(subject, htmlBody, textBody);
    }

    private String loadRequiredTemplate(String fileName) {
        Resource resource = resourceLoader.getResource(TEMPLATE_BASE_PATH + fileName);
        if (!resource.exists()) {
            throw new AppException(
                    "EMAIL_TEMPLATE_NOT_FOUND",
                    "Could not load email template.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        return readTemplate(resource);
    }

    private String loadOptionalTemplate(String fileName) {
        Resource resource = resourceLoader.getResource(TEMPLATE_BASE_PATH + fileName);
        if (!resource.exists()) {
            return null;
        }
        return readTemplate(resource);
    }

    private String readTemplate(Resource resource) {
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AppException(
                    "EMAIL_TEMPLATE_READ_ERROR",
                    "Could not load email template.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String substitute(String template, Map<String, String> parameters, String templateName) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!parameters.containsKey(key)) {
                throw new AppException(
                        "EMAIL_TEMPLATE_PARAM_MISSING",
                        "Could not render email template parameter: " + key + " in " + templateName,
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
            String value = parameters.get(key);
            String replacement = value == null ? "" : value;
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    public record RenderedEmailTemplate(
            String subject,
            String htmlBody,
            String textBody
    ) {
    }
}
