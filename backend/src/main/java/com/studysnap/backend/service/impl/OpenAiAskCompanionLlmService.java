package com.studysnap.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studysnap.backend.config.OpenAiPromptResources;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.CompanionContent;
import com.studysnap.backend.entity.AskCompanionTurn;
import com.studysnap.backend.exception.AskCompanionUnavailableException;
import com.studysnap.backend.service.AskCompanionLlmService;
import com.studysnap.backend.util.LlmResponseUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "studysnap.llm.api", name = "provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiAskCompanionLlmService implements AskCompanionLlmService {
    private static final String RESPONSES_PATH = "/responses";
    private static final String SCHEMA_NAME = "note_lib_ask_companion_answer";
    private static final String COMPANION_PLACEHOLDER = "{COMPANION_CONTENT}";
    private static final String ROLE_USER = "user";

    private final StudySnapProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final OpenAiPromptResources promptResources;

    public OpenAiAskCompanionLlmService(
            StudySnapProperties properties,
            ObjectMapper objectMapper,
            RestClient restClient,
            OpenAiPromptResources promptResources
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.promptResources = promptResources;
    }

    @Override
    public String answer(CompanionContent companion, List<AskCompanionTurn> turnHistory, String question) {
        try {
            ObjectNode request = buildRequest(companion, turnHistory, question);
            String responseBody = restClient.post()
                    .uri(RESPONSES_PATH)
                    .body(objectMapper.writeValueAsString(request))
                    .retrieve()
                    .body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                throw new AskCompanionUnavailableException();
            }
            JsonNode responseJson = objectMapper.readTree(responseBody);
            String payload = LlmResponseUtils.findOutputJson(responseJson)
                    .orElseThrow(AskCompanionUnavailableException::new);
            PromptAnswer parsed = objectMapper.readValue(payload, PromptAnswer.class);
            if (parsed.answer() == null || parsed.answer().isBlank()) {
                throw new AskCompanionUnavailableException();
            }
            return parsed.answer().trim();
        } catch (AskCompanionUnavailableException ex) {
            throw ex;
        } catch (IOException | RestClientException ex) {
            throw new AskCompanionUnavailableException();
        }
    }

    private ObjectNode buildRequest(
            CompanionContent companion,
            List<AskCompanionTurn> turnHistory,
            String question
    ) throws IOException {
        String model = properties.getSettings().getModelCritique();
        if (model == null || model.isBlank()
                || properties.getLlm().getApi().getApiKey() == null
                || properties.getLlm().getApi().getApiKey().isBlank()) {
            throw new AskCompanionUnavailableException();
        }
        ArrayNode input = objectMapper.createArrayNode();
        input.add(buildMessage("system", promptResources.askCompanionSystemPrompt()));
        input.add(buildMessage(
                "developer",
                promptResources.askCompanionDeveloperPromptTemplate()
                        .replace(COMPANION_PLACEHOLDER, objectMapper.writeValueAsString(companion))
        ));
        if (turnHistory != null) {
            for (AskCompanionTurn turn : turnHistory) {
                input.add(buildMessage(ROLE_USER, turn.question()));
                input.add(buildMessage("assistant", turn.answer()));
            }
        }
        input.add(buildMessage(ROLE_USER, question));

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.set("input", input);
        ObjectNode format = request.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", SCHEMA_NAME);
        format.put("strict", true);
        format.set("schema", buildAnswerSchema());
        return request;
    }

    private ObjectNode buildMessage(String role, String text) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        ObjectNode content = message.putArray("content").addObject();
        content.put("type", "input_text");
        content.put("text", text);
        return message;
    }

    private ObjectNode buildAnswerSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putArray("required").add("answer");
        schema.putObject("properties").putObject("answer").put("type", "string");
        return schema;
    }

    private record PromptAnswer(String answer) {
    }
}
