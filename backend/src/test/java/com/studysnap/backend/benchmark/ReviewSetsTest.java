package com.studysnap.backend.benchmark;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewSetsTest {
    @TempDir Path temp;
    private static final EvidenceMapping MAPPING = ClassAAdjudicationFixture.MAPPING;
    private static final String STEM = "NOTE_ADAPTIVE";
    private static final String CLOCK = BlindReviewFixtures.CLOCK;

    @Test void bothPlacementsDetermineMandatoryAndAgreedQualityRemainsInFrame() throws Exception {
        PairMap pairs = ClassAAdjudicationFixture.export(temp, 6, ClassACaseManifest.Workflow.NOTE_ADAPTIVE);
        Map<String,String> p = clean(pairs), s = clean(pairs);
        List<PairMap.Pair> ps = pairs.pairs();
        String otherOutput = pairs.itemsOf(ps.get(0).id(), "B").getFirst();
        String agreedQ = pairs.itemsOf(ps.get(1).id(), "A").getFirst();
        String borderline = pairs.itemsOf(ps.get(2).id(), "A").getFirst();
        String disagreement = pairs.itemsOf(ps.get(3).id(), "A").getFirst();
        String missing = pairs.itemsOf(ps.get(4).id(), "A").getFirst();
        p.put(otherOutput,"K"); s.put(otherOutput,"K");
        p.put(agreedQ,"Q"); s.put(agreedQ,"Q");
        s.put(disagreement,"Q"); s.put(missing,"MISSING");
        var scores = ClassAAdjudicationFixture.load(temp,pairs,p,s,Map.of(),Map.of(borderline,1));
        ReviewSets sets = ReviewSets.compute(pairs,scores,MAPPING);
        assertThat(sets.mandatory()).containsExactlyInAnyOrder(ps.get(0).id(),ps.get(2).id(),
                ps.get(3).id(),ps.get(4).id());
        assertThat(sets.criticalCleanFrame()).containsExactlyInAnyOrder(ps.get(1).id(),ps.get(5).id());
        assertThat(sets.mandatory()).doesNotContainAnyElementsOf(sets.criticalCleanFrame());
        assertThat(sets.mandatory().size()+sets.criticalCleanFrame().size()).isEqualTo(ps.size());
        assertThat(sets.frameByStem().get(STEM)).contains(ps.get(1).id());
        SeedStreamRegistry registry = SeedStreamRegistry.load(BlindReviewFixtures.registry(temp));
        List<String> frameDraw = ReviewSets.spotCheck(sets,registry,
                new DrawLog(temp.resolve("frame-draw.jsonl")),pairs,STEM,1,2,CLOCK);
        assertThat(frameDraw).contains(ps.get(1).id());
        Set<String> required = ReviewSets.reviewRequiredPairs(sets,List.of(ps.get(1).id()));
        assertThat(required).contains(ps.get(0).id(),ps.get(1).id());
        String cleanInsideMandatory = pairs.itemsOf(ps.get(0).id(),"A").getFirst();
        QuestionStatus pending = FinalStatus.of(scores.get(cleanInsideMandatory),MAPPING,
                required.contains(ps.get(0).id()));
        assertThat(pending.state()).isEqualTo(QuestionStatus.State.PENDING_REVIEW);
        assertThat(pending.f()).isNull();
        assertThat(PairEvidence.build(pairs, statuses(pairs,scores,required)).get(ps.get(0).id()).complete()).isFalse();
    }

    @Test void spotCheckUsesOnlyFrameAndDrawOnceIncludingEmptyFrame() throws Exception {
        PairMap pairs = ClassAAdjudicationFixture.export(temp,4,ClassACaseManifest.Workflow.NOTE_ADAPTIVE);
        Map<String,String> p = clean(pairs),s=clean(pairs);
        String flagged = pairs.itemsOf(pairs.pairs().getFirst().id(),"B").getFirst();
        p.put(flagged,"K");
        ReviewSets sets = ReviewSets.compute(pairs,ClassAAdjudicationFixture.load(temp,pairs,p,s,Map.of(),Map.of()),MAPPING);
        SeedStreamRegistry registry = SeedStreamRegistry.load(BlindReviewFixtures.registry(temp));
        DrawLog log = new DrawLog(temp.resolve("draw.jsonl"));
        List<String> selected = ReviewSets.spotCheck(sets,registry,log,pairs,STEM,1,2,CLOCK);
        assertThat(selected).hasSize(2).isSubsetOf(sets.frameByStem().get(STEM));
        assertThat(ReviewSets.spotCheck(sets,registry,log,pairs,STEM,1,2,CLOCK)).isEqualTo(selected);
        assertThatThrownBy(() -> ReviewSets.spotCheck(sets,registry,log,pairs,STEM,1,1,CLOCK))
                .isInstanceOf(PairSampler.RedrawWithDifferentFrame.class);
        for (String id : pairs.blindItemIds()) p.put(id,"K");
        ReviewSets allMandatory = ReviewSets.compute(pairs,
                ClassAAdjudicationFixture.load(temp,pairs,p,s,Map.of(),Map.of()),MAPPING);
        assertThat(allMandatory.frameByStem()).containsKey(STEM);
        assertThat(allMandatory.frameByStem().get(STEM)).isEmpty();
        List<String> empty = ReviewSets.spotCheck(allMandatory,registry,
                new DrawLog(temp.resolve("empty.jsonl")),pairs,STEM,2,3,CLOCK);
        assertThat(empty).isEmpty();
        assertThat(new DrawLog(temp.resolve("empty.jsonl")).entries().getFirst().exhausted()).isTrue();
    }

    @Test void missingOrForeignScoresAreRejected() throws Exception {
        PairMap pairs = ClassAAdjudicationFixture.export(temp,1,ClassACaseManifest.Workflow.NOTE_ADAPTIVE);
        var scores = new HashMap<>(ClassAAdjudicationFixture.load(temp,pairs,clean(pairs),clean(pairs),Map.of(),Map.of()));
        String id = pairs.blindItemIds().iterator().next();
        var saved = scores.remove(id);
        assertThatThrownBy(() -> ReviewSets.compute(pairs,scores,MAPPING)).isInstanceOf(InvalidEvidence.class);
        scores.put(id,saved);
        scores.put("foreign",saved);
        assertThatThrownBy(() -> ReviewSets.compute(pairs,scores,MAPPING)).isInstanceOf(InvalidEvidence.class);
    }

    @Test void spotCheckNeverCrossesStemWhenSeveralFramesExist() throws Exception {
        PairMap pairs = ClassAAdjudicationFixture.exportLong(temp,2);
        ReviewSets sets = ReviewSets.compute(pairs,ClassAAdjudicationFixture.load(temp,pairs,
                clean(pairs),clean(pairs),Map.of(),Map.of()),MAPPING);
        String stem = "LONG_EXAM_DIRECT";
        Path registryFile = temp.resolve("seeds.json");
        Files.writeString(registryFile,"{\"seed\":\"0123456789abcdef-master\",\"streams\":["
                + "{\"workflow\":\"LONG_EXAM_DIRECT\",\"purpose\":\"SPOT_CHECK\",\"rounds\":1}]}");
        BlindReviewFixtures.seal(registryFile);
        SeedStreamRegistry registry = SeedStreamRegistry.load(registryFile);
        List<String> selected = ReviewSets.spotCheck(sets,registry,
                new DrawLog(temp.resolve("spot.jsonl")),pairs,stem,1,2,CLOCK);
        assertThat(selected).hasSize(2).allSatisfy(id -> assertThat(pairs.workflowOf(id)).isEqualTo(stem));
        assertThat(sets.frameByStem()).containsKeys("LONG_EXAM_LONG_EXAM_POOL","LONG_EXAM_BOARD_EXAM_POOL");
    }

    static Map<String,String> clean(PairMap pairs) {
        Map<String,String> values = new HashMap<>();
        for (String id : pairs.blindItemIds()) values.put(id,"C");
        return values;
    }
    static Map<String,QuestionStatus> statuses(PairMap pairs,Map<String,ClassAScoreFilesV2.ItemScores> scores,
                                                Set<String> required) {
        Map<String,QuestionStatus> values = new HashMap<>();
        for (String id : pairs.blindItemIds()) values.put(id,
                FinalStatus.of(scores.get(id),MAPPING,required.contains(pairs.pairOf(id))));
        return values;
    }
}
