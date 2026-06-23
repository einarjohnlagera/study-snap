package com.studysnap.backend.controller;

import com.studysnap.backend.exception.GlobalExceptionHandler;
import com.studysnap.backend.exception.InvalidUnsubscribeTokenException;
import com.studysnap.backend.service.EmailUnsubscribeService;
import com.studysnap.backend.service.UnsubscribeCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailUnsubscribeControllerTest {
    private EmailUnsubscribeService emailUnsubscribeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        emailUnsubscribeService = mock(EmailUnsubscribeService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EmailUnsubscribeController(emailUnsubscribeService))
                .setControllerAdvice(new GlobalExceptionHandler(DataSize.ofMegabytes(10)))
                .build();
    }

    @Test
    void unsubscribe_acceptsQueryParamToken() throws Exception {
        when(emailUnsubscribeService.unsubscribe("query-token"))
                .thenReturn(new EmailUnsubscribeService.UnsubscribeResult(
                        UnsubscribeCategory.WEEKLY_SUMMARY,
                        "Weekly summary"
                ));

        mockMvc.perform(post("/email/unsubscribe").param("token", "query-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category", is("WEEKLY_SUMMARY")))
                .andExpect(jsonPath("$.displayName", is("Weekly summary")));

        verify(emailUnsubscribeService).unsubscribe("query-token");
    }

    @Test
    void unsubscribe_acceptsFormToken() throws Exception {
        when(emailUnsubscribeService.unsubscribe("form-token"))
                .thenReturn(new EmailUnsubscribeService.UnsubscribeResult(
                        UnsubscribeCategory.MARKETING,
                        "Product news & tips"
                ));

        mockMvc.perform(post("/email/unsubscribe")
                        .contentType("application/x-www-form-urlencoded")
                        .content("token=form-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category", is("MARKETING")));

        verify(emailUnsubscribeService).unsubscribe("form-token");
    }

    @Test
    void unsubscribe_invalidTokenReturnsBadRequest() throws Exception {
        when(emailUnsubscribeService.unsubscribe("bad-token")).thenThrow(new InvalidUnsubscribeTokenException());

        mockMvc.perform(post("/email/unsubscribe").param("token", "bad-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_UNSUBSCRIBE_TOKEN")));
    }
}
