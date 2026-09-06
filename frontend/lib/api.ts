import {
  beginManualLogoutRedirect,
  clearAuthUser,
  getAccessToken,
  getAuthUser,
  patchAuthUser,
  getRefreshToken,
  handleUnauthorizedSession,
  setAuthUser,
  type AuthUser,
} from "./auth";
import type { MePlanResponse } from "./me-plan";
import type { ThemePreference } from "./theme-preferences";
import type { AdaptivePracticeEntry } from "./adaptive-practice-entry";
import { ADAPTIVE_PRACTICE_ENTRY_QUERY_PARAM } from "./adaptive-practice-entry";

export type { MePlanResponse } from "./me-plan";

export type QuizItem = {
  question: string;
  choices: string[];
  correctIndex: number | null;
  correctIndices?: number[] | null;
  answerIndex?: number;
  correctAnswerIndex?: number;
  answer?: string | null;
  concept?: string;
  sourceStudyPackId?: string;
  explanation: string;
  questionFormat?: "MCQ" | "TRUE_FALSE" | "MULTI_SELECT" | "MATCHING" | "IDENTIFICATION" | "ENUMERATION" | null;
  questionGroup?: string | null;
  questionType?: "CONCEPTUAL" | "COMPUTATIONAL" | null;
  workingSolution?: string | null;
  acceptableAnswers?: string[] | null;
  acceptableAnswerGroups?: string[][] | null;
};

export type GeneratedQuizResponse = {
  id?: string;
  noteId: string;
  questions: QuizItem[];
  generatedAt: string;
};

export type QuizShareLinkResponse = {
  id: string;
  token: string;
  shareUrl: string;
  isActive: boolean;
  createdAt: string;
};

export type CombinedQuizSection = {
  title: string;
  questions: QuizItem[];
};

export type CombinedQuizResponse = {
  id: string;
  title: string;
  sections: CombinedQuizSection[];
  createdAt: string;
};

export type CombinedQuizSummaryResponse = {
  id: string;
  title: string;
  createdAt: string;
  sectionCount: number;
  questionCount: number;
  sharing: "NO_LINK" | "SHARING_ON" | "SHARING_OFF";
};

export type CreateCombinedQuizRequest = {
  title: string;
  sections: Array<{
    noteId: string;
    questionIndexes: number[];
  }>;
};

export type PublicQuizItem = {
  question: string;
  choices: string[];
  concept?: string | null;
  /**
   * Present so a recipient can be given the right control. It never carries the answer key --
   * correctIndex/correctIndices/explanation reach them only in SharedQuizResultItem, after submitting.
   */
  questionFormat?: string | null;
};

export type PublicSharedQuizResponse = {
  quizId: string;
  noteTitle: string;
  questions: PublicQuizItem[];
};

export type SharedQuizResultItem = {
  correct: boolean;
  correctIndex: number;
  /** Non-empty only for MULTI_SELECT. Prefer it when non-empty; otherwise use correctIndex. */
  correctIndices?: number[] | null;
  explanation?: string | null;
};

export type SharedQuizResultsResponse = {
  score: number;
  total: number;
  items: SharedQuizResultItem[];
};

export type QuizDocxExportMode = "QUIZ_ONLY" | "WITH_ANSWERS";

export type StudyPackResponse = {
  id: string;
  noteId: string | null;
  inputType: "text" | "image";
  extractedText: string | null;
  title: string;
  summary: string;
  sourceText?: string | null;
  subject?: string | null;
  keyConcepts: string[];
  tags: string[];
  quiz: QuizItem[];
  quizMastered: boolean;
  quizMasteredAt: string | null;
  createdAt: string;
  meta: {
    ocrConfidence: number | null;
    latencyMs: number | null;
  };
};

export type MemorizationGrade = "AGAIN" | "HARD" | "GOOD" | "EASY";

export type MemorizationCardResponse = {
  concept: string;
  intervalDays: number;
  easeFactor: number;
  repetitions: number;
  dueAt: string;
  lastReviewedAt: string | null;
  lastGrade: MemorizationGrade | null;
};

export type NoteTextExtractionResponse = {
  inputType: "image" | "txt" | "pdf" | "docx";
  extractedText: string;
  meta: {
    ocrConfidence: number | null;
    lowConfidence: boolean;
  };
};

export type BulkImportedNote = {
  noteId: string;
  title: string;
  fileName: string;
  lowConfidence: boolean;
};

export type BulkImportFailure = {
  fileName: string;
  errorCode: string;
  message: string;
};

export type BulkImportResult = {
  created: BulkImportedNote[];
  failed: BulkImportFailure[];
};

export type BulkGenerateNotesRequest = {
  subject: string;
  topics: string[];
  makePublic: boolean;
  courseProgramIds?: string[];
  /** Free-text personal-note value. Curator requests use courseProgramIds instead. */
  courseProgramText?: string | null;
  domainContext?: DomainContext | null;
  learnerLevel?: LearnerLevel | null;
  collectionId?: string | null;
  sectionLabel?: string | null;
};

export type BulkGenerateNotesResponse = {
  resultId: string;
  acceptedTopics: number;
  queuedTopics: number;
  rejectedTopics: number;
};

export type BulkGenerationFailureReason = {
  topic: string;
  code: string;
  reason: string;
};

export type BulkGenerationResultResponse = {
  id: string;
  subject: string;
  courseProgram: string | null;
  domainContext: DomainContext | null;
  learnerLevel: LearnerLevel | null;
  collectionId: string | null;
  makePublic: boolean;
  requestedCount: number;
  createdCount: number;
  failedTopics: string[];
  failedTopicReasons?: BulkGenerationFailureReason[] | null;
  quotaBlockedTopics: string[];
  createdAt: string;
};

export type StudyPackListItemResponse = {
  id: string;
  title: string;
  summaryPreview: string;
  quizCount: number;
  subject?: string | null;
  tags: string[];
  createdAt: string;
};

export type StudyPackListPageResponse = {
  items: StudyPackListItemResponse[];
  nextCursor: string | null;
  hasMore: boolean;
};

export type ContinueStudyingReason =
  | "RESUME_REVIEW"
  | "LOW_SCORE_RECENT"
  | "SUGGESTED_CHALLENGE"
  | "RECENTLY_OPENED"
  | "RECENTLY_CREATED";
export type ContinueStudyingResumeState = "QUESTION_IN_PROGRESS" | "RETRY_TRANSITION" | "RETRY_IN_PROGRESS";
export type ContinueStudyingResumeType = "QUICK_REVIEW" | "CHALLENGE" | "ADAPTIVE" | "LONG_EXAM" | "INTERVIEW_PRACTICE";
export type TodayFocusType =
  | "RESUME_REVIEW"
  | "RETRY_REVIEW"
  | "DUE_CONCEPTS_REVIEW"
  | "PRACTICE_WEAK_CONCEPT"
  | "REDO_MISSED_QUESTIONS"
  | "REVIEW_PACK"
  | "STUDY_SUGGESTION";

export type ContinueStudyingResponse = {
  studyPackId: string | null;
  noteId: string | null;
  noteTitle: string | null;
  subject: string | null;
  courseProgram: string | null;
  summaryPreview: string | null;
  resumeType: ContinueStudyingResumeType | null;
  reason: ContinueStudyingReason | null;
  lastScorePercentage: number | null;
  lastReviewedAt: string | null;
  lastOpenedAt: string | null;
  createdAt: string | null;
  currentQuestionIndex: number | null;
  totalQuestions: number | null;
  currentRound: "INITIAL" | "RETRY" | null;
  remainingQuestions: number | null;
  resumeState: ContinueStudyingResumeState | null;
  sessionId?: string | null;
};

export type TodayFocusResponse = {
  type: TodayFocusType;
  studyPackId: string | null;
  noteId: string | null;
  title: string;
  message: string;
  actionLabel: string;
  concepts: Array<{
    concept: string;
    noteId: string | null;
    noteTitle: string | null;
  }>;
  adaptivePracticeAvailable: boolean;
};

export type PostSessionNextStepResponse = {
  type: Extract<TodayFocusType, "PRACTICE_WEAK_CONCEPT" | "RETRY_REVIEW" | "REDO_MISSED_QUESTIONS" | "REVIEW_PACK">;
  studyPackId: string;
  noteId: string | null;
  title: string;
  message: string;
  actionLabel: string;
  actionHref: string;
  concepts: string[];
  adaptivePracticeAvailable: boolean;
  adaptivePracticeRemaining: number | null;
  goalNudge: GoalNudgeResponse | null;
  secondaryAction: {
    actionLabel: string;
    actionHref: string;
    adaptivePractice: boolean;
    studyPlanRecommendation?: boolean;
    courseProgram?: string | null;
    recommendedPlanId?: string | null;
    nextPlanItem?: boolean;
  } | null;
};

export type GoalNudgeResponse = {
  studyGoal: string;
  goalType: "EXAM" | "SUBJECT";
  goalName: string;
  goalLabel: string;
  masteryPercentage: number;
  dueConcepts: number;
  weakestGoalSubject: string | null;
};

export type SubjectProgressEntry = {
  subject: string;
  totalConcepts: number;
  masteredConcepts: number;
  dueConcepts: number;
  notPracticedConcepts: number;
  masteryPercentage: number;
};

export type GoalSummaryResponse = {
  studyGoal: string;
  goalType: "EXAM" | "SUBJECT" | "SUBJECT_FOCUS";
  goalName: string;
  goalLabel: string;
  masteryPercentage: number;
  masteredConcepts: number;
  totalConcepts: number;
  notPracticedConcepts: number;
  weakestGoalSubject: string | null;
};

export type ProgressReportResponse = {
  subjects: SubjectProgressEntry[];
  goalSummary?: GoalSummaryResponse | null;
  userCoursePrograms?: string[] | null;
  profileType?: ProfileType | null;
};

export type PlanReadinessResponse = {
  collectionId: string;
  totalNotes: number;
  notesWithStudyPack: number;
  overallReadinessPercentage: number;
  totalConcepts: number;
  masteredConcepts: number;
  dueConcepts: number;
  notPracticedConcepts: number;
  subjects: SubjectProgressEntry[];
};

export type GoalCollectionChildResponse = {
  collectionId: string;
  title: string;
  description: string | null;
  itemCount: number;
  overallReadinessPercentage: number;
  masteredConcepts: number;
  dueConcepts: number;
  notPracticedConcepts: number;
  totalConcepts: number;
  todaysConceptBudget: number | null;
};

export type WeeklyFocusDayEntry = {
  dayOfWeek: "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";
  collectionIds: string[];
};

export type CompanionFaqItem = {
  question: string | null;
  answer: string | null;
};

export type CompanionMentorTipAction =
  | "NONE"
  | "CONTINUE_STUDYING"
  | "REVIEW_DUE_CONCEPTS"
  | "TERMINAL_ACTION";

export type CompanionMentorTipSurfacingConditionType =
  | "DAYS_BEFORE_TARGET_DATE"
  | "AFTER_SUBJECTS_COMPLETED";

export type CompanionMentorTipSurfacingCondition = {
  type: CompanionMentorTipSurfacingConditionType;
  threshold: number;
};

export type CompanionMentorTip = {
  id: string | null;
  title: string | null;
  body: string | null;
  linkedAction: CompanionMentorTipAction | null;
  surfacingCondition: CompanionMentorTipSurfacingCondition | null;
};

export type CompanionContent = {
  overview: string | null;
  studyStrategy: string | null;
  commonMistakes: string | null;
  resources?: string | null;
  faq: CompanionFaqItem[];
  mentorTips: CompanionMentorTip[];
};

export type CompanionSection = "OVERVIEW" | "STUDY_STRATEGY" | "COMMON_MISTAKES" | "FAQ" | "MENTOR_TIPS";

export type AskCompanionTurn = {
  question: string;
  answer: string;
  createdAt: string;
};

export type AskCompanionSessionResponse = {
  sessionId: string;
  collectionId: string;
  status: "ACTIVE" | "ENDED";
  turnCount: number;
  turnLimit: number;
  turnsRemaining: number;
  turns: AskCompanionTurn[];
  usedThisMonth: number;
  monthlyLimit: number;
  usagePeriodEndsAt: string;
};

export type GoalCollectionDetailResponse = {
  collectionId: string;
  title: string;
  description: string | null;
  visibility: "PRIVATE" | "PUBLIC";
  courseProgram: string | null;
  targetCompletionDate: string | null;
  companion: CompanionContent | null;
  companionMayBeOutdated: boolean;
  sourcePlanId: string | null;
  parentCollectionId: string | null;
  itemCount: number;
  childCount: number;
  overallReadinessPercentage: number;
  masteredConcepts: number;
  dueConcepts: number;
  notPracticedConcepts: number;
  totalConcepts: number;
  weeksRemaining: number | null;
  conceptsRemaining: number | null;
  todaysConceptBudget: number | null;
  weeklyFocusByDay: WeeklyFocusDayEntry[];
  createdAt: string;
  updatedAt: string;
  children: GoalCollectionChildResponse[];
};

export type DashboardConceptInsightResponse = {
  conceptName: string;
  accuracyPercentage: number;
};

export type DashboardPerformanceSummaryResponse = {
  averageQuizScore: number | null;
  totalQuizzesTaken: number;
  studyPacksCreated: number;
  strongestConcept: DashboardConceptInsightResponse | null;
  weakestConcept: DashboardConceptInsightResponse | null;
};

export type DashboardFocusAreasResponse = {
  concepts: DashboardConceptInsightResponse[];
  practiceNoteId: string | null;
  /** The plan to practise across, resolved SERVER-side by v0.78.0's rule (Primary Review Set if it
   *  contains the weakest note, else the most recently updated containing collection). Null when the
   *  weakest note belongs to no plan. Deliberately not a weakness ranking. */
  practiceCollectionId: string | null;
  practiceCollectionTitle: string | null;
  adaptivePracticeAvailable: boolean;
};

export type DashboardWeeklyActivityResponse = {
  studyPacksCreated: number;
  quizzesTaken: number;
  adaptiveSessions: number;
  studyDays: number;
};

export type ExamPacingPlanResponse = {
  dueConceptCount: number;
  dailyConceptTarget: number;
  daysRemaining: number;
};

export type DashboardOverviewResponse = {
  performanceSummary: DashboardPerformanceSummaryResponse;
  focusAreas: DashboardFocusAreasResponse;
  weeklyActivity: DashboardWeeklyActivityResponse;
  examPacingPlan: ExamPacingPlanResponse | null;
  totalNoteCount: number;
  hasQuizQuestions: boolean;
  mostRecentReadyNoteId: string | null;
};

export type ProfileType = "STUDENT" | "BOARD_EXAM" | "TEACHER" | "PARENT" | "PROFESSIONAL";
export type NoteTargetProfileType = "STUDENT" | "BOARD_TAKER" | "PROFESSIONAL";
export type DomainContext =
  | "ENGINEERING_MATHEMATICS"
  | "ENGINEERING_SCIENCES"
  | "CIVIL_ENGINEERING"
  | "PROFESSIONAL_PRACTICE_AND_REGULATION"
  | "GENERAL_EDUCATION"
  | "PROFESSIONAL_EDUCATION"
  | "NURSING"
  | "ACCOUNTANCY"
  | "ARCHITECTURAL_DESIGN"
  | "ARCHITECTURAL_HISTORY_AND_THEORY"
  | "PLANNING_AND_SITE_DEVELOPMENT";
export type PaidPlanType = "PLUS" | "PRO";
export type LearnerLevel =
  | "GRADE_SCHOOL"
  | "JUNIOR_HIGH"
  | "SENIOR_HIGH"
  | "COLLEGE"
  | "BOARD_EXAM_REVIEW"
  | "PROFESSIONAL"
  | "PERSONAL_LEARNING";
export type PlanType = "FREE" | PaidPlanType;
export type TeacherQuizQuestionCount = 10 | 20 | 30;
export type BillingCycle = "MONTHLY" | "YEARLY" | "EXAM_CYCLE";
export type UserRole = "USER" | "ADMIN";
export type EngagementMode = "FOCUSED" | "CONSISTENCY" | "STREAK";
export type SubscriptionCancellationReason =
  | "TOO_EXPENSIVE"
  | "NOT_USING_ENOUGH"
  | "MISSING_FEATURES"
  | "TECHNICAL_ISSUES"
  | "FOUND_ANOTHER_TOOL"
  | "JUST_TRYING_IT_OUT"
  | "OTHER";

export type SubscriptionPlanStatusResponse = {
  cancelAtPeriodEnd: boolean;
  premiumEndsAt: string | null;
  cancelledAt: string | null;
};

export type BillingPricingCycleResponse = {
  amount: number | null;
  durationDays: number | null;
  introAmount: number | null;
  introEligible: boolean;
  available: boolean;
};

export type BillingPlanPricingResponse = {
  planType: PaidPlanType;
  monthly: BillingPricingCycleResponse;
  yearly: BillingPricingCycleResponse;
  examCycle: BillingPricingCycleResponse;
};

export type BillingPricingResponse = {
  region: string;
  currency: string;
  plus: BillingPlanPricingResponse;
  pro: BillingPlanPricingResponse;
};

