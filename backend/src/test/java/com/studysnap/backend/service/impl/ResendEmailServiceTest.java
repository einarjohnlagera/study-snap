package com.studysnap.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.EmailMessage;
import com.studysnap.backend.service.SuppressedEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ResendEmailServiceTest {
    private HttpClient httpClient;
    private SuppressedEmailService suppressedEmailService;
    private ResendEmailService service;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        StudySnapProperties properties = mock(StudySnapProperties.class);
        StudySnapProperties.Email email = mock(StudySnapProperties.Email.class);
        when(properties.getEmail()).thenReturn(email);
        when(email.getResendApiKey()).thenReturn("test-key");
        when(email.getFrom()).thenReturn("NoteLib <noreply@notelib.app>");
        when(email.getResendApiBaseUrl()).thenReturn(null);
        suppressedEmailService = mock(SuppressedEmailService.class);
        service = new ResendEmailService(properties, new ObjectMapper(), httpClient, suppressedEmailService);
    }

    private static EmailMessage message() {
        return new EmailMessage("to@example.com", "Subject", "<p>hi</p>", "hi");
    }

    private static HttpResponse<String> response(int status, String retryAfterSeconds) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("{}");
        Map<String, List<String>> headerMap = retryAfterSeconds == null
                ? Map.of()
                : Map.of("Retry-After", List.of(retryAfterSeconds));
        when(response.headers()).thenReturn(HttpHeaders.of(headerMap, (name, value) -> true));
        return response;
    }

    @Test
    void sendsSuccessfully_whenProviderReturns2xx() throws Exception {
        HttpResponse<String> ok = response(200, null);
        when(httpClient.<String>send(any(), any())).thenReturn(ok);
        EmailMessage message = message();

        assertThatCode(() -> service.sendEmail(message)).doesNotThrowAnyException();
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void skipsSuppressedRecipientWithoutCallingProvider() throws Exception {
        EmailMessage message = message();
        when(suppressedEmailService.isSuppressed(message.to())).thenReturn(true);

        assertThat(service.sendEmail(message)).isFalse();
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    void retriesOn429_thenSucceeds() throws Exception {
        HttpResponse<String> rateLimited = response(429, "0");
        HttpResponse<String> ok = response(200, null);
        when(httpClient.<String>send(any(), any())).thenReturn(rateLimited, ok);
        EmailMessage message = message();

        assertThatCode(() -> service.sendEmail(message)).doesNotThrowAnyException();
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    void throwsAfterMaxAttempts_whenAlways429() throws Exception {
        HttpResponse<String> rateLimited = response(429, "0");
        when(httpClient.<String>send(any(), any())).thenReturn(rateLimited);
        EmailMessage message = message();

        assertThatThrownBy(() -> service.sendEmail(message)).isInstanceOf(AppException.class);
        verify(httpClient, times(3)).send(any(), any());
    }

    @Test
    void doesNotRetry_onNon429Error() throws Exception {
        HttpResponse<String> badRequest = response(400, null);
        when(httpClient.<String>send(any(), any())).thenReturn(badRequest);
        EmailMessage message = message();

        assertThatThrownBy(() -> service.sendEmail(message)).isInstanceOf(AppException.class);
        verify(httpClient, times(1)).send(any(), any());
    }
}
