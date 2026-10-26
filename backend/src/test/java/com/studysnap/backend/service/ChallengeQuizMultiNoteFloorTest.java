package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengeQuizMultiNoteFloorTest {
    private final StudySnapProperties.ChallengeQuiz policy = new StudySnapProperties().getChallengeQuiz();

    @Test
    void configuredDefaultsMatchTheApprovedTwoThirdsPolicyAndFiveMinuteDeadline() {
        assertThat(policy.getChallengeMultiNoteMinimumQuestionNumerator()).isEqualTo(2);
        assertThat(policy.getChallengeMultiNoteMinimumQuestionDenominator()).isEqualTo(3);
        assertThat(policy.getChallengeMultiNoteMinimumCoverageNumerator()).isEqualTo(2);
        assertThat(policy.getChallengeMultiNoteMinimumCoverageDenominator()).isEqualTo(3);
        assertThat(policy.getChallengeMultiNoteWaitDeadlineSeconds()).isEqualTo(300);
    }

    @ParameterizedTest
    @CsvSource({"18, 12", "6, 4", "2, 2", "9, 6", "10, 7", "3, 2"})
    void approvedTwoThirdsMinimumUsesExactIntegerCeiling(int count, int minimum) {
        assertThat(ChallengeQuizService.minimumByRatio(count, 2, 3)).isEqualTo(minimum);
    }

    @Test
    void eighteenRequestedAcrossSixSourcesNeedsTwelveQuestionsAndFourSources() {
        assertThat(ChallengeQuizService.multiNoteFloorMet(18, 12, 4, 6, policy)).isTrue();
        assertThat(ChallengeQuizService.multiNoteFloorMet(18, 11, 4, 6, policy)).isFalse();
        assertThat(ChallengeQuizService.multiNoteFloorMet(18, 12, 3, 6, policy)).isFalse();
        assertThat(ChallengeQuizService.multiNoteFloorMet(18, 19, 6, 6, policy)).isFalse();
    }

    @Test
    void twoSourcesRequireBothEvenWhenOneSuppliesEnoughQuestions() {
        assertThat(ChallengeQuizService.multiNoteFloorMet(18, 12, 1, 2, policy)).isFalse();
    }

    @Test
    void unevenFourThreeThreeAllocationCountsOneQuestionFromShortSourceAsCoverage() {
        var allocations = java.util.List.of(4, 3, 3);
        var delivered = java.util.List.of(4, 2, 1);
        assertThat(allocations.stream().mapToInt(Integer::intValue).sum()).isEqualTo(10);
        assertThat(ChallengeQuizService.multiNoteFloorMet(10,
                delivered.stream().mapToInt(Integer::intValue).sum(),
                (int) delivered.stream().filter(count -> count > 0).count(), allocations.size(), policy)).isTrue();
        assertThat(ChallengeQuizService.multiNoteFloorMet(10, 7, 1, 3, policy)).isFalse();
    }
}
