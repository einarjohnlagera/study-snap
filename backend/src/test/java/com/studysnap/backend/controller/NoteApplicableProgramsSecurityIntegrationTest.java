package com.studysnap.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NoteApplicableProgramsSecurityIntegrationTest {
    private static final String APPLICABLE_PROGRAMS_PATH =
            "/notes/00000000-0000-0000-0000-000000000001/applicable-programs";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get(APPLICABLE_PROGRAMS_PATH))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put(APPLICABLE_PROGRAMS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseProgramIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }
}