export type AnalyticsEventType =
  | "NOTE_CREATED"
  | "NOTE_AUTHORING_DOMAIN_RECORDED"
  | "COURSE_PROGRAM_VALUE_SELECTED"
  | "NOTES_BULK_IMPORTED"
  | "COLLECTION_CREATED"
  | "COLLECTION_SECTION_ASSIGNED"
  | "STUDY_PLAN_ADOPTED"
  | "STUDY_GOAL_ADOPTED"
  | "PLAN_READINESS_VIEWED"
  | "COMPANION_GENERATED"
  | "STUDY_PACK_GENERATED"
  | "QUICK_REVIEW_STARTED"
  | "QUICK_REVIEW_COMPLETED"
  | "QUICK_REVIEW_MASTERED"
  | "STUDY_PACK_QUIZ_UNLOCKED"
  | "STUDY_PACK_QUIZ_TAB_OPENED_AFTER_UNLOCK"
  | "CHALLENGE_QUIZ_LAUNCHED_BEFORE_MASTERY"
  | "CHALLENGE_QUIZ_LAUNCHED_AFTER_MASTERY"
  | "POST_SESSION_CHALLENGE_CTA_IMPRESSION"
  | "POST_SESSION_CHALLENGE_CTA_CLICKED"
  | "STUDY_PLAN_RECOMMENDATION_IMPRESSION"
  | "STUDY_PLAN_RECOMMENDATION_CLICKED"
  | "POST_SESSION_NEXT_PLAN_ITEM_IMPRESSION"
  | "POST_SESSION_NEXT_PLAN_ITEM_CLICKED"
  | "QUICK_REVIEW_OPEN_LOOP_SHOWN"
  | "REVIEW_COMMITMENT_PROMPT_SHOWN"
  | "REVIEW_COMMITMENT_COMMITTED"
  | "REVIEW_COMMITMENT_DECLINED"
  | "DUE_CONCEPTS_DIGEST_LANDED"
  | "DUE_CONCEPTS_DIGEST_FIRST_ANSWER_SUBMITTED"
  | "CHALLENGE_QUIZ_STARTED"
  | "CHALLENGE_QUIZ_COMPLETED"
  | "BOARD_EXAM_STARTED"
  | "LONG_EXAM_STARTED"
  | "LONG_EXAM_COMPLETED"
  | "LONG_EXAM_FORFEITED"
  | "ADAPTIVE_PRACTICE_STARTED"
  | "ADAPTIVE_PRACTICE_COMPLETED"
  | "INTERVIEW_PRACTICE_STARTED"
  | "INTERVIEW_PRACTICE_COMPLETED"
  | "INTERVIEW_PRACTICE_FORFEITED"
  | "INTERVIEW_PRACTICE_QUOTA_EXHAUSTED"
  | "ASK_COMPANION_STARTED"
  | "ASK_COMPANION_MESSAGE_SENT"
  | "ASK_COMPANION_QUOTA_EXHAUSTED"
  | "PAYWALL_VIEWED"
  | "PAYWALL_DISMISSED"
  | "FEATURE_LOCKED_CLICKED"
  | "UPGRADE_CLICKED"
  | "CHECKOUT_INITIATED"
  | "SUBSCRIPTION_STARTED"
  | "PUBLIC_NOTE_VIEWED"
  | "PUBLIC_NOTE_COPIED"
  | "PUBLIC_NOTE_PUBLISHED"
  | "PUBLIC_NOTE_COPY_CLICKED"
  | "PUBLIC_NOTE_QUIZ_YOURSELF_CLICKED"
  | "PUBLIC_NOTE_FLASHCARDS_CLICKED"
  | "PUBLIC_NOTE_SHARED"
  | "NOTE_SHARED_WITH_CONNECTION"
  | "NOTE_SHARE_REVOKED"
  | "SHARED_NOTE_OPENED"
  | "SHARED_STUDY_PACK_OPENED"
  | "SHARED_NOTE_COPIED"
  | "CONNECTION_ACTIVITY_SHARED"
  | "CONNECTION_ACTIVITY_SHARE_REVOKED"
  | "CONNECTION_ACTIVITY_VIEWED"
  | "PUBLIC_PROFILE_SHARED"
  | "KNOWLEDGE_IMPACT_DASHBOARD_VIEWED"
  | "EXAM_HUB_VIEWED"
  | "EXAM_HUB_CTA_CLICKED"
  | "EXAM_HUB_OFFICIAL_SET_PREVIEWED"
  | "EXAM_HUB_OFFICIAL_SET_ADOPT_CLICKED"
  | "EXPLORE_VIEWED"
  | "EXPLORE_TAB_SWITCHED"
  | "EXPLORE_OFFICIAL_SET_PREVIEWED"
  | "EXPLORE_OFFICIAL_SET_ADOPT_CLICKED"
  | "PUBLISHED_PLANS_VIEWED"
  | "OFFLINE_FALLBACK_SERVED"
  | "STUDY_GOAL_SET"
  | "STUDY_GOAL_DISMISSED"
  | "GOAL_NUDGE_SHOWN"
  | "GOAL_NUDGE_CTA_CLICKED"
  | "OCR_DISABLED_NOTICE_SHOWN"
  | "OCR_DISABLED_FEEDBACK_INTERESTED"
  | "DASHBOARD_GOAL_CARD_VIEWED"
  | "DASHBOARD_GOAL_CARD_CTA_CLICKED"
  | "DASHBOARD_RECOMMENDATION_SHOWN"
  | "DASHBOARD_RECOMMENDATION_CTA_CLICKED"
  | "GUIDANCE_TIP_SHOWN"
  | "GUIDANCE_TIP_CTA_CLICKED"
  | "QUIZ_REVIEW_EXPORTED"
  | "COPY_ON_SIGNUP_COMPLETED"
  | "QUIZ_SHARE_LINK_CREATED"
  | "QUIZ_SHARE_LINK_TOGGLED"
  | "QUIZ_SHARE_LINK_OPENED"
  | "LOGIN"
  | "SIGNUP"
  | "LANDING_PAGE_VIEWED"
  | "LANDING_CTA_CLICKED"
  | "DEMO_OPENED"
  | "SIGNUP_STARTED"
  | "SIGNUP_COMPLETED"
  | "EMAIL_VERIFICATION_SENT"
  | "EMAIL_VERIFIED"
  | "ONBOARDING_V2_STARTED"
  | "ONBOARDING_V2_STEP_VIEWED"
  | "ONBOARDING_V2_PROFILE_SELECTED"
  | "ONBOARDING_V2_EXAM_DATE_SET"
  // Intent is "what do I want to do first"; INPUT_METHOD_SELECTED is "generate from a topic vs paste my
  // own", a sub-choice inside the create branch. Both are needed; they are not the same signal.
  | "ONBOARDING_V2_INTENT_SELECTED"
  | "ONBOARDING_V2_INTENT_UNSUPPORTED_VIEWED"
  | "ONBOARDING_V2_OFFICIAL_PLAN_REQUESTED"
  | "ONBOARDING_V2_FALLBACK_SELECTED"
  | "ONBOARDING_V2_INPUT_METHOD_SELECTED"
  | "ONBOARDING_V2_TOPIC_SUBMITTED"
  | "ONBOARDING_V2_OWN_NOTE_SUBMITTED"
  | "ONBOARDING_V2_STUDY_PACK_GENERATED"
  | "ONBOARDING_V2_STUDY_PACK_ERROR"
  | "ONBOARDING_V2_COMPLETED"
  | "ONBOARDING_V2_PRACTICE_FIRST_ELIGIBLE"
  | "ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED"
  | "ONBOARDING_V2_CTA_CONTINUE_STUDYING"
  | "ONBOARDING_V2_CTA_GO_TO_DASHBOARD"
  | "ONBOARDING_V2_CTA_GO_TO_SAVED_NOTE"
  | "ONBOARDING_V2_BACK_NAVIGATED"
  | "ONBOARDING_V2_ABANDONED";

export type AnalyticsEventRequest = {
  eventType: AnalyticsEventType;
  entityId?: string | null;
  metadata?: Record<string, unknown> | null;
};

export type AdminAnalyticsSummaryResponse = {
  landingPageViews: number;
  landingCtaClicks: number;
  publicNoteViews: number;
  publicNoteCopyClicks: number;
  signupsStarted: number;
  signupsCompleted: number;
  emailVerificationsCompleted: number;
  totalUsers: number;
  totalNotes: number;
  totalStudyPacksGenerated: number;
  totalChallengeQuizzes: number;
  totalAdaptiveQuizzes: number;
  totalUpgrades: number;
};

export type AdminDashboardSummaryResponse = {
  overview: {
    totalUsers: number;
    verifiedUsers: number;
    premiumUsers: number;
    premiumWaitlistCount: number;
    totalNotes: number;
    totalStudyPacksGenerated: number;
    totalPublicNotes: number;
    totalPublicNoteViews: number;
    totalPublicNoteCopies: number;
    totalUpgrades: number;
  };
  billing: {
    activePremiumSubscriptions: number;
    monthlySubscriptions: number;
    yearlySubscriptions: number;
    cancelAtPeriodEndSubscriptions: number;
    failedPayments: number;
    estimatedMrr: number;
    estimatedArr: number;
  };
  engagement: {
    studyPacksGeneratedThisWeek: number;
    quickReviewsStarted: number;
    challengeQuizzesStarted: number;
    adaptivePracticeStarted: number;
    longExamsStarted: number;
    interviewPracticeStarted: number;
    paywallViews: number;
    upgradeClicks: number;
    signups: number;
    verifiedAccounts: number;
  };
};

export type AdminPublicNoteMetricItemResponse = {
  noteId: string;
  title: string | null;
  subject: string | null;
  totalCount: number;
};

export type AdminSubjectMetricItemResponse = {
  subject: string | null;
  studyPackCount: number;
};

export type AdminRecentUpgradeItemResponse = {
  subscriptionId: string;
  userEmail: string;
  billingCycle: "MONTHLY" | "YEARLY";
  provider: "NONE" | "INTERNAL_MIGRATION" | "XENDIT";
  transactionId: string | null;
  amount: number | null;
  currency: string | null;
  cancelAtPeriodEnd: boolean;
  startedAt: string;
};

export type AdminRecentFailedPaymentItemResponse = {
  transactionId: string;
  userEmail: string;
  amount: number;
  currency: string;
  provider: "NONE" | "INTERNAL_MIGRATION" | "XENDIT";
  createdAt: string;
};

export type AdminIssueRefundResponse = {
  transactionId: string;
  userEmail: string;
  amount: number;
  currency: string;
};

export type AdminRecentFeedbackItemResponse = {
  feedbackId: string;
  userEmail: string;
  message: string;
  pageUrl: string | null;
  status: "NEW" | "REVIEWED" | "CLOSED";
  createdAt: string;
  hasImage: boolean;
};

export type AdminDashboardTopContentResponse = {
  mostViewedPublicNotes: AdminPublicNoteMetricItemResponse[];
  mostCopiedPublicNotes: AdminPublicNoteMetricItemResponse[];
  topSubjectsByStudyPackGeneration: AdminSubjectMetricItemResponse[];
};

export type AdminDashboardRecentEventsResponse = {
  recentPremiumUpgrades: AdminRecentUpgradeItemResponse[];
  recentFailedPayments: AdminRecentFailedPaymentItemResponse[];
  recentFeedback: AdminRecentFeedbackItemResponse[];
};

export type AdminOrganicLandingsResponse = {
  landings: Array<{
    weekStart: string;
    eventType: "LANDING_PAGE_VIEWED" | "EXAM_HUB_VIEWED" | "PUBLISHED_PLANS_VIEWED";
    referrerSource: "google" | "other-search" | "social" | "direct";
    count: number;
  }>;
  googleExamHubViews: number;
  examHubCtaClicks: number;
  examHubOrganicClickThroughRatio: number | null;
};

export type AdminFunnelMetricsResponse = {
  windowDays: number | null;
  windowStartedAt: string | null;
  onboarding: {
    totalSignups: number;
    onboardingCompletedUsers: number;
    completionRatePercent: number;
    steps: Array<{
      stepName: string;
      label: string;
      userCount: number;
      dropOffFromPrevious: number | null;
    }>;
    branchSteps: Array<{
      stepName: string;
      label: string;
      userCount: number;
      dropOffFromPrevious: number | null;
    }>;
    legacyStep: {
      stepName: string;
      label: string;
      userCount: number;
      dropOffFromPrevious: number | null;
    };
    requestedPrograms: Array<{
      courseProgram: string;
      requestCount: number;
    }>;
  };
  activation: {
    totalVerifiedUsers: number;
    activatedUsers: number;
    activationRatePercent: number;
    medianDaysToFirstPack: number | null;
  };
  stuckUsers: {
    stuckUsersCount: number;
  };
  quotaHit: {
    freeUsersHitQuota: number;
    totalFreeUsers: number;
    ratePercent: number;
    quotaTypes: Array<{
      quotaType: string;
      label: string;
      monthlyLimit: number;
      usersHitQuota: number;
      applicableFreeUsers: number;
      ratePercent: number;
      applicable: boolean;
    }>;
  };
  paywallConversion: {
    usersSeenPaywall: number;
    usersUpgradedAfterPaywall: number;
    ratePercent: number;
  };
  valueLoop: {
    usersGeneratedPack: number;
    usersStartedQuizWithin7Days: number;
    ratePercent: number;
  };
  retentionCohort: {
    eligibleActivatedUsers: number;
    returnedWeek2Users: number;
    ratePercent: number;
    wideRetention: {
      eligibleActivatedUsers: number;
      returnedAfterDay7Users: number;
      afterDay7RatePercent: number;
      returnedDays2To30Users: number;
      days2To30RatePercent: number;
      returnedAfterDay1Users: number;
      afterDay1RatePercent: number;
    };
    weeklyCohorts: Array<{
      weekStart: string;
      cohortSize: number;
      returnedCount: number;
      ratePercent: number;
      returnedAfterDay7Count: number;
      afterDay7RatePercent: number;
    }>;
  };
  checkoutConversion: {
    usersClickedUpgrade: number;
    usersInitiatedCheckout: number;
    usersSubscribed: number;
    clickToCheckoutRatePercent: number;
    checkoutToPaidRatePercent: number;
    clickToPaidRatePercent: number;
  };
};

export type AdminRegenerateSummariesResponse = {
  queued: number;
  skipped: number;
};

export type AdminRegenerationStatusResponse = {
  total: number;
  processed: number;
  failed: number;
  done: boolean;
};

export type AdminSeedOfficialChallengeQuizTemplatesResponse = {
  queued: number;
  skipped: number;
  rejected: number;
};

export type SubmitFeedbackRequest = {
  message: string;
};

export type FirstTouchAttribution = {
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  utmContent?: string;
  utmTerm?: string;
  referrer?: string;
};

export type SignupRequest = FirstTouchAttribution & {
  email: string;
  password: string;
  firstName: string;
  displayName?: string;
};

export type LoginRequest = {
  email: string;
  password: string;
  keepSignedIn?: boolean;
};

export type GoogleAuthRequest = FirstTouchAttribution & {
  code: string;
  keepSignedIn?: boolean;
};

export type GoogleConnectRequest = {
  code: string;
};

export type ReactivateAccountRequest = {
  email?: string;
  password?: string;
  googleCode?: string;
  keepSignedIn?: boolean;
};

export type AuthResponse = {
  userId: string;
  email: string;
  displayName: string | null;
  profileType: ProfileType | null;
  emailVerifiedAt: string | null;
  onboardingCompletedAt: string | null;
  productOnboardingCompletedAt: string | null;
  themePreference?: ThemePreference | null;
  role: UserRole;
  planType: PlanType;
  token: string;
  refreshToken: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
};

export type MeResponse = {
  id: string;
  email: string;
  pendingEmail: string | null;
  firstName: string;
  lastName: string | null;
  displayName: string | null;
  username?: string | null;
  bio: string | null;
  learnerLevel: LearnerLevel | null;
  courseProgram: string | null;
  studyGoal?: string | null;
  focusSubjects?: string[] | null;
  schoolName: string | null;
  publicProfileVisible: boolean;
  countryCode: string | null;
  profileType: ProfileType | null;
  examDate: string | null;
  reviewDays: ReviewDay[];
  reviewCommitmentOutstanding: boolean;
  engagementMode: EngagementMode;
  inactivityRemindersEnabled: boolean;
  weakConceptRemindersEnabled: boolean;
  weeklySummaryRemindersEnabled: boolean;
  dueConceptsDigestRemindersEnabled: boolean;
  knowledgeImpactDigestRemindersEnabled: boolean;
  marketingEmailsEnabled: boolean;
  mobileTabBarEnabled: boolean;
  themePreference?: ThemePreference | null;
  emailVerifiedAt: string | null;
  onboardingCompletedAt: string | null;
  productOnboardingCompletedAt: string | null;
  primaryCollectionId: string | null;
  studyDaysPerWeek: number | null;
  studyPackCount: number;
  role: UserRole;
  status: "ACTIVE" | "SUSPENDED" | "PENDING_DELETION";
  planType: PlanType;
  subscription: SubscriptionPlanStatusResponse;
};

export type FeedbackPromptContextResponse = {
  returningAfterInactivity: boolean;
  hasCompletedQuizSession: boolean;
};

export type SignInMethodsResponse = {
  email: string;
  passwordEnabled: boolean;
  googleConnected: boolean;
  googleEmail: string | null;
};

export type UpdateEngagementModeRequest = {
  engagementMode: EngagementMode;
};

export type UpdateEmailPreferencesRequest = {
  inactivityRemindersEnabled: boolean;
  weakConceptRemindersEnabled: boolean;
  weeklySummaryRemindersEnabled: boolean;
  dueConceptsDigestRemindersEnabled: boolean;
  knowledgeImpactDigestRemindersEnabled: boolean;
  marketingEmailsEnabled: boolean;
  reviewDays: ReviewDay[];
};

export type ReviewDay =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

export type UpdateReviewCommitmentRequest = {
  examDate: string | null;
  reviewDays: ReviewDay[];
};

export type UpdateMobileTabBarPreferenceRequest = {
  mobileTabBarEnabled: boolean;
};

export type UpdateThemePreferenceRequest = {
  themePreference: ThemePreference;
};

export type UpdateUserProfileRequest = {
  firstName?: string;
  lastName?: string;
  displayName?: string;
  username?: string;
  bio?: string;
  learnerLevel?: LearnerLevel | null;
  courseProgram?: string;
  schoolName?: string;
  email?: string;
};

export type UpdatePublicProfileVisibilityRequest = {
  publicProfileVisible: boolean;
};

export type CancelPremiumSubscriptionRequest = {
  reason?: SubscriptionCancellationReason | null;
  feedback?: string | null;
};

export type StudyEngagementResponse = {
  engagementMode: EngagementMode;
  currentStreak: number;
  longestStreak: number;
  studyDaysThisWeek: number;
};

export type MasterySnapshotResponse = {
  averageRecentScore: number | null;
  bestRecentScore: number | null;
  studyPacksReviewed: number;
};

export type OnboardingProfileTypeRequest = {
  profileType: ProfileType;
};

export type CompleteOnboardingRequest = {
  profileType: ProfileType;
  examDate: string | null;
};

export type SimpleMessageResponse = {
  message: string;
};

export type LinkedLearnerSide = "SUPPORTER" | "LEARNER";
export type LinkedLearnerStatus = "PENDING" | "ACCEPTED" | "REVOKED" | "EXPIRED";

export type LinkedLearnerResponse = {
  id: string;
  callerRole: LinkedLearnerSide;
  initiatedBy: LinkedLearnerSide;
  incomingInvitation: boolean;
  counterpartyDisplayName: string;
  counterpartyEmail: string | null;
  status: LinkedLearnerStatus;
  createdAt: string;
  acceptedAt: string | null;
  revokedAt: string | null;
  /**
   * When an unconfirmed request lapses, or null when it cannot lapse.
   * ⚠️ Null means "not on the expiry clock" — acceptance clears it and a consent pause leaves it
   * clear — so render its absence as no deadline, never as a missing value.
   */
  expiresAt: string | null;
  birthYearRequired: boolean;
  guardianConsentRequired: boolean;
  guardianConsentRecorded: boolean;
  activitySharedByMe: boolean;
  activitySharedWithMe: boolean;
  progressSharedByMe: boolean;
  progressSharedWithMe: boolean;
};

export type LinkedLearnerActivityResponse = {
  displayName: string;
  engagementMode: "FOCUSED" | "CONSISTENCY" | "STREAK";
  currentStreak: number;
  longestStreak: number;
  studyDaysThisWeek: number;
};

export type LinkedLearnerBirthYearCorrectionPreviewResponse = {
  affectedConnectionCount: number;
};

export type LinkedLearnerProgressResponse = {
  relationshipId: string;
  learnerDisplayName: string;
  quizPerformance: MasterySnapshotResponse;
  readiness: {
    totalConcepts: number;
    masteredConcepts: number;
    dueConcepts: number;
    notStartedConcepts: number;
    readinessPercentage: number;
  };
  collectionProgress: {
    collectionCount: number;
    totalItems: number;
    readyItems: number;
    practicedItems: number;
  };
  hasActivity: boolean;
};

export type UnsubscribeCategory =
  | "MARKETING"
  | "WEEKLY_SUMMARY"
  | "STUDY_REMINDERS"
  | "WEAK_CONCEPT";

export type EmailUnsubscribeResponse = {
  category: UnsubscribeCategory;
  displayName: string;
  message: string;
};

export const ACCOUNT_PENDING_DELETION_CODE = "ACCOUNT_PENDING_DELETION";

export type NeedsTextConfirmationResponse = {
  status: "needs_text_confirmation";
  id: string;
  extractedText: string;
  meta: {
    ocrConfidence: number | null;
    latencyMs: number | null;
  };
};

export type StudyPackApiResponse = StudyPackResponse | NeedsTextConfirmationResponse;
export type QuickReviewActivityType =
  | "STARTED_QUICK_REVIEW"
  | "COMPLETED_QUICK_REVIEW"
  | "COMPLETED_ADAPTIVE_QUIZ";

