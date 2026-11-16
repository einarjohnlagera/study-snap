package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PairEvidenceTest {
    @TempDir Path temp;
    private static final EvidenceMapping MAPPING = ClassAAdjudicationFixture.MAPPING;

    @Test void realPairCountsBothPlacementsAndSeparatesPendingFromUnresolved() throws Exception {
        PairMap pairs = ClassAAdjudicationFixture.export(temp,1,ClassACaseManifest.Workflow.NOTE_ADAPTIVE);
        String pair = pairs.pairs().getFirst().id();
        String a = pairs.itemsOf(pair,"A").getFirst(), b = pairs.itemsOf(pair,"B").getFirst();
        var scores = ClassAAdjudicationFixture.load(temp,pairs,Map.of(a,"C",b,"C"),
                Map.of(a,"C",b,"C"),Map.of(a,"K",b,"U"),Map.of());
        Map<String,QuestionStatus> statuses = ReviewSetsTest.statuses(pairs,scores,Set.of(pair));
        PairEvidence evidence = PairEvidence.build(pairs,statuses).get(pair);
        assertThat(evidence.placementA().questions()).isEqualTo(1);
        assertThat(evidence.placementA().criticalQuestions()).isEqualTo(1);
        assertThat(evidence.placementA().doubleMisses()).isEqualTo(1);
        assertThat(evidence.placementA().t2Event()).isTrue();
        assertThat(evidence.placementB().unresolvedQuestions()).isEqualTo(1);
        assertThat(evidence.placementB().criticalRange()).isEqualTo(new CountRange(0,1));
        assertThat(evidence.complete()).isFalse();
        statuses.put(b,FinalStatus.of(ClassAAdjudicationFixture.direct(b,"C","C",0,null),MAPPING,true));
        evidence = PairEvidence.build(pairs,statuses).get(pair);
        assertThat(evidence.placementB().pendingQuestions()).isEqualTo(1);
        assertThat(evidence.placementB().criticalRange()).isEqualTo(new CountRange(0,1));
        Set<String> required = Set.of(pair);
        assertThatThrownBy(() -> LockReadiness.check(statuses,required))
                .isInstanceOf(InvalidEvidence.class).hasMessageContaining(b);
    }

    @Test void differenceBoundsHaveCandidateMinusBaselineOrientationAndSwapSymmetry() {
        var knownC = output(3,0,0); var knownB = output(1,0,0);
        var unknownC = output(3,2,0); var unknownB = output(1,1,0);
        assertThat(PairEvidence.differenceRange(knownC,knownB)).isEqualTo(new CountRange(2,2));
        assertThat(PairEvidence.differenceRange(unknownC,knownB)).isEqualTo(new CountRange(2,4));
        assertThat(PairEvidence.differenceRange(knownC,unknownB)).isEqualTo(new CountRange(1,2));
        CountRange both = PairEvidence.differenceRange(unknownC,unknownB);
        assertThat(both).isEqualTo(new CountRange(1,4));
        CountRange swapped = PairEvidence.differenceRange(unknownB,unknownC);
        assertThat(swapped).isEqualTo(new CountRange(-both.high(),-both.low()));
        assertThat(PairEvidence.differenceRange(output(0,0,1),knownB))
                .isEqualTo(new CountRange(-1,0));
    }

    @Test void agreedQualityDenominatorCountsOnlyHumanReviewedQuestions() throws Exception {
        PairMap pairs = ClassAAdjudicationFixture.export(temp,2,ClassACaseManifest.Workflow.NOTE_ADAPTIVE);
        Map<String,String> p = ReviewSetsTest.clean(pairs),s=ReviewSetsTest.clean(pairs);
        String reviewed = pairs.itemsOf(pairs.pairs().getFirst().id(),"A").getFirst();
        String unreviewed = pairs.itemsOf(pairs.pairs().get(1).id(),"A").getFirst();
        p.put(reviewed,"Q");s.put(reviewed,"Q");p.put(unreviewed,"Q");s.put(unreviewed,"Q");
        var scores = ClassAAdjudicationFixture.load(temp,pairs,p,s,Map.of(reviewed,"C"),Map.of());
        var evidence = PairEvidence.build(pairs,ReviewSetsTest.statuses(pairs,scores,Set.of()));
        assertThat(evidence.get(pairs.pairOf(reviewed)).placementA().agreedQualityReviewed()).isEqualTo(1);
        assertThat(evidence.get(pairs.pairOf(reviewed)).placementA().overturnedAgreedQualityFail()).isEqualTo(1);
        assertThat(evidence.get(pairs.pairOf(unreviewed)).placementA().agreedQualityReviewed()).isZero();
        assertThat(evidence.get(pairs.pairOf(unreviewed)).placementA().unreviewedAgreedQualityFail()).isEqualTo(1);
        assertThat(evidence.get(pairs.pairOf(unreviewed)).placementA().t3Event()).isTrue();
    }

    @Test void noncriticalScorerMissAndAgreedFailStaySeparateByPlacement() throws Exception {
        PairMap pairs = ClassAAdjudicationFixture.export(temp,1,ClassACaseManifest.Workflow.NOTE_ADAPTIVE);
        String pair = pairs.pairs().getFirst().id();
        String a = pairs.itemsOf(pair,"A").getFirst(), b = pairs.itemsOf(pair,"B").getFirst();
        var scores = ClassAAdjudicationFixture.load(temp,pairs,Map.of(a,"C",b,"Q"),
                Map.of(a,"C",b,"Q"),Map.of(a,"Q"),Map.of());
        var evidence = PairEvidence.build(pairs,ReviewSetsTest.statuses(pairs,scores,Set.of())).get(pair);
        assertThat(evidence.placementA().scorerMissNoncritical()).isEqualTo(1);
        assertThat(evidence.placementA().qualityFailQuestions()).isEqualTo(1);
        assertThat(evidence.placementB().unreviewedAgreedQualityFail()).isEqualTo(1);
        assertThat(evidence.placementB().qualityFailQuestions()).isEqualTo(1);
    }

    @Test void humanClearDowngradeAndOverturnCountersStayOnTheirPlacements() throws Exception {
        PairMap pairs = ClassAAdjudicationFixture.export(temp,3,ClassACaseManifest.Workflow.NOTE_ADAPTIVE);
        String first = pairs.pairs().getFirst().id(), second = pairs.pairs().get(1).id();
        String cleared = pairs.itemsOf(first,"A").getFirst();
        String downgraded = pairs.itemsOf(first,"B").getFirst();
        String overturned = pairs.itemsOf(second,"A").getFirst();
        String noncriticalMiss = pairs.itemsOf(second,"B").getFirst();
        Map<String,String> p = ReviewSetsTest.clean(pairs),s = ReviewSetsTest.clean(pairs);
        p.put(cleared,"K"); p.put(downgraded,"K");
        p.put(overturned,"Q"); s.put(overturned,"Q");
        var scores = ClassAAdjudicationFixture.load(temp,pairs,p,s,
                Map.of(cleared,"C",downgraded,"Q",overturned,"C",noncriticalMiss,"Q"),Map.of());
        var evidence = PairEvidence.build(pairs,ReviewSetsTest.statuses(pairs,scores,Set.of(first,second)));
        assertThat(evidence.get(first).placementA().humanCleared()).isEqualTo(1);
        assertThat(evidence.get(first).placementA().criticalQuestions()).isZero();
        assertThat(evidence.get(first).placementB().humanDowngraded()).isEqualTo(1);
        assertThat(evidence.get(first).placementB().qualityFailQuestions()).isEqualTo(1);
        assertThat(evidence.get(second).placementA().overturnedAgreedQualityFail()).isEqualTo(1);
        assertThat(evidence.get(second).placementA().agreedQualityReviewed()).isEqualTo(1);
        assertThat(evidence.get(second).placementB().scorerMissNoncritical()).isEqualTo(1);
    }

    @Test void lockRecordValidatorBlocksPendingAndCreatesAfterReview() throws Exception {
        var exported = ArmKeyGateFixtures.classA(temp);
        PairMap pairs = PairMap.fromPackets(temp.resolve("packets"));
        String pair = pairs.pairs().getFirst().id();
        Map<String,QuestionStatus> statuses = new HashMap<>();
        for (String id : pairs.blindItemIds()) statuses.put(id,
                FinalStatus.of(ClassAAdjudicationFixture.direct(id,"C","C",0,null),MAPPING,true));
        Path sealPath = ArmKeyGateFixtures.seal(temp,exported.armKey());
        Path finding = ArmKeyGateFixtures.findings(temp);
        Path lockPath = temp.resolve("lock.json");
        var validator = LockReadiness.asValidator(() -> statuses,() -> Set.of(pair));
        var seal = ArmKeySeal.load(sealPath);
        Set<String> workflows = seal.workflowStems();
        List<Path> findings = List.of(finding);
        assertThatThrownBy(() -> LockRecord.create(lockPath,LockRecord.Kind.PRIMARY,
                workflows,findings,seal,sealPath,validator,ArmKeyGateFixtures.LOCKED))
                .isInstanceOf(LockRecord.InvalidLock.class).hasCauseInstanceOf(InvalidEvidence.class);
        assertThat(Files.exists(lockPath)).isFalse();
        for (String id : pairs.blindItemIds()) statuses.put(id,
                FinalStatus.of(ClassAAdjudicationFixture.direct(id,"C","C",0,"C"),MAPPING,true));
        LockRecord.create(lockPath,LockRecord.Kind.PRIMARY,seal.workflowStems(),List.of(finding),
                seal,sealPath,validator,ArmKeyGateFixtures.LOCKED);
        assertThat(Files.exists(lockPath)).isTrue();
    }

    @Test void newEvidenceRecordsCarryOnlyPlacementFields() {
        assertThat(java.util.Arrays.stream(PairEvidence.class.getRecordComponents()).map(c -> c.getName()).toList())
                .containsExactly("pairId","stem","placementA","placementB","complete");
        assertThat(java.util.Arrays.stream(Expansion.ExpansionResult.class.getRecordComponents())
                .map(c -> c.getName()).toList()).containsExactly("outcome","unresolvedFindingPresent","reason");
    }

    private static PairEvidence.OutputEvidence output(int critical,int unresolved,int pending) {
        return new PairEvidence.OutputEvidence(critical+unresolved+pending,critical,0,unresolved,pending,
                0,0,0,0,0,0,0,0);
    }
}
