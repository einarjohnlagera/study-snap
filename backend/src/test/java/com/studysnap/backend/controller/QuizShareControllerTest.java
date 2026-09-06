package com.studysnap.backend.controller;

import com.studysnap.backend.dto.PublicQuizItem;
import com.studysnap.backend.dto.PublicSharedQuizResponse;
import com.studysnap.backend.dto.SharedQuizResultsResponse;
import com.studysnap.backend.service.QuizShareLinkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * ⚠️ REAL-REQUEST tests, not direct handler calls, and the distinction is the whole reason this class
 * exists. {@code v0.119.0} shipped a feature whose JSON POSTs carried no {@code Content-Type}, so Spring
 * rejected every request with {@code HttpMediaTypeNotSupportedException} BEFORE the controller was entered
 * -- while 2,182 frontend tests and a full three-agent pressure test stayed green, because the controller
 * tests called handlers as methods and the component tests mocked the API layer wholesale. The owner found
 * it on first use. A direct handler call passes under that defect by construction.
 */
@ExtendWith(MockitoExtension.class)
class QuizShareControllerTest {

    private static final String TOKEN = "shareToken123";

    @Mock
    private QuizShareLinkService quizShareLinkService;

    private MockMvc mockMvc() {
        return standaloneSetup(new QuizShareController(quizShareLinkService)).build();
    }

    @Test
    void publicSharedQuizIsServedOverARealRequest() throws Exception {
        when(quizShareLinkService.getActivePublicQuiz(TOKEN)).thenReturn(new PublicSharedQuizResponse(
                UUID.randomUUID(),
                "Cell Structure",
                List.of(new PublicQuizItem("Answerable?", List.of("A", "B", "C", "D"), "Concept", "MCQ"))
        ));

        mockMvc().perform(get("/quiz/share/{token}", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noteTitle").value("Cell Structure"))
                .andExpect(jsonPath("$.questions.length()").value(1))
                .andExpect(jsonPath("$.questions[0].questionFormat").value("MCQ"))
                // The answer key must never reach the recipient over the wire either.
                .andExpect(jsonPath("$.questions[0].correctIndex").doesNotExist())
                .andExpect(jsonPath("$.questions[0].explanation").doesNotExist());
    }

    /**
     * ⚠️ The Content-Type and the body are the point. Without {@code .contentType(...)} Spring answers 415
     * before the handler runs, which is exactly the defect this class was created for.
     */
    @Test
    void resultsSubmissionBindsAFullLengthAnswerArrayFromARealRequest() throws Exception {
        when(quizShareLinkService.getSharedQuizResults(eq(TOKEN), any(), any()))
                .thenReturn(new SharedQuizResultsResponse(1, 2, List.of()));

        mockMvc().perform(post("/quiz/share/{token}/results", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [1, null],
                                  "multiAnswers": [null, [0, 2]]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.total").value(2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Integer>> answers = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<List<Integer>>> multiAnswers = ArgumentCaptor.forClass(List.class);
        verify(quizShareLinkService).getSharedQuizResults(eq(TOKEN), answers.capture(), multiAnswers.capture());

        // A null slot survives transport: it is how "this question is MULTI_SELECT" is expressed, and the
        // grader reads both arrays positionally.
        assertThat(answers.getValue()).containsExactly(1, null);
        assertThat(multiAnswers.getValue()).containsExactly(null, List.of(0, 2));
    }
}