export type QuickReviewSessionStartResponse = {
  sessionId: string | null;
  status: "IN_PROGRESS" | "COMPLETED" | "FORFEITED" | null;
  currentQuestionIndex: number;
  currentRound: "INITIAL" | "RETRY" | null;
  retryCount: number;
  sessionState: Record<string, unknown> | null;
};

export type QuickReviewSessionCompleteRequest = {
  correctAnswers: number;
  totalQuestions: number;
  retryCount: number;
  durationSeconds?: number;
  sessionMetadata?: {
    weakConcepts?: string[];
    [key: string]: unknown;
  };
};

export type QuickReviewConfidenceLevel = "HIGH" | "MEDIUM" | "LOW";

export type QuickReviewSessionProgressRequest = {
  currentQuestionIndex: number;
  currentRound: "INITIAL" | "RETRY";
  retryCount: number;
  sessionState?: Record<string, unknown>;
};

export type QuickReviewSessionSummaryResponse = {
  id: string;
  studyPackId: string;
  status?: "IN_PROGRESS" | "COMPLETED" | "FORFEITED";
  currentQuestionIndex?: number;
  currentRound?: "INITIAL" | "RETRY";
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  retryCount: number;
  durationSeconds: number | null;
  confidenceLevel?: QuickReviewConfidenceLevel | null;
  weakConcepts?: string[];
  sessionState?: Record<string, unknown> | null;
  createdAt: string;
  completedAt: string | null;
  isFirstCompletedQuiz?: boolean;
  isFirstCompletedSessionEver?: boolean;
  isSecondCompletedSessionEver?: boolean;
  twiceMissedConcepts?: string[];
};

export type QuickReviewPerformanceSummaryResponse = {
  bestScorePercentage: number | null;
  attempts: number;
  lastScorePercentage: number | null;
  lastReviewedAt: string | null;
};

export type NoteQuickReviewLastReviewedResponse = {
  noteId: string;
  lastReviewedAt: string | null;
};

export type QuickReviewIncorrectQuestionInput = {
  question: string;
  correctAnswer: string;
  explanation: string;
};

export type QuickReviewStudyTipRequest = {
  incorrectQuestions: QuickReviewIncorrectQuestionInput[];
};

export type QuickReviewStudyTipResponse = {
  studyTip: string | null;
};

export type QuizSessionStatus = "GENERATING" | "FAILED" | "IN_PROGRESS" | "PAUSED" | "COMPLETED" | "FORFEITED";

export type AdaptiveConceptSelectionReason = "DUE" | "WEAK" | "BOTH";

/**
 * One focus concept, carried WITH the pack it came from.
 *
 * Two packs in a plan can be weak on the same concept string, and they stay two entries: concept
 * identity is scoped per Study Pack, so merging them by name would assert a cross-pack identity the
 * product does not have. This replaces the old parallel `weakConcepts` / `conceptSelectionReasons`
 * arrays, which could not express that at all.
 */
export type AdaptivePracticeFocusConcept = {
  concept: string;
  sourceStudyPackId: string;
  sourceTitle: string;
  selectionReason: AdaptiveConceptSelectionReason | null;
};

export type QuickReviewAdaptiveQuizResponse = {
  sessionId: string | null;
  status: QuizSessionStatus | null;
  /** Null for a collection-anchored session or when the server declined to start one. */
  studyPackId: string | null;
  /** Null for a collection-anchored session. Use sessionId for its enter/resume route. */
  noteId: string | null;
  title: string;
  focusConcepts: AdaptivePracticeFocusConcept[];
  quiz: QuizItem[];
  message: string;
};

export type AdaptivePracticeCompleteRequest = {
  correctAnswers: number;
  totalQuestions: number;
  durationSeconds?: number;
  correctConceptNames?: string[];
  /**
   * The learner's answers, keyed by ABSOLUTE index in the session's quiz array.
   *
   * ⚠️ These are what let the server attribute ConceptHealth PER SOURCE PACK. Adaptive Practice has
   * no progress endpoint, so nothing persists selections into session state during the session --
   * if the client does not send them here, the server's per-source breakdown is empty and it falls
   * back to attributing everything to the anchor pack and recording NO MISSES at all. That fallback
   * is correct for a single-note session and wrong for a plan-scoped one.
   */
  selectedChoices?: Record<number, number>;
  selectedMultiChoices?: Record<number, number[]>;
};

export type AdaptivePracticeCompleteResponse = SimpleMessageResponse & {
  isFirstCompletedSessionEver?: boolean;
  isSecondCompletedSessionEver?: boolean;
  twiceMissedConcepts?: string[];
};

export type ConceptHealthEntry = {
  concept: string;
  readinessStatus?: "MASTERED" | "DUE" | "NOT_STARTED";
  lastCorrectAt: string | null;
  lastIncorrectAt: string | null;
  isStruggling: boolean;
  isDue: boolean;
  daysSinceReview: number | null;
};

export type ChallengeQuizMode = "challenge" | "board_exam";
export type QuizSessionMode = "QUICK_REVIEW" | "CHALLENGE" | "ADAPTIVE" | "LONG_EXAM";

export type QuizSessionHistoryMode =
  | QuizSessionMode
  | "BOARD_EXAM"
  | "INTERVIEW_PRACTICE";

export type NotePerformanceSummaryResponse = {
  noteId: string;
  noteTitle: string | null;
  bestScore: number;
  averageScore: number | null;
  attemptCount: number;
  lastAttemptedAt: string;
  bestSessionId: string;
  bestSessionMode: Extract<QuizSessionMode, "QUICK_REVIEW" | "CHALLENGE">;
};

/**
 * LEARNING — session is scored and persisted as a performance record (default).
 * PREVIEW  — quiz runs normally but the backend does not record a scored session.
 *            Default for Creator mode (Teacher) so reviewing quiz material does not
 *            pollute performance history. The user can explicitly choose LEARNING to
 *            take the quiz as a learner.
 */
export type QuizStartSessionMode = "LEARNING" | "PREVIEW";

export type ChallengeQuizStartRequest = {
  mode?: ChallengeQuizMode;
  sessionMode?: QuizStartSessionMode;
  additionalStudyPackIds?: string[];
  /**
   * The Study Plan these sources came from, when the Board Exam was launched from one.
   *
   * ⚠️ A CLAIM the server re-verifies (ownership plus per-source membership). Sending it is what lets a
   * mixed-subject plan selection be accepted; it is never a way to switch the same-subject rule off.
   */
  sourceCollectionId?: string;
};

export type ChallengeQuizStartResponse = {
  sessionId: string | null;
  status: QuizSessionStatus | null;
  studyPackId: string;
  title: string;
  totalQuestions: number;
  timeLimitSeconds: number;
  usedThisMonth: number;
  monthlyLimit: number;
  boardExamUsedThisMonth: number;
  boardExamMonthlyLimit: number;
  mode: ChallengeQuizMode;
  selectedDifficulty: "easy" | "medium" | "hard" | "mixed";
  quiz: QuizItem[];
  currentQuestionIndex: number;
  sessionState: Record<string, unknown> | null;
  sourceNoteRefs?: LongExamSourceNoteRef[] | null;
  /** Server-derived so the browser never re-implements plan/level source-cap policy. */
  maxSourceNotes: number;
};

export type ChallengeQuizProgressRequest = {
  currentQuestionIndex: number;
  sessionState?: Record<string, unknown>;
};

export type ChallengeQuizCompleteRequest = {
  correctAnswers: number;
  totalQuestions: number;
  durationSeconds: number;
};

export type ChallengeQuizSessionResponse = {
  sessionId: string;
  studyPackId: string;
  status: QuizSessionStatus;
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  performanceLevel: "Excellent" | "Good" | "Fair" | "Needs Improvement";
  conceptBreakdown: {
    concept: string;
    correctAnswers: number;
    totalQuestions: number;
    accuracyPercentage: number;
  }[];
  weakConcepts: string[];
  durationSeconds: number | null;
  createdAt: string;
  completedAt: string | null;
  isFirstCompletedSessionEver?: boolean;
  isSecondCompletedSessionEver?: boolean;
  twiceMissedConcepts?: string[];
};

export type GenerateMoreChallengeQuizResponse = {
  newQuestions: QuizItem[];
  totalQuestions: number;
  timeLimitSeconds: number;
  timerStartedAtEpochSeconds: number;
};

export type ChallengeQuizSessionSummaryResponse = {
  sessionId: string;
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  performanceLevel: "Excellent" | "Good" | "Fair" | "Needs Improvement";
  conceptBreakdown: {
    concept: string;
    correctAnswers: number;
    totalQuestions: number;
    accuracyPercentage: number;
  }[];
  weakConcepts: string[];
  createdAt: string;
  completedAt: string | null;
};

export type RecentQuizSessionHistoryResponse = {
  sessionId: string;
  sessionMode: QuizSessionHistoryMode;
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  retryCount: number;
  performanceLevel: string | null;
  weakConcepts: string[];
  participatingNoteCount: number;
  createdAt: string;
  completedAt: string | null;
};

export type QuizSessionReviewResponse = {
  sessionId: string;
  studyPackId: string;
  sessionMode: QuizSessionHistoryMode;
  status: QuizSessionStatus;
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  retryCount: number;
  durationSeconds: number | null;
  weakConcepts: string[];
  conceptBreakdown: {
    concept: string;
    correctAnswers: number;
    totalQuestions: number;
    accuracyPercentage: number;
  }[];
  quiz: QuizItem[];
  selectedChoices: Record<string, number>;
  selectedMultiChoices?: Record<string, number[]>;
  selectedIdentificationAnswers?: Record<string, string>;
  selectedEnumerationAnswers?: Record<string, string[]>;
  createdAt: string;
  completedAt: string | null;
};

export type LongExamStartResponse = {
  sessionId: string | null;
  status: QuizSessionStatus | null;
  quiz: QuizItem[];
  totalQuestions: number;
  difficulty: string | null;
  canResume: boolean;
  timeLimitSeconds: number;
  timerStartedAtEpochSeconds: number;
  sourceNoteRefs: LongExamSourceNoteRef[];
  usedThisMonth: number;
  monthlyLimit: number;
  /**
   * Most sources this learner may combine, INCLUDING the primary note.
   *
   * ⚠️ Server-derived. Do NOT re-derive it here: it is floor(questionCount / 3) and questionCount comes
   * from the learner's LEVEL via backend config, so a client-side copy of that mapping is guaranteed drift.
   */
  maxSourceNotes: number;
};

export type LongExamSessionResponse = {
  sessionId: string;
  status: QuizSessionStatus;
  quiz: QuizItem[];
  selectedChoices: Record<string, number>;
  selectedMultiChoices?: Record<string, number[]>;
  selectedIdentificationAnswers?: Record<string, string>;
  currentQuestionIndex: number;
  totalQuestions: number;
  difficulty: string | null;
  paused: boolean;
  timeLimitSeconds: number;
  timerStartedAtEpochSeconds: number;
  sourceNoteRefs: LongExamSourceNoteRef[];
};

export type LongExamSourceNoteRef = {
  studyPackId: string;
  noteId: string;
  noteTitle: string;
  questionCount: number;
};

export type LongExamSourceNote = {
  noteId: string;
  noteTitle: string;
};

export type LongExamMasteryReportResponse = {
  sessionId: string;
  totalQuestions: number;
  answeredQuestions: number;
  scorePercentage: number;
  domainBreakdown: Array<{
    domain: string;
    totalQuestions: number;
    correctAnswers: number;
    accuracyPercentage: number;
  }>;
  weakDomains: string[];
  performanceSummary: string;
  suggestedNextStep: string;
  sourceNotes: LongExamSourceNote[];
  shortExam: boolean;
  isFirstCompletedSessionEver?: boolean;
  isSecondCompletedSessionEver?: boolean;
};

export type InterviewPracticeStartResponse = {
  sessionId: string;
  status: QuizSessionStatus;
  noteId: string;
  studyPackId: string;
  questionCount: number;
  currentQuestionIndex: number;
  softTimerSeconds: number;
  question: QuizItem | null;
  sourceNoteRefs?: Array<{
    noteId: string;
    noteTitle: string | null;
    studyPackId: string;
    questionCount: number;
  }>;
};

export type InterviewPracticeAnswerResponse = {
  verdict: "STRONG" | "WORKABLE" | "RECONSIDER";
  rationale: string;
  followUp: string;
  nextQuestion: QuizItem | null;
};

export type InterviewReadinessReportResponse = {
  sessionId: string;
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  band: "READY" | "ALMOST_READY" | "NEEDS_PRACTICE";
  strengths: string[];
  gaps: Array<{
    concept: string;
    noteId: string | null;
  }>;
  talkingPoints: string[];
  pacingNotes: number[];
};

export type ChallengeQuizPerformanceSummaryResponse = {
  bestScorePercentage: number | null;
  attempts: number;
  lastScorePercentage: number | null;
  lastCompletedAt: string | null;
  latestPerformanceLevel: "Excellent" | "Good" | "Fair" | "Needs Improvement" | null;
  latestWeakConcepts: string[];
};

export type ShareLinkResponse = {
  token: string;
  shareUrl: string;
};

export type PublicShareResponse = {
  id: string;
  title: string;
  summary: string;
  keyConcepts: string[];
  quiz: QuizItem[];
};

export type ShareRemixResponse = {
  studyPackId: string;
  noteId: string | null;
};

export type BillingCheckoutSessionResponse = {
  checkoutUrl: string;
};

export type BillingCheckoutSessionRequest = {
  planType?: PaidPlanType | null;
  billingCycle?: BillingCycle | null;
  returnUrl?: string | null;
};

export type BillingUsageSummaryResponse = {
  planType: PlanType;
  studyPacksUsed: number;
  studyPacksLimit: number;
  challengeQuizUsed: number;
  challengeQuizLimit: number;
  adaptivePracticeUsed: number;
  adaptivePracticeLimit: number;
  interviewPracticeUsed: number;
  interviewPracticeLimit: number;
  longExamUsed: number;
  longExamLimit: number;
  boardExamUsed: number;
  boardExamLimit: number;
  adaptivePracticeAvailable: boolean;
  interviewPracticeAvailable: boolean;
};

export type BillingHistoryItemResponse = {
  id: string;
  date: string;
  description: string;
  amount: number;
  currency: string;
  status: "PENDING" | "SUCCESS" | "FAILED" | "REFUNDED";
  provider: "NONE" | "XENDIT";
  providerReferenceId: string;
};

export type BillingHistoryResponse = {
  currentPlan: PlanType;
  subscriptionStatus: "ACTIVE" | "CANCELED" | "EXPIRED" | null;
  billingType: BillingCycle | null;
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  cancelAtPeriodEnd: boolean;
  cancellationEffectiveAt: string | null;
  transactions: BillingHistoryItemResponse[];
};

export type UpdateStudyPackMetadataRequest = {
  title: string;
  subject?: string | null;
};

/**
 * PUT /notes/{id} is a FULL REPLACE, not a merge — `updateNote` sends this object verbatim and the
 * backend writes every field unconditionally, so an omitted key persists as null.
 *
 * `domainContext` and `learnerLevel` are therefore deliberately NOT optional. Two update call sites
 * on the note detail page omitted them and silently wiped both axes off any note they touched; making
 * the fields required turns that from a runtime data loss into a compile error at every present and
 * future caller. Same reasoning that removed `UpsertNoteRequest`'s silent-null convenience constructor
 * on the backend in v0.69.0 — an interface that hides a field from its callers will eventually lose it.
 * Pass an explicit `null` where the caller genuinely means "no value".
 */
export type UpsertNoteRequest = {
  title?: string | null;
  subject?: string | null;
  courseProgramIds?: string[];
  /** Free-text personal-note value. Curator requests use courseProgramIds instead. */
  courseProgramText?: string | null;
  domainContext: DomainContext | null;
  learnerLevel: LearnerLevel | null;
  tags?: string[];
  content: string;
};

export type GenerateNoteFromTopicResponse = {
  content: string;
};

export type NoteResponse = {
  id: string;
  title: string | null;
  subject: string | null;
  courseProgram?: string | null;
  domainContext: DomainContext | null;
  learnerLevel: LearnerLevel | null;
  tags: string[];
  content: string;
  visibility: NoteVisibility;
  createdAt: string;
  updatedAt: string;
  copiedFromNoteId: string | null;
  copiedFromUserId: string | null;
  copiedFromTitle: string | null;
  copiedFromPublic: boolean;
  copiedAt: string | null;
  studyPackId?: string | null;
  studyPackStatus?: NoteStudyPackStatus;
  summary: string | null;
  keyConcepts: string[];
  quiz: QuizItem[];
  quizMastered: boolean;
  quizMasteredAt: string | null;
  generatedQuiz: GeneratedQuizResponse | null;
  lastUsedTargetLearnerLevel: LearnerLevel | null;
  quizCount: number;
  quickReviewAvailable: boolean;
  challengeQuizAvailable: boolean;
  adaptivePracticeAvailable: boolean;
};

export type CourseProgramCatalogItem = {
  id: string;
  name: string;
  programFamilyId: string | null;
  programFamilyName: string | null;
};

export type CreateCourseProgramRequest = {
  name: string;
  programFamilyId?: string | null;
  examGoalSlug?: "ale" | "pnle" | "let" | "cpale" | null;
};

export type ApplicableProgram = {
  id: string;
  name: string;
};

export type NoteApplicableProgramsResponse = {
  programs: ApplicableProgram[];
  courseProgramShadowed: boolean;
  effectiveWritingDomain: string | null;
};

export type AdminNoteApplicableProgramsItem = {
  noteId: string;
  title: string | null;
  courseProgram: string | null;
  domainContext: DomainContext | null;
  applicablePrograms: ApplicableProgram[];
};

export type AdminNoteApplicableProgramsPage = {
  items: AdminNoteApplicableProgramsItem[];
  page: number;
  size: number;
  totalElements: number;
};

export type NoteStudyPackStatus = "DRAFT" | "GENERATING" | "FAILED" | "STUDY_PACK_READY";
export type NoteVisibility = "PRIVATE" | "PUBLIC";

export type NoteShareResponse = {
  relationshipId: string;
  granteeDisplayName: string;
  granteeEmail: string;
  createdAt: string;
};

export type SharedNoteListItemResponse = {
  noteId: string;
  title: string | null;
  subject: string | null;
  ownerDisplayName: string;
  studyPackReady: boolean;
  sharedAt: string;
};

export type SharedNotesPageResponse = {
  items: SharedNoteListItemResponse[];
  nextCursor: string | null;
  hasMore: boolean;
};

export type SharedNoteResponse = {
  id: string;
  title: string | null;
  content: string;
  subject: string | null;
  courseProgram: string | null;
  learnerLevel: LearnerLevel | null;
  tags: string[];
  status: string;
  ownerDisplayName: string;
  sharedAt: string;
  studyPackId: string | null;
  canCopy: boolean;
};

export type SharedStudyPackResponse = {
  id: string;
  noteId: string;
  title: string;
  summary: string;
  fullNotes: string | null;
  keyConcepts: string[];
  quiz: QuizItem[];
  ownerDisplayName: string;
};
export type SubjectSuggestionScope = "mine" | "public";
export type CourseProgramSuggestionScope = "mine" | "public";

export type NoteListItemResponse = {
  id: string;
  ownerUserId: string | null;
  title: string | null;
  courseProgram: string | null;
  applicablePrograms?: string[];
  subject: string | null;
  tags: string[];
  contentPreview: string;
  summaryPreview: string;
  visibility: NoteVisibility;
  studyPackId: string | null;
  studyPackStatus: NoteStudyPackStatus;
  quizCount: number | null;
  keyConceptCount: number | null;
  copyCount: number | null;
  likeCount: number | null;
  shareCount: number | null;
  viewCount: number | null;
  authorDisplayName: string;
  authorUsername?: string | null;
  isOfficialAuthor: boolean;
  isCurrentUser?: boolean;
  createdAt: string;
  updatedAt: string;
  lastSessionCompletedAt?: string | null;
  generatedQuizId?: string | null;
  generatedQuizGeneratedAt?: string | null;
  generatedQuizQuestionCount?: number | null;
  copiedFromNoteId?: string | null;
  copiedFromPublic?: boolean;
  likedByCurrentUser: boolean;
};

