package com.studysnap.backend.benchmark;

import com.studysnap.backend.dto.QuickReviewIncorrectQuestionRequest;
import com.studysnap.backend.entity.LearnerLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Blind-placement and privacy checks for preregistration section 7, Revision 2.2. */
class ClassBBlindPacketExportTest {
    private static final String WORKFLOW_FILE = "STUDY_TIP.jsonl";
    private static final String FIRST_DIR = "first";
    private static final String SECOND_DIR = "second";
    private static final String FIRST_KEY = "first-arm-key.jsonl";
    private static final String TOKEN_COUNT = "tokenCount";
    private static final String KEY_FILE = "key.jsonl";
    @TempDir Path temp;

    private List<ClassBCaseManifest.Case> fixtures() {
        List<ClassBCaseManifest.Case> cases = new ArrayList<>();
        for (int i = 0; i < 10; i++) cases.add(new ClassBCaseManifest.Case(
                "synthetic-" + i, ClassBCaseManifest.Workflow.STUDY_TIP, "synthetic", false,
                LearnerLevel.COLLEGE, List.of(new QuickReviewIncorrectQuestionRequest(
                        "Question?", "Answer", "Explanation.")), null, null, null, null, null,
                null, null, null));
        return cases;
    }
    private List<ClassBBlindPacketExport.Output> outputs() {
        List<ClassBBlindPacketExport.Output> outputs = new ArrayList<>();
        for (ClassBCaseManifest.Case item : fixtures()) {
            outputs.add(new ClassBBlindPacketExport.Output(item.fixtureId(),
                    ClassBScoreAggregator.Arm.BASELINE, "Synthetic answer one."));
            outputs.add(new ClassBBlindPacketExport.Output(item.fixtureId(),
                    ClassBScoreAggregator.Arm.CANDIDATE, "Synthetic answer two."));
        }
        return outputs;
    }
    private ClassBBlindPacketExport.Export export(String name, long seed,
                                                  List<ClassBBlindPacketExport.Output> outputs) {
        return ClassBBlindPacketExport.export(fixtures(), outputs, temp.resolve(name),
                temp.resolve(name + "-arm-key.jsonl"), seed);
    }
    @Test void sameSeedIsByteIdenticalAndArmKeyIsSeparate() throws Exception {
        ClassBBlindPacketExport.Export first = export(FIRST_DIR, 182L, outputs());
        List<ClassBBlindPacketExport.Output> reversed = outputs();
        Collections.reverse(reversed);
        ClassBBlindPacketExport.Export second = export(SECOND_DIR, 182L, reversed);
        String packet = Files.readString(temp.resolve(FIRST_DIR).resolve(WORKFLOW_FILE));
        assertThat(packet).isEqualTo(Files.readString(temp.resolve(SECOND_DIR).resolve(WORKFLOW_FILE)));
        assertThat(Files.readString(temp.resolve(FIRST_KEY)))
                .isEqualTo(Files.readString(temp.resolve("second-arm-key.jsonl")));
        assertThat(first.armKey()).isEqualTo(second.armKey());
        assertThat(packet).doesNotContain("\"arm\"", "\"armKey\"", "\"fixtureId\"",
                "\"caseId\"", "BASELINE", "CANDIDATE", "gpt-", TOKEN_COUNT);
        assertThat(Files.exists(temp.resolve(FIRST_DIR).resolve(FIRST_KEY))).isFalse();
        ClassBBlindPacketExport.Export changed = export("changed", 183L, outputs());
        assertThat(changed.armKey()).isNotEqualTo(first.armKey());
        assertThat(firstPlacement(first)).isNotEqualTo(firstPlacement(changed));
    }
    @Test void packetRefusesArmModelAndTokenLeaks() {
        for (String leaked : List.of("baseline", "gpt-4.1-mini", "Claude", TOKEN_COUNT,
                "input_tokens: 123")) {
            List<ClassBBlindPacketExport.Output> bad = outputs();
            bad.set(0, new ClassBBlindPacketExport.Output(bad.get(0).caseId(), bad.get(0).arm(), leaked));
            assertThatThrownBy(() -> export("leak", 182L, bad))
                    .isInstanceOf(ClassBBlindPacketExport.InvalidPacket.class);
        }
    }
    @Test void packetRefusesRepositoryPathAndKeyInsidePacketDirectory() {
        Path repoPath = GateTestFixture.SOURCE;
        List<ClassBCaseManifest.Case> fixtureRows = fixtures();
        List<ClassBBlindPacketExport.Output> outputRows = outputs();
        Path externalKey = temp.resolve(KEY_FILE);
        assertThatThrownBy(() -> ClassBBlindPacketExport.export(fixtureRows, outputRows, repoPath,
                externalKey, 182L))
                .isInstanceOf(ClassBBlindPacketExport.InvalidPacket.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        Path packet = temp.resolve("packet");
        Path nestedKey = packet.resolve(KEY_FILE);
        assertThatThrownBy(() -> ClassBBlindPacketExport.export(fixtureRows, outputRows, packet,
                nestedKey, 182L))
                .isInstanceOf(ClassBBlindPacketExport.InvalidPacket.class);
    }

    private java.util.Map<String, ClassBScoreAggregator.Arm> firstPlacement(ClassBBlindPacketExport.Export value) {
        java.util.Map<String, ClassBScoreAggregator.Arm> result = new java.util.HashMap<>();
        for (int i = 0; i < value.armKey().size(); i += 2) {
            ClassBScoreAggregator.Key first = value.armKey().get(i);
            result.put(first.caseId(), first.arm());
        }
        return result;
    }
}
