package com.studysnap.backend.benchmark;

/** Single pre-registration surface for every numerical quality decision. */
public final class QualityGateParameters {
    private QualityGateParameters() {}
    // SIGNED OFF 2026-10-27
    static final double G4_ONE_SIDED_CONFIDENCE = .90;
    // SIGNED OFF 2026-10-27
    static final double G4_UPPER_LIMIT = .10;
    // SIGNED OFF 2026-10-27
    static final int UNFLAGGED_REVIEW_FLOOR = 60;
    // SIGNED OFF 2026-10-27
    static final int G4_MIN_CHOICE_ITEMS = 150;
    // SIGNED OFF 2026-10-27
    static final int G4_RECURRING_ITEMS = 3;
    // SIGNED OFF 2026-10-27
    static final int G4_RECURRING_NOTES = 2;
    // SIGNED OFF 2026-10-27
    static final double G7_NOISE_POINTS = .10;
    // PROPOSED, set at rubric sign-off
    static final double REVIEW_SAMPLE_FRACTION = .20;
    // PROPOSED, set at rubric sign-off
    static final int REVIEW_BATCH_SIZE = 100;
    // PROPOSED, set at rubric sign-off
    static final double G4_TARGET = .05;
    // PROPOSED, set at rubric sign-off
    static final double G4_TRANSPARENCY_CONFIDENCE = .95;
    // PROPOSED, set at rubric sign-off
    static final double G2_MIN_MEAN = 4.0;
    // PROPOSED, set at rubric sign-off
    static final double G2_MIN_GAIN = .3;
    // PROPOSED, set at rubric sign-off
    static final double G3_MIN_MEAN = 4.0;
    // PROPOSED, set at rubric sign-off
    static final double G5_FORMAT_MIN_MEAN = 3.5;
    // PROPOSED, set at rubric sign-off
    static final int G5_FORMAT_MIN_ITEMS = 20;
    // PROPOSED, set at rubric sign-off
    static final double G6_SCORE_TOLERANCE = .2;
    // SIGNED OFF 2026-10-27
    static final double G6_COUNT_RATE_POINTS = .02;
    // SIGNED OFF 2026-10-27
    static final double G6_RESOURCE_RELATIVE_TOLERANCE = .15;
    // PROPOSED, set at rubric sign-off
    static final int G8_MIN_PAIRED_NOTES = 15;
    // PROPOSED, set at rubric sign-off
    static final double NEAR_DUPLICATE_START_LIMIT = .10;
    // PROPOSED, set at rubric sign-off
    static final int EXPECTED_NOTES = 20;
    // PROPOSED, set at rubric sign-off
    static final int REQUESTED_PER_START = 15;
    // PROPOSED, set at rubric sign-off
    static final int BOOTSTRAP_REPLICATES = 10_000;
    static final long BOOTSTRAP_SEED = 176L;
    // PROPOSED, set at rubric sign-off
    static final double BOOTSTRAP_CONFIDENCE = .95;
}
