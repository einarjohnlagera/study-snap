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
class OfficialStudyPlanWishlistSecurityIntegrationTest {
    private static final String WISHLIST_PATH = "/official-study-plan-wishlist";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get(WISHLIST_PATH + "/status")
                        .param("courseProgram", "Nursing"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(WISHLIST_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseProgram\":\"Nursing\"}"))
                .andExpect(status().isUnauthorized());
    }
}
