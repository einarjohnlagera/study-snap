package com.studysnap.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.OpenAiPromptResources;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.entity.AskCompanionTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiAskCompanionLlmServiceTest {
    private static final String CRITIQUE_MODEL = "gpt-4.1-mini-test";
    private static final String COMPANION_OVERVIEW = "Backend foundations only.";
    private static final String UNRELATED_QUESTION = "Who is the president?";
    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestBodyUriSpec requestSpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    private ObjectMapper objectMapper;
    private OpenAiAskCompanionLlmService service;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getLlm().getApi().setApiKey("test-api-key");
        properties.getSettings().setModelCritique(CRITIQUE_MODEL);
        objectMapper = new ObjectMapper();
        service = new OpenAiAskCompanionLlmService(
                properties,
                objectMapper,
                restClient,
                prompts()
        );
    }

    @Test
    void answerUsesCritiqueModelAndGroundedCompanionPrompt() throws Exception {
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri("/responses")).thenReturn(requestSpec);
        when(requestSpec.body(bodyCaptor.capture())).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("""
                {"output_text":"{\\\"answer\\\":\\\"The guide says to practice retrieval after each section.\\\"}"}
                """);
        CompanionContent companion = new CompanionContent(
                COMPANION_OVERVIEW,
                "Practice retrieval after each section.",
                "Avoid passive reading.",
                null,
                List.of(),
                List.of()
        );

        String answer = service.answer(companion, List.<AskCompanionTurn>of(), UNRELATED_QUESTION);

        assertThat(answer).isEqualTo("The guide says to practice retrieval after each section.");
        JsonNode request = objectMapper.readTree(bodyCaptor.getValue());
        assertThat(request.path("model").asText()).isEqualTo(CRITIQUE_MODEL);
        String serializedInput = request.path("input").toString();
        assertThat(serializedInput)
                .contains(COMPANION_OVERVIEW)
                .contains(UNRELATED_QUESTION)
                .contains("Use only this authored content")
                .contains("do not use outside knowledge");
    }

    @Test
    void answerSendsPriorAssistantTurnsAsOutputTextNotInputText() throws Exception {
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        when(restClient.post()).thenReturn(requestSpec);
        when(requestSpec.uri("/responses")).thenReturn(requestSpec);
        when(requestSpec.body(bodyCaptor.capture())).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("""
                {"output_text":"{\\\"answer\\\":\\\"Follow-up answer.\\\"}"}
                """);
        CompanionContent companion = new CompanionContent(
                COMPANION_OVERVIEW,
                "Practice retrieval after each section.",
                "Avoid passive reading.",
                null,
                List.of(),
                List.of()
        );
        List<AskCompanionTurn> turnHistory = List.of(
                new AskCompanionTurn("First question?", "First answer.", OffsetDateTime.now())
        );

        service.answer(companion, turnHistory, "Follow-up question?");

        JsonNode request = objectMapper.readTree(bodyCaptor.getValue());
        JsonNode input = request.path("input");
        JsonNode assistantMessage = null;
        JsonNode userFollowUpMessage = null;
        for (JsonNode message : input) {
            String text = message.path("content").path(0).path("text").asText();
            if ("assistant".equals(message.path("role").asText())) {
                assistantMessage = message;
            } else if ("Follow-up question?".equals(text)) {
                userFollowUpMessage = message;
            }
        }

        assertThat(assistantMessage).isNotNull();
        assertThat(assistantMessage.path("content").path(0).path("type").asText()).isEqualTo("output_text");
        assertThat(userFollowUpMessage).isNotNull();
        assertThat(userFollowUpMessage.path("content").path(0).path("type").asText()).isEqualTo("input_text");
    }

    private OpenAiPromptResources prompts() {
        return new OpenAiPromptResources(
                "system",
                "developer",
                objectMapper.createObjectNode(),
                "note system",
                "note developer",
                "companion system",
                "companion developer",
                "challenge system",
                "challenge developer",
                "board system",
                "board developer",
                "teacher system",
                "teacher developer",
                "adaptive system",
                "adaptive developer",
                "interview system",
                "interview developer",
                "critique system",
                "critique developer",
                "Answer only from the Companion and do not use outside knowledge.",
                "Use only this authored content: {COMPANION_CONTENT}",
                "long system",
                "long developer"
        );
    }
}
