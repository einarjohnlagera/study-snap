package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

final class VerifierRequestBuilder {
    static final String[] NAMES = {"DRAFT-verifier-system.txt", "DRAFT-verifier-developer.txt", "DRAFT-verifier-user-template.txt", "DRAFT-verifier-schema.json"};
    private final byte[][] bytes = new byte[4][];
    private final ObjectMapper mapper;
    VerifierRequestBuilder(ObjectMapper mapper, Path directory) throws Exception {
        this.mapper = mapper;
        for (int i = 0; i < 4; i++) bytes[i] = Files.readAllBytes(directory.resolve(NAMES[i]));
        mapper.readTree(bytes[3]);
    }
    String promptVersion() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (byte[] part : bytes) digest.update(part);
        return HexFormat.of().formatHex(digest.digest());
    }
    ObjectNode build(String model, JsonNode note, List<StatementPairVerifierBenchmark.Item> items) throws Exception {
        ArrayNode itemArray = mapper.createArrayNode();
        for (int i = 0; i < items.size(); i++) {
            var pair = items.get(i).pair();
            itemArray.addObject().put("itemId", String.valueOf(i + 1))
                    .put("statement1", pair.statement1()).put("statement2", pair.statement2());
        }
        String user = new String(bytes[2], java.nio.charset.StandardCharsets.UTF_8);
        String[][] fields = {{"DOMAIN_CONTEXT","domainContext"},{"SUBJECT","subject"},
                {"LEARNER_LEVEL","learnerLevel"},{"NOTE_TEXT","noteText"},{"STUDY_PACK_SUMMARY","summary"}};
        for (String[] field : fields) {
            JsonNode value = note.get(field[1]);
            user = user.replace("{" + field[0] + "}", value == null || value.isNull() ? "(not provided)" : value.asText());
        }
        user = user.replace("{ITEMS_JSON}", mapper.writeValueAsString(itemArray));
        ObjectNode body = mapper.createObjectNode().put("model", model);
        ArrayNode input = body.putArray("input");
        addMessage(input,"system",new String(bytes[0],java.nio.charset.StandardCharsets.UTF_8));
        addMessage(input,"developer",new String(bytes[1],java.nio.charset.StandardCharsets.UTF_8));
        addMessage(input,"user",user);
        ObjectNode format = body.putObject("text").putObject("format");
        format.put("type","json_schema").put("name","note_lib_statement_pair_verifier");
        format.set("schema", mapper.readTree(bytes[3])); format.put("strict",true);
        return body;
    }
    private static void addMessage(ArrayNode input,String role,String text) {
        input.addObject().put("role",role).putArray("content").addObject().put("type","input_text").put("text",text);
    }
}
