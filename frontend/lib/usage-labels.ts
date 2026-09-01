/**
 * The quiz-generation meter, split across two surfaces on purpose.
 *
 * <p>These used to be one constant, `AI_QUIZZES_USAGE_LABEL`, interpolated into both the Settings
 * meter and four pricing strings in `src/config/plans.ts`. That coupling meant a meter fix silently
 * rewrote public pricing copy, so the two are now separate values with separate jobs: the meter
 * names the metered ACT ("we generated a quiz for you"), pricing names a COUNT of things you get.
 * Keep them apart — a single shared string makes one of the two surfaces read as the other's.
 */

/**
 * ⚠️ Must stay distinct from the Challenge Quiz MODE name. `quiz-session-history.test.ts` pins it:
 * the mode keeps its name everywhere it names the mode, and this labels the quota.
 */
export const QUIZ_GENERATIONS_USAGE_LABEL = "Quiz generations";

/**
 * ⚠️ Deliberately mode-agnostic, and the second sentence is a disclosure rather than a list.
 *
 * <p>Three call sites spend this meter, and only three: `ChallengeQuizService:235` (Board Exam,
 * pooled branch), `ChallengeQuizService:343` (Challenge Quiz and Board Exam, live branch) and
 * `GeneratedQuizService:158` ("Quiz for someone"). `+5 Questions` spends nothing, so the metered
 * unit is a session or quiz CREATED — never a question and never a generation call.
 *
 * <p>It does not name today's modes, because a later multi-note session for Free and Plus rides the
 * Challenge engine and spends this same meter at `:343`; an enumeration would go stale the day that
 * ships. And it does not enumerate who has their own allowance — Board Exam increments both this
 * meter and `board_exam_used_this_month`, and Settings renders a Board Exam row directly beneath
 * this one, so any such list is falsified by a row the reader is already looking at. Naming the
 * double-spend is the only thing about Board Exam that is worth the words.
 */
export const QUIZ_GENERATIONS_USAGE_DESCRIPTION =
  "Quiz sessions we generate for you, plus quizzes you make for someone. Board Exam sessions also count against their own allowance.";

/** Pricing-surface noun for the same meter: a count of things you get, not the act of metering. */
export const GENERATED_QUIZZES_PRICING_NOUN = "generated quizzes";

/** Sentence-initial form of {@link GENERATED_QUIZZES_PRICING_NOUN}, for the comparison table's row label. */
export const GENERATED_QUIZZES_PRICING_NOUN_CAPITALIZED = "Generated quizzes";
