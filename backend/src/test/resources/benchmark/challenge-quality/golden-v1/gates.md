# Challenge quality gates

Verdict: **PASS**

Wording-sensitive regression is a mechanical excess-over-same-model-current-arm screen, not proof of individual causal attribution.


## Luna

BOARD_EXAM_REVIEW: Flat or negative BOARD_EXAM_REVIEW change: distractor=1.0, mode=0.0

### Missingness

| Note | Level | Stratum | Current | Proposed |
| --- | --- | --- | --- | --- |

| Note level | Paired | Unpaired |
| --- | ---: | ---: |


| Stratum | Paired | Unpaired |
| --- | ---: | ---: |

Failed-start difference (proposed minus current): 


G7 discarded attempts (current/proposed): 0 / 0. Verified starts: null / null. retried == rejected: true

G1 overall no-regression: true

Confirmed critical defects (each listed regardless of type):

- currentCritical: A2-0 · note-0 · AMBIGUOUS_STEM
- proposedCritical: B2-0 · note-0 · AMBIGUOUS_STEM

```json
{
  "missingness" : null,
  "current" : {
    "items" : 200,
    "means" : {
      "distractorPlausibility" : 4.0,
      "modeAlignment" : 4.0,
      "cognitiveDemand" : 4.0,
      "clarity" : 4.0,
      "faithfulness" : 4.0
    },
    "formatMeans" : {
      "MCQ" : {
        "count" : 160,
        "means" : {
          "distractorPlausibility" : 4.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        }
      },
      "MULTI_SELECT" : {
        "count" : 40,
        "means" : {
          "distractorPlausibility" : 4.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        }
      }
    },
    "criticalDefects" : [ {
      "noteRef" : "note-0",
      "type" : "AMBIGUOUS_STEM",
      "id" : "A2-0",
      "arm" : "A2"
    } ],
    "confirmedCriticalCount" : 1,
    "G4" : {
      "N" : 200,
      "C_F" : 0,
      "U" : 199,
      "sum_n" : 60,
      "sum_k" : 0,
      "confirmedMinimumRate" : 0.0,
      "G_hat" : 0.0,
      "p_up" : 0.03764937360191141,
      "UL" : 0.03746112673390185,
      "ci95Lower" : 0.0,
      "ci95Upper" : 0.059331344824736065,
      "recurring" : false,
      "verdict" : "PASS",
      "strata" : {
        "MCQ" : {
          "k_h" : 0,
          "U_h" : 160,
          "n_h" : 41
        },
        "MULTI_SELECT" : {
          "k_h" : 0,
          "U_h" : 39,
          "n_h" : 19
        }
      }
    },
    "recurringGiveawayPatterns" : [ ],
    "missedDefects" : 0,
    "independentlyFoundCritical" : 0,
    "reviewedUnflagged" : 60,
    "missedDefectRate" : 0.0,
    "unresolved" : 0,
    "descriptorsG6" : {
      "telemetryMissingStarts" : 0,
      "deliveredTotal" : 200,
      "starts" : 20,
      "validatorRejected" : 0,
      "validatorRetried" : 0,
      "tokensPerDeliveredQuestion" : 10,
      "validatorAccepted" : 200,
      "p50LatencyMs" : 100
    },
    "descriptorsG7" : {
      "validatorRequested" : 200,
      "telemetryMissingStarts" : 0,
      "validatorRejected" : 0,
      "multiSelectThreeCorrect" : 20,
      "starts" : 20,
      "multiSelectTwoCorrect" : 20,
      "validatorRetried" : 0,
      "validatorAccepted" : 200
    },
    "nearDuplicateStartRate" : 0.0
  },
  "proposed" : {
    "items" : 200,
    "means" : {
      "distractorPlausibility" : 5.0,
      "modeAlignment" : 4.0,
      "cognitiveDemand" : 4.0,
      "clarity" : 4.0,
      "faithfulness" : 4.0
    },
    "formatMeans" : {
      "MCQ" : {
        "count" : 160,
        "means" : {
          "distractorPlausibility" : 5.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        }
      },
      "MULTI_SELECT" : {
        "count" : 40,
        "means" : {
          "distractorPlausibility" : 5.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        }
      }
    },
    "criticalDefects" : [ {
      "noteRef" : "note-0",
      "type" : "AMBIGUOUS_STEM",
      "id" : "B2-0",
      "arm" : "B2"
    } ],
    "confirmedCriticalCount" : 1,
    "G4" : {
      "N" : 200,
      "C_F" : 0,
      "U" : 200,
      "sum_n" : 200,
      "sum_k" : 0,
      "confirmedMinimumRate" : 0.0,
      "G_hat" : 0.0,
      "p_up" : 0.01144690534306116,
      "UL" : 0.01144690534306116,
      "ci95Lower" : 0.0,
      "ci95Upper" : 0.01827534035513624,
      "recurring" : false,
      "verdict" : "PASS",
      "strata" : {
        "MCQ" : {
          "k_h" : 0,
          "U_h" : 160,
          "n_h" : 160
        },
        "MULTI_SELECT" : {
          "k_h" : 0,
          "U_h" : 40,
          "n_h" : 40
        }
      }
    },
    "recurringGiveawayPatterns" : [ ],
    "missedDefects" : 1,
    "independentlyFoundCritical" : 1,
    "reviewedUnflagged" : 200,
    "missedDefectRate" : 0.005,
    "unresolved" : 0,
    "descriptorsG6" : {
      "telemetryMissingStarts" : 0,
      "deliveredTotal" : 200,
      "starts" : 20,
      "validatorRejected" : 0,
      "validatorRetried" : 0,
      "tokensPerDeliveredQuestion" : 10,
      "validatorAccepted" : 200,
      "p50LatencyMs" : 100
    },
    "descriptorsG7" : {
      "validatorRequested" : 200,
      "telemetryMissingStarts" : 0,
      "validatorRejected" : 0,
      "multiSelectThreeCorrect" : 20,
      "starts" : 20,
      "multiSelectTwoCorrect" : 20,
      "validatorRetried" : 0,
      "validatorAccepted" : 200
    },
    "nearDuplicateStartRate" : 0.0
  },
  "G1" : {
    "proposedCritical" : [ {
      "noteRef" : "note-0",
      "type" : "AMBIGUOUS_STEM",
      "id" : "B2-0",
      "arm" : "B2"
    } ],
    "verdict" : "PASS",
    "overallNoRegression" : true,
    "wordingSensitiveRegressionTypeExcess" : {
      "AMBIGUOUS_STEM" : 0,
      "COMPLEXITY_UNSUPPORTED_BY_INPUTS" : 0,
      "PARTLY_CORRECT_DISTRACTOR" : 0,
      "SECOND_DEFENSIBLE_ANSWER" : 0
    },
    "currentCritical" : [ {
      "noteRef" : "note-0",
      "type" : "AMBIGUOUS_STEM",
      "id" : "A2-0",
      "arm" : "A2"
    } ]
  },
  "G2" : "PASS",
  "G3" : "PASS",
  "G4" : {
    "N" : 200,
    "C_F" : 0,
    "U" : 200,
    "sum_n" : 200,
    "sum_k" : 0,
    "confirmedMinimumRate" : 0.0,
    "G_hat" : 0.0,
    "p_up" : 0.01144690534306116,
    "UL" : 0.01144690534306116,
    "ci95Lower" : 0.0,
    "ci95Upper" : 0.01827534035513624,
    "recurring" : false,
    "verdict" : "PASS",
    "strata" : {
      "MCQ" : {
        "k_h" : 0,
        "U_h" : 160,
        "n_h" : 160
      },
      "MULTI_SELECT" : {
        "k_h" : 0,
        "U_h" : 40,
        "n_h" : 40
      }
    }
  },
  "G5" : "PASS",
  "G6" : {
    "clarity" : 0.0,
    "faithfulness" : 0.0,
    "deliveredRate" : 0.0,
    "p50LatencyMs" : 0.0,
    "tokensPerDeliveredQuestion" : 0.0,
    "verdict" : "PASS"
  },
  "G7" : {
    "telemetryScope" : "ALL_FORMAT; generation-count log has no per-format rejection or retry breakdown",
    "currentDiscardedAttempts" : 0,
    "proposedDiscardedAttempts" : 0,
    "currentVerifiedStarts" : null,
    "proposedVerifiedStarts" : null,
    "currentRetryMismatchStarts" : null,
    "proposedRetryMismatchStarts" : null,
    "retriedEqualsRejected" : true,
    "currentSuccessfulStarts" : 20,
    "proposedSuccessfulStarts" : 20,
    "currentTelemetryMissingStarts" : 0,
    "proposedTelemetryMissingStarts" : 0,
    "currentMultiSelectThreeCorrectShare" : 0.5,
    "proposedMultiSelectThreeCorrectShare" : 0.5,
    "currentAllFormatValidatorRejectionRate" : 0.0,
    "proposedAllFormatValidatorRejectionRate" : 0.0,
    "currentAllFormatValidatorRetryRate" : 0.0,
    "proposedAllFormatValidatorRetryRate" : 0.0,
    "telemetryVerdict" : "OBSERVED",
    "multiSelectThreeCorrectShareVerdict" : "PASS",
    "allFormatValidatorRejectionVerdict" : "PASS",
    "allFormatValidatorRetryVerdict" : "PASS",
    "absolutePercentagePointDifferences" : {
      "allFormatValidatorRetryRate" : 0.0,
      "allFormatValidatorRejectionRate" : 0.0,
      "multiSelectThreeCorrectShare" : 0.0
    },
    "verdict" : "PASS"
  },
  "G8" : {
    "missingOrFailedSides" : [ ],
    "nearDuplicateStartRate" : 0.0,
    "pairedNotes" : 20,
    "verdict" : "PASS"
  },
  "escalation" : {
    "currentMissedDefectRate" : 0.0,
    "proposedMissedDefectRate" : 0.005,
    "escalationRequired" : true,
    "completeArmReReview" : true
  },
  "boardExamReviewFinding" : "Flat or negative BOARD_EXAM_REVIEW change: distractor=1.0, mode=0.0",
  "perNoteLevel" : {
    "COLLEGE" : {
      "interpretation" : "DESCRIPTIVE_ONLY",
      "current" : {
        "items" : 100,
        "means" : {
          "distractorPlausibility" : 4.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        },
        "formatMeans" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        },
        "criticalDefects" : [ {
          "noteRef" : "note-0",
          "type" : "AMBIGUOUS_STEM",
          "id" : "A2-0",
          "arm" : "A2"
        } ],
        "confirmedCriticalCount" : 1,
        "G4" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 99,
          "sum_n" : 30,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.07388127187120652,
          "UL" : 0.07314245915249445,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.11454627513980747,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 21
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 19,
              "n_h" : 9
            }
          }
        },
        "recurringGiveawayPatterns" : [ ],
        "missedDefects" : 0,
        "independentlyFoundCritical" : 0,
        "reviewedUnflagged" : 30,
        "missedDefectRate" : 0.0,
        "unresolved" : 0,
        "descriptorsG6" : {
          "telemetryMissingStarts" : 0,
          "deliveredTotal" : 100,
          "starts" : 10,
          "validatorRejected" : 0,
          "validatorRetried" : 0,
          "tokensPerDeliveredQuestion" : 10,
          "validatorAccepted" : 100,
          "p50LatencyMs" : 100
        },
        "descriptorsG7" : {
          "validatorRequested" : 100,
          "telemetryMissingStarts" : 0,
          "validatorRejected" : 0,
          "multiSelectThreeCorrect" : 10,
          "starts" : 10,
          "multiSelectTwoCorrect" : 10,
          "validatorRetried" : 0,
          "validatorAccepted" : 100
        },
        "nearDuplicateStartRate" : 0.0
      },
      "proposed" : {
        "items" : 100,
        "means" : {
          "distractorPlausibility" : 5.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        },
        "formatMeans" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        },
        "criticalDefects" : [ {
          "noteRef" : "note-0",
          "type" : "AMBIGUOUS_STEM",
          "id" : "B2-0",
          "arm" : "B2"
        } ],
        "confirmedCriticalCount" : 1,
        "G4" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 100,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.022762779044189316,
          "UL" : 0.022762779044189316,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.03621669264517641,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 80
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 20
            }
          }
        },
        "recurringGiveawayPatterns" : [ ],
        "missedDefects" : 1,
        "independentlyFoundCritical" : 1,
        "reviewedUnflagged" : 100,
        "missedDefectRate" : 0.01,
        "unresolved" : 0,
        "descriptorsG6" : {
          "telemetryMissingStarts" : 0,
          "deliveredTotal" : 100,
          "starts" : 10,
          "validatorRejected" : 0,
          "validatorRetried" : 0,
          "tokensPerDeliveredQuestion" : 10,
          "validatorAccepted" : 100,
          "p50LatencyMs" : 100
        },
        "descriptorsG7" : {
          "validatorRequested" : 100,
          "telemetryMissingStarts" : 0,
          "validatorRejected" : 0,
          "multiSelectThreeCorrect" : 10,
          "starts" : 10,
          "multiSelectTwoCorrect" : 10,
          "validatorRetried" : 0,
          "validatorAccepted" : 100
        },
        "nearDuplicateStartRate" : 0.0
      },
      "G1" : {
        "wordingSensitiveRegressionTypeExcess" : {
          "AMBIGUOUS_STEM" : 0,
          "COMPLEXITY_UNSUPPORTED_BY_INPUTS" : 0,
          "PARTLY_CORRECT_DISTRACTOR" : 0,
          "SECOND_DEFENSIBLE_ANSWER" : 0
        },
        "overallNoRegression" : true,
        "currentCritical" : [ {
          "noteRef" : "note-0",
          "type" : "AMBIGUOUS_STEM",
          "id" : "A2-0",
          "arm" : "A2"
        } ],
        "proposedCritical" : [ {
          "noteRef" : "note-0",
          "type" : "AMBIGUOUS_STEM",
          "id" : "B2-0",
          "arm" : "B2"
        } ]
      },
      "G2" : {
        "currentMean" : 4.0,
        "proposedMean" : 5.0,
        "difference" : 1.0
      },
      "G3" : {
        "proposedMean" : 4.0,
        "currentMean" : 4.0
      },
      "G4" : {
        "current" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 99,
          "sum_n" : 30,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.07388127187120652,
          "UL" : 0.07314245915249445,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.11454627513980747,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 21
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 19,
              "n_h" : 9
            }
          }
        },
        "proposed" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 100,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.022762779044189316,
          "UL" : 0.022762779044189316,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.03621669264517641,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 80
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 20
            }
          }
        }
      },
      "G5" : {
        "current" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        },
        "proposed" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        }
      },
      "G6" : {
        "clarity" : 0.0,
        "faithfulness" : 0.0,
        "deliveredRate" : 0.0,
        "p50LatencyMs" : 0.0,
        "tokensPerDeliveredQuestion" : 0.0,
        "verdict" : "PASS"
      },
      "G7" : {
        "telemetryScope" : "ALL_FORMAT; generation-count log has no per-format rejection or retry breakdown",
        "currentDiscardedAttempts" : 0,
        "proposedDiscardedAttempts" : 0,
        "currentVerifiedStarts" : null,
        "proposedVerifiedStarts" : null,
        "currentRetryMismatchStarts" : null,
        "proposedRetryMismatchStarts" : null,
        "retriedEqualsRejected" : true,
        "currentSuccessfulStarts" : 10,
        "proposedSuccessfulStarts" : 10,
        "currentTelemetryMissingStarts" : 0,
        "proposedTelemetryMissingStarts" : 0,
        "currentMultiSelectThreeCorrectShare" : 0.5,
        "proposedMultiSelectThreeCorrectShare" : 0.5,
        "currentAllFormatValidatorRejectionRate" : 0.0,
        "proposedAllFormatValidatorRejectionRate" : 0.0,
        "currentAllFormatValidatorRetryRate" : 0.0,
        "proposedAllFormatValidatorRetryRate" : 0.0,
        "telemetryVerdict" : "OBSERVED",
        "multiSelectThreeCorrectShareVerdict" : "PASS",
        "allFormatValidatorRejectionVerdict" : "PASS",
        "allFormatValidatorRetryVerdict" : "PASS",
        "absolutePercentagePointDifferences" : {
          "allFormatValidatorRetryRate" : 0.0,
          "allFormatValidatorRejectionRate" : 0.0,
          "multiSelectThreeCorrectShare" : 0.0
        },
        "verdict" : "PASS"
      },
      "G8" : {
        "nearDuplicateStartRate" : 0.0,
        "pairedNotes" : 10
      },
      "pairedBootstrap" : {
        "distractorPlausibility" : {
          "bootstrap95Lower" : 1.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 1.0,
          "meanDifference" : 1.0
        },
        "modeAlignment" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "cognitiveDemand" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "clarity" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "faithfulness" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "deliveredRate" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "allFormatValidatorRetryRate" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "latencyMs" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "tokensPerDeliveredQuestion" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "multiSelectThreeShare" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "allFormatValidatorRejectionRate" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "nearDuplicateStart" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        }
      }
    },
    "BOARD_EXAM_REVIEW" : {
      "interpretation" : "DESCRIPTIVE_ONLY",
      "current" : {
        "items" : 100,
        "means" : {
          "distractorPlausibility" : 4.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        },
        "formatMeans" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        },
        "criticalDefects" : [ ],
        "confirmedCriticalCount" : 0,
        "G4" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 30,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.07388127187120652,
          "UL" : 0.07388127187120652,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.11570330822202775,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 20
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 10
            }
          }
        },
        "recurringGiveawayPatterns" : [ ],
        "missedDefects" : 0,
        "independentlyFoundCritical" : 0,
        "reviewedUnflagged" : 30,
        "missedDefectRate" : 0.0,
        "unresolved" : 0,
        "descriptorsG6" : {
          "telemetryMissingStarts" : 0,
          "deliveredTotal" : 100,
          "starts" : 10,
          "validatorRejected" : 0,
          "validatorRetried" : 0,
          "tokensPerDeliveredQuestion" : 10,
          "validatorAccepted" : 100,
          "p50LatencyMs" : 100
        },
        "descriptorsG7" : {
          "validatorRequested" : 100,
          "telemetryMissingStarts" : 0,
          "validatorRejected" : 0,
          "multiSelectThreeCorrect" : 10,
          "starts" : 10,
          "multiSelectTwoCorrect" : 10,
          "validatorRetried" : 0,
          "validatorAccepted" : 100
        },
        "nearDuplicateStartRate" : 0.0
      },
      "proposed" : {
        "items" : 100,
        "means" : {
          "distractorPlausibility" : 5.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        },
        "formatMeans" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        },
        "criticalDefects" : [ ],
        "confirmedCriticalCount" : 0,
        "G4" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 100,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.022762779044189316,
          "UL" : 0.022762779044189316,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.03621669264517641,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 80
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 20
            }
          }
        },
        "recurringGiveawayPatterns" : [ ],
        "missedDefects" : 0,
        "independentlyFoundCritical" : 0,
        "reviewedUnflagged" : 100,
        "missedDefectRate" : 0.0,
        "unresolved" : 0,
        "descriptorsG6" : {
          "telemetryMissingStarts" : 0,
          "deliveredTotal" : 100,
          "starts" : 10,
          "validatorRejected" : 0,
          "validatorRetried" : 0,
          "tokensPerDeliveredQuestion" : 10,
          "validatorAccepted" : 100,
          "p50LatencyMs" : 100
        },
        "descriptorsG7" : {
          "validatorRequested" : 100,
          "telemetryMissingStarts" : 0,
          "validatorRejected" : 0,
          "multiSelectThreeCorrect" : 10,
          "starts" : 10,
          "multiSelectTwoCorrect" : 10,
          "validatorRetried" : 0,
          "validatorAccepted" : 100
        },
        "nearDuplicateStartRate" : 0.0
      },
      "G1" : {
        "wordingSensitiveRegressionTypeExcess" : {
          "AMBIGUOUS_STEM" : 0,
          "COMPLEXITY_UNSUPPORTED_BY_INPUTS" : 0,
          "PARTLY_CORRECT_DISTRACTOR" : 0,
          "SECOND_DEFENSIBLE_ANSWER" : 0
        },
        "overallNoRegression" : true,
        "currentCritical" : [ ],
        "proposedCritical" : [ ]
      },
      "G2" : {
        "currentMean" : 4.0,
        "proposedMean" : 5.0,
        "difference" : 1.0
      },
      "G3" : {
        "proposedMean" : 4.0,
        "currentMean" : 4.0
      },
      "G4" : {
        "current" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 30,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.07388127187120652,
          "UL" : 0.07388127187120652,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.11570330822202775,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 20
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 10
            }
          }
        },
        "proposed" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 100,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.022762779044189316,
          "UL" : 0.022762779044189316,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.03621669264517641,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 80
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 20
            }
          }
        }
      },
      "G5" : {
        "current" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        },
        "proposed" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        }
      },
      "G6" : {
        "clarity" : 0.0,
        "faithfulness" : 0.0,
        "deliveredRate" : 0.0,
        "p50LatencyMs" : 0.0,
        "tokensPerDeliveredQuestion" : 0.0,
        "verdict" : "PASS"
      },
      "G7" : {
        "telemetryScope" : "ALL_FORMAT; generation-count log has no per-format rejection or retry breakdown",
        "currentDiscardedAttempts" : 0,
        "proposedDiscardedAttempts" : 0,
        "currentVerifiedStarts" : null,
        "proposedVerifiedStarts" : null,
        "currentRetryMismatchStarts" : null,
        "proposedRetryMismatchStarts" : null,
        "retriedEqualsRejected" : true,
        "currentSuccessfulStarts" : 10,
        "proposedSuccessfulStarts" : 10,
        "currentTelemetryMissingStarts" : 0,
        "proposedTelemetryMissingStarts" : 0,
        "currentMultiSelectThreeCorrectShare" : 0.5,
        "proposedMultiSelectThreeCorrectShare" : 0.5,
        "currentAllFormatValidatorRejectionRate" : 0.0,
        "proposedAllFormatValidatorRejectionRate" : 0.0,
        "currentAllFormatValidatorRetryRate" : 0.0,
        "proposedAllFormatValidatorRetryRate" : 0.0,
        "telemetryVerdict" : "OBSERVED",
        "multiSelectThreeCorrectShareVerdict" : "PASS",
        "allFormatValidatorRejectionVerdict" : "PASS",
        "allFormatValidatorRetryVerdict" : "PASS",
        "absolutePercentagePointDifferences" : {
          "allFormatValidatorRetryRate" : 0.0,
          "allFormatValidatorRejectionRate" : 0.0,
          "multiSelectThreeCorrectShare" : 0.0
        },
        "verdict" : "PASS"
      },
      "G8" : {
        "nearDuplicateStartRate" : 0.0,
        "pairedNotes" : 10
      },
      "pairedBootstrap" : {
        "distractorPlausibility" : {
          "bootstrap95Lower" : 1.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 1.0,
          "meanDifference" : 1.0
        },
        "modeAlignment" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "cognitiveDemand" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "clarity" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "faithfulness" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "deliveredRate" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "allFormatValidatorRetryRate" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "latencyMs" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "tokensPerDeliveredQuestion" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "multiSelectThreeShare" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "allFormatValidatorRejectionRate" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "nearDuplicateStart" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        }
      }
    }
  },
  "pairedBootstrap" : {
    "distractorPlausibility" : {
      "bootstrap95Lower" : 1.0,
      "pairedNotes" : 20,
      "bootstrap95Upper" : 1.0,
      "meanDifference" : 1.0
    },
    "modeAlignment" : {
      "bootstrap95Lower" : 0.0,
      "pairedNotes" : 20,
      "bootstrap95Upper" : 0.0,
      "meanDifference" : 0.0
    },
    "cognitiveDemand" : {
      "bootstrap95Lower" : 0.0,
      "pairedNotes" : 20,
      "bootstrap95Upper" : 0.0,
      "meanDifference" : 0.0
    },
    "clarity" : {
      "bootstrap95Lower" : 0.0,
      "pairedNotes" : 20,
      "bootstrap95Upper" : 0.0,
      "meanDifference" : 0.0
    },
    "faithfulness" : {
      "bootstrap95Lower" : 0.0,
      "pairedNotes" : 20,
      "bootstrap95Upper" : 0.0,
      "meanDifference" : 0.0
    },
    "deliveredRate" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    },
    "allFormatValidatorRetryRate" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    },
    "latencyMs" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    },
    "tokensPerDeliveredQuestion" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    },
    "multiSelectThreeShare" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    },
    "allFormatValidatorRejectionRate" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    },
    "nearDuplicateStart" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    }
  },
  "representativePairedNotes" : {
    "selectionRule" : "Largest positive and largest negative paired per-note distractor-mean difference; packId lexical tie-break",
    "improvement" : {
      "difference" : 1.0,
      "currentIds" : [ "A2-0", "A2-1", "A2-2", "A2-3", "A2-4", "A2-5", "A2-6", "A2-7", "A2-8", "A2-9" ],
      "packId" : "pack-0",
      "proposedIds" : [ "B2-0", "B2-1", "B2-2", "B2-3", "B2-4", "B2-5", "B2-6", "B2-7", "B2-8", "B2-9" ],
      "noteRef" : "note-0"
    },
    "failure" : "NONE"
  },
  "reasons" : [ ],
  "verdict" : "PASS"
}
```