export type NoteStatusResponse = {
  id: string;
  studyPackStatus: string;
};

export type LibraryFilterParams = {
  search?: string;
  readiness?: string;
  courseProgram?: string;
  subject?: string;
  tags?: string[];
  visibility?: string;
  /** Review Set membership. Undefined means no collection filter, never "notes in no collection". */
  collectionId?: string;
};

export type LibraryPageParams = LibraryFilterParams & {
  sort?: string;
  page?: number;
  pageSize?: number;
};

export type NotesLibraryPageResponse = {
  items: NoteListItemResponse[];
  page: number;
  pageSize: number;
  totalMatching: number;
  hasMore: boolean;
};

export type NotesLibraryIdsResponse = {
  noteIds: string[];
  totalMatching: number;
  truncated: boolean;
};

export type SubjectFacetCount = {
  subject: string;
  count: number;
};

export type SubjectStatsResponse = {
  topSubjects: SubjectFacetCount[];
  otherSubjectsCount: number;
  total: number;
};

export type FacetCount = {
  value: string;
  count: number;
};

export type NotesLibraryFilterOptionsResponse = {
  subjects: FacetCount[];
  coursePrograms: FacetCount[];
  tags: FacetCount[];
};

export type PublicNoteListResponse = {
  items: NoteListItemResponse[];
  total: number;
  page?: number | null;
  pageSize?: number | null;
  totalMatching?: number | null;
  hasMore?: boolean | null;
};

export type PublicLibraryDiscoverySectionsResponse = {
  featured: NoteListItemResponse[];
  popular: NoteListItemResponse[];
  recent: NoteListItemResponse[];
};

export type SavedLibraryFilterState = {
  search?: string;
  subject?: string;
  courseProgram?: string;
  tags?: string[];
  status?: string;
  visibility?: string;
  sort?: string;
};

export type SavedLibraryFilterResponse = {
  id: string;
  name: string;
  filterState: SavedLibraryFilterState;
  createdAt: string;
};

export type NoteCollectionSummary = {
  id: string;
  title: string;
  description: string | null;
  visibility: "PRIVATE" | "PUBLIC";
  courseProgram: string | null;
  learnerLevel?: LearnerLevel | null;
  resolvedLearnerLevel?: LearnerLevel | null;
  sourcePlanId: string | null;
  parentCollectionId: string | null;
  itemCount: number;
  readyCount?: number | null;
  childCount: number;
  notesPracticed: number;
  createdAt: string;
  updatedAt: string;
};

export type NoteCollectionItem = {
  noteId: string;
  label: string | null;
  position: number;
  title: string | null;
  subject: string | null;
  courseProgram: string | null;
  studyPackStatus: NoteStudyPackStatus;
  generatedQuizId: string | null;
  lastSessionCompletedAt: string | null;
  dueConceptCount: number;
  dueConcepts: string[];
  updatedAt: string;
};

export type NoteCollectionProgress = {
  totalNotes: number;
  notesWithStudyPack: number;
  notesPracticed: number;
};

export type NoteCollectionDetail = {
  id: string;
  title: string;
  description: string | null;
  visibility: "PRIVATE" | "PUBLIC";
  courseProgram: string | null;
  learnerLevel?: LearnerLevel | null;
  resolvedLearnerLevel?: LearnerLevel | null;
  estimatedStudyHours: number | null;
  targetCompletionDate: string | null;
  companion: CompanionContent | null;
  sourcePlanId: string | null;
  parentCollectionId: string | null;
  childCount: number;
  readyCount?: number | null;
  createdAt: string;
  updatedAt: string;
  progress: NoteCollectionProgress;
  items: NoteCollectionItem[];
};

export type NoteConceptCountsResponse = {
  totalConceptCount: number;
  masteredConceptCount: number;
  dueConceptCount: number;
  notPracticedConceptCount: number;
};

export type AdoptStudyPlanResponse = {
  collectionId: string;
  copiedCount: number;
  skippedCount: number;
  alreadyAdopted: boolean;
};

export type AdoptGoalResponse = {
  goalCollectionId: string;
  adoptedSubjectCount: number;
  skippedSubjectCount: number;
  totalNotesCopied: number;
  totalNotesSkipped: number;
  alreadyAdopted: boolean;
};

export type ReviewSetUpdateChange = {
  type: "ADDED_NOTE" | "ADDED_SUBJECT_PLAN" | "RENAMED" | "REORDERED" | "RETIRED" | "MOVED" | "SKIPPED_NOT_PUBLIC";
  sourcePlanId: string;
  sourceNoteId: string | null;
  subjectTitle: string | null;
  noteTitle: string | null;
  previousValue: string | null;
  currentValue: string | null;
  applied: boolean;
};

export type ReviewSetUpdateResponse = {
  collectionId: string;
  sourceState: "CONNECTED" | "DETACHED";
  status: "UPDATES_AVAILABLE" | "ALREADY_UP_TO_DATE" | "UPDATED" | "PARTIALLY_UPDATED" | "DETACHED_FROM_SOURCE";
  additionsAvailable: number;
  notesAdded: number;
  subjectPlansAdded: number;
  skippedCount: number;
  changes: ReviewSetUpdateChange[];
};

export type SetCollectionItemOrderRequestItem = {
  noteId: string;
  label?: string | null;
};

export type MultiNoteQuizDocxExportRequest = {
  sections: Array<{
    title: string;
    questionRefs: Array<{
      noteId: string;
      questionIndex: number;
    }>;
  }>;
  includeAnswerKey: boolean;
  includeExplanations: boolean;
  headerOverride?: QuizDocxHeaderOverride | null;
  versionCount?: number | null;
};

export type QuizDocxHeaderOverride = {
  className?: string | null;
  includeDate?: boolean | null;
};

export type PublicNoteLikeResponse = {
  liked: boolean;
  likeCount: number;
};

export type PublicNoteDetailResponse = {
  id: string;
  ownerUserId: string | null;
  title: string | null;
  subject: string | null;
  coursePrograms?: string[];
  tags: string[];
  content: string;
  contentPreview: string;
  studyPackStatus: NoteStudyPackStatus;
  summary: string | null;
  keyConcepts: string[];
  quiz: QuizItem[];
  authorDisplayName: string;
  authorUsername?: string | null;
  isOfficialAuthor: boolean;
  isCurrentUser: boolean;
  updatedAt: string;
};

export type PublicProfileNoteResponse = {
  noteId: string;
  title: string | null;
  courseProgram: string | null;
  applicablePrograms: string[];
  subject: string | null;
  tags: string[];
  contentPreview: string;
  summaryPreview: string;
  copyCount: number;
  shareCount: number;
  viewCount: number;
  slug: string;
};

export type SubjectCountResponse = {
  subject: string;
  count: number;
};

export type PublicProfileResponse = {
  displayName: string;
  username?: string | null;
  bio: string | null;
  learnerLevel: LearnerLevel | null;
  courseProgram: string | null;
  profileType: ProfileType | null;
  isOfficial: boolean;
  publicProfileVisible: boolean;
  isCurrentUser: boolean;
  userId: string;
  publicNotesCount: number;
  totalCopies: number;
  totalShares: number;
  totalViews: number;
  totalProfileShares: number;
  notesBySubject: SubjectCountResponse[];
  totalPublicSubjectCount: number;
  publicNotes: PublicProfileNoteResponse[];
};

export type CreatorImpactNoteResponse = {
  noteId: string;
  title: string | null;
  distinctLearnersHelped: number;
  viewCount: number;
  copyCount: number;
};

export type CreatorImpactResponse = {
  distinctLearnersHelped: number;
  notes: CreatorImpactNoteResponse[];
};

type ApiErrorPayload = {
  error?: {
    code?: string;
    message?: string;
    details?: string;
    action?: string;
  };
};

export class ApiRequestError extends Error {
  readonly code: string | null;
  readonly action: string | null;
  readonly details: string | null;
  readonly status: number;

  constructor(message: string, options: { code?: string | null; action?: string | null; details?: string | null; status: number }) {
    super(message);
    this.name = "ApiRequestError";
    this.code = options.code ?? null;
    this.action = options.action ?? null;
    this.details = options.details ?? null;
    this.status = options.status;
  }
}

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";
export const OCR_DISABLED_ERROR_CODE = "OCR_DISABLED";

function buildUrl(path: string) {
  return `${API_BASE_URL}${path}`;
}

function buildAuthHeaders(contentType?: string): HeadersInit {
  const headers: Record<string, string> = {};
  if (contentType) {
    headers["Content-Type"] = contentType;
  }
  const accessToken = getAccessToken();
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  return headers;
}

export function isNeedsTextConfirmationResponse(
  payload: StudyPackApiResponse,
): payload is NeedsTextConfirmationResponse {
  return "status" in payload && payload.status === "needs_text_confirmation";
}

async function parseApiResponse<T>(
  response: Response,
  fallbackMessage = "Request failed. Please try again.",
): Promise<T> {
  if (response.ok) {
    return (await response.json()) as T;
  }

  let errorPayload: ApiErrorPayload | null = null;
  try {
    errorPayload = (await response.json()) as ApiErrorPayload;
  } catch {
    // Ignore JSON parse failures and use fallback below.
  }

  const message = errorPayload?.error?.message ?? fallbackMessage;
  throw new ApiRequestError(message, {
    code: errorPayload?.error?.code ?? null,
    action: errorPayload?.error?.action ?? null,
    details: errorPayload?.error?.details ?? null,
    status: response.status,
  });
}

async function throwApiRequestError(
  response: Response,
  fallbackMessage = "Request failed. Please try again.",
): Promise<never> {
  let errorPayload: ApiErrorPayload | null = null;
  try {
    errorPayload = (await response.json()) as ApiErrorPayload;
  } catch {
    // Ignore JSON parse failures and use fallback below.
  }

  const message = errorPayload?.error?.message ?? fallbackMessage;
  throw new ApiRequestError(message, {
    code: errorPayload?.error?.code ?? null,
    action: errorPayload?.error?.action ?? null,
    details: errorPayload?.error?.details ?? null,
    status: response.status,
  });
}

function extractDownloadFilename(contentDisposition: string | null, fallback: string): string {
  if (!contentDisposition) {
    return fallback;
  }
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1]);
  }
  const basicMatch = contentDisposition.match(/filename=\"?([^\";]+)\"?/i);
  return basicMatch?.[1] ?? fallback;
}

function triggerBlobDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function toAuthUser(payload: AuthResponse): AuthUser {
  return {
    id: payload.userId,
    email: payload.email,
    displayName: payload.displayName,
    profileType: payload.profileType,
    emailVerifiedAt: payload.emailVerifiedAt,
    onboardingCompletedAt: payload.onboardingCompletedAt,
    productOnboardingCompletedAt: payload.productOnboardingCompletedAt,
    themePreference: payload.themePreference,
    role: payload.role,
    planType: payload.planType,
    accessToken: payload.token,
    refreshToken: payload.refreshToken,
    accessTokenExpiresAt: payload.accessTokenExpiresAt,
    refreshTokenExpiresAt: payload.refreshTokenExpiresAt,
  };
}

function syncStoredAuthUserFromMe(me: MeResponse): void {
  const authUser = getAuthUser();
  if (authUser?.id !== me.id) {
    return;
  }

  patchAuthUser({
    email: me.pendingEmail ?? me.email,
    displayName: me.displayName,
    username: me.username,
    profileType: me.profileType,
    emailVerifiedAt: me.emailVerifiedAt,
    onboardingCompletedAt: me.onboardingCompletedAt,
    productOnboardingCompletedAt: me.productOnboardingCompletedAt,
    themePreference: me.themePreference,
    mobileTabBarEnabled: me.mobileTabBarEnabled,
  });
}

let refreshPromise: Promise<boolean> | null = null;

async function tryRefreshAccessToken(): Promise<boolean> {
  if (refreshPromise) {
    return refreshPromise;
  }
  refreshPromise = doRefreshAccessToken().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

async function doRefreshAccessToken(): Promise<boolean> {
  // Refresh is shared by product and analytics calls. The caller owns its failure policy: product
  // requests may expire the session, while analytics must leave a working learner session alone.
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return false;
  }

  const response = await fetch(buildUrl("/auth/refresh"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ refreshToken }),
  });

  if (!response.ok) {
    return false;
  }

  const payload = (await response.json()) as AuthResponse;
  setAuthUser(toAuthUser(payload));
  return true;
}

async function fetchWithAuth(path: string, init: RequestInit, retry = true): Promise<Response> {
  const response = await fetch(buildUrl(path), init);
  if (response.status !== 401 || !retry) {
    if (response.status === 401) {
      handleUnauthorizedSession();
    }
    return response;
  }
  const refreshed = await tryRefreshAccessToken();
  if (!refreshed) {
    clearAuthUser();
    handleUnauthorizedSession();
    return response;
  }
  const updatedHeaders = new Headers(init.headers ?? {});
  const token = getAccessToken();
  if (token) {
    updatedHeaders.set("Authorization", `Bearer ${token}`);
  }
  const retriedResponse = await fetch(buildUrl(path), {
    ...init,
    headers: updatedHeaders,
  });
  if (retriedResponse.status === 401) {
    handleUnauthorizedSession();
  }
  return retriedResponse;
}

