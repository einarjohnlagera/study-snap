package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpansionTest {
    private static final String STEM = "NOTE_ADAPTIVE";
    private static final String CLOCK = BlindReviewFixtures.CLOCK;
    private static final int CAP = 120;
    private static final double RATE = 1.25;
    private static final List<String> LONG_STEMS = List.of("LONG_EXAM_DIRECT",
            "LONG_EXAM_LONG_EXAM_POOL", "LONG_EXAM_BOARD_EXAM_POOL");
    @TempDir Path temp;

    @Test void planningUsesBothOutputsFrameSizeMedianAndInclusiveCap() {
        PairMap pairs = ClassAAdjudicationFixture.export(temp,4,ClassACaseManifest.Workflow.NOTE_ADAPTIVE);
        List<String> ids = pairs.pairs().stream().map(PairMap.Pair::id).toList();
        List<String> three = ids.subList(0,3);
        var ordinary = Expansion.plan(pairs,three,3,CAP,RATE,null);
        assertThat(ordinary.requiredPairs()).isEqualTo(3);
        assertThat(ordinary.totalMinutes()).isEqualTo(7.5);
        assertThat(ordinary.minutesPerPair()).isEqualTo(2.5);
        assertThat(ordinary.fitsCap()).isTrue();
        assertThat(Expansion.plan(pairs,three,3,CAP,RATE,40.0).totalMinutes()).isEqualTo(120);
        assertThat(Expansion.plan(pairs,three,3,CAP,RATE,40.0).fitsCap()).isTrue();
        assertThat(Expansion.plan(pairs,three,3,CAP,RATE,41.0).fitsCap()).isFalse();
        assertThat(Expansion.plan(pairs,ids,3,CAP,RATE,null).requiredPairs()).isEqualTo(3);
        assertThat(Expansion.plan(pairs,ids,3,CAP,RATE,null).totalMinutes()).isEqualTo(10);
        var shortFrame = Expansion.plan(pairs,ids.subList(0,2),3,CAP,RATE,null);
        assertThat(shortFrame.requiredPairs()).isEqualTo(2);
        assertThat(shortFrame.plannedPairs()).isEqualTo(2);
        assertThat(shortFrame.frameInsufficient()).isTrue();
    }

    @Test void feasibilityPinsSixWorkflowsAndOneLongExamCap() {
        Map<String,Map<String,Double>> q = new LinkedHashMap<>();
        q.put("NOTE_ADAPTIVE",Map.of("NOTE_ADAPTIVE",7.0));
        q.put("PLAN_ADAPTIVE",Map.of("PLAN_ADAPTIVE",7.0));
        q.put("TEACHER_QUIZ",Map.of("TEACHER_QUIZ",10.0));
        q.put("INTERVIEW_GENERATION",Map.of("INTERVIEW_GENERATION",10.0));
        q.put("BOARD_EXAM",Map.of("BOARD_EXAM",30.0));
        Map<String,Double> longQ = new LinkedHashMap<>();
        longQ.put(LONG_STEMS.get(0),27.5); longQ.put(LONG_STEMS.get(1),48.0);
        longQ.put(LONG_STEMS.get(2),24.0);
        q.put("LONG_EXAM",longQ);
        Map<String,Integer> counts = new HashMap<>();
        q.values().forEach(stems -> stems.keySet().forEach(stem -> counts.put(stem,
                LONG_STEMS.contains(stem) ? 1 : 3)));
        var report = Expansion.feasibility(q,counts,CAP,RATE);
        assertThat(report.get("NOTE_ADAPTIVE")).isEqualTo(new Expansion.Feasibility(52.5,true,0));
        assertThat(report.get("PLAN_ADAPTIVE")).isEqualTo(new Expansion.Feasibility(52.5,true,0));
        assertThat(report.get("TEACHER_QUIZ")).isEqualTo(new Expansion.Feasibility(75,true,0));
        assertThat(report.get("INTERVIEW_GENERATION")).isEqualTo(new Expansion.Feasibility(75,true,0));
        assertThat(report.get("BOARD_EXAM")).isEqualTo(new Expansion.Feasibility(225,false,105));
        assertThat(report.get("LONG_EXAM")).isEqualTo(new Expansion.Feasibility(248.75,false,128.75));
        // Arithmetic diagnostic only: Long Exam itself remains one three-stem workflow.
        var longPoolThreePairs = Expansion.feasibility(Map.of("ARITHMETIC_ONLY",Map.of("LONG_POOL",48.0)),
                Map.of("LONG_POOL",3),CAP,RATE);
        assertThat(longPoolThreePairs.get("ARITHMETIC_ONLY"))
                .isEqualTo(new Expansion.Feasibility(360,false,240));
    }

    @Test void longExamDrawsOnePerStemWithSingleTriggerAndSinglePlan() throws Exception {
        PairMap pairs = ClassAAdjudicationFixture.exportLong(temp,2);
        var scores = ClassAAdjudicationFixture.load(temp,pairs,ReviewSetsTest.clean(pairs),
                ReviewSetsTest.clean(pairs),Map.of(),Map.of());
        ReviewSets sets = ReviewSets.compute(pairs,scores,ClassAAdjudicationFixture.MAPPING);
        Path registryFile = temp.resolve("registry.json");
        StringBuilder json = new StringBuilder("{\"seed\":\"0123456789abcdef-master\",\"streams\":[");
        for (String stem : LONG_STEMS) json.append("{\"workflow\":\"").append(stem)
                .append("\",\"purpose\":\"EXPANSION\",\"rounds\":1},");
        json.setLength(json.length()-1); json.append("]}");
        Files.writeString(registryFile,json.toString()); BlindReviewFixtures.seal(registryFile);
        SeedStreamRegistry registry = SeedStreamRegistry.load(registryFile);
        Map<String,Integer> perStem = new LinkedHashMap<>();
        LONG_STEMS.forEach(stem -> perStem.put(stem,1));
        var scope = new Expansion.WorkflowScope("LONG_EXAM",perStem);
        DrawLog log = new DrawLog(temp.resolve("draw.jsonl"));
        List<String> selected = Expansion.draw(registry,log,pairs,sets,Set.of(),scope,1,CLOCK);
        assertThat(selected).hasSize(3);
        assertThat(selected.stream().map(pairs::workflowOf).toList()).containsExactlyElementsOf(LONG_STEMS);
        assertThat(log.entries()).hasSize(3);
        assertThat(Expansion.draw(registry,log,pairs,sets,Set.of(),scope,1,CLOCK)).isEqualTo(selected);
        var plan = Expansion.plan(pairs,selected,3,CAP,RATE,248.75 / 3);
        assertThat(plan.requiredPairs()).isEqualTo(3);
        assertThat(Expansion.outcome(plan,Set.of(selected.getFirst()),List.of(),false,false).outcome())
                .isEqualTo(Expansion.ExpansionOutcome.INCONCLUSIVE_WORKLOAD_EXHAUSTED);
    }

    @Test void expansionDrawExcludesSpotChecksAndMandatoryPairsAndIsDrawOnce() throws Exception {
        PairMap pairs = ClassAAdjudicationFixture.export(temp,7,ClassACaseManifest.Workflow.NOTE_ADAPTIVE);
        Map<String,String> p = ReviewSetsTest.clean(pairs),s = ReviewSetsTest.clean(pairs);
        String mandatoryId = pairs.pairs().getFirst().id();
        p.put(pairs.itemsOf(mandatoryId,"B").getFirst(),"K");
        ReviewSets sets = ReviewSets.compute(pairs,
                ClassAAdjudicationFixture.load(temp,pairs,p,s,Map.of(),Map.of()),ClassAAdjudicationFixture.MAPPING);
        SeedStreamRegistry registry = SeedStreamRegistry.load(BlindReviewFixtures.registry(temp));
        DrawLog log = new DrawLog(temp.resolve("draw.jsonl"));
        List<String> spot = ReviewSets.spotCheck(sets,registry,log,pairs,STEM,1,1,CLOCK);
        String alreadyReviewed = sets.criticalCleanFrame().stream().filter(id -> !spot.contains(id)).findFirst().orElseThrow();
        var scope = new Expansion.WorkflowScope(STEM,Map.of(STEM,2));
        List<String> draw = Expansion.draw(registry,log,pairs,sets,Set.of(alreadyReviewed),scope,1,CLOCK);
        assertThat(draw).hasSize(2).doesNotContain(mandatoryId,spot.getFirst(),alreadyReviewed);
        assertThat(draw).isSubsetOf(sets.criticalCleanFrame());
        assertThat(Expansion.draw(registry,log,pairs,sets,Set.of(alreadyReviewed),scope,1,CLOCK)).isEqualTo(draw);
        var changed = new Expansion.WorkflowScope(STEM,Map.of(STEM,1));
        Set<String> reviewed = Set.of(alreadyReviewed);
        assertThatThrownBy(() -> Expansion.draw(registry,log,pairs,sets,reviewed,changed,1,CLOCK))
                .isInstanceOf(PairSampler.RedrawWithDifferentFrame.class);
    }

    @Test void incidentUnitIsDistinctPairAndSecondPairStopsImmediately() {
        var plan = new Expansion.ExpansionPlan(3,10,30,true,false,3);
        Set<String> triggerSet = Set.of("trigger");
        var noMiss = new Expansion.PairReview("exp-1",true,false,0,false);
        var twoQuestionsOnePair = new Expansion.PairReview("exp-2",true,true,2,false);
        assertThat(Expansion.outcome(plan,Set.of("trigger"),List.of(noMiss),true,false).outcome())
                .isEqualTo(Expansion.ExpansionOutcome.INCONCLUSIVE_WORKLOAD_EXHAUSTED);
        assertThat(Expansion.outcome(plan,Set.of("trigger"),List.of(twoQuestionsOnePair),false,false).outcome())
                .isEqualTo(Expansion.ExpansionOutcome.INCONCLUSIVE_SCORER_UNRELIABLE);
        assertThat(Expansion.outcome(plan,Set.of("trigger","other"),List.of(),false,false).outcome())
                .isEqualTo(Expansion.ExpansionOutcome.INCONCLUSIVE_SCORER_UNRELIABLE);
        for (int index = 0; index < 3; index++) {
            List<Expansion.PairReview> prefix = new ArrayList<>();
            for (int i = 0; i < index; i++) prefix.add(new Expansion.PairReview("before-"+i,true,false,0,false));
            prefix.add(new Expansion.PairReview("second",true,true,1,false));
            assertThat(Expansion.outcome(plan,Set.of("trigger"),prefix,false,false).outcome())
                    .isEqualTo(Expansion.ExpansionOutcome.INCONCLUSIVE_SCORER_UNRELIABLE);
            prefix.add(new Expansion.PairReview("after",true,false,0,false));
            assertThatThrownBy(() -> Expansion.outcome(plan,triggerSet,prefix,false,false))
                    .isInstanceOf(InvalidExpansion.class);
        }
        var onePlan = new Expansion.ExpansionPlan(1,10,10,true,false,1);
        var twoPlan = new Expansion.ExpansionPlan(2,10,20,true,false,2);
        List<Expansion.PairReview> repeatsTrigger = List.of(new Expansion.PairReview("trigger",true,false,0,false));
        List<Expansion.PairReview> repeatsPair = List.of(noMiss,noMiss);
        assertThatThrownBy(() -> Expansion.outcome(onePlan,triggerSet,repeatsTrigger,false,false))
                .isInstanceOf(InvalidExpansion.class);
        assertThatThrownBy(() -> Expansion.outcome(twoPlan,triggerSet,repeatsPair,false,false))
                .isInstanceOf(InvalidExpansion.class);
    }

    @Test void exhaustionIsDistinctFromUnreliabilityAndPendingDoesNotCount() {
        var overCap = new Expansion.ExpansionPlan(3,75,225,false,false,3);
        var shortPlan = new Expansion.ExpansionPlan(1,10,10,true,true,1);
        var pending = new Expansion.PairReview("exp",false,false,0,false);
        var complete = new Expansion.PairReview("exp",true,false,0,true);
        Set<String> triggerSet = Set.of("trigger");
        List<Expansion.PairReview> pendingList = List.of(pending);
        var exhausted = Expansion.outcome(overCap,Set.of("trigger"),List.of(),false,false);
        assertThat(exhausted.outcome()).isEqualTo(Expansion.ExpansionOutcome.INCONCLUSIVE_WORKLOAD_EXHAUSTED);
        assertThat(exhausted.reason()).contains("no scorer unreliability");
        assertThat(Expansion.outcome(shortPlan,Set.of("trigger"),List.of(pending),true,false).outcome())
                .isEqualTo(Expansion.ExpansionOutcome.INCONCLUSIVE_WORKLOAD_EXHAUSTED);
        assertThat(Expansion.outcome(shortPlan,Set.of("trigger"),List.of(complete),false,false))
                .extracting(Expansion.ExpansionResult::unresolvedFindingPresent).isEqualTo(true);
        assertThat(Expansion.outcome(shortPlan,Set.of("trigger"),List.of(complete),false,false).outcome())
                .isEqualTo(Expansion.ExpansionOutcome.COMPLETE_NO_SECOND_DOUBLE_MISS);
        assertThat(Expansion.outcome(overCap,Set.of("trigger"),List.of(
                new Expansion.PairReview("one",true,false,0,false),
                new Expansion.PairReview("two",true,false,0,false),
                new Expansion.PairReview("three",true,false,0,false)),false,true).outcome())
                .isEqualTo(Expansion.ExpansionOutcome.COMPLETE_NO_SECOND_DOUBLE_MISS);
        assertThatThrownBy(() -> Expansion.outcome(shortPlan,triggerSet,pendingList,false,false))
                .isInstanceOf(InvalidExpansion.class);
        assertThat(Expansion.outcome(shortPlan,Set.of(),List.of(),false,false).outcome())
                .isEqualTo(Expansion.ExpansionOutcome.NOT_TRIGGERED);
    }

    @Test void pairReviewCountsQuestionsButIncidentIsOnePair() throws Exception {
        PairMap pairs = ClassAAdjudicationFixture.export(temp,1,ClassACaseManifest.Workflow.NOTE_ADAPTIVE);
        String id = pairs.pairs().getFirst().id();
        Map<String,QuestionStatus> statuses = new HashMap<>();
        for (String item : pairs.blindItemIds()) statuses.put(item,
                FinalStatus.of(ClassAAdjudicationFixture.direct(item,"C","C",0,"K"),
                        ClassAAdjudicationFixture.MAPPING,true));
        var pair = Expansion.PairReview.from(PairEvidence.build(pairs,statuses).get(id));
        assertThat(pair.doubleMissQuestions()).isEqualTo(2);
        assertThat(pair.hasDoubleMiss()).isTrue();
        assertThat(pair.complete()).isTrue();
        var plan = new Expansion.ExpansionPlan(1,10,10,true,false,1);
        assertThat(Expansion.outcome(plan,Set.of(id),List.of(
                new Expansion.PairReview("different",true,false,0,false)),false,false).outcome())
                .isEqualTo(Expansion.ExpansionOutcome.COMPLETE_NO_SECOND_DOUBLE_MISS);
    }
}
