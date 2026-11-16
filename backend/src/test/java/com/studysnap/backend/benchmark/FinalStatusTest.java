package com.studysnap.backend.benchmark;

import com.studysnap.backend.benchmark.ClassAScoreSchemaV2.Verdict;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class FinalStatusTest {
    private static final String ID = "blind-00001";
    private static final EvidenceMapping MAPPING = ClassAAdjudicationFixture.MAPPING;
    @TempDir Path temp;
    record Oracle(String p, String s, int border, String h, Verdict f, boolean pending,
                  boolean miss, boolean cleared, boolean downgraded, boolean overturned,
                  boolean t2, boolean t3, boolean pairMissing) {}

    @Test void sixteenLiteralDecisionRowsRunThroughValidatedScoreFiles() throws Exception {
        ArmKeyGateFixtures.classA(temp);
        PairMap pairs = PairMap.fromPackets(temp.resolve("packets"));
        List<Oracle> rows = List.of(
            new Oracle("C","C",0,null,Verdict.CLEAN,false,false,false,false,false,false,false,false),
            new Oracle("C","C",0,"C",Verdict.CLEAN,false,false,false,false,false,false,false,false),
            new Oracle("C","C",0,"K",Verdict.CRITICAL,false,true,false,false,false,true,false,false),
            new Oracle("C","C",0,"Q",Verdict.QUALITY_FAIL,false,false,false,false,false,false,true,false),
            new Oracle("C","C",0,"U",Verdict.UNRESOLVED,false,false,false,false,false,false,false,true),
            new Oracle("K","C",0,"K",Verdict.CRITICAL,false,false,false,false,false,true,false,false),
            new Oracle("K","C",0,"C",Verdict.CLEAN,false,false,true,false,false,false,false,false),
            new Oracle("K","C",0,"Q",Verdict.QUALITY_FAIL,false,false,false,true,false,false,true,false),
            new Oracle("K","C",0,"U",Verdict.UNRESOLVED,false,false,false,false,false,false,false,true),
            new Oracle("Q","Q",0,null,Verdict.QUALITY_FAIL,false,false,false,false,false,false,true,false),
            new Oracle("Q","Q",0,"C",Verdict.CLEAN,false,false,false,false,true,false,false,false),
            new Oracle("Q","Q",0,"K",Verdict.CRITICAL,false,true,false,false,false,true,false,false),
            new Oracle("C","Q",0,"K",Verdict.CRITICAL,false,true,false,false,false,true,false,false),
            new Oracle("C","C",2,null,null,true,false,false,false,false,false,false,true),
            new Oracle("C","MISSING",0,"K",Verdict.CRITICAL,false,false,false,false,false,true,false,false),
            new Oracle("C","C",0,"U",Verdict.UNRESOLVED,false,false,false,false,false,false,false,true));
        assertThat(rows).hasSize(16);
        for (Oracle row : rows) {
            var loaded = ClassAAdjudicationFixture.load(temp, pairs, Map.of(ID,row.p()), Map.of(ID,row.s()),
                    row.h() == null ? Map.of() : Map.of(ID,row.h()), Map.of(ID,row.border()));
            QuestionStatus actual = FinalStatus.of(loaded.get(ID), MAPPING, false);
            assertThat(actual.f()).as("row %s", row).isEqualTo(row.f());
            assertThat(actual.state()).isEqualTo(row.pending()
                    ? QuestionStatus.State.PENDING_REVIEW : QuestionStatus.State.FINAL);
            assertThat(actual.doubleMiss()).isEqualTo(row.miss());
            assertThat(actual.humanCleared()).isEqualTo(row.cleared());
            assertThat(actual.humanDowngraded()).isEqualTo(row.downgraded());
            assertThat(actual.overturnedAgreedQualityFail()).isEqualTo(row.overturned());
            assertThat(actual.t2Event()).isEqualTo(row.t2());
            assertThat(actual.t3Event()).isEqualTo(row.t3());
            assertThat(actual.pairMissing()).isEqualTo(row.pairMissing());
        }
    }

    @Test void all640FeasibleCombinationsObeyIndependentInvariants() {
        // Each slot has 3 OK verdicts x 2 border values + MISSING + INVALID = 8 states.
        // Thus 8 x 8 x 5 human states x 2 pair-review states = 640 feasible cells.
        List<String> scorers = List.of("C", "Q", "K", "MISSING", "INVALID");
        List<String> humans = java.util.Arrays.asList(null, "C", "Q", "K", "U");
        int cells = 0;
        for (String p : scorers) for (String s : scorers) for (int bits = 0; bits < 4; bits++) {
            if ((bits & 1) != 0 && (p.equals("MISSING") || p.equals("INVALID"))) continue;
            if ((bits & 2) != 0 && (s.equals("MISSING") || s.equals("INVALID"))) continue;
            for (String h : humans) for (boolean pairReview : List.of(false, true)) {
                cells++;
                var item = ClassAAdjudicationFixture.direct(ID, p, s, bits, h);
                QuestionStatus actual = FinalStatus.of(item, MAPPING, pairReview);
                boolean missing = p.equals("MISSING") || p.equals("INVALID")
                        || s.equals("MISSING") || s.equals("INVALID");
                boolean flagged = p.equals("K") || s.equals("K");
                boolean independentReview = flagged || bits != 0 || missing || !p.equals(s);
                assertThat(actual.reviewReasons().isEmpty()).isEqualTo(!independentReview);
                assertThat(ClassAScoreFilesV2.needsHumanReview(item, MAPPING)).isEqualTo(independentReview);
                boolean pending = h == null && (independentReview || pairReview);
                assertThat(actual.state() == QuestionStatus.State.PENDING_REVIEW).isEqualTo(pending);
                if (h != null) assertThat(actual.f()).isEqualTo(switch (h) {
                    case "C" -> Verdict.CLEAN; case "Q" -> Verdict.QUALITY_FAIL;
                    case "K" -> Verdict.CRITICAL; default -> Verdict.UNRESOLVED;
                });
                if (pending) assertThat(actual.f()).isNull();
                if (h == null && !pending) assertThat(actual.f()).isIn(Verdict.CLEAN, Verdict.QUALITY_FAIL);
                assertThat(actual.doubleMiss()).isEqualTo("K".equals(h) && !missing && !flagged);
                assertThat(actual.scorerFailureCritical()).isEqualTo("K".equals(h) && missing && !flagged);
                assertThat(actual.doubleMiss() && actual.scorerFailureCritical()).isFalse();
                assertThat(actual.t2Event() && actual.t3Event()).isFalse();
                assertThat(actual.pairMissing()).isEqualTo(pending || actual.f() == Verdict.UNRESOLVED);
            }
        }
        assertThat(cells).isEqualTo(640);
    }

    @Test void missingAndInvalidCriticalAreOperationalFailuresNotDoubleMisses() throws Exception {
        ArmKeyGateFixtures.classA(temp);
        PairMap pairs = PairMap.fromPackets(temp.resolve("packets"));
        for (String failed : List.of("MISSING", "INVALID")) {
            var scores = ClassAAdjudicationFixture.load(temp, pairs, Map.of(ID,failed), Map.of(ID,"C"),
                    Map.of(ID,"K"), Map.of());
            QuestionStatus status = FinalStatus.of(scores.get(ID), MAPPING, true);
            assertThat(status.scorerFailureCritical()).isTrue();
            assertThat(status.doubleMiss()).isFalse();
            assertThat(status.t2Event()).isTrue();
            assertThat(status.f()).isEqualTo(Verdict.CRITICAL);
            assertThat(status.reviewReasons()).contains(QuestionStatus.ReviewReason.SCORER_MISSING);
            assertThat(status.reviewReasons()).contains(QuestionStatus.ReviewReason.DISAGREEMENT);
        }
    }
}