export async function signup(request: SignupRequest): Promise<AuthUser> {
  const response = await fetch(buildUrl("/auth/signup"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
  const payload = await parseApiResponse<AuthResponse>(response, "Could not create account. Please try again.");
  return toAuthUser(payload);
}

export async function login(request: LoginRequest): Promise<AuthUser> {
  const response = await fetch(buildUrl("/auth/login"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
  const payload = await parseApiResponse<AuthResponse>(response, "Could not log in. Please try again.");
  return toAuthUser(payload);
}

export async function loginWithGoogle(request: GoogleAuthRequest): Promise<AuthUser> {
  const response = await fetch(buildUrl("/auth/google"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
  const payload = await parseApiResponse<AuthResponse>(response, "Could not continue with Google. Please try again.");
  return toAuthUser(payload);
}

export async function reactivateAccount(request: ReactivateAccountRequest): Promise<AuthUser> {
  const response = await fetch(buildUrl("/auth/account/reactivate"), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
  const payload = await parseApiResponse<AuthResponse>(response, "Could not reactivate account. Please try again.");
  return toAuthUser(payload);
}

export async function getSignInMethods(): Promise<SignInMethodsResponse> {
  const response = await fetchWithAuth(
    "/auth/sign-in-methods",
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${getAccessToken()}`,
      },
    },
    true,
  );
  return parseApiResponse<SignInMethodsResponse>(response, "Could not load sign-in methods.");
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  const response = await fetchWithAuth(
    "/auth/change-password",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${getAccessToken()}`,
      },
      body: JSON.stringify({ currentPassword, newPassword }),
    },
    true,
  );
  await parseApiResponse<{ message: string }>(response, "Could not change password. Please try again.");
}

export async function connectGoogle(request: GoogleConnectRequest): Promise<SignInMethodsResponse> {
  const response = await fetchWithAuth(
    "/auth/google/connect",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${getAccessToken()}`,
      },
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<SignInMethodsResponse>(response, "Could not connect Google. Please try again.");
}

export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();
  beginManualLogoutRedirect();
  try {
    if (!refreshToken) {
      return;
    }
    await fetch(buildUrl("/auth/logout"), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ refreshToken }),
    });
  } finally {
    clearAuthUser();
  }
}

export async function deleteAccount(confirmation: string): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    "/auth/account/delete",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ confirmation }),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not delete account. Please try again.");
}

export async function downloadMyData(): Promise<{ filename: string }> {
  const response = await fetchWithAuth(
    "/auth/account/export",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (!response.ok) {
    return throwApiRequestError(response, "Could not download your data. Please try again.");
  }
  const blob = await response.blob();
  const fallbackFilename = `notelib-export-${new Date().toISOString().slice(0, 10)}.json`;
  const filename = extractDownloadFilename(response.headers.get("content-disposition"), fallbackFilename);
  triggerBlobDownload(blob, filename);
  return { filename };
}

export async function unsubscribeEmail(token: string): Promise<EmailUnsubscribeResponse> {
  const form = new URLSearchParams();
  form.set("token", token);
  const response = await fetch(buildUrl("/email/unsubscribe"), {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: form.toString(),
  });
  return parseApiResponse<EmailUnsubscribeResponse>(response, "Could not unsubscribe from this email.");
}

export async function getMe(): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/me",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not load profile. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updateUserProfile(request: UpdateUserProfileRequest): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/profile",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update profile. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updateProfileLearnerLevel(level: LearnerLevel): Promise<MeResponse> {
  const current = await getMe();
  return updateUserProfile({
    firstName: current.firstName,
    lastName: current.lastName ?? "",
    displayName: current.displayName ?? "",
    username: current.username ?? "",
    bio: current.bio ?? "",
    learnerLevel: level,
    courseProgram: current.courseProgram ?? "",
    schoolName: current.schoolName ?? "",
    email: current.email,
  });
}

/**
 * Writes the two learning-context fields through the narrow `PUT /users/profile/learning-context`.
 *
 * This used to `getMe()` and then full-replace through `PUT /users/profile`, which meant resending
 * firstName, email and username on every call — a lost update against any concurrent profile edit, and a
 * `pendingEmail` clobber. It also could not run before those identity fields existed, which is why
 * onboarding deferred it to the very last step and lost the values outright when it failed.
 *
 * The caller is expected to await this and surface a failure. It must not be fire-and-forget.
 */
export async function updateLearningProfileContext(
  learnerLevel: LearnerLevel | null,
  courseProgram: string | null,
): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/profile/learning-context",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({
        learnerLevel,
        courseProgram: courseProgram?.trim() || null,
      }),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(
    response,
    "Could not save your learner level and course / program. Please try again.",
  );
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updateExamDate(examDate: string | null): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/profile/exam-date",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ examDate: examDate ?? null }),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update exam date. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updateStudyDaysPerWeek(studyDaysPerWeek: number | null): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/profile/study-days-per-week",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ studyDaysPerWeek: studyDaysPerWeek ?? null }),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update study intensity. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function setStudyGoal(studyGoal: string | null): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/profile/goal",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ studyGoal: studyGoal ?? null }),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update study goal. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function setFocusSubjects(subjects: string[]): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/profile/focus-subjects",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ subjects }),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update focus subjects. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updatePublicProfileVisibility(
  request: UpdatePublicProfileVisibilityRequest,
): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/profile/public-visibility",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update public profile visibility.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function getAdminDashboardSummary(): Promise<AdminDashboardSummaryResponse> {
  const response = await fetchWithAuth(
    "/admin/dashboard/summary",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminDashboardSummaryResponse>(response, "Could not load admin summary.");
}

export async function getAdminDashboardTopContent(): Promise<AdminDashboardTopContentResponse> {
  const response = await fetchWithAuth(
    "/admin/dashboard/top-content",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminDashboardTopContentResponse>(response, "Could not load admin content metrics.");
}

export async function getAdminDashboardRecentEvents(): Promise<AdminDashboardRecentEventsResponse> {
  const response = await fetchWithAuth(
    "/admin/dashboard/recent-events",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminDashboardRecentEventsResponse>(response, "Could not load recent admin events.");
}

export async function getAdminOrganicLandings(): Promise<AdminOrganicLandingsResponse> {
  const response = await fetchWithAuth(
    "/admin/dashboard/organic-landings",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminOrganicLandingsResponse>(response, "Could not load organic landing metrics.");
}

export async function getAdminNoteApplicablePrograms(
  page = 0,
  size = 25,
): Promise<AdminNoteApplicableProgramsPage> {
  const response = await fetchWithAuth(
    `/admin/notes/applicable-programs?page=${encodeURIComponent(String(page))}&size=${encodeURIComponent(String(size))}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminNoteApplicableProgramsPage>(
    response,
    "Could not load note Applicable Programs.",
  );
}

export async function getAdminFunnelMetrics(days?: number): Promise<AdminFunnelMetricsResponse> {
  const query = typeof days === "number" && days > 0 ? `?days=${encodeURIComponent(String(days))}` : "";
  const response = await fetchWithAuth(
    `/admin/funnel/metrics${query}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminFunnelMetricsResponse>(response, "Could not load funnel metrics.");
}

export type OfficialStudyPlanWishlistStatusResponse = {
  requested: boolean;
};

export async function getOfficialStudyPlanWishlistStatus(
  courseProgram: string,
): Promise<OfficialStudyPlanWishlistStatusResponse> {
  const query = new URLSearchParams({ courseProgram }).toString();
  const response = await fetchWithAuth(
    `/official-study-plan-wishlist/status?${query}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<OfficialStudyPlanWishlistStatusResponse>(
    response,
    "Could not check your Official Study Plan request.",
  );
}

export async function requestOfficialStudyPlan(
  courseProgram: string,
): Promise<OfficialStudyPlanWishlistStatusResponse> {
  const response = await fetchWithAuth(
    "/official-study-plan-wishlist",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ courseProgram }),
    },
    true,
  );
  return parseApiResponse<OfficialStudyPlanWishlistStatusResponse>(
    response,
    "Could not record your request. Please try again.",
  );
}

export async function regenerateAdminSummaries(): Promise<AdminRegenerateSummariesResponse> {
  const response = await fetchWithAuth(
    "/admin/study-packs/regenerate-summaries",
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminRegenerateSummariesResponse>(response, "Could not queue regeneration.");
}

export async function seedOfficialChallengeQuizTemplates(): Promise<AdminSeedOfficialChallengeQuizTemplatesResponse> {
  const response = await fetchWithAuth(
    "/admin/study-packs/seed-official-challenge-quiz-templates",
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminSeedOfficialChallengeQuizTemplatesResponse>(
    response,
    "Could not queue Official Challenge Quiz template seeding.",
  );
}

export async function getAdminRegenerationStatus(): Promise<AdminRegenerationStatusResponse> {
  const response = await fetchWithAuth(
    "/admin/study-packs/regeneration-status",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AdminRegenerationStatusResponse>(response, "Could not fetch regeneration status.");
}

export type CampaignStatusResponse = {
  eligibleCount: number;
  totalSent: number;
  lastSentAt: string | null;
};

export type ReEngagementSendResult = {
  sent: number;
  skipped: number;
};

export async function getReEngagementCampaignStatus(): Promise<CampaignStatusResponse> {
  const response = await fetchWithAuth(
    "/admin/campaigns/re-engagement/status",
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<CampaignStatusResponse>(response, "Could not load campaign status.");
}

export async function sendReEngagementCampaign(): Promise<ReEngagementSendResult> {
  const response = await fetchWithAuth(
    "/admin/campaigns/re-engagement/send",
    { method: "POST", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<ReEngagementSendResult>(response, "Could not send campaign.");
}

export async function issueAdminRefund(transactionId: string): Promise<AdminIssueRefundResponse> {
  const response = await fetchWithAuth(
    "/admin/billing/refund",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ transactionId }),
    },
    true,
  );
  return parseApiResponse<AdminIssueRefundResponse>(response, "Could not issue refund.");
}

export async function submitFeedback(
  request: SubmitFeedbackRequest,
  pageUrl: string | null,
): Promise<SimpleMessageResponse & { feedbackId: string | null }> {
  const headers = new Headers(buildAuthHeaders("application/json"));
  if (pageUrl && pageUrl.trim().length > 0) {
    headers.set("X-Page-Url", pageUrl);
  }

  const response = await fetchWithAuth(
    "/feedback",
    {
      method: "POST",
      headers,
      body: JSON.stringify(request),
    },
    true,
  );
  const payload = await parseApiResponse<SimpleMessageResponse>(response, "Could not send feedback. Please try again.");
  return {
    ...payload,
    feedbackId: response.headers.get("X-Feedback-Id"),
  };
}

export async function uploadFeedbackImage(feedbackId: string, image: File): Promise<void> {
  const body = new FormData();
  body.set("image", image);
  const response = await fetchWithAuth(
    `/feedback/${feedbackId}/image`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
      body,
    },
    true,
  );
  if (!response.ok) {
    await throwApiRequestError(response, "Could not attach screenshot.");
  }
}

export async function getAdminFeedbackImage(feedbackId: string): Promise<Blob | null> {
  const response = await fetchWithAuth(
    `/admin/feedback/${feedbackId}/image`,
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    await throwApiRequestError(response, "Could not load feedback screenshot.");
  }
  return response.blob();
}

export async function getFeedbackPromptContext(): Promise<FeedbackPromptContextResponse> {
  const response = await fetchWithAuth(
    "/feedback/context",
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<FeedbackPromptContextResponse>(response, "Could not load feedback prompt context.");
}

export async function completeOnboardingProfileType(
  request: OnboardingProfileTypeRequest,
): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/onboarding/profile-type",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not complete onboarding. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function completeOnboarding(
  request: CompleteOnboardingRequest,
): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/onboarding",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not complete onboarding. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function completeProductOnboarding(
  skipped = false,
): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/product-onboarding/complete",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ skipped }),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not complete onboarding. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function requestEmailVerification(): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    "/auth/resend-verification",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not send verification email. Please try again.");
}

export async function verifyEmailToken(token: string): Promise<SimpleMessageResponse> {
  const query = new URLSearchParams({ token }).toString();
  const response = await fetch(buildUrl(`/auth/verify-email?${query}`), {
    method: "GET",
  });
  return parseApiResponse<SimpleMessageResponse>(response, "Could not verify email. Please try again.");
}

export async function trackAnalyticsEvent(request: AnalyticsEventRequest): Promise<void> {
  try {
    const path = "/analytics/events";
    const init: RequestInit = {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({
        eventType: request.eventType,
        entityId: request.entityId ?? null,
        metadata: request.metadata ?? {},
      }),
      keepalive: true,
    };
    const response = await fetch(buildUrl(path), init);
    // A hidden document may already be unloading. Keep the original keepalive request best-effort;
    // starting a refresh here cannot reliably finish before the page is gone.
    if (response.status !== 401 || globalThis.document?.visibilityState === "hidden") {
      return;
    }

    const refreshed = await tryRefreshAccessToken();
    if (!refreshed) {
      return;
    }

    const updatedHeaders = new Headers(init.headers ?? {});
    const token = getAccessToken();
    if (token) {
      updatedHeaders.set("Authorization", `Bearer ${token}`);
    }
    await fetch(buildUrl(path), {
      ...init,
      headers: updatedHeaders,
    });
  } catch {
    // Analytics must never interrupt the main product flow.
  }
}

export async function confirmEmailVerification(token: string): Promise<SimpleMessageResponse> {
  return verifyEmailToken(token);
}

export async function requestEmailVerificationLegacy(): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    "/auth/verify-email/request",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not send verification email. Please try again.");
}

export async function forgotPassword(email: string): Promise<SimpleMessageResponse> {
  const response = await fetch(buildUrl("/auth/forgot-password"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email }),
  });
  return parseApiResponse<SimpleMessageResponse>(response, "Could not send reset email. Please try again.");
}

export async function resetPassword(token: string, newPassword: string): Promise<SimpleMessageResponse> {
  const response = await fetch(buildUrl("/auth/reset-password"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, newPassword }),
  });
  return parseApiResponse<SimpleMessageResponse>(response, "Could not reset password. Please try again.");
}

export async function updateEngagementMode(request: UpdateEngagementModeRequest): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/preferences/engagement-mode",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update engagement mode. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updateEmailPreferences(request: UpdateEmailPreferencesRequest): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/preferences/email-preferences",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update email preferences. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updateReviewCommitment(request: UpdateReviewCommitmentRequest): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/users/review-commitment",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not save your review plan. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updateMobileTabBarPreference(request: UpdateMobileTabBarPreferenceRequest): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/preferences/mobile-tab-bar",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update mobile navigation preference. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function updateThemePreference(request: UpdateThemePreferenceRequest): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/auth/preferences/theme",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const me = await parseApiResponse<MeResponse>(response, "Could not update theme preference. Please try again.");
  syncStoredAuthUserFromMe(me);
  return me;
}

export async function createStudyPackFromText(notesText: string): Promise<StudyPackResponse> {
  const response = await fetchWithAuth(
    "/study-packs",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ notesText }),
    },
    true,
  );

  const payload = await parseApiResponse<StudyPackApiResponse>(
    response,
    "We could not generate your study pack right now. Please try again.",
  );
  if (isNeedsTextConfirmationResponse(payload)) {
    throw new Error("Unexpected OCR confirmation response for text input.");
  }
  return payload;
}

const AUTO_APPLY_METADATA_QUERY_PARAM = "autoApplyMetadata";

export async function createStudyPackFromNote(
  noteId: string,
  options: { autoApplyMetadata?: boolean } = {},
): Promise<NoteResponse> {
  const query = options.autoApplyMetadata ? `?${AUTO_APPLY_METADATA_QUERY_PARAM}=true` : "";
  const response = await fetchWithAuth(
    `/notes/${noteId}/generate${query}`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteResponse>(
    response,
    "We could not generate your study pack right now. Please try again.",
  );
}

export type NoteRegenerationScope = "STUDY_PACK" | "NOTE_AND_STUDY_PACK";

/**
 * Regenerates a note's Study Pack, and with `NOTE_AND_STUDY_PACK` its content too, as one operation.
 *
 * Both scopes go through this one endpoint on purpose: `STUDY_PACK` delegates server-side to the same
 * call `POST /notes/{id}/generate` makes, so the scope selector has a single code path rather than two
 * behind one control. First generation on a DRAFT note still uses `createStudyPackFromNote` — that is a
 * different control, not a scope choice.
 */
export async function regenerateNote(
  noteId: string,
  scope: NoteRegenerationScope,
): Promise<NoteResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/regenerate`,
    {
      method: "POST",
      headers: { ...buildAuthHeaders(), "Content-Type": "application/json" },
      body: JSON.stringify({ scope }),
    },
    true,
  );
  return parseApiResponse<NoteResponse>(
    response,
    "We could not regenerate this note right now. Please try again.",
  );
}

export async function generateNoteFromTopic(
  topic: string,
  courseProgram?: string,
  domainContext?: DomainContext,
  courseProgramIds?: string[],
): Promise<GenerateNoteFromTopicResponse> {
  const body: Record<string, string | string[]> = { topic };
  if (courseProgram && courseProgram.trim().length > 0) {
    body.courseProgramText = courseProgram.trim();
  }
  if (courseProgramIds && courseProgramIds.length > 0) {
    body.courseProgramIds = courseProgramIds;
  }
  if (domainContext) {
    body.domainContext = domainContext;
  }
  const response = await fetchWithAuth(
    "/notes/generate",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(body),
    },
    true,
  );
  return parseApiResponse<GenerateNoteFromTopicResponse>(
    response,
    "We could not create a note right now. Please try again.",
  );
}

export async function createStudyPackFromImage(imageFile: File): Promise<StudyPackApiResponse> {
  const formData = new FormData();
  formData.append("image", imageFile);

  const response = await fetchWithAuth(
    "/study-packs",
    {
      method: "POST",
      headers: buildAuthHeaders(),
      body: formData,
    },
    true,
  );
  return parseApiResponse<StudyPackApiResponse>(
    response,
    "We could not generate your study pack right now. Please try again.",
  );
}

export async function extractNoteTextFromFile(file: File): Promise<NoteTextExtractionResponse> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetchWithAuth(
    "/notes/extract-text",
    {
      method: "POST",
      headers: buildAuthHeaders(),
      body: formData,
    },
    true,
  );
  return parseApiResponse<NoteTextExtractionResponse>(
    response,
    "We could not import text from this file right now. Please try again.",
  );
}

export async function importNotesBatch(files: File[]): Promise<BulkImportResult> {
  const formData = new FormData();
  files.forEach((file) => formData.append("files", file));

  const response = await fetchWithAuth(
    "/notes/import-batch",
    {
      method: "POST",
      headers: buildAuthHeaders(),
      body: formData,
    },
    true,
  );
  return parseApiResponse<BulkImportResult>(
    response,
    "We could not import these files right now. Please try again.",
  );
}

export async function bulkGenerateNotes(
  request: BulkGenerateNotesRequest,
): Promise<BulkGenerateNotesResponse> {
  const response = await fetchWithAuth(
    "/notes/bulk-generate",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<BulkGenerateNotesResponse>(
    response,
    "We could not queue these notes right now. Please try again.",
  );
}

export async function getBulkGenerationResult(resultId: string): Promise<BulkGenerationResultResponse> {
  const response = await fetchWithAuth(
    `/notes/bulk-generate/results/${encodeURIComponent(resultId)}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<BulkGenerationResultResponse>(
    response,
    "We could not load this bulk generation result right now.",
  );
}

export async function confirmStudyPackText(
  draftId: string,
  notesText: string,
): Promise<StudyPackResponse> {
  const response = await fetchWithAuth(
    "/study-packs/confirm-text",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ draftId, notesText }),
    },
    true,
  );

  const payload = await parseApiResponse<StudyPackApiResponse>(
    response,
    "We could not generate your study pack right now. Please try again.",
  );
  if (isNeedsTextConfirmationResponse(payload)) {
    throw new Error("Unexpected OCR confirmation response for text confirmation.");
  }
  return payload;
}

export async function listMyStudyPacksPage(
  options: { limit?: number; cursor?: string | null } = {},
): Promise<StudyPackListPageResponse> {
  const queryParams = new URLSearchParams();
  if (options.limit && options.limit > 0) {
    queryParams.set("limit", String(options.limit));
  }
  if (options.cursor) {
    queryParams.set("cursor", options.cursor);
  }

  const path = queryParams.size > 0 ? `/study-packs?${queryParams.toString()}` : "/study-packs";
  const response = await fetchWithAuth(
    path,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<StudyPackListPageResponse>(response, "Could not load your Study Packs.");
}

export async function listMyStudyPacks(): Promise<StudyPackListItemResponse[]> {
  const pageSize = 100;
  const maxPages = 50;
  const items: StudyPackListItemResponse[] = [];

  let cursor: string | null = null;
  let hasMore = true;
  let pagesFetched = 0;

  while (hasMore && pagesFetched < maxPages) {
    const page = await listMyStudyPacksPage({ limit: pageSize, cursor });
    items.push(...page.items);
    hasMore = page.hasMore;
    cursor = page.nextCursor;
    pagesFetched += 1;
    if (!cursor) {
      break;
    }
  }

  return items;
}

export async function getContinueStudyingRecommendation(): Promise<ContinueStudyingResponse> {
  const response = await fetchWithAuth(
    "/dashboard/continue-studying",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ContinueStudyingResponse>(
    response,
    "Could not load continue studying recommendation.",
  );
}

export async function getMyStudyPack(id: string): Promise<StudyPackResponse> {
  const response = await fetchWithAuth(
    `/study-packs/${id}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<StudyPackResponse>(response, "Could not load this Study Pack.");
}

export async function getConceptHealth(studyPackId: string): Promise<ConceptHealthEntry[]> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/concept-health`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ConceptHealthEntry[]>(response, "Could not load concept review signals.");
}

export async function getMemorizationCards(studyPackId: string): Promise<MemorizationCardResponse[]> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/memorization`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<MemorizationCardResponse[]>(response, "Could not load memorization schedule.");
}

export async function gradeMemorizationCard(
  studyPackId: string,
  concept: string,
  grade: MemorizationGrade,
): Promise<MemorizationCardResponse> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/memorization/grade`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ concept, grade }),
    },
    true,
  );
  return parseApiResponse<MemorizationCardResponse>(response, "Could not save memorization grade.");
}

export async function getPostSessionNextStep(studyPackId: string): Promise<PostSessionNextStepResponse> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/next-step`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<PostSessionNextStepResponse>(response, "Could not load the next study step.");
}

export async function getGoalSummary(): Promise<GoalNudgeResponse | null> {
  const response = await fetchWithAuth(
    "/me/goal",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (!response.ok) {
    await throwApiRequestError(response, "Could not load your study goal.");
  }
  if (response.status === 204) {
    return null;
  }
  const body = await response.text();
  if (!body.trim()) {
    return null;
  }
  return JSON.parse(body) as GoalNudgeResponse | null;
}

export async function getProgressReport(): Promise<ProgressReportResponse> {
  const response = await fetchWithAuth(
    "/me/progress",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ProgressReportResponse>(response, "Could not load your progress report.");
}

export async function updateStudyPackTags(studyPackId: string, tags: string[]): Promise<StudyPackResponse> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/tags`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ tags }),
    },
    true,
  );
  return parseApiResponse<StudyPackResponse>(response, "Could not update tags.");
}

export async function updateStudyPackMetadata(
  studyPackId: string,
  request: UpdateStudyPackMetadataRequest,
): Promise<StudyPackResponse> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/metadata`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<StudyPackResponse>(response, "Could not update Study Pack metadata.");
}

export async function deleteMyStudyPack(id: string): Promise<void> {
  const response = await fetchWithAuth(
    `/study-packs/${id}`,
    {
      method: "DELETE",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (!response.ok) {
    await parseApiResponse<void>(response, "Could not delete this Study Pack.");
  }
}

export async function createStudyPackShareLink(studyPackId: string): Promise<ShareLinkResponse> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/share`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ShareLinkResponse>(response, "Could not create a share link.");
}

export async function getPublicSharedStudyPack(token: string): Promise<PublicShareResponse> {
  const response = await fetch(buildUrl(`/p/${token}`), {
    method: "GET",
  });
  return parseApiResponse<PublicShareResponse>(response, "Could not load this shared Study Pack.");
}

export async function remixSharedStudyPack(token: string): Promise<ShareRemixResponse> {
  const response = await fetchWithAuth(
    `/p/${token}/remix`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ShareRemixResponse>(response, "Could not copy this Study Pack.");
}

export async function trackQuickReviewActivity(
  studyPackId: string,
  activityType: QuickReviewActivityType,
): Promise<void> {
  const response = await fetchWithAuth(
    `/study-packs/${studyPackId}/quick-review/activity`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ activityType }),
    },
    true,
  );
  if (!response.ok) {
    await parseApiResponse<void>(response, "Could not track quick review activity.");
  }
}

export async function startQuickReviewSession(
  noteId: string,
): Promise<QuickReviewSessionStartResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quick-review/start`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionStartResponse>(response, "Could not start Quick Review.");
}

export async function getInProgressQuickReviewSession(
  noteId: string,
): Promise<QuickReviewSessionStartResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quick-review/in-progress`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionStartResponse>(response, "Could not load in-progress Quick Review.");
}

