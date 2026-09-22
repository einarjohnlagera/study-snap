package com.studysnap.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CampaignFeedbackSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/feedback/campaign"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/feedback/campaign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryBlockers\":[\"TOO_MANY_STEPS\"]}"))
                .andExpect(status().isUnauthorized());
    }
}
