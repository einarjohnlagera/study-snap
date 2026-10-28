package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualityGateG5Test {
    private static final String FORMAT = "TRUE_FALSE";
    private static final String DISTRACTOR = "distractorPlausibility";
    private static final String MODE = "modeAlignment";
    @TempDir Path temp;

    private static QualityGateReport.CountSum score(int n,long sum) { return new QualityGateReport.CountSum(n,sum); }
    private static QualityGateReport.FormatCounts format(int items,QualityGateReport.CountSum distractor,QualityGateReport.CountSum mode) {
        return new QualityGateReport.FormatCounts(items,Map.of(DISTRACTOR,distractor,MODE,mode));
    }
    private static QualityGateReport.FormatCounts mode(int items,int n,long sum) {
        return format(items,score(0,0),score(n,sum));
    }
    private static QualityGateReport.G5V2 compare(QualityGateReport.FormatCounts current,QualityGateReport.FormatCounts proposed) {
        return QualityGateReport.g5V2(Map.of(FORMAT,current),current.items(),Map.of(FORMAT,proposed),proposed.items());
    }
    private static QualityGateReport.G5Dimension dimension(QualityGateReport.G5V2 result,String name) {
        return result.formats().getFirst().dimensions().get(name);
    }

    @Test void unchangedShortTrueFalseMeanFailsV1ButPassesV2() {
        var means=Map.<String,Double>of(DISTRACTOR,Double.NaN,MODE,87d/25,"cognitiveDemand",2.48,"clarity",4d,"faithfulness",4d);
        assertThat(QualityGateReport.g5V1(Map.of(FORMAT,Map.of("count",25,"means",means)))).isFalse();
        var result=compare(mode(25,25,87),mode(25,25,87));
        assertThat(result.verdict()).isEqualTo("PASS");
        assertThat(dimension(result,MODE).status()).isEqualTo("PASS");
    }
    @Test void proposedMeanExactlyAtFloorPassesDespiteLargeDecline() {
        assertThat(compare(mode(20,20,90),mode(20,20,70)).verdict()).isEqualTo("PASS");
    }
    @Test void declineExactlyOneFifthPassesUsingIntegerArithmetic() {
        assertThat(compare(mode(20,20,72),mode(20,20,68)).verdict()).isEqualTo("PASS");
    }
    @Test void declineAboveOneFifthBelowFloorFails() {
        assertThat(compare(mode(20,20,73),mode(20,20,68)).verdict()).isEqualTo("FAIL");
    }
    @Test void belowFloorWithSmallDeclinePasses() {
        assertThat(compare(mode(20,20,70),mode(20,20,69)).verdict()).isEqualTo("PASS");
    }
    @Test void nineteenCurrentItemsAreNotEvaluatedAndTriggerOwnerReview() {
        var result=compare(mode(19,19,76),mode(20,20,60));
        assertThat(result.verdict()).isEqualTo("INCONCLUSIVE");
        assertThat(result.formats().getFirst().status()).isEqualTo("NOT_EVALUATED");
        assertThat(result.ownerReviewFlag()).isTrue();
    }
    @Test void twentyItemsOnBothSidesAreEvaluated() {
        assertThat(compare(mode(20,20,80),mode(20,20,60)).verdict()).isEqualTo("FAIL");
    }
    @Test void nineteenProposedItemsAreNotEvaluatedAndTriggerOwnerReview() {
        var result=compare(mode(20,20,80),mode(19,19,57));
        assertThat(result.formats().getFirst().status()).isEqualTo("NOT_EVALUATED");
        assertThat(result.ownerReviewFlag()).isTrue();
    }
    @Test void formatAbsentFromCurrentArmIsNotEvaluated() {
        var result=QualityGateReport.g5V2(Map.of(),0,Map.of(FORMAT,mode(20,20,60)),20);
        assertThat(result.verdict()).isEqualTo("INCONCLUSIVE");
        assertThat(dimension(result,MODE).currentN()).isZero();
        assertThat(dimension(result,MODE).currentMean()).isNull();
        assertThat(result.ownerReviewFlag()).isTrue();
    }
    @Test void nonChoiceFormatUsesModeAloneWhenDistractorIsAbsent() {
        var result=compare(mode(20,20,80),mode(20,20,80));
        assertThat(dimension(result,DISTRACTOR).status()).isEqualTo("NOT_EVALUATED");
        assertThat(dimension(result,MODE).status()).isEqualTo("PASS");
        assertThat(result.formats().getFirst().status()).isEqualTo("PASS");
    }
    @Test void lowCognitiveDemandDoesNotEnterV2() {
        var current=new QualityGateReport.FormatCounts(20,Map.of(MODE,score(20,80),DISTRACTOR,score(20,80),
                "cognitiveDemand",score(20,100)));
        var proposed=new QualityGateReport.FormatCounts(20,Map.of(MODE,score(20,80),DISTRACTOR,score(20,80),
                "cognitiveDemand",score(20,20)));
        var result=compare(current,proposed);
        assertThat(result.verdict()).isEqualTo("PASS");
        assertThat(result.formats().getFirst().dimensions()).doesNotContainKey("cognitiveDemand");
    }
    @Test void failingDistractorDimensionFailsFormatWhenModePasses() {
        var result=compare(format(20,score(20,73),score(20,80)),format(20,score(20,68),score(20,80)));
        assertThat(dimension(result,DISTRACTOR).status()).isEqualTo("FAIL");
        assertThat(dimension(result,MODE).status()).isEqualTo("PASS");
        assertThat(result.verdict()).isEqualTo("FAIL");
    }
    @Test void noEvaluatedFormatsAreInconclusiveEvenWhenBothArmsHaveItems() {
        var result=compare(mode(19,19,76),mode(19,19,76));
        assertThat(result.verdict()).isEqualTo("INCONCLUSIVE");
        assertThat(result.ownerReviewFlag()).isFalse();
    }
    @Test void oneFailedFormatFailsModelDespiteAnotherPassing() {
        var current=Map.of("A",mode(20,20,80),"B",mode(20,20,73));
        var proposed=Map.of("A",mode(20,20,80),"B",mode(20,20,68));
        var result=QualityGateReport.g5V2(current,40,proposed,40);
        assertThat(result.verdict()).isEqualTo("FAIL");
        assertThat(result.formats().stream().map(QualityGateReport.G5Format::format)).containsExactly("A","B");
    }
    @Test void unevaluatedFormatDoesNotFailPassingModelOrMasqueradeAsPass() {
        var result=QualityGateReport.g5V2(Map.of("A",mode(20,20,80),"B",mode(19,19,76)),39,
                Map.of("A",mode(20,20,80),"B",mode(19,19,76)),39);
        assertThat(result.verdict()).isEqualTo("PASS");
        assertThat(result.formats().get(1).status()).isEqualTo("NOT_EVALUATED");
    }
    @Test void proposedOnlyTwentyItemUnevaluatedFormatFlagsOwnerReview() {
        var result=QualityGateReport.g5V2(Map.of(FORMAT,mode(19,19,76)),19,Map.of(FORMAT,mode(20,20,80)),20);
        assertThat(result.ownerReviewFlag()).isTrue();
    }
    @Test void currentOnlyTwentyItemUnevaluatedFormatFlagsOwnerReview() {
        var result=QualityGateReport.g5V2(Map.of(FORMAT,mode(20,20,80)),20,Map.of(FORMAT,mode(19,19,76)),19);
        assertThat(result.ownerReviewFlag()).isTrue();
    }
    @Test void unevaluatedFormatBelowTwentyOnBothSidesDoesNotFlagOwnerReview() {
        assertThat(compare(mode(19,19,76),mode(19,19,76)).ownerReviewFlag()).isFalse();
    }
    @Test void parseAcceptsTrimmedCaseInsensitiveVersions() {
        assertThat(RubricVersion.parse(" v1 ")).isEqualTo(RubricVersion.V1);
        assertThat(RubricVersion.parse("V2")).isEqualTo(RubricVersion.V2);
    }
    @Test void parseRejectsMissingBlankAndUnknownVersions() {
        for (String value:new String[]{null," ","v3"})
            assertThatThrownBy(() -> RubricVersion.parse(value)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("llm.benchmark.rubricVersion").hasMessageContaining("v1").hasMessageContaining("v2");
    }
    @Test void entryRejectsMissingAndInvalidVersionBeforeWritingAnyOutput() {
        String previousGate=System.getProperty("llm.benchmark.qualityGates");
        String previousVersion=System.getProperty("llm.benchmark.rubricVersion");
        try {
            System.setProperty("llm.benchmark.qualityGates","true");
            Path out=temp.resolve("out");
            System.setProperty("llm.benchmark.gatesOutDir",out.toString());
            System.clearProperty("llm.benchmark.rubricVersion");
            assertThatThrownBy(() -> new QualityGateReport().reportWhenRequested())
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("llm.benchmark.rubricVersion");
            System.setProperty("llm.benchmark.rubricVersion","v3");
            assertThatThrownBy(() -> new QualityGateReport().reportWhenRequested())
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("v1 or v2");
            System.setProperty("llm.benchmark.rubricVersion"," ");
            assertThatThrownBy(() -> new QualityGateReport().reportWhenRequested())
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("v1 or v2");
            assertThat(Files.exists(out)).isFalse();
        } finally {
            restore("llm.benchmark.qualityGates",previousGate);
            restore("llm.benchmark.rubricVersion",previousVersion);
            System.clearProperty("llm.benchmark.gatesOutDir");
        }
    }
    @Test void reportRejectsNullVersionBeforeReadingOrWriting() {
        Path out=temp.resolve("null-version-out");
        assertThatThrownBy(() -> QualityGateReport.report(null,null,null,null,null,null,null,out,null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("llm.benchmark.rubricVersion");
        assertThat(Files.exists(out)).isFalse();
    }
    private static void restore(String key,String value) {
        if (value==null) System.clearProperty(key); else System.setProperty(key,value);
    }
}