export async function updateQuickReviewSessionProgress(
  sessionId: string,
  request: QuickReviewSessionProgressRequest,
): Promise<QuickReviewSessionSummaryResponse> {
  const response = await fetchWithAuth(
    `/quick-review/${sessionId}/progress`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionSummaryResponse>(
    response,
    "Could not save Quick Review progress.",
  );
}

export async function completeQuickReviewSession(
  sessionId: string,
  request: QuickReviewSessionCompleteRequest,
): Promise<QuickReviewSessionSummaryResponse> {
  const response = await fetchWithAuth(
    `/quick-review/${sessionId}/complete`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionSummaryResponse>(response, "Could not save Quick Review results.");
}

export async function forfeitQuickReviewSession(
  sessionId: string,
): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/quick-review/${sessionId}/forfeit`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not leave Quick Review.");
}

export async function saveQuickReviewConfidence(
  sessionId: string,
  confidenceLevel: QuickReviewConfidenceLevel,
): Promise<QuickReviewSessionSummaryResponse> {
  const response = await fetchWithAuth(
    `/quick-review/${sessionId}/confidence`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ confidenceLevel }),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionSummaryResponse>(
    response,
    "Could not save confidence feedback.",
  );
}

export async function listRecentQuickReviewSessions(
  noteId: string,
  limit = 5,
): Promise<QuickReviewSessionSummaryResponse[]> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quick-review/recent?limit=${limit}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewSessionSummaryResponse[]>(
    response,
    "Could not load recent Quick Review sessions.",
  );
}

export async function getQuickReviewPerformanceSummary(
  noteId: string,
): Promise<QuickReviewPerformanceSummaryResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quick-review/performance-summary`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewPerformanceSummaryResponse>(
    response,
    "Could not load Quick Review performance summary.",
  );
}

export async function getQuickReviewLastReviewedBatch(
  noteIds: string[],
): Promise<NoteQuickReviewLastReviewedResponse[]> {
  const searchParams = new URLSearchParams();
  noteIds.forEach((noteId) => searchParams.append("noteIds", noteId));
  const response = await fetchWithAuth(
    `/notes/quick-review/last-reviewed?${searchParams.toString()}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteQuickReviewLastReviewedResponse[]>(
    response,
    "Could not load Quick Review history.",
  );
}

export async function getQuickReviewSessionReview(
  noteId: string,
  sessionId: string,
): Promise<QuizSessionReviewResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quick-review/sessions/${sessionId}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuizSessionReviewResponse>(
    response,
    "Could not load Quick Review session review.",
  );
}

export async function generateQuickReviewStudyTip(
  noteId: string,
  request: QuickReviewStudyTipRequest,
): Promise<QuickReviewStudyTipResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quick-review/study-tip`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<QuickReviewStudyTipResponse>(response, "Could not generate study tip.");
}

export async function generateAdaptiveQuickReviewQuiz(
  noteId: string,
  entry?: AdaptivePracticeEntry | null,
): Promise<QuickReviewAdaptiveQuizResponse> {
  const path = `/notes/${noteId}/adaptive-practice/start`;
  const requestPath = entry
    ? `${path}?${new URLSearchParams({ [ADAPTIVE_PRACTICE_ENTRY_QUERY_PARAM]: entry }).toString()}`
    : path;
  const response = await fetchWithAuth(
    requestPath,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewAdaptiveQuizResponse>(
    response,
    "Could not generate adaptive practice quiz.",
  );
}

/**
 * Plan- and Review-Set-scoped Adaptive Practice.
 *
 * Collection-addressed on purpose: the collection is the session anchor. The client must NOT
 * compute and send a pack anchor -- a plan's item order is mutable.
 */
export async function generateAdaptivePracticeForCollection(
  collectionId: string,
  entry?: AdaptivePracticeEntry | null,
): Promise<QuickReviewAdaptiveQuizResponse> {
  const path = `/collections/${collectionId}/adaptive-practice/start`;
  const requestPath = entry
    ? `${path}?${new URLSearchParams({ [ADAPTIVE_PRACTICE_ENTRY_QUERY_PARAM]: entry }).toString()}`
    : path;
  const response = await fetchWithAuth(
    requestPath,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewAdaptiveQuizResponse>(
    response,
    "Could not generate adaptive practice for this plan.",
  );
}


export async function getInProgressAdaptivePracticeSession(
  noteId: string,
): Promise<QuickReviewAdaptiveQuizResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/adaptive-practice/in-progress`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewAdaptiveQuizResponse>(
    response,
    "Could not load Adaptive Practice session.",
  );
}

export async function getAdaptivePracticeSession(
  sessionId: string,
): Promise<QuickReviewAdaptiveQuizResponse> {
  const response = await fetchWithAuth(
    `/adaptive-practice/sessions/${sessionId}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuickReviewAdaptiveQuizResponse>(
    response,
    "Could not load Adaptive Practice session.",
  );
}

export async function completeAdaptivePracticeSession(
  sessionId: string,
  request: AdaptivePracticeCompleteRequest,
): Promise<AdaptivePracticeCompleteResponse> {
  const response = await fetchWithAuth(
    `/adaptive-practice/sessions/${sessionId}/complete`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<AdaptivePracticeCompleteResponse>(
    response,
    "Could not complete adaptive practice session.",
  );
}

export async function forfeitAdaptivePracticeSession(
  sessionId: string,
): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/adaptive-practice/sessions/${sessionId}/forfeit`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(
    response,
    "Could not leave Adaptive Practice.",
  );
}

export async function startChallengeQuizSession(
  noteId: string,
  request: ChallengeQuizStartRequest = {},
): Promise<ChallengeQuizStartResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/challenge-quiz/start`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<ChallengeQuizStartResponse>(response, "Could not start Challenge Quiz.");
}

export async function startRedoMissedChallengeQuizSession(
  noteId: string,
): Promise<ChallengeQuizStartResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/challenge-quiz/redo-missed`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ChallengeQuizStartResponse>(response, "Could not start missed-question redo.");
}

export async function getInProgressChallengeQuizSession(
  noteId: string,
): Promise<ChallengeQuizStartResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/challenge-quiz/in-progress`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ChallengeQuizStartResponse>(
    response,
    "Could not load in-progress Challenge Quiz.",
  );
}

export async function updateChallengeQuizSessionProgress(
  sessionId: string,
  request: ChallengeQuizProgressRequest,
  options: { keepalive?: boolean } = {},
): Promise<ChallengeQuizStartResponse> {
  const response = await fetchWithAuth(
    `/challenge-quiz/sessions/${sessionId}/progress`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
      keepalive: options.keepalive ?? false,
    },
    true,
  );
  return parseApiResponse<ChallengeQuizStartResponse>(
    response,
    "Could not save Challenge Quiz progress.",
  );
}

export async function completeChallengeQuizSession(
  sessionId: string,
  request: ChallengeQuizCompleteRequest,
): Promise<ChallengeQuizSessionResponse> {
  const response = await fetchWithAuth(
    `/challenge-quiz/sessions/${sessionId}/complete`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<ChallengeQuizSessionResponse>(response, "Could not save Challenge Quiz results.");
}

export async function forfeitChallengeQuizSession(
  sessionId: string,
): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/challenge-quiz/sessions/${sessionId}/forfeit`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not leave Challenge Quiz.");
}

export async function generateMoreChallengeQuizQuestions(
  sessionId: string,
): Promise<GenerateMoreChallengeQuizResponse> {
  const response = await fetchWithAuth(
    `/challenge-quiz/sessions/${sessionId}/generate-more`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<GenerateMoreChallengeQuizResponse>(
    response,
    "Could not generate more questions.",
  );
}

export async function listRecentChallengeQuizSessions(
  noteId: string,
  limit = 5,
): Promise<ChallengeQuizSessionSummaryResponse[]> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/challenge-quiz/recent?limit=${limit}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ChallengeQuizSessionSummaryResponse[]>(
    response,
    "Could not load recent Challenge Quiz sessions.",
  );
}

export async function listRecentQuizSessions(
  noteId: string,
  limit = 5,
): Promise<RecentQuizSessionHistoryResponse[]> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/quiz-sessions/recent?limit=${limit}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<RecentQuizSessionHistoryResponse[]>(
    response,
    "Could not load recent quiz sessions.",
  );
}

export async function getChallengeQuizPerformanceSummary(
  noteId: string,
): Promise<ChallengeQuizPerformanceSummaryResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/challenge-quiz/performance-summary`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<ChallengeQuizPerformanceSummaryResponse>(
    response,
    "Could not load Challenge Quiz performance summary.",
  );
}

export async function getChallengeQuizSessionReview(
  noteId: string,
  sessionId: string,
): Promise<QuizSessionReviewResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/challenge-quiz/sessions/${sessionId}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<QuizSessionReviewResponse>(
    response,
    "Could not load Challenge Quiz session review.",
  );
}

export async function startLongExam(
  studyPackId: string,
  body: { difficulty?: string; additionalStudyPackIds?: string[]; sourceCollectionId?: string } = {},
): Promise<LongExamStartResponse> {
  const response = await fetchWithAuth(
    `/long-exam/study-packs/${studyPackId}/start`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(body),
    },
    true,
  );
  return parseApiResponse<LongExamStartResponse>(response, "Could not start Long Exam.");
}

export async function getActiveLongExamSession(
  studyPackId: string,
): Promise<LongExamStartResponse | null> {
  const response = await fetchWithAuth(
    `/long-exam/study-packs/${studyPackId}/active`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (!response.ok) {
    return throwApiRequestError(response, "Could not load active Long Exam.");
  }
  const text = await response.text();
  if (!text || text.trim() === "" || text.trim() === "null") {
    return null;
  }
  return JSON.parse(text) as LongExamStartResponse;
}

export async function getLongExamSession(
  sessionId: string,
): Promise<LongExamSessionResponse> {
  const response = await fetchWithAuth(
    `/long-exam/sessions/${sessionId}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<LongExamSessionResponse>(response, "Could not load Long Exam session.");
}

export async function saveLongExamProgress(
  sessionId: string,
  body: { questionIndex: number; selectedChoiceIndex: number; selectedMultiChoiceIndices?: number[]; selectedIdentificationAnswer?: string },
): Promise<LongExamSessionResponse> {
  const response = await fetchWithAuth(
    `/long-exam/sessions/${sessionId}/progress`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(body),
    },
    true,
  );
  return parseApiResponse<LongExamSessionResponse>(response, "Could not save Long Exam progress.");
}

export async function pauseLongExamSession(
  sessionId: string,
): Promise<LongExamSessionResponse> {
  const response = await fetchWithAuth(
    `/long-exam/sessions/${sessionId}/pause`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<LongExamSessionResponse>(response, "Could not pause Long Exam.");
}

export async function resumeLongExamSession(
  sessionId: string,
): Promise<LongExamSessionResponse> {
  const response = await fetchWithAuth(
    `/long-exam/sessions/${sessionId}/resume`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<LongExamSessionResponse>(response, "Could not resume Long Exam.");
}

export async function completeLongExamSession(
  sessionId: string,
  body: { durationSeconds: number },
): Promise<LongExamMasteryReportResponse> {
  const response = await fetchWithAuth(
    `/long-exam/sessions/${sessionId}/complete`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(body),
    },
    true,
  );
  return parseApiResponse<LongExamMasteryReportResponse>(response, "Could not complete Long Exam.");
}

export async function forfeitLongExamSession(
  sessionId: string,
): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/long-exam/sessions/${sessionId}/forfeit`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not forfeit Long Exam.");
}

export async function startInterviewPractice(
  body: { noteId: string; questionCount: number; additionalNoteIds?: string[] },
): Promise<InterviewPracticeStartResponse> {
  const response = await fetchWithAuth(
    "/interview-practice/start",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(body),
    },
    true,
  );
  return parseApiResponse<InterviewPracticeStartResponse>(response, "Could not start Interview Practice.");
}

export async function answerInterviewPracticeQuestion(
  sessionId: string,
  body: { questionIndex: number; selectedChoice: "A" | "B" | "C" | "D"; timeSpentSeconds: number },
): Promise<InterviewPracticeAnswerResponse> {
  const response = await fetchWithAuth(
    `/interview-practice/sessions/${sessionId}/answer`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(body),
    },
    true,
  );
  return parseApiResponse<InterviewPracticeAnswerResponse>(response, "Could not review your interview answer.");
}

export async function completeInterviewPracticeSession(
  sessionId: string,
): Promise<InterviewReadinessReportResponse> {
  const response = await fetchWithAuth(
    `/interview-practice/sessions/${sessionId}/complete`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<InterviewReadinessReportResponse>(response, "Could not complete Interview Practice.");
}

export async function forfeitInterviewPracticeSession(
  sessionId: string,
): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/interview-practice/sessions/${sessionId}/forfeit`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not forfeit Interview Practice.");
}

export async function getTodayFocus(): Promise<TodayFocusResponse> {
  const response = await fetchWithAuth(
    "/dashboard/today-focus",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<TodayFocusResponse>(response, "Could not load today's focus.");
}

export async function getDashboardOverview(): Promise<DashboardOverviewResponse> {
  const response = await fetchWithAuth(
    "/dashboard/overview",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<DashboardOverviewResponse>(response, "Could not load dashboard overview.");
}

export async function getUserNotePerformanceSummary(limit = 5): Promise<NotePerformanceSummaryResponse[]> {
  const response = await fetchWithAuth(
    `/dashboard/note-performance?limit=${limit}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NotePerformanceSummaryResponse[]>(response, "Could not load note performance summary.");
}

export async function getStudyEngagement(): Promise<StudyEngagementResponse> {
  const response = await fetchWithAuth(
    "/dashboard/study-engagement",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<StudyEngagementResponse>(response, "Could not load study engagement.");
}

export async function getMasterySnapshot(): Promise<MasterySnapshotResponse> {
  const response = await fetchWithAuth(
    "/dashboard/mastery-snapshot",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<MasterySnapshotResponse>(response, "Could not load mastery snapshot.");
}

export async function createPremiumCheckoutSession(
  request?: BillingCheckoutSessionRequest,
): Promise<BillingCheckoutSessionResponse> {
  const response = await fetchWithAuth(
    "/payments/create",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({
        planType: request?.planType ?? null,
        billingCycle: request?.billingCycle ?? null,
        returnUrl: request?.returnUrl ?? null,
      }),
    },
    true,
  );
  return parseApiResponse<BillingCheckoutSessionResponse>(
    response,
    "Could not start checkout. Please try again.",
  );
}

export async function getBillingPricing(): Promise<BillingPricingResponse> {
  const accessToken = getAccessToken();
  const response = accessToken
    ? await fetchWithAuth(
        "/billing/pricing",
        {
          method: "GET",
          headers: buildAuthHeaders(),
        },
        true,
      )
    : await fetch(buildUrl("/billing/pricing"), {
        method: "GET",
      });

  return parseApiResponse<BillingPricingResponse>(
    response,
    "Could not load pricing.",
  );
}

export async function cancelPremiumSubscription(
  request: CancelPremiumSubscriptionRequest = {},
): Promise<MeResponse> {
  const response = await fetchWithAuth(
    "/billing/subscription/cancel",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<MeResponse>(
    response,
    "Could not schedule plan cancellation. Please try again.",
  );
}

export async function getBillingUsageSummary(): Promise<BillingUsageSummaryResponse> {
  const response = await fetchWithAuth(
    "/billing/usage-summary",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<BillingUsageSummaryResponse>(
    response,
    "Could not load billing usage.",
  );
}

export async function getMyPlan(): Promise<MePlanResponse> {
  const response = await fetchWithAuth(
    "/me/plan",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<MePlanResponse>(
    response,
    "Could not load plan details.",
  );
}

export async function getBillingHistory(): Promise<BillingHistoryResponse> {
  const response = await fetchWithAuth(
    "/billing/history",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<BillingHistoryResponse>(
    response,
    "Could not load billing history.",
  );
}

export async function createNote(
  request: UpsertNoteRequest,
  options: { keepalive?: boolean } = {},
): Promise<NoteResponse> {
  const response = await fetchWithAuth(
    "/notes",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
      keepalive: options.keepalive ?? false,
    },
    true,
  );
  return parseApiResponse<NoteResponse>(response, "Could not save note.");
}

export async function getNote(noteId: string): Promise<NoteResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteResponse>(response, "Could not load note.");
}

export async function getNoteShares(noteId: string): Promise<NoteShareResponse[]> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/shares`,
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<NoteShareResponse[]>(response, "Could not load note sharing.");
}

export async function replaceNoteShares(
  noteId: string,
  relationshipIds: string[],
): Promise<NoteShareResponse[]> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/shares`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ relationshipIds }),
    },
    true,
  );
  return parseApiResponse<NoteShareResponse[]>(response, "Could not update note sharing.");
}

export async function listSharedWithMe(
  options: { limit?: number; cursor?: string | null } = {},
): Promise<SharedNotesPageResponse> {
  const params = new URLSearchParams();
  if (options.limit) params.set("limit", String(options.limit));
  if (options.cursor) params.set("cursor", options.cursor);
  const query = params.size > 0 ? `?${params.toString()}` : "";
  const response = await fetchWithAuth(
    `/notes/shared-with-me${query}`,
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<SharedNotesPageResponse>(response, "Could not load notes shared with you.");
}

export async function getSharedNote(noteId: string): Promise<SharedNoteResponse> {
  const response = await fetchWithAuth(
    `/notes/shared/${noteId}`,
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<SharedNoteResponse>(response, "This note is no longer shared with you.");
}

export async function getSharedStudyPack(studyPackId: string): Promise<SharedStudyPackResponse> {
  const response = await fetchWithAuth(
    `/study-packs/shared/${studyPackId}`,
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<SharedStudyPackResponse>(response, "This note is no longer shared with you.");
}

export async function copySharedNote(noteId: string): Promise<NoteResponse> {
  const response = await fetchWithAuth(
    `/notes/shared/${noteId}/copy`,
    { method: "POST", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<NoteResponse>(response, "Could not copy this shared note.");
}

export async function getGeneratedQuiz(noteId: string): Promise<GeneratedQuizResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/generated-quiz`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<GeneratedQuizResponse>(response, "Could not load generated quiz.");
}

export async function generateGeneratedQuiz(
  noteId: string,
  questionCount: TeacherQuizQuestionCount = 10,
  targetLearnerLevel?: string | null,
): Promise<GeneratedQuizResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/generated-quiz`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ questionCount, targetLearnerLevel: targetLearnerLevel ?? null }),
    },
    true,
  );
  return parseApiResponse<GeneratedQuizResponse>(response, "Could not generate quiz.");
}

export async function createQuizShareLink(generatedQuizId: string): Promise<QuizShareLinkResponse> {
  const response = await fetchWithAuth(
    "/quiz-share",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ generatedQuizId }),
    },
    true,
  );
  return parseApiResponse<QuizShareLinkResponse>(response, "Could not create share link.");
}

export async function createCombinedQuiz(request: CreateCombinedQuizRequest): Promise<CombinedQuizResponse> {
  const response = await fetchWithAuth(
    "/combined-quizzes",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<CombinedQuizResponse>(response, "Could not assemble combined quiz.");
}

export async function getCombinedQuiz(combinedQuizId: string): Promise<CombinedQuizResponse> {
  const response = await fetchWithAuth(
    `/combined-quizzes/${combinedQuizId}`,
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<CombinedQuizResponse>(response, "Could not load combined quiz.");
}

export async function listCombinedQuizzes(): Promise<CombinedQuizSummaryResponse[]> {
  const response = await fetchWithAuth(
    "/combined-quizzes",
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<CombinedQuizSummaryResponse[]>(response, "Could not load combined quizzes.");
}

export async function createCombinedQuizShareLink(combinedQuizId: string): Promise<QuizShareLinkResponse> {
  const response = await fetchWithAuth(
    "/combined-quiz-share",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ combinedQuizId }),
    },
    true,
  );
  return parseApiResponse<QuizShareLinkResponse>(response, "Could not create share link.");
}

