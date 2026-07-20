package com.studysnap.backend.controller;

import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.service.NoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class TagControllerTest {
    private static final String INVALID_TAG_SCOPE_CODE = "INVALID_TAG_SCOPE";
    private static final String BIOLOGY_TAG = "Biology";
    private static final String CHEMISTRY_TAG = "Chemistry";

    @Mock
    private NoteService noteService;

    private TagController tagController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tagController = new TagController(noteService);
        mockMvc = standaloneSetup(tagController).build();
    }

    @Test
    void listTags_returnsPublicTagsForAnonymousUsers() throws Exception {
        when(noteService.listPublicTags()).thenReturn(List.of(BIOLOGY_TAG, CHEMISTRY_TAG));

        mockMvc.perform(get("/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(BIOLOGY_TAG))
                .andExpect(jsonPath("$[1]").value(CHEMISTRY_TAG));

        verify(noteService).listPublicTags();
    }

    @Test
    void listTags_rejectsUnknownScope() {
        assertThatThrownBy(() -> tagController.listTags("mine"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", INVALID_TAG_SCOPE_CODE)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }
}