## Sol

BOARD_EXAM_REVIEW: Flat or negative BOARD_EXAM_REVIEW change: distractor=1.0, mode=0.0

### Missingness

| Note | Level | Stratum | Current | Proposed |
| --- | --- | --- | --- | --- |

| Note level | Paired | Unpaired |
| --- | ---: | ---: |


| Stratum | Paired | Unpaired |
| --- | ---: | ---: |

Failed-start difference (proposed minus current): 


G7 discarded attempts (current/proposed): 0 / 0. Verified starts: null / null. retried == rejected: true

G1 overall no-regression: true

Confirmed critical defects (each listed regardless of type):


```json
{
  "missingness" : null,
  "current" : {
    "items" : 200,
    "means" : {
      "distractorPlausibility" : 4.0,
      "modeAlignment" : 4.0,
      "cognitiveDemand" : 4.0,
      "clarity" : 4.0,
      "faithfulness" : 4.0
    },
    "formatMeans" : {
      "MCQ" : {
        "count" : 160,
        "means" : {
          "distractorPlausibility" : 4.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        }
      },
      "MULTI_SELECT" : {
        "count" : 40,
        "means" : {
          "distractorPlausibility" : 4.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        }
      }
    },
    "criticalDefects" : [ ],
    "confirmedCriticalCount" : 0,
    "G4" : {
      "N" : 200,
      "C_F" : 0,
      "U" : 200,
      "sum_n" : 60,
      "sum_k" : 0,
      "confirmedMinimumRate" : 0.0,
      "G_hat" : 0.0,
      "p_up" : 0.03764937360191141,
      "UL" : 0.03764937360191141,
      "ci95Lower" : 0.0,
      "ci95Upper" : 0.0596294922861669,
      "recurring" : false,
      "verdict" : "PASS",
      "strata" : {
        "MCQ" : {
          "k_h" : 0,
          "U_h" : 160,
          "n_h" : 40
        },
        "MULTI_SELECT" : {
          "k_h" : 0,
          "U_h" : 40,
          "n_h" : 20
        }
      }
    },
    "recurringGiveawayPatterns" : [ ],
    "missedDefects" : 0,
    "independentlyFoundCritical" : 0,
    "reviewedUnflagged" : 60,
    "missedDefectRate" : 0.0,
    "unresolved" : 0,
    "descriptorsG6" : {
      "telemetryMissingStarts" : 0,
      "deliveredTotal" : 200,
      "starts" : 20,
      "validatorRejected" : 0,
      "validatorRetried" : 0,
      "tokensPerDeliveredQuestion" : 10,
      "validatorAccepted" : 200,
      "p50LatencyMs" : 100
    },
    "descriptorsG7" : {
      "validatorRequested" : 200,
      "telemetryMissingStarts" : 0,
      "validatorRejected" : 0,
      "multiSelectThreeCorrect" : 20,
      "starts" : 20,
      "multiSelectTwoCorrect" : 20,
      "validatorRetried" : 0,
      "validatorAccepted" : 200
    },
    "nearDuplicateStartRate" : 0.0
  },
  "proposed" : {
    "items" : 200,
    "means" : {
      "distractorPlausibility" : 5.0,
      "modeAlignment" : 4.0,
      "cognitiveDemand" : 4.0,
      "clarity" : 4.0,
      "faithfulness" : 4.0
    },
    "formatMeans" : {
      "MCQ" : {
        "count" : 160,
        "means" : {
          "distractorPlausibility" : 5.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        }
      },
      "MULTI_SELECT" : {
        "count" : 40,
        "means" : {
          "distractorPlausibility" : 5.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        }
      }
    },
    "criticalDefects" : [ ],
    "confirmedCriticalCount" : 0,
    "G4" : {
      "N" : 200,
      "C_F" : 0,
      "U" : 200,
      "sum_n" : 60,
      "sum_k" : 0,
      "confirmedMinimumRate" : 0.0,
      "G_hat" : 0.0,
      "p_up" : 0.03764937360191141,
      "UL" : 0.03764937360191141,
      "ci95Lower" : 0.0,
      "ci95Upper" : 0.0596294922861669,
      "recurring" : false,
      "verdict" : "PASS",
      "strata" : {
        "MCQ" : {
          "k_h" : 0,
          "U_h" : 160,
          "n_h" : 40
        },
        "MULTI_SELECT" : {
          "k_h" : 0,
          "U_h" : 40,
          "n_h" : 20
        }
      }
    },
    "recurringGiveawayPatterns" : [ ],
    "missedDefects" : 0,
    "independentlyFoundCritical" : 0,
    "reviewedUnflagged" : 60,
    "missedDefectRate" : 0.0,
    "unresolved" : 0,
    "descriptorsG6" : {
      "telemetryMissingStarts" : 0,
      "deliveredTotal" : 200,
      "starts" : 20,
      "validatorRejected" : 0,
      "validatorRetried" : 0,
      "tokensPerDeliveredQuestion" : 10,
      "validatorAccepted" : 200,
      "p50LatencyMs" : 100
    },
    "descriptorsG7" : {
      "validatorRequested" : 200,
      "telemetryMissingStarts" : 0,
      "validatorRejected" : 0,
      "multiSelectThreeCorrect" : 20,
      "starts" : 20,
      "multiSelectTwoCorrect" : 20,
      "validatorRetried" : 0,
      "validatorAccepted" : 200
    },
    "nearDuplicateStartRate" : 0.0
  },
  "G1" : {
    "proposedCritical" : [ ],
    "verdict" : "PASS",
    "overallNoRegression" : true,
    "wordingSensitiveRegressionTypeExcess" : {
      "AMBIGUOUS_STEM" : 0,
      "COMPLEXITY_UNSUPPORTED_BY_INPUTS" : 0,
      "PARTLY_CORRECT_DISTRACTOR" : 0,
      "SECOND_DEFENSIBLE_ANSWER" : 0
    },
    "currentCritical" : [ ]
  },
  "G2" : "PASS",
  "G3" : "PASS",
  "G4" : {
    "N" : 200,
    "C_F" : 0,
    "U" : 200,
    "sum_n" : 60,
    "sum_k" : 0,
    "confirmedMinimumRate" : 0.0,
    "G_hat" : 0.0,
    "p_up" : 0.03764937360191141,
    "UL" : 0.03764937360191141,
    "ci95Lower" : 0.0,
    "ci95Upper" : 0.0596294922861669,
    "recurring" : false,
    "verdict" : "PASS",
    "strata" : {
      "MCQ" : {
        "k_h" : 0,
        "U_h" : 160,
        "n_h" : 40
      },
      "MULTI_SELECT" : {
        "k_h" : 0,
        "U_h" : 40,
        "n_h" : 20
      }
    }
  },
  "G5" : "PASS",
  "G6" : {
    "clarity" : 0.0,
    "faithfulness" : 0.0,
    "deliveredRate" : 0.0,
    "p50LatencyMs" : 0.0,
    "tokensPerDeliveredQuestion" : 0.0,
    "verdict" : "PASS"
  },
  "G7" : {
    "telemetryScope" : "ALL_FORMAT; generation-count log has no per-format rejection or retry breakdown",
    "currentDiscardedAttempts" : 0,
    "proposedDiscardedAttempts" : 0,
    "currentVerifiedStarts" : null,
    "proposedVerifiedStarts" : null,
    "currentRetryMismatchStarts" : null,
    "proposedRetryMismatchStarts" : null,
    "retriedEqualsRejected" : true,
    "currentSuccessfulStarts" : 20,
    "proposedSuccessfulStarts" : 20,
    "currentTelemetryMissingStarts" : 0,
    "proposedTelemetryMissingStarts" : 0,
    "currentMultiSelectThreeCorrectShare" : 0.5,
    "proposedMultiSelectThreeCorrectShare" : 0.5,
    "currentAllFormatValidatorRejectionRate" : 0.0,
    "proposedAllFormatValidatorRejectionRate" : 0.0,
    "currentAllFormatValidatorRetryRate" : 0.0,
    "proposedAllFormatValidatorRetryRate" : 0.0,
    "telemetryVerdict" : "OBSERVED",
    "multiSelectThreeCorrectShareVerdict" : "PASS",
    "allFormatValidatorRejectionVerdict" : "PASS",
    "allFormatValidatorRetryVerdict" : "PASS",
    "absolutePercentagePointDifferences" : {
      "allFormatValidatorRetryRate" : 0.0,
      "allFormatValidatorRejectionRate" : 0.0,
      "multiSelectThreeCorrectShare" : 0.0
    },
    "verdict" : "PASS"
  },
  "G8" : {
    "missingOrFailedSides" : [ ],
    "nearDuplicateStartRate" : 0.0,
    "pairedNotes" : 20,
    "verdict" : "PASS"
  },
  "escalation" : {
    "currentMissedDefectRate" : 0.0,
    "proposedMissedDefectRate" : 0.0,
    "escalationRequired" : false,
    "completeArmReReview" : false
  },
  "boardExamReviewFinding" : "Flat or negative BOARD_EXAM_REVIEW change: distractor=1.0, mode=0.0",
  "perNoteLevel" : {
    "COLLEGE" : {
      "interpretation" : "DESCRIPTIVE_ONLY",
      "current" : {
        "items" : 100,
        "means" : {
          "distractorPlausibility" : 4.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        },
        "formatMeans" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        },
        "criticalDefects" : [ ],
        "confirmedCriticalCount" : 0,
        "G4" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 30,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.07388127187120652,
          "UL" : 0.07388127187120652,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.11570330822202775,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 20
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 10
            }
          }
        },
        "recurringGiveawayPatterns" : [ ],
        "missedDefects" : 0,
        "independentlyFoundCritical" : 0,
        "reviewedUnflagged" : 30,
        "missedDefectRate" : 0.0,
        "unresolved" : 0,
        "descriptorsG6" : {
          "telemetryMissingStarts" : 0,
          "deliveredTotal" : 100,
          "starts" : 10,
          "validatorRejected" : 0,
          "validatorRetried" : 0,
          "tokensPerDeliveredQuestion" : 10,
          "validatorAccepted" : 100,
          "p50LatencyMs" : 100
        },
        "descriptorsG7" : {
          "validatorRequested" : 100,
          "telemetryMissingStarts" : 0,
          "validatorRejected" : 0,
          "multiSelectThreeCorrect" : 10,
          "starts" : 10,
          "multiSelectTwoCorrect" : 10,
          "validatorRetried" : 0,
          "validatorAccepted" : 100
        },
        "nearDuplicateStartRate" : 0.0
      },
      "proposed" : {
        "items" : 100,
        "means" : {
          "distractorPlausibility" : 5.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        },
        "formatMeans" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        },
        "criticalDefects" : [ ],
        "confirmedCriticalCount" : 0,
        "G4" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 30,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.07388127187120652,
          "UL" : 0.07388127187120652,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.11570330822202775,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 20
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 10
            }
          }
        },
        "recurringGiveawayPatterns" : [ ],
        "missedDefects" : 0,
        "independentlyFoundCritical" : 0,
        "reviewedUnflagged" : 30,
        "missedDefectRate" : 0.0,
        "unresolved" : 0,
        "descriptorsG6" : {
          "telemetryMissingStarts" : 0,
          "deliveredTotal" : 100,
          "starts" : 10,
          "validatorRejected" : 0,
          "validatorRetried" : 0,
          "tokensPerDeliveredQuestion" : 10,
          "validatorAccepted" : 100,
          "p50LatencyMs" : 100
        },
        "descriptorsG7" : {
          "validatorRequested" : 100,
          "telemetryMissingStarts" : 0,
          "validatorRejected" : 0,
          "multiSelectThreeCorrect" : 10,
          "starts" : 10,
          "multiSelectTwoCorrect" : 10,
          "validatorRetried" : 0,
          "validatorAccepted" : 100
        },
        "nearDuplicateStartRate" : 0.0
      },
      "G1" : {
        "wordingSensitiveRegressionTypeExcess" : {
          "AMBIGUOUS_STEM" : 0,
          "COMPLEXITY_UNSUPPORTED_BY_INPUTS" : 0,
          "PARTLY_CORRECT_DISTRACTOR" : 0,
          "SECOND_DEFENSIBLE_ANSWER" : 0
        },
        "overallNoRegression" : true,
        "currentCritical" : [ ],
        "proposedCritical" : [ ]
      },
      "G2" : {
        "currentMean" : 4.0,
        "proposedMean" : 5.0,
        "difference" : 1.0
      },
      "G3" : {
        "proposedMean" : 4.0,
        "currentMean" : 4.0
      },
      "G4" : {
        "current" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 30,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.07388127187120652,
          "UL" : 0.07388127187120652,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.11570330822202775,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 20
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 10
            }
          }
        },
        "proposed" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 30,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.07388127187120652,
          "UL" : 0.07388127187120652,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.11570330822202775,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 20
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 10
            }
          }
        }
      },
      "G5" : {
        "current" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        },
        "proposed" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        }
      },
      "G6" : {
        "clarity" : 0.0,
        "faithfulness" : 0.0,
        "deliveredRate" : 0.0,
        "p50LatencyMs" : 0.0,
        "tokensPerDeliveredQuestion" : 0.0,
        "verdict" : "PASS"
      },
      "G7" : {
        "telemetryScope" : "ALL_FORMAT; generation-count log has no per-format rejection or retry breakdown",
        "currentDiscardedAttempts" : 0,
        "proposedDiscardedAttempts" : 0,
        "currentVerifiedStarts" : null,
        "proposedVerifiedStarts" : null,
        "currentRetryMismatchStarts" : null,
        "proposedRetryMismatchStarts" : null,
        "retriedEqualsRejected" : true,
        "currentSuccessfulStarts" : 10,
        "proposedSuccessfulStarts" : 10,
        "currentTelemetryMissingStarts" : 0,
        "proposedTelemetryMissingStarts" : 0,
        "currentMultiSelectThreeCorrectShare" : 0.5,
        "proposedMultiSelectThreeCorrectShare" : 0.5,
        "currentAllFormatValidatorRejectionRate" : 0.0,
        "proposedAllFormatValidatorRejectionRate" : 0.0,
        "currentAllFormatValidatorRetryRate" : 0.0,
        "proposedAllFormatValidatorRetryRate" : 0.0,
        "telemetryVerdict" : "OBSERVED",
        "multiSelectThreeCorrectShareVerdict" : "PASS",
        "allFormatValidatorRejectionVerdict" : "PASS",
        "allFormatValidatorRetryVerdict" : "PASS",
        "absolutePercentagePointDifferences" : {
          "allFormatValidatorRetryRate" : 0.0,
          "allFormatValidatorRejectionRate" : 0.0,
          "multiSelectThreeCorrectShare" : 0.0
        },
        "verdict" : "PASS"
      },
      "G8" : {
        "nearDuplicateStartRate" : 0.0,
        "pairedNotes" : 10
      },
      "pairedBootstrap" : {
        "distractorPlausibility" : {
          "bootstrap95Lower" : 1.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 1.0,
          "meanDifference" : 1.0
        },
        "modeAlignment" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "cognitiveDemand" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "clarity" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "faithfulness" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "deliveredRate" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "allFormatValidatorRetryRate" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "latencyMs" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "tokensPerDeliveredQuestion" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "multiSelectThreeShare" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "allFormatValidatorRejectionRate" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "nearDuplicateStart" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        }
      }
    },
    "BOARD_EXAM_REVIEW" : {
      "interpretation" : "DESCRIPTIVE_ONLY",
      "current" : {
        "items" : 100,
        "means" : {
          "distractorPlausibility" : 4.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        },
        "formatMeans" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        },
        "criticalDefects" : [ ],
        "confirmedCriticalCount" : 0,
        "G4" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 30,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.07388127187120652,
          "UL" : 0.07388127187120652,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.11570330822202775,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 20
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 10
            }
          }
        },
        "recurringGiveawayPatterns" : [ ],
        "missedDefects" : 0,
        "independentlyFoundCritical" : 0,
        "reviewedUnflagged" : 30,
        "missedDefectRate" : 0.0,
        "unresolved" : 0,
        "descriptorsG6" : {
          "telemetryMissingStarts" : 0,
          "deliveredTotal" : 100,
          "starts" : 10,
          "validatorRejected" : 0,
          "validatorRetried" : 0,
          "tokensPerDeliveredQuestion" : 10,
          "validatorAccepted" : 100,
          "p50LatencyMs" : 100
        },
        "descriptorsG7" : {
          "validatorRequested" : 100,
          "telemetryMissingStarts" : 0,
          "validatorRejected" : 0,
          "multiSelectThreeCorrect" : 10,
          "starts" : 10,
          "multiSelectTwoCorrect" : 10,
          "validatorRetried" : 0,
          "validatorAccepted" : 100
        },
        "nearDuplicateStartRate" : 0.0
      },
      "proposed" : {
        "items" : 100,
        "means" : {
          "distractorPlausibility" : 5.0,
          "modeAlignment" : 4.0,
          "cognitiveDemand" : 4.0,
          "clarity" : 4.0,
          "faithfulness" : 4.0
        },
        "formatMeans" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        },
        "criticalDefects" : [ ],
        "confirmedCriticalCount" : 0,
        "G4" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 30,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.07388127187120652,
          "UL" : 0.07388127187120652,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.11570330822202775,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 20
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 10
            }
          }
        },
        "recurringGiveawayPatterns" : [ ],
        "missedDefects" : 0,
        "independentlyFoundCritical" : 0,
        "reviewedUnflagged" : 30,
        "missedDefectRate" : 0.0,
        "unresolved" : 0,
        "descriptorsG6" : {
          "telemetryMissingStarts" : 0,
          "deliveredTotal" : 100,
          "starts" : 10,
          "validatorRejected" : 0,
          "validatorRetried" : 0,
          "tokensPerDeliveredQuestion" : 10,
          "validatorAccepted" : 100,
          "p50LatencyMs" : 100
        },
        "descriptorsG7" : {
          "validatorRequested" : 100,
          "telemetryMissingStarts" : 0,
          "validatorRejected" : 0,
          "multiSelectThreeCorrect" : 10,
          "starts" : 10,
          "multiSelectTwoCorrect" : 10,
          "validatorRetried" : 0,
          "validatorAccepted" : 100
        },
        "nearDuplicateStartRate" : 0.0
      },
      "G1" : {
        "wordingSensitiveRegressionTypeExcess" : {
          "AMBIGUOUS_STEM" : 0,
          "COMPLEXITY_UNSUPPORTED_BY_INPUTS" : 0,
          "PARTLY_CORRECT_DISTRACTOR" : 0,
          "SECOND_DEFENSIBLE_ANSWER" : 0
        },
        "overallNoRegression" : true,
        "currentCritical" : [ ],
        "proposedCritical" : [ ]
      },
      "G2" : {
        "currentMean" : 4.0,
        "proposedMean" : 5.0,
        "difference" : 1.0
      },
      "G3" : {
        "proposedMean" : 4.0,
        "currentMean" : 4.0
      },
      "G4" : {
        "current" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 30,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.07388127187120652,
          "UL" : 0.07388127187120652,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.11570330822202775,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 20
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 10
            }
          }
        },
        "proposed" : {
          "N" : 100,
          "C_F" : 0,
          "U" : 100,
          "sum_n" : 30,
          "sum_k" : 0,
          "confirmedMinimumRate" : 0.0,
          "G_hat" : 0.0,
          "p_up" : 0.07388127187120652,
          "UL" : 0.07388127187120652,
          "ci95Lower" : 0.0,
          "ci95Upper" : 0.11570330822202775,
          "recurring" : false,
          "verdict" : "INCONCLUSIVE",
          "strata" : {
            "MCQ" : {
              "k_h" : 0,
              "U_h" : 80,
              "n_h" : 20
            },
            "MULTI_SELECT" : {
              "k_h" : 0,
              "U_h" : 20,
              "n_h" : 10
            }
          }
        }
      },
      "G5" : {
        "current" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 4.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        },
        "proposed" : {
          "MCQ" : {
            "count" : 80,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          },
          "MULTI_SELECT" : {
            "count" : 20,
            "means" : {
              "distractorPlausibility" : 5.0,
              "modeAlignment" : 4.0,
              "cognitiveDemand" : 4.0,
              "clarity" : 4.0,
              "faithfulness" : 4.0
            }
          }
        }
      },
      "G6" : {
        "clarity" : 0.0,
        "faithfulness" : 0.0,
        "deliveredRate" : 0.0,
        "p50LatencyMs" : 0.0,
        "tokensPerDeliveredQuestion" : 0.0,
        "verdict" : "PASS"
      },
      "G7" : {
        "telemetryScope" : "ALL_FORMAT; generation-count log has no per-format rejection or retry breakdown",
        "currentDiscardedAttempts" : 0,
        "proposedDiscardedAttempts" : 0,
        "currentVerifiedStarts" : null,
        "proposedVerifiedStarts" : null,
        "currentRetryMismatchStarts" : null,
        "proposedRetryMismatchStarts" : null,
        "retriedEqualsRejected" : true,
        "currentSuccessfulStarts" : 10,
        "proposedSuccessfulStarts" : 10,
        "currentTelemetryMissingStarts" : 0,
        "proposedTelemetryMissingStarts" : 0,
        "currentMultiSelectThreeCorrectShare" : 0.5,
        "proposedMultiSelectThreeCorrectShare" : 0.5,
        "currentAllFormatValidatorRejectionRate" : 0.0,
        "proposedAllFormatValidatorRejectionRate" : 0.0,
        "currentAllFormatValidatorRetryRate" : 0.0,
        "proposedAllFormatValidatorRetryRate" : 0.0,
        "telemetryVerdict" : "OBSERVED",
        "multiSelectThreeCorrectShareVerdict" : "PASS",
        "allFormatValidatorRejectionVerdict" : "PASS",
        "allFormatValidatorRetryVerdict" : "PASS",
        "absolutePercentagePointDifferences" : {
          "allFormatValidatorRetryRate" : 0.0,
          "allFormatValidatorRejectionRate" : 0.0,
          "multiSelectThreeCorrectShare" : 0.0
        },
        "verdict" : "PASS"
      },
      "G8" : {
        "nearDuplicateStartRate" : 0.0,
        "pairedNotes" : 10
      },
      "pairedBootstrap" : {
        "distractorPlausibility" : {
          "bootstrap95Lower" : 1.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 1.0,
          "meanDifference" : 1.0
        },
        "modeAlignment" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "cognitiveDemand" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "clarity" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "faithfulness" : {
          "bootstrap95Lower" : 0.0,
          "pairedNotes" : 10,
          "bootstrap95Upper" : 0.0,
          "meanDifference" : 0.0
        },
        "deliveredRate" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "allFormatValidatorRetryRate" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "latencyMs" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "tokensPerDeliveredQuestion" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "multiSelectThreeShare" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "allFormatValidatorRejectionRate" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        },
        "nearDuplicateStart" : {
          "pairedNotes" : 0,
          "interval" : "MISSING"
        }
      }
    }
  },
  "pairedBootstrap" : {
    "distractorPlausibility" : {
      "bootstrap95Lower" : 1.0,
      "pairedNotes" : 20,
      "bootstrap95Upper" : 1.0,
      "meanDifference" : 1.0
    },
    "modeAlignment" : {
      "bootstrap95Lower" : 0.0,
      "pairedNotes" : 20,
      "bootstrap95Upper" : 0.0,
      "meanDifference" : 0.0
    },
    "cognitiveDemand" : {
      "bootstrap95Lower" : 0.0,
      "pairedNotes" : 20,
      "bootstrap95Upper" : 0.0,
      "meanDifference" : 0.0
    },
    "clarity" : {
      "bootstrap95Lower" : 0.0,
      "pairedNotes" : 20,
      "bootstrap95Upper" : 0.0,
      "meanDifference" : 0.0
    },
    "faithfulness" : {
      "bootstrap95Lower" : 0.0,
      "pairedNotes" : 20,
      "bootstrap95Upper" : 0.0,
      "meanDifference" : 0.0
    },
    "deliveredRate" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    },
    "allFormatValidatorRetryRate" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    },
    "latencyMs" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    },
    "tokensPerDeliveredQuestion" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    },
    "multiSelectThreeShare" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    },
    "allFormatValidatorRejectionRate" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    },
    "nearDuplicateStart" : {
      "pairedNotes" : 0,
      "interval" : "MISSING"
    }
  },
  "representativePairedNotes" : {
    "selectionRule" : "Largest positive and largest negative paired per-note distractor-mean difference; packId lexical tie-break",
    "improvement" : {
      "difference" : 1.0,
      "currentIds" : [ "A3-0", "A3-1", "A3-2", "A3-3", "A3-4", "A3-5", "A3-6", "A3-7", "A3-8", "A3-9" ],
      "packId" : "pack-0",
      "proposedIds" : [ "B3-0", "B3-1", "B3-2", "B3-3", "B3-4", "B3-5", "B3-6", "B3-7", "B3-8", "B3-9" ],
      "noteRef" : "note-0"
    },
    "failure" : "NONE"
  },
  "reasons" : [ ],
  "verdict" : "PASS"
}
```
