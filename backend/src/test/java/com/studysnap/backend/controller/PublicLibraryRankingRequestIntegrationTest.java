package com.studysnap.backend.controller;

import com.studysnap.backend.dto.PublicNoteListResponse;
import com.studysnap.backend.exception.PublicLibraryRankingBusyException;
import com.studysnap.backend.service.NoteService;
import com.studysnap.backend.service.PublicLibraryRankingSnapshotCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full filter-chain request, not the standalone controller harness that bypasses security. */
@SpringBootTest
@AutoConfigureMockMvc
class PublicLibraryRankingRequestIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private NoteService noteService;
    @MockitoBean private PublicLibraryRankingSnapshotCache snapshotCache;

    @Test
    void anonymousRankedTagRequestKeepsTheExistingJsonShape() throws Exception {
        when(snapshotCache.resolve()).thenReturn(new PublicLibraryRankingSnapshotCache.Resolution(null, null));
        when(noteService.listPublic(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new PublicNoteListResponse(List.of(), 0, 0, 20, 0L, false));

        mockMvc.perform(get("/notes/public")
                        .param("tag", "common-tag")
                        .param("sort", "recommended")
                        .param("page", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalMatching").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
        verify(snapshotCache).resolve();
    }

    @Test
    void exhaustedFallbackBulkheadReturnsCalm503WithRetryAfter() throws Exception {
        when(snapshotCache.resolve()).thenThrow(new PublicLibraryRankingBusyException());
        mockMvc.perform(get("/notes/public")
                        .param("tag", "common-tag")
                        .param("sort", "recommended")
                        .param("page", "0")
                        .param("pageSize", "20"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "3"))
                .andExpect(jsonPath("$.error.code").value("PUBLIC_LIBRARY_RANKING_BUSY"))
                .andExpect(jsonPath("$.error.message")
                        .value("Explore is a little busy right now. Please try again in a moment."));
    }
}