/** A 404 is the normal no-link-yet state; this read must never create a replacement link. */
export async function getCombinedQuizShareLink(combinedQuizId: string): Promise<QuizShareLinkResponse | null> {
  const response = await fetchWithAuth(
    `/combined-quiz-share/${combinedQuizId}`,
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  if (response.status === 404) {
    return null;
  }
  return parseApiResponse<QuizShareLinkResponse>(response, "Could not load share link.");
}

export async function getQuizShareLinkByQuizId(generatedQuizId: string): Promise<QuizShareLinkResponse | null> {
  const response = await fetchWithAuth(
    `/quiz-share/${generatedQuizId}`,
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  if (response.status === 404) {
    return null;
  }
  return parseApiResponse<QuizShareLinkResponse>(response, "Could not load share link.");
}

export async function toggleQuizShareLink(token: string): Promise<QuizShareLinkResponse> {
  const response = await fetchWithAuth(
    `/quiz-share/${token}/toggle`,
    {
      method: "PATCH",
      headers: buildAuthHeaders("application/json"),
    },
    true,
  );
  return parseApiResponse<QuizShareLinkResponse>(response, "Could not update share link.");
}

export async function getPublicSharedQuiz(token: string): Promise<PublicSharedQuizResponse> {
  const response = await fetch(buildUrl(`/quiz/share/${token}`), {
    method: "GET",
  });
  return parseApiResponse<PublicSharedQuizResponse>(response, "Could not load shared quiz.");
}

/**
 * `answers` holds one entry per question and is null at a MULTI_SELECT position, whose selections travel
 * in the index-aligned `multiAnswers`. The server rejects either list at the wrong length.
 */
export async function getSharedQuizResults(
  token: string,
  answers: (number | null)[],
  multiAnswers: (number[] | null)[],
): Promise<SharedQuizResultsResponse> {
  const response = await fetch(buildUrl(`/quiz/share/${token}/results`), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ answers, multiAnswers }),
  });
  return parseApiResponse<SharedQuizResultsResponse>(response, "Could not check quiz results.");
}

export async function exportGeneratedQuizDocx(
  quizId: string,
  mode: QuizDocxExportMode,
  headerOverride?: QuizDocxHeaderOverride | null,
  versionCount?: number | null,
): Promise<{ filename: string }> {
  const response = await fetchWithAuth(
    `/quizzes/${quizId}/export-docx?mode=${mode}`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({
        headerOverride: headerOverride ?? null,
        versionCount: versionCount ?? null,
      }),
    },
    true,
  );
  if (!response.ok) {
    return throwApiRequestError(response, "Could not export quiz.");
  }
  const blob = await response.blob();
  const fallbackFilename = mode === "WITH_ANSWERS" ? "generated-quiz-with-answers.docx" : "generated-quiz.docx";
  const filename = extractDownloadFilename(response.headers.get("content-disposition"), fallbackFilename);
  triggerBlobDownload(blob, filename);
  return { filename };
}

export async function exportCombinedGeneratedQuizDocx(
  request: MultiNoteQuizDocxExportRequest,
): Promise<{ filename: string }> {
  const response = await fetchWithAuth(
    "/quizzes/export-docx",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  if (!response.ok) {
    return throwApiRequestError(response, "Could not export exam.");
  }
  const blob = await response.blob();
  const fallbackFilename = request.includeAnswerKey || request.includeExplanations
    ? "combined-exam-with-answers.docx"
    : "combined-exam.docx";
  const filename = extractDownloadFilename(response.headers.get("content-disposition"), fallbackFilename);
  triggerBlobDownload(blob, filename);
  return { filename };
}

export async function listNotes(limit?: number): Promise<NoteListItemResponse[]> {
  const query = typeof limit === "number" ? `?limit=${encodeURIComponent(String(limit))}` : "";
  const response = await fetchWithAuth(
    `/notes${query}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteListItemResponse[]>(response, "Could not load notes.");
}

function buildLibraryFilterSearchParams(params: LibraryFilterParams): URLSearchParams {
  const searchParams = new URLSearchParams();
  if (params.search) searchParams.set("search", params.search);
  if (params.readiness) searchParams.set("readiness", params.readiness);
  if (params.courseProgram) searchParams.set("courseProgram", params.courseProgram);
  if (params.subject) searchParams.set("subject", params.subject);
  params.tags?.forEach((tag) => searchParams.append("tag", tag));
  if (params.visibility) searchParams.set("visibility", params.visibility);
  if (params.collectionId) searchParams.set("collectionId", params.collectionId);
  return searchParams;
}

export async function listLibraryPage(params: LibraryPageParams): Promise<NotesLibraryPageResponse> {
  const searchParams = buildLibraryFilterSearchParams(params);
  if (params.sort) searchParams.set("sort", params.sort);
  if (typeof params.page === "number") searchParams.set("page", String(params.page));
  if (typeof params.pageSize === "number") searchParams.set("pageSize", String(params.pageSize));
  const response = await fetchWithAuth(
    `/notes/library?${searchParams.toString()}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NotesLibraryPageResponse>(response, "Could not load notes.");
}

export async function listLibraryMatchingIds(params: LibraryFilterParams): Promise<NotesLibraryIdsResponse> {
  const searchParams = buildLibraryFilterSearchParams(params);
  const response = await fetchWithAuth(
    `/notes/library/ids?${searchParams.toString()}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NotesLibraryIdsResponse>(response, "Could not select matching notes.");
}

export async function getLibrarySubjectStats(
  params: Omit<LibraryFilterParams, "subject">,
): Promise<SubjectStatsResponse> {
  const searchParams = buildLibraryFilterSearchParams(params);
  const response = await fetchWithAuth(
    `/notes/library/subject-stats?${searchParams.toString()}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SubjectStatsResponse>(response, "Could not load subject statistics.");
}

export async function getLibraryFilterOptions(): Promise<NotesLibraryFilterOptionsResponse> {
  const response = await fetchWithAuth(
    "/notes/library/filter-options",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NotesLibraryFilterOptionsResponse>(response, "Could not load library filters.");
}

export async function listNoteStatuses(): Promise<NoteStatusResponse[]> {
  const response = await fetchWithAuth(
    "/notes/status",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteStatusResponse[]>(response, "Could not load note statuses.");
}

export async function getSavedLibraryFilters(): Promise<SavedLibraryFilterResponse[]> {
  const response = await fetchWithAuth(
    "/library-filters",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<SavedLibraryFilterResponse[]>(response, "Could not load saved filters.");
}

export async function createSavedLibraryFilter(
  name: string,
  filterState: SavedLibraryFilterState,
): Promise<SavedLibraryFilterResponse> {
  const response = await fetchWithAuth(
    "/library-filters",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ name, filterState }),
    },
    true,
  );
  return parseApiResponse<SavedLibraryFilterResponse>(response, "Could not save filter.");
}

export async function deleteSavedLibraryFilter(id: string): Promise<void> {
  const response = await fetchWithAuth(
    `/library-filters/${id}`,
    {
      method: "DELETE",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (response.ok) {
    return;
  }
  await parseApiResponse<never>(response, "Could not delete filter.");
}

export async function listCollections(params?: { noteAccepting?: boolean }): Promise<NoteCollectionSummary[]> {
  const query = params?.noteAccepting ? "?noteAccepting=true" : "";
  const response = await fetchWithAuth(
    `/collections${query}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteCollectionSummary[]>(response, "Could not load collections.");
}

export async function listPublicStudyPlans(params?: {
  courseProgram?: string | null;
}): Promise<NoteCollectionSummary[]> {
  const searchParams = new URLSearchParams();
  if (params?.courseProgram) {
    searchParams.set("courseProgram", params.courseProgram);
  }
  const query = searchParams.size > 0 ? `?${searchParams.toString()}` : "";
  const response = await fetch(buildUrl(`/collections/public${query}`), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<NoteCollectionSummary[]>(response, "Could not load public study plans.");
}

export async function getPublicStudyPlanDetail(id: string): Promise<NoteCollectionDetail> {
  const response = await fetch(buildUrl(`/collections/public/${id}`), {
    method: "GET",
  });
  return parseApiResponse<NoteCollectionDetail>(response, "Could not load this public study plan.");
}

export async function createCollection(request: {
  title: string;
  description?: string | null;
  noteIds?: string[];
  learnerLevel?: LearnerLevel | null;
}): Promise<NoteCollectionDetail> {
  const response = await fetchWithAuth(
    "/collections",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<NoteCollectionDetail>(response, "Could not create this collection.");
}

export async function getCollection(id: string): Promise<NoteCollectionDetail> {
  const response = await fetchWithAuth(
    `/collections/${id}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteCollectionDetail>(response, "Could not load this collection.");
}

export async function getPlanReadiness(id: string): Promise<PlanReadinessResponse> {
  const response = await fetchWithAuth(
    `/collections/${id}/readiness`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<PlanReadinessResponse>(response, "Could not load plan readiness.");
}

export async function getNoteConceptCounts(id: string): Promise<Record<string, NoteConceptCountsResponse>> {
  const response = await fetchWithAuth(
    `/collections/${id}/note-concept-counts`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<Record<string, NoteConceptCountsResponse>>(response, "Could not load concept counts.");
}

export async function getCollectionGoal(id: string): Promise<GoalCollectionDetailResponse> {
  const response = await fetchWithAuth(
    `/collections/${id}/goal`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<GoalCollectionDetailResponse>(response, "Could not load goal details.");
}

export async function getActiveAskCompanionSession(
  collectionId: string,
): Promise<AskCompanionSessionResponse | null> {
  const response = await fetchWithAuth(
    `/collections/${collectionId}/ask-companion/sessions/active`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (response.status === 204) {
    return null;
  }
  return parseApiResponse<AskCompanionSessionResponse>(
    response,
    "Could not load your Ask Companion conversation.",
  );
}

export async function startAskCompanionSession(
  collectionId: string,
): Promise<AskCompanionSessionResponse> {
  const response = await fetchWithAuth(
    `/collections/${collectionId}/ask-companion/sessions`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<AskCompanionSessionResponse>(
    response,
    "Could not start Ask Companion.",
  );
}

export async function askCompanionQuestion(
  sessionId: string,
  question: string,
): Promise<AskCompanionSessionResponse> {
  const response = await fetchWithAuth(
    `/ask-companion/sessions/${sessionId}/messages`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ question }),
    },
    true,
  );
  return parseApiResponse<AskCompanionSessionResponse>(
    response,
    "Ask Companion could not answer right now.",
  );
}

export async function setPrimaryCollection(id: string): Promise<void> {
  const response = await fetchWithAuth(
    `/collections/${id}/primary`,
    {
      method: "PUT",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (response.ok) {
    return;
  }
  await parseApiResponse<never>(response, "Could not set primary collection.");
}

export async function clearPrimaryCollection(id: string): Promise<void> {
  const response = await fetchWithAuth(
    `/collections/${id}/primary`,
    {
      method: "DELETE",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (response.ok) {
    return;
  }
  await parseApiResponse<never>(response, "Could not clear primary collection.");
}

export async function setCompanion(id: string, content: CompanionContent): Promise<NoteCollectionDetail> {
  const response = await fetchWithAuth(
    `/collections/${id}/companion`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(content),
    },
    true,
  );
  return parseApiResponse<NoteCollectionDetail>(response, "Could not save this Companion.");
}

export async function generateCompanion(id: string, sections: CompanionSection[]): Promise<CompanionContent> {
  const response = await fetchWithAuth(
    `/collections/${id}/companion/generate`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ sections }),
    },
    true,
  );
  return parseApiResponse<CompanionContent>(response, "Could not generate Companion draft content.");
}

export async function clearCompanion(id: string): Promise<NoteCollectionDetail> {
  const response = await fetchWithAuth(
    `/collections/${id}/companion`,
    {
      method: "DELETE",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteCollectionDetail>(response, "Could not remove this Companion.");
}

export async function setCollectionParent(id: string, parentId: string | null): Promise<NoteCollectionDetail> {
  const response = await fetchWithAuth(
    `/collections/${id}/parent`,
    {
      method: "PATCH",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ parentId }),
    },
    true,
  );
  return parseApiResponse<NoteCollectionDetail>(response, "Could not update collection nesting.");
}

export async function reorderCollectionChildren(id: string, childIds: string[]): Promise<GoalCollectionDetailResponse> {
  const response = await fetchWithAuth(
    `/collections/${id}/children/order`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ childIds }),
    },
    true,
  );
  return parseApiResponse<GoalCollectionDetailResponse>(response, "Could not save subject order.");
}

export async function updateCollection(
  id: string,
  request: {
    title?: string;
    description?: string | null;
    courseProgram?: string | null;
    estimatedStudyHours?: number | null;
    targetCompletionDate?: string | null;
    learnerLevel?: LearnerLevel | "" | null;
  },
): Promise<NoteCollectionDetail> {
  const response = await fetchWithAuth(
    `/collections/${id}`,
    {
      method: "PATCH",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  return parseApiResponse<NoteCollectionDetail>(response, "Could not update this collection.");
}

export async function clearCollectionTargetDate(id: string): Promise<NoteCollectionDetail> {
  const response = await fetchWithAuth(
    `/collections/${id}/target-date`,
    {
      method: "DELETE",
      headers: buildAuthHeaders("application/json"),
    },
    true,
  );
  return parseApiResponse<NoteCollectionDetail>(response, "Could not clear the target completion date.");
}

export async function updateCollectionVisibility(
  id: string,
  visibility: "PRIVATE" | "PUBLIC",
): Promise<NoteCollectionDetail> {
  const response = await fetchWithAuth(
    `/collections/${id}/visibility`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ visibility }),
    },
    true,
  );
  return parseApiResponse<NoteCollectionDetail>(response, "Could not update this collection visibility.");
}

export async function adoptStudyPlan(id: string): Promise<AdoptStudyPlanResponse> {
  const response = await fetchWithAuth(
    `/collections/${encodeURIComponent(id)}/adopt`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
    },
    true,
  );
  return parseApiResponse<AdoptStudyPlanResponse>(response, "Could not start this study plan.");
}

export async function adoptGoal(id: string): Promise<AdoptGoalResponse> {
  const response = await fetchWithAuth(
    `/collections/${encodeURIComponent(id)}/adopt-goal`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
    },
    true,
  );
  return parseApiResponse<AdoptGoalResponse>(response, "Could not start this Goal.");
}

export async function getReviewSetSourceUpdate(id: string): Promise<ReviewSetUpdateResponse> {
  const response = await fetchWithAuth(
    `/collections/${encodeURIComponent(id)}/source-update`,
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<ReviewSetUpdateResponse>(response, "Could not check this Review Set for updates.");
}

export async function applyReviewSetSourceUpdate(id: string): Promise<ReviewSetUpdateResponse> {
  const response = await fetchWithAuth(
    `/collections/${encodeURIComponent(id)}/source-update`,
    { method: "POST", headers: buildAuthHeaders("application/json") },
    true,
  );
  return parseApiResponse<ReviewSetUpdateResponse>(response, "Could not update this Review Set.");
}

export async function deleteCollection(id: string): Promise<void> {
  const response = await fetchWithAuth(
    `/collections/${id}`,
    {
      method: "DELETE",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (response.ok) {
    return;
  }
  await parseApiResponse<never>(response, "Could not delete this collection.");
}

export async function addCollectionItems(id: string, noteIds: string[]): Promise<NoteCollectionDetail> {
  const response = await fetchWithAuth(
    `/collections/${id}/items`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ noteIds }),
    },
    true,
  );
  return parseApiResponse<NoteCollectionDetail>(response, "Could not add notes to this collection.");
}

export async function removeCollectionItem(id: string, noteId: string): Promise<void> {
  const response = await fetchWithAuth(
    `/collections/${id}/items/${noteId}`,
    {
      method: "DELETE",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (response.ok) {
    return;
  }
  await parseApiResponse<never>(response, "Could not remove this note from the collection.");
}

export async function setCollectionItemOrder(
  id: string,
  items: SetCollectionItemOrderRequestItem[],
): Promise<NoteCollectionDetail> {
  const response = await fetchWithAuth(
    `/collections/${id}/items/order`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ items }),
    },
    true,
  );
  return parseApiResponse<NoteCollectionDetail>(response, "Could not save this collection order.");
}

export async function listPublicNotes(params?: {
  courseProgram?: string;
  creator?: string | null;
  level?: LearnerLevel;
  page?: number;
  pageSize?: number;
  readyOnly?: boolean;
  search?: string;
  size?: number;
  sort?: "copied" | "featured" | "most_copied" | "popular" | "recent" | "recommended" | "title" | "views";
  source?: Array<"by_you" | "official" | "community">;
  subject?: string;
  tags?: string[];
}): Promise<PublicNoteListResponse> {
  const searchParams = new URLSearchParams();
  if (params?.courseProgram) {
    searchParams.set("courseProgram", params.courseProgram);
  }
  if (params?.creator) {
    searchParams.set("creator", params.creator);
  }
  if (params?.level) {
    searchParams.set("level", params.level);
  }
  if (typeof params?.page === "number") {
    searchParams.set("page", String(params.page));
  }
  if (typeof params?.pageSize === "number") {
    searchParams.set("pageSize", String(params.pageSize));
  }
  if (params?.readyOnly) {
    searchParams.set("readyOnly", "true");
  }
  if (params?.search) {
    searchParams.set("search", params.search);
  }
  if (typeof params?.size === "number") {
    searchParams.set("size", String(params.size));
  }
  if (params?.sort) {
    searchParams.set("sort", params.sort);
  }
  (params?.source ?? []).forEach((source) => searchParams.append("source", source));
  if (params?.subject) {
    searchParams.set("subject", params.subject);
  }
  (params?.tags ?? []).forEach((tag) => searchParams.append("tag", tag));
  const query = searchParams.size > 0 ? `?${searchParams.toString()}` : "";
  const response = await fetch(buildUrl(`/notes/public${query}`), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<PublicNoteListResponse>(response, "Could not load public notes.");
}

export async function listPublicLibraryDiscoverySections(): Promise<PublicLibraryDiscoverySectionsResponse> {
  const response = await fetch(buildUrl("/notes/public/discovery-sections"), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<PublicLibraryDiscoverySectionsResponse>(
    response,
    "Could not load public library discovery sections.",
  );
}

export async function listPublicLearnerLevels(): Promise<LearnerLevel[]> {
  const response = await fetch(buildUrl("/notes/public/learner-levels"), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<LearnerLevel[]>(response, "Could not load authored depths.");
}

export async function togglePublicNoteLike(noteId: string): Promise<PublicNoteLikeResponse> {
  const response = await fetchWithAuth(
    `/notes/public/${noteId}/like`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<PublicNoteLikeResponse>(response, "Could not update note like.");
}

export async function listSubjects(scope: SubjectSuggestionScope = "public"): Promise<string[]> {
  const path = `/subjects?scope=${scope}`;
  const response = scope === "mine"
    ? await fetchWithAuth(
        path,
        {
          method: "GET",
          headers: buildAuthHeaders(),
        },
        true,
      )
    : await fetch(buildUrl(path), {
        method: "GET",
        headers: buildAuthHeaders(),
      });
  return parseApiResponse<string[]>(response, "Could not load subjects.");
}

export async function listCoursePrograms(scope: CourseProgramSuggestionScope = "public"): Promise<string[]> {
  const path = `/course-programs?scope=${scope}`;
  const response = scope === "mine"
    ? await fetchWithAuth(
        path,
        {
          method: "GET",
          headers: buildAuthHeaders(),
        },
        true,
      )
    : await fetch(buildUrl(path), {
        method: "GET",
        headers: buildAuthHeaders(),
      });
  return parseApiResponse<string[]>(response, "Could not load course/program suggestions.");
}

export async function getCourseProgramCatalog(): Promise<CourseProgramCatalogItem[]> {
  const response = await fetchWithAuth(
    "/course-program-catalog",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<CourseProgramCatalogItem[]>(response, "Could not load the course program catalog.");
}

function parseCourseProgramCatalogItem(payload: unknown, fallbackMessage: string): CourseProgramCatalogItem {
  if (
    typeof payload !== "object"
    || payload === null
    || !("id" in payload)
    || typeof payload.id !== "string"
    || !("name" in payload)
    || typeof payload.name !== "string"
    || !("programFamilyId" in payload)
    || (payload.programFamilyId !== null && typeof payload.programFamilyId !== "string")
    || !("programFamilyName" in payload)
    || (payload.programFamilyName !== null && typeof payload.programFamilyName !== "string")
  ) {
    throw new Error(fallbackMessage);
  }
  return payload as CourseProgramCatalogItem;
}

export async function createCourseProgram(request: CreateCourseProgramRequest): Promise<CourseProgramCatalogItem> {
  const fallbackMessage = "Could not add the Course / Program to the catalog.";
  const response = await fetchWithAuth(
    "/course-program-catalog",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
    },
    true,
  );
  const payload = await parseApiResponse<unknown>(response, fallbackMessage);
  return parseCourseProgramCatalogItem(payload, fallbackMessage);
}

export async function findSimilarCoursePrograms(name: string): Promise<CourseProgramCatalogItem[]> {
  const fallbackMessage = "Could not check for similar Course / Programs.";
  const response = await fetchWithAuth(
    `/course-program-catalog/similar?name=${encodeURIComponent(name)}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  const payload = await parseApiResponse<unknown>(response, fallbackMessage);
  if (!Array.isArray(payload)) {
    throw new Error(fallbackMessage);
  }
  return payload.map((item) => parseCourseProgramCatalogItem(item, fallbackMessage));
}

export async function getNoteApplicablePrograms(noteId: string): Promise<NoteApplicableProgramsResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/applicable-programs`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  const payload = await parseApiResponse<unknown>(response, "Could not load Course / Program(s).");
  if (
    typeof payload !== "object"
    || payload === null
    || !("programs" in payload)
    || !Array.isArray(payload.programs)
    || !("courseProgramShadowed" in payload)
    || typeof payload.courseProgramShadowed !== "boolean"
  ) {
    throw new Error("Could not load Course / Program(s).");
  }
  const programs = payload.programs.filter((program): program is ApplicableProgram => (
    typeof program === "object"
    && program !== null
    && "id" in program
    && typeof program.id === "string"
    && "name" in program
    && typeof program.name === "string"
  ));
  // The backend serves this as a derived value that may legitimately be null ("nothing resolved"), so a
  // missing or non-string field is normalized to null rather than left undefined. Without this the field
  // is absent at runtime and the writing-domain line never renders, which no mocked test would catch.
  const effectiveWritingDomain = "effectiveWritingDomain" in payload
    && typeof payload.effectiveWritingDomain === "string"
    ? payload.effectiveWritingDomain
    : null;
  return {
    programs,
    courseProgramShadowed: payload.courseProgramShadowed,
    effectiveWritingDomain,
  };
}

export async function replaceNoteApplicablePrograms(
  noteId: string,
  courseProgramIds: string[],
): Promise<ApplicableProgram[]> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/applicable-programs`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ courseProgramIds }),
    },
    true,
  );
  return parseApiResponse<ApplicableProgram[]>(response, "Could not save Course / Program(s).");
}

export async function listTags(scope: "public" = "public"): Promise<string[]> {
  const response = await fetch(buildUrl(`/tags?scope=${scope}`), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<string[]>(response, "Could not load tags.");
}

export async function getPublicNote(noteId: string): Promise<PublicNoteDetailResponse> {
  const response = await fetch(buildUrl(`/notes/public/${noteId}`), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<PublicNoteDetailResponse>(response, "Could not load this public note.");
}

export async function getPublicNoteBySeoPath(
  subject: string,
  slug: string,
): Promise<PublicNoteDetailResponse> {
  const response = await fetch(buildUrl(`/notes/public/seo/${subject}/${slug}`), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<PublicNoteDetailResponse>(response, "Could not load this public note.");
}

export async function getPublicProfile(userId: string): Promise<PublicProfileResponse> {
  const response = await fetch(buildUrl(`/public/profile/${userId}`), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<PublicProfileResponse>(response, "Could not load this public profile.");
}

export async function getPublicCreatorProfile(username: string): Promise<PublicProfileResponse> {
  const response = await fetch(buildUrl(`/public/creator/${username}`), {
    method: "GET",
    headers: buildAuthHeaders(),
  });
  return parseApiResponse<PublicProfileResponse>(response, "Could not load this public profile.");
}

export async function getCreatorImpact(): Promise<CreatorImpactResponse> {
  const response = await fetchWithAuth(
    "/creator-impact/me",
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<CreatorImpactResponse>(response, "Could not load your impact.");
}

export async function updateNote(
  noteId: string,
  request: UpsertNoteRequest,
  options: { keepalive?: boolean } = {},
): Promise<NoteResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify(request),
      keepalive: options.keepalive ?? false,
    },
    true,
  );
  return parseApiResponse<NoteResponse>(response, "Could not save note.");
}

export async function deleteNote(noteId: string): Promise<void> {
  const response = await fetchWithAuth(
    `/notes/${noteId}`,
    {
      method: "DELETE",
      headers: buildAuthHeaders(),
    },
    true,
  );
  if (!response.ok) {
    await parseApiResponse<void>(response, "Could not delete note.");
  }
}

export function isEmailNotVerifiedError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError
    && (error.code === "EMAIL_NOT_VERIFIED" || error.code === "EMAIL_VERIFICATION_REQUIRED");
}

export function isOcrLimitReachedError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "OCR_LIMIT_REACHED";
}

export function isOcrDisabledError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === OCR_DISABLED_ERROR_CODE;
}

export function isNoteGenerationLimitReachedError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "NOTE_GENERATION_LIMIT_REACHED";
}

/**
 * A note edit was rejected because a Study Pack is being generated for that note right now.
 *
 * Callers must surface this WITHOUT discarding what the user typed: the edit is still valid, it is
 * only the timing that is wrong.
 */
export function isNoteGenerationInProgressError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "NOTE_GENERATION_IN_PROGRESS";
}

export function isExportLimitReachedError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "MONTHLY_EXPORT_LIMIT_REACHED";
}

export function isQuestionCountNotAllowedError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "QUESTION_COUNT_NOT_ALLOWED";
}

export function isMultipleExamVersionsNotAllowedError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "MULTIPLE_EXAM_VERSIONS_NOT_ALLOWED";
}

export function isQuizShareLinkLimitExceededError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "QUIZ_SHARE_LINK_LIMIT_EXCEEDED";
}

export function isNotEnoughNewQuestionsError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "NOT_ENOUGH_NEW_QUESTIONS";
}

export function isNotEnoughMissedChallengeQuestionsError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError && error.code === "NOT_ENOUGH_MISSED_CHALLENGE_QUESTIONS";
}

export async function updateNoteVisibility(noteId: string, visibility: NoteVisibility): Promise<NoteResponse> {
  const response = await fetchWithAuth(
    `/notes/${noteId}/visibility`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ visibility }),
    },
    true,
  );
  return parseApiResponse<NoteResponse>(response, "Could not update note visibility.");
}

export async function copyNote(noteId: string, options?: { includeStudyPack?: boolean }): Promise<NoteResponse> {
  const params = options?.includeStudyPack === false ? "?includeStudyPack=false" : "";
  const response = await fetchWithAuth(
    `/notes/${noteId}/copy${params}`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteResponse>(response, "Could not copy note.");
}

export async function copyNoteOnSignup(publicNoteId: string): Promise<{ noteId: string }> {
  const response = await fetchWithAuth(
    "/notes/copy-on-signup",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ publicNoteId }),
    },
    true,
  );
  return parseApiResponse<{ noteId: string }>(response, "Could not copy note.");
}

export async function inviteLinkedLearner(
  email: string,
  inviterRole: LinkedLearnerSide,
  /** Required only when the inviter IS the learner and has no year recorded yet. */
  learnerBirthYear: number | null = null,
): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    "/linked-learners/invite",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ email, inviterRole, learnerBirthYear }),
    },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not send the invitation.");
}

export async function getLinkedLearners(): Promise<LinkedLearnerResponse[]> {
  const response = await fetchWithAuth(
    "/linked-learners",
    { headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<LinkedLearnerResponse[]>(response, "Could not load your connections.");
}

export async function getLinkedLearnerProgress(
  relationshipId: string,
): Promise<LinkedLearnerProgressResponse> {
  const response = await fetchWithAuth(
    `/linked-learners/${relationshipId}/progress`,
    { headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<LinkedLearnerProgressResponse>(response, "Could not load this learner's progress.");
}

export async function setLinkedLearnerActivityGrant(
  relationshipId: string,
  granted: boolean,
): Promise<{ granted: boolean }> {
  const response = await fetchWithAuth(
    `/linked-learners/${relationshipId}/grants/activity`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ granted }),
    },
    true,
  );
  return parseApiResponse<{ granted: boolean }>(response, "Could not update activity sharing.");
}

export async function setLinkedLearnerProgressGrant(
  relationshipId: string,
  granted: boolean,
): Promise<{ granted: boolean }> {
  const response = await fetchWithAuth(
    `/linked-learners/${relationshipId}/grants/progress`,
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ granted }),
    },
    true,
  );
  return parseApiResponse<{ granted: boolean }>(response, "Could not update progress sharing.");
}

export async function getLinkedLearnerActivity(
  relationshipId: string,
): Promise<LinkedLearnerActivityResponse> {
  const response = await fetchWithAuth(
    `/linked-learners/${relationshipId}/activity`,
    { headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<LinkedLearnerActivityResponse>(response, "Could not load study activity.");
}

export type LinkedLearnerInvitationResponse = {
  id: string;
  incoming: boolean;
  inviterRole: "SUPPORTER" | "LEARNER";
  invitedEmail: string;
  /** Null for an outgoing invitation: the inviter typed the address and must learn nothing more. */
  inviterName: string | null;
  createdAt: string;
  expiresAt: string;
  /** Server-computed so the expiry boundary never depends on the browser clock. */
  expired: boolean;
};

export async function listLinkedLearnerInvitations(): Promise<LinkedLearnerInvitationResponse[]> {
  const response = await fetchWithAuth(
    "/linked-learners/invitations",
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<LinkedLearnerInvitationResponse[]>(response, "Could not load invitations.");
}

export async function acceptLinkedLearnerInvitation(
  invitationId: string,
  learnerBirthYear: number | null,
  guardianConsentAttested: boolean,
): Promise<LinkedLearnerResponse> {
  const response = await fetchWithAuth(
    `/linked-learners/invitations/${invitationId}/accept`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ learnerBirthYear, guardianConsentAttested }),
    },
    true,
  );
  return parseApiResponse<LinkedLearnerResponse>(response, "Could not accept the invitation.");
}

export async function revokeLinkedLearnerInvitation(invitationId: string): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/linked-learners/invitations/${invitationId}/revoke`,
    { method: "POST", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not withdraw the invitation.");
}

export type LinkedLearnerInvitationLinkResponse = {
  id: string;
  token: string;
  url: string;
  creatorRole: LinkedLearnerSide;
  createdAt: string;
  expiresAt: string;
};

export type LinkedLearnerInvitationLinkResolveResponse = {
  inviterName: string;
  inviterRole: LinkedLearnerSide;
};

export async function createLinkedLearnerInvitationLink(
  creatorRole: LinkedLearnerSide,
  learnerBirthYear: number | null,
): Promise<LinkedLearnerInvitationLinkResponse> {
  const response = await fetchWithAuth(
    "/linked-learners/invitation-links",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ creatorRole, learnerBirthYear }),
    },
    true,
  );
  return parseApiResponse<LinkedLearnerInvitationLinkResponse>(response, "Could not create the invitation link.");
}

export async function listLinkedLearnerInvitationLinks(): Promise<LinkedLearnerInvitationLinkResponse[]> {
  const response = await fetchWithAuth(
    "/linked-learners/invitation-links",
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<LinkedLearnerInvitationLinkResponse[]>(response, "Could not load invitation links.");
}

export async function revokeLinkedLearnerInvitationLink(linkId: string): Promise<SimpleMessageResponse> {
  const response = await fetchWithAuth(
    `/linked-learners/invitation-links/${linkId}/revoke`,
    { method: "POST", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<SimpleMessageResponse>(response, "Could not revoke the invitation link.");
}

export async function resolveLinkedLearnerInvitationLink(
  token: string,
): Promise<LinkedLearnerInvitationLinkResolveResponse> {
  const response = await fetchWithAuth(
    `/linked-learners/invitation-links/${encodeURIComponent(token)}/resolve`,
    { method: "GET", headers: buildAuthHeaders() },
    true,
  );
  return parseApiResponse<LinkedLearnerInvitationLinkResolveResponse>(
    response,
    "This invitation link is not available.",
  );
}

export async function redeemLinkedLearnerInvitationLink(
  token: string,
  learnerBirthYear: number | null,
): Promise<{ relationshipId: string; status: "PENDING" }> {
  const response = await fetchWithAuth(
    `/linked-learners/invitation-links/${encodeURIComponent(token)}/redeem`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ learnerBirthYear }),
    },
    true,
  );
  return parseApiResponse<{ relationshipId: string; status: "PENDING" }>(
    response,
    "Could not use this invitation link.",
  );
}

export async function acceptLinkedLearner(
  relationshipId: string,
  learnerBirthYear: number | null,
  guardianConsentAttested: boolean,
): Promise<LinkedLearnerResponse> {
  const response = await fetchWithAuth(
    `/linked-learners/${relationshipId}/accept`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ learnerBirthYear, guardianConsentAttested }),
    },
    true,
  );
  return parseApiResponse<LinkedLearnerResponse>(response, "Could not accept the invitation.");
}

