package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerTimingSheetTest {
    private static final String L_FULLREVIEWS = "fullReviews";
    private static final String L_PACKETID = "packetId";
    private static final String L_QUESTIONS = "questions";
    private static final String L_MINUTES = "minutes";
    private static final String L_FLAGGEDONLYREVIEWS = "flaggedOnlyReviews";
    private static final String L_PACKET_2 = "packet-2";
    private static final String L_RATIONALERATINGS = "rationaleRatings";
    private static final String L_ITEMID = "itemId";
    private static final String L_P = "P";
    private static final String L_UNKNOWN = "unknown";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;
    private ObjectNode sheet() {
        ObjectNode root = JSON.createObjectNode();
        root.putObject("usageMeter").putNull("before").putNull("after").put("unit", "percent");
        ArrayNode full = root.putArray(L_FULLREVIEWS);
        full.addObject().put(L_PACKETID, "packet-0").put(L_QUESTIONS, 7).put(L_MINUTES, 14);
        full.addObject().put(L_PACKETID, "packet-3").put(L_QUESTIONS, 24).put(L_MINUTES, 48);
        full.addObject().put(L_PACKETID, "packet-4").put(L_QUESTIONS, 30).put(L_MINUTES, 60);
        root.putArray(L_FLAGGEDONLYREVIEWS).addObject().put(L_PACKETID, L_PACKET_2)
                .put("flaggedQuestions", 2).put(L_MINUTES, 3);
        root.putArray(L_RATIONALERATINGS).addObject().put(L_ITEMID, "pilot-00001")
                .put("scorerRole", L_P).put("adequate", true);
        return root;
    }
    private OwnerTimingSheet load(ObjectNode root) throws Exception {
        Path file = temp.resolve("timing.json"); Files.writeString(file, root.toString());
        return OwnerTimingSheet.load(file);
    }
    private OwnerTimingSheet.Summary summarize(ObjectNode root) throws Exception {
        return OwnerTimingSheet.summarize(load(root), PilotTestKeys.key(temp), PilotTestKeys.bars(temp));
    }
    @Test void validSheetReportsMeasuredRatesAndCounts() throws Exception {
        var result = summarize(sheet());
        assertThat(result.totalFullMinutes()).isEqualTo(122);
        assertThat(result.totalFullQuestions()).isEqualTo(61);
        assertThat(result.minutesPerQuestionBySize()).containsEntry(7, 2.0).containsEntry(24, 2.0)
                .containsEntry(30, 2.0);
        assertThat(result.flaggedMinutesPerQuestion()).isEqualTo(1.5);
        assertThat(result.ratingCounts()).containsEntry(L_P, 1);
    }
    @Test void requiresSizesFromFrozenBarsAndMatchingPacketQuestionCounts() throws Exception {
        ObjectNode root = sheet(); ((ArrayNode) root.path(L_FULLREVIEWS)).remove(1);
        ObjectNode missing = root;
        assertThatThrownBy(() -> summarize(missing)).isInstanceOf(OwnerTimingSheet.InvalidTimingSheet.class);
        root = sheet(); ((ObjectNode) root.path(L_FULLREVIEWS).get(1)).put(L_PACKETID, L_PACKET_2).put(L_QUESTIONS, 10);
        ObjectNode wrongSize = root;
        assertThatThrownBy(() -> summarize(wrongSize)).isInstanceOf(OwnerTimingSheet.InvalidTimingSheet.class);
        root = sheet(); ((ObjectNode) root.path(L_FULLREVIEWS).get(1)).put(L_QUESTIONS, 23);
        ObjectNode wrongCount = root;
        assertThatThrownBy(() -> summarize(wrongCount)).isInstanceOf(OwnerTimingSheet.InvalidTimingSheet.class);
    }
    @Test void rejectsInvalidMinutesPacketsAndRatings() throws Exception {
        ObjectNode root = sheet(); ((ObjectNode) root.path(L_FULLREVIEWS).get(0)).put(L_MINUTES, 0);
        ObjectNode zero = root;
        assertThatThrownBy(() -> summarize(zero)).isInstanceOf(OwnerTimingSheet.InvalidTimingSheet.class);
        root = sheet(); ((ObjectNode) root.path(L_FLAGGEDONLYREVIEWS).get(0)).put(L_PACKETID, L_UNKNOWN);
        ObjectNode unknownPacket = root;
        assertThatThrownBy(() -> summarize(unknownPacket)).isInstanceOf(OwnerTimingSheet.InvalidTimingSheet.class);
        root = sheet(); ((ObjectNode) root.path(L_RATIONALERATINGS).get(0)).put(L_ITEMID, L_UNKNOWN);
        ObjectNode unknownItem = root;
        assertThatThrownBy(() -> summarize(unknownItem)).isInstanceOf(OwnerTimingSheet.InvalidTimingSheet.class);
        root = sheet(); ((ArrayNode) root.path(L_RATIONALERATINGS)).add(root.path(L_RATIONALERATINGS).get(0).deepCopy());
        ObjectNode duplicate = root;
        assertThatThrownBy(() -> summarize(duplicate)).isInstanceOf(OwnerTimingSheet.InvalidTimingSheet.class);
        root = sheet(); ((ObjectNode) root.path(L_FULLREVIEWS).get(0)).put(L_MINUTES, "NaN");
        ObjectNode nonFinite = root;
        assertThatThrownBy(() -> load(nonFinite)).isInstanceOf(OwnerTimingSheet.InvalidTimingSheet.class);
    }
    @Test void requiredSizesAreReadFromBarsRatherThanFixed() throws Exception {
        ObjectNode barsJson = (ObjectNode) JSON.readTree(Files.readString(Path.of(
                "src/test/java/com/studysnap/backend/benchmark/pilot-bars-test-only.json")));
        ArrayNode sizes = ((ObjectNode) barsJson.path("bars")).putArray("ownerFullReviewSizes");
        sizes.add(10).add(24).add(30);
        Path file = temp.resolve("custom-bars.json"); Files.writeString(file, barsJson.toString());
        Files.writeString(file.resolveSibling("custom-bars.json.sha256"), FreezeRecord.sha256(Files.readAllBytes(file)));
        ObjectNode sheet = sheet(); ((ObjectNode) sheet.path(L_FULLREVIEWS).get(0)).put(L_PACKETID, L_PACKET_2)
                .put(L_QUESTIONS, 10);
        var result = OwnerTimingSheet.summarize(load(sheet), PilotTestKeys.key(temp), PilotBars.load(file));
        assertThat(result.minutesPerQuestionBySize()).containsKey(10);
    }
}