export async function recordLinkedLearnerBirthYear(
  relationshipId: string,
  birthYear: number,
): Promise<LinkedLearnerResponse> {
  const response = await fetchWithAuth(
    `/linked-learners/${relationshipId}/birth-year`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ birthYear }),
    },
    true,
  );
  return parseApiResponse<LinkedLearnerResponse>(response, "Could not save the birth year.");
}

export async function previewLinkedLearnerBirthYearCorrection(
  birthYear: number,
): Promise<LinkedLearnerBirthYearCorrectionPreviewResponse> {
  const response = await fetchWithAuth(
    "/linked-learners/birth-year/correction-preview",
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ birthYear }),
    },
    true,
  );
  return parseApiResponse<LinkedLearnerBirthYearCorrectionPreviewResponse>(
    response,
    "Could not check this birth year correction.",
  );
}

export async function correctLinkedLearnerBirthYear(
  birthYear: number,
): Promise<LinkedLearnerResponse[]> {
  const response = await fetchWithAuth(
    "/linked-learners/birth-year",
    {
      method: "PUT",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ birthYear }),
    },
    true,
  );
  return parseApiResponse<LinkedLearnerResponse[]>(response, "Could not correct your birth year.");
}

export async function recordLinkedLearnerGuardianConsent(
  relationshipId: string,
): Promise<LinkedLearnerResponse> {
  const response = await fetchWithAuth(
    `/linked-learners/${relationshipId}/guardian-consent`,
    {
      method: "POST",
      headers: buildAuthHeaders("application/json"),
      body: JSON.stringify({ attested: true }),
    },
    true,
  );
  return parseApiResponse<LinkedLearnerResponse>(response, "Could not record guardian consent.");
}

export async function revokeLinkedLearner(relationshipId: string): Promise<LinkedLearnerResponse> {
  const response = await fetchWithAuth(
    `/linked-learners/${relationshipId}/revoke`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<LinkedLearnerResponse>(response, "Could not revoke the connection.");
}

export type NoteRegenerationScopeValue = "STUDY_PACK" | "NOTE_AND_STUDY_PACK";

export type NoteRegenerationPreflightItem = {
  noteId: string;
  title: string | null;
  readiness: string;
  reasonCode: string | null;
  reason: string | null;
};

export type NoteRegenerationPreflightResponse = {
  scope: NoteRegenerationScopeValue;
  requestedCount: number;
  readyCount: number;
  blockedCount: number;
  notEligibleCount: number;
  publicNotesAffected: number;
  sharedQuizzesToDeactivate: number;
  noteGenerationUnitsRequired: number;
  noteGenerationUnitsRemaining: number;
  studyPackUnitsRequired: number;
  studyPackUnitsRemaining: number;
  quotaExceeded: boolean;
  itemsToRemove: number;
  maxBatchSize: number;
  items: NoteRegenerationPreflightItem[];
};

export type BulkRegenerateNotesResponse = {
  batchId: string;
  scope: NoteRegenerationScopeValue;
  acceptedCount: number;
};

export type NoteBulkRegenerationItemReceipt = {
  noteId: string;
  title: string | null;
  state: "PENDING" | "RUNNING" | "REGENERATED" | "BLOCKED" | "FAILED" | "NOT_RUN";
  reasonCode: string | null;
  reason: string | null;
  shareLinkDeactivated: boolean;
};

export type NoteBulkRegenerationReceiptResponse = {
  batchId: string;
  scope: NoteRegenerationScopeValue;
  totalCount: number;
  regeneratedCount: number;
  blockedCount: number;
  failedCount: number;
  notRunCount: number;
  pendingCount: number;
  finished: boolean;
  stale: boolean;
  retryableNoteIds: string[];
  items: NoteBulkRegenerationItemReceipt[];
};

export async function preflightNoteRegeneration(
  noteIds: string[],
  scope: NoteRegenerationScopeValue,
): Promise<NoteRegenerationPreflightResponse> {
  const response = await fetchWithAuth(
    "/notes/regenerate/preflight",
    {
      method: "POST",
      headers: buildAuthHeaders(),
      body: JSON.stringify({ noteIds, scope }),
    },
    true,
  );
  return parseApiResponse<NoteRegenerationPreflightResponse>(response, "Could not check these notes.");
}

export async function bulkRegenerateNotes(
  noteIds: string[],
  scope: NoteRegenerationScopeValue,
): Promise<BulkRegenerateNotesResponse> {
  const response = await fetchWithAuth(
    "/notes/bulk-regenerate",
    {
      method: "POST",
      headers: buildAuthHeaders(),
      body: JSON.stringify({ noteIds, scope }),
    },
    true,
  );
  return parseApiResponse<BulkRegenerateNotesResponse>(response, "Could not start regeneration.");
}

export async function getBulkRegenerationReceipt(
  batchId: string,
): Promise<NoteBulkRegenerationReceiptResponse> {
  const response = await fetchWithAuth(
    `/notes/bulk-regenerate/${batchId}`,
    {
      method: "GET",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<NoteBulkRegenerationReceiptResponse>(response, "Could not load the regeneration receipt.");
}

/**
 * Re-runs the FAILED items of a batch as a NEW batch.
 *
 * ⚠️ Takes a batch id, never a note list: the server derives which items failed, so "retry only the
 * failed ones" is a server guarantee rather than something this client is trusted to get right on a
 * path that spends metered units.
 */
export async function retryBulkRegeneration(
  batchId: string,
): Promise<BulkRegenerateNotesResponse> {
  const response = await fetchWithAuth(
    `/notes/bulk-regenerate/${batchId}/retry`,
    {
      method: "POST",
      headers: buildAuthHeaders(),
    },
    true,
  );
  return parseApiResponse<BulkRegenerateNotesResponse>(response, "Could not retry the failed notes.");
}
