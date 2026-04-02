export type LearnGuideSection = {
  heading: string;
  paragraphs: string[];
};

export type LearnGuidePracticeQuestion = {
  question: string;
  answer: string;
};

export type LearnGuideCategory = "students" | "board-exams" | "teachers" | "study-tips";

export type LearnGuide = {
  slug: string;
  category: LearnGuideCategory;
  title: string;
  description: string;
  intro: string;
  summary: string[];
  sections: LearnGuideSection[];
  practiceQuestions: LearnGuidePracticeQuestion[];
};

type LearnGuideSeed = Omit<LearnGuide, "summary" | "practiceQuestions">;
type LearnGuideArticleContent = Pick<LearnGuide, "summary" | "practiceQuestions">;

export type LearnGuideCategoryMeta = {
  key: LearnGuideCategory;
  title: string;
  description: string;
};

export const learnGuideCategories: LearnGuideCategoryMeta[] = [
  {
    key: "students",
    title: "For Students",
    description: "Guides for turning class notes into reviewers, practice questions, and faster exam prep.",
  },
  {
    key: "board-exams",
    title: "For Board Exams",
    description: "Guides for turning reviewer notes into practice questions and finding weak areas before exam day.",
  },
  {
    key: "teachers",
    title: "For Teachers",
    description: "Guides for turning lesson notes into quiz and reviewer materials faster.",
  },
  {
    key: "study-tips",
    title: "Study Tips",
    description: "Guides about active recall, practice questions, and how to study smarter.",
  },
];

const learnGuideSeeds: LearnGuideSeed[] = [
  {
    slug: "how-to-turn-notes-into-reviewers",
    category: "students",
    title: "How to Turn Notes Into Reviewers",
    description: "Turn one lesson note into a reviewer you can actually use before quizzes and exams.",
    intro:
      "A reviewer should make your next study session easier, not just repeat the same notes. The fastest way to get there is to shorten the lesson, pull out the key concepts, and practice recall with questions.",
    sections: [
      {
        heading: "Start with one focused note",
        paragraphs: [
          "Keep each note focused on one lesson or topic so the reviewer stays clear and easier to revisit.",
          "When one note tries to cover too many chapters, the reviewer becomes harder to scan before an exam.",
        ],
      },
      {
        heading: "Pull out the important ideas first",
        paragraphs: [
          "Summaries and key concepts help you see the structure of the lesson before you start memorizing details.",
          "That makes it easier to tell which topics need more review and which ones are already clear.",
        ],
      },
      {
        heading: "Turn the reviewer into recall practice",
        paragraphs: [
          "A reviewer becomes more useful when it leads directly into practice questions.",
          "That is how you move from reading notes to checking whether you can actually remember the material.",
        ],
      },
    ],
  },
  {
    slug: "how-to-study-using-practice-questions",
    category: "students",
    title: "How to Study Using Practice Questions",
    description: "Use practice questions to turn passive rereading into actual recall before a quiz.",
    intro:
      "Practice questions help you see whether you really know the lesson or only recognize it when you look at the notes. That makes them one of the fastest ways to prepare for quizzes.",
    sections: [
      {
        heading: "Use questions right after reviewing",
        paragraphs: [
          "Answering questions right after a lesson helps move information from short-term familiarity into stronger recall.",
          "This works better than waiting until the night before the exam to test yourself.",
        ],
      },
      {
        heading: "Pay attention to the questions you miss",
        paragraphs: [
          "Missed questions show which concepts need another quick review block.",
          "That means you spend less time rereading everything and more time fixing what is still weak.",
        ],
      },
      {
        heading: "Repeat in short cycles",
        paragraphs: [
          "Read, answer a few questions, review mistakes, then repeat on the next session.",
          "Short practice cycles make it easier to keep studying consistent across the week.",
        ],
      },
    ],
  },
  {
    slug: "how-to-review-faster-for-exams",
    category: "students",
    title: "How to Review Faster for Exams",
    description: "A faster review method built around reviewers, practice questions, and weak-area tracking.",
    intro:
      "Reviewing faster does not mean rushing. It means using a study loop that turns notes into something you can scan, test, and improve without starting over every time.",
    sections: [
      {
        heading: "Shorten the lesson before you memorize",
        paragraphs: [
          "Start with a summary and key concepts so you can see the lesson clearly before drilling details.",
          "This makes each review session easier to restart when time is limited.",
        ],
      },
      {
        heading: "Use quizzes to find weak areas fast",
        paragraphs: [
          "Questions quickly show whether a topic still feels shaky.",
          "That saves time because you can review weak concepts directly instead of rereading the whole chapter.",
        ],
      },
      {
        heading: "Review the weak areas, not everything",
        paragraphs: [
          "The fastest review is focused review.",
          "Once you know which ideas are weak, you can spend the next session improving only those parts.",
        ],
      },
    ],
  },
  {
    slug: "how-to-use-notelib-for-board-exam-review",
    category: "board-exams",
    title: "How to Use NoteLib for Board Exam Review",
    description: "Use NoteLib as a board-exam review loop: reviewer, questions, weak areas, and repeat practice.",
    intro:
      "Board exam review works better when you turn reviewer notes into practice questions and then use weak areas to decide what to study next. That is the review loop NoteLib is built for.",
    sections: [
      {
        heading: "Start with your reviewer notes",
        paragraphs: [
          "Use one reviewer, topic outline, or lecture note set as the base for your session.",
          "A consistent source makes your practice questions easier to trust and easier to revisit.",
        ],
      },
      {
        heading: "Turn the reviewer into questions",
        paragraphs: [
          "Board exams reward recall, not rereading. Practice questions force you to retrieve what you know.",
          "That makes them a better study signal than simply highlighting more pages.",
        ],
      },
      {
        heading: "Use weak areas to plan the next session",
        paragraphs: [
          "The concepts you miss should shape the next review block.",
          "Over time that gives you a more targeted board-exam routine instead of repeating the same full reviewer every week.",
        ],
      },
    ],
  },
  {
    slug: "how-to-turn-reviewers-into-practice-questions",
    category: "board-exams",
    title: "How to Turn Reviewers Into Practice Questions",
    description: "Convert long reviewer notes into practice questions you can answer in short focused sessions.",
    intro:
      "Reviewer notes are useful, but questions make them active. Turning a reviewer into practice questions is one of the simplest ways to make board-exam review more effective.",
    sections: [
      {
        heading: "Break the reviewer into topics",
        paragraphs: [
          "Start with one subject block or one major topic instead of the whole reviewer at once.",
          "Smaller topic blocks lead to clearer practice questions and more usable weak-area feedback.",
        ],
      },
      {
        heading: "Use questions to check recall",
        paragraphs: [
          "Practice questions tell you whether you can explain a concept without looking.",
          "That is more useful than feeling familiar with a page you just reread.",
        ],
      },
      {
        heading: "Track which topics stay weak",
        paragraphs: [
          "When the same topic keeps showing up as weak, it needs deeper review, not just one more pass.",
          "That insight helps you decide where to spend the next board-exam study block.",
        ],
      },
    ],
  },
  {
    slug: "how-to-find-weak-areas-before-the-board-exam",
    category: "board-exams",
    title: "How to Find Weak Areas Before the Board Exam",
    description: "Use practice performance to spot weak concepts before exam week gets too close.",
    intro:
      "Weak areas are easier to fix when you find them early. Practice questions make those weak areas visible before they become last-minute surprises.",
    sections: [
      {
        heading: "Let your practice questions reveal the pattern",
        paragraphs: [
          "One missed question can be random. Repeated misses on the same concept are a real study signal.",
          "That is why your practice history matters more than one lucky or unlucky session.",
        ],
      },
      {
        heading: "Review the concept, not just the answer",
        paragraphs: [
          "If you only memorize the correct answer, the weakness usually comes back later.",
          "Reviewing the concept behind the mistake leads to better long-term recall.",
        ],
      },
      {
        heading: "Use weak areas as the next study queue",
        paragraphs: [
          "Weak concepts should become your next reviewer topics and practice targets.",
          "That keeps your board-exam review loop targeted instead of overwhelming.",
        ],
      },
    ],
  },
  {
    slug: "how-teachers-can-use-notelib-to-create-quizzes",
    category: "teachers",
    title: "How Teachers Can Use NoteLib to Create Quizzes",
    description: "Turn lesson notes into quiz questions and reviewer materials faster.",
    intro:
      "Teachers can use NoteLib as a faster first draft for quiz and reviewer creation. The workflow stays simple: start with the lesson note, generate the study pack, then refine the quiz output for class use.",
    sections: [
      {
        heading: "Start with lesson notes or review material",
        paragraphs: [
          "Using material you already teach helps the generated outputs stay aligned with your lesson objectives.",
          "That makes quiz drafting faster because the source is already familiar.",
        ],
      },
      {
        heading: "Use the quiz output as a working draft",
        paragraphs: [
          "The first output should save time, not replace teacher judgment.",
          "You can refine tone, depth, and difficulty after the first pass is generated.",
        ],
      },
      {
        heading: "Reuse the same material for reviewer support",
        paragraphs: [
          "The same lesson note can help produce both review content and quiz material.",
          "That reduces repeated formatting work during busy teaching weeks.",
        ],
      },
    ],
  },
  {
    slug: "how-to-turn-lesson-notes-into-quiz-questions",
    category: "teachers",
    title: "How to Turn Lesson Notes Into Quiz Questions",
    description: "A simple workflow for turning one lesson note into quiz-ready questions.",
    intro:
      "Lesson notes already contain the structure of your quiz. The faster path is to organize the material, generate question drafts, and then edit them for classroom use.",
    sections: [
      {
        heading: "Use one lesson objective at a time",
        paragraphs: [
          "Questions become clearer when the note stays focused on one lesson or one concept block.",
          "That also makes the resulting quiz easier to balance and revise.",
        ],
      },
      {
        heading: "Review the key concepts before finalizing",
        paragraphs: [
          "Key concepts help you see whether the quiz is covering the right parts of the lesson.",
          "This is especially helpful when building short formative assessments.",
        ],
      },
      {
        heading: "Adjust the quiz for class level",
        paragraphs: [
          "Generated questions save time, but the last pass should still match your class depth and wording.",
          "That keeps the final quiz practical for real classroom use.",
        ],
      },
    ],
  },
  {
    slug: "how-to-create-reviewer-materials-faster",
    category: "teachers",
    title: "How to Create Reviewer Materials Faster",
    description: "Build reviewer sheets and quiz support material faster from the notes you already use.",
    intro:
      "Reviewer creation takes time because teachers often repeat the same formatting work across lessons. Starting from existing lesson notes shortens that process.",
    sections: [
      {
        heading: "Reuse the notes you already have",
        paragraphs: [
          "Lesson notes, topic outlines, and class review sheets are already strong starting points.",
          "Turning them into summaries and key concepts reduces the time spent making a reviewer from scratch.",
        ],
      },
      {
        heading: "Use one workflow for review and quiz prep",
        paragraphs: [
          "The same material can support reviewer creation and quiz drafting.",
          "That makes it easier to build a consistent review package for students.",
        ],
      },
      {
        heading: "Improve the material after the first draft",
        paragraphs: [
          "Fast drafting is most useful when it leaves more time for refinement.",
          "That extra time can go into examples, class-specific context, or clearer wording.",
        ],
      },
    ],
  },
  {
    slug: "what-is-active-recall",
    category: "study-tips",
    title: "What is Active Recall",
    description: "A simple explanation of active recall and why questions work better than passive rereading.",
    intro:
      "Active recall means trying to remember information without looking at the notes first. It feels harder than rereading because it is real practice, and that is exactly why it helps.",
    sections: [
      {
        heading: "Recall is different from recognition",
        paragraphs: [
          "Seeing a familiar sentence in your notes can make you feel ready even when you cannot explain it yourself.",
          "Questions reveal whether the idea is actually staying in memory.",
        ],
      },
      {
        heading: "Questions make review active",
        paragraphs: [
          "Active recall works because it forces your brain to retrieve information instead of passively seeing it again.",
          "That makes practice questions a better review tool than endless rereading.",
        ],
      },
      {
        heading: "Short recall sessions are enough",
        paragraphs: [
          "You do not need extremely long study blocks to use active recall well.",
          "Short review sessions built around questions and weak areas are often more effective.",
        ],
      },
    ],
  },
  {
    slug: "why-practice-questions-are-effective",
    category: "study-tips",
    title: "Why Practice Questions Are Effective",
    description: "Why practice questions help students, board exam reviewees, and teachers focus on what matters.",
    intro:
      "Practice questions do more than check scores. They show what you remember, what you confuse, and what needs another review pass before exam day.",
    sections: [
      {
        heading: "Questions show what still feels weak",
        paragraphs: [
          "A question can reveal weak understanding in seconds.",
          "That is faster than finding the same weakness through another full reread.",
        ],
      },
      {
        heading: "Questions help review stay focused",
        paragraphs: [
          "Once you know which topics are weak, the next study session becomes easier to plan.",
          "You can review what needs attention instead of repeating what you already know.",
        ],
      },
      {
        heading: "Questions build exam confidence",
        paragraphs: [
          "Practice questions help the real exam feel more familiar because you are already used to retrieving information under pressure.",
          "That makes them useful across class quizzes, finals, and board-exam review.",
        ],
      },
    ],
  },
  {
    slug: "how-to-study-smarter",
    category: "study-tips",
    title: "How to Study Smarter",
    description: "A practical study loop for turning notes into recall, review, and improvement.",
    intro:
      "Studying smarter usually means spending less time on passive review and more time on the parts that actually improve recall. A simple cycle works: notes, summary, quiz, weak concepts, practice.",
    sections: [
      {
        heading: "Start with clearer notes",
        paragraphs: [
          "Focused notes are easier to review, easier to summarize, and easier to turn into questions.",
          "That gives the rest of the study loop a better foundation.",
        ],
      },
      {
        heading: "Turn review into practice",
        paragraphs: [
          "Summaries and key concepts help you prepare, but questions help you measure whether you really know the lesson.",
          "That shift turns note review into active recall.",
        ],
      },
      {
        heading: "Use weak concepts as the next step",
        paragraphs: [
          "Weak concepts tell you where the next review session should begin.",
          "That is how you keep improving without feeling like you are starting over every time.",
        ],
      },
    ],
  },
];

const learnGuideArticleContentBySlug: Record<string, LearnGuideArticleContent> = {
  "how-to-turn-notes-into-reviewers": {
    summary: [
      "A good reviewer works best when one note covers one lesson, one topic, or one concept block.",
      "Once the lesson is shortened into summaries and key concepts, practice questions turn the reviewer into something you can actually use before quizzes and exams.",
    ],
    practiceQuestions: [
      {
        question: "Why should one note focus on one lesson or topic?",
        answer: "A focused note keeps the reviewer clear, easier to scan, and easier to revisit before an exam.",
      },
      {
        question: "What do summaries and key concepts help you do before memorizing details?",
        answer: "They help you see the structure of the lesson and spot which topics still need more review.",
      },
      {
        question: "When does a reviewer become more useful?",
        answer: "It becomes more useful when it leads directly into recall practice through questions.",
      },
    ],
  },
  "how-to-study-using-practice-questions": {
    summary: [
      "Practice questions turn studying from passive recognition into active recall.",
      "The best routine is to review briefly, answer questions right away, and use mistakes to decide what to revisit next.",
    ],
    practiceQuestions: [
      {
        question: "Why should you answer questions right after reviewing a lesson?",
        answer: "Answering right after review strengthens recall while the lesson is still fresh instead of waiting until the night before the exam.",
      },
      {
        question: "What should you do with questions you miss?",
        answer: "Treat them as signals for the concepts that need another short review block.",
      },
      {
        question: "Why do short practice cycles work well?",
        answer: "They keep study sessions focused and easier to repeat throughout the week.",
      },
    ],
  },
  "how-to-review-faster-for-exams": {
    summary: [
      "Faster review comes from shortening the lesson, testing recall quickly, and using weak areas to guide the next session.",
      "The goal is not to reread everything faster, but to review only what still needs attention.",
    ],
    practiceQuestions: [
      {
        question: "Why start with a summary and key concepts before memorizing?",
        answer: "They make the lesson easier to restart and easier to scan when time is limited.",
      },
      {
        question: "How do quizzes help you review faster?",
        answer: "They quickly reveal which topics still feel shaky so you can focus your time.",
      },
      {
        question: "What does focused review mean?",
        answer: "It means reviewing the weak areas instead of repeating the entire chapter.",
      },
    ],
  },
  "how-to-use-notelib-for-board-exam-review": {
    summary: [
      "Board exam review becomes more effective when reviewer notes lead into practice questions and weak-area follow-up.",
      "A repeatable loop of reviewer, questions, weak areas, and targeted practice helps you improve faster than rereading alone.",
    ],
    practiceQuestions: [
      {
        question: "Why start with one reviewer or topic outline as the base?",
        answer: "A consistent source makes the resulting practice questions easier to trust and easier to revisit.",
      },
      {
        question: "Why are practice questions better than rereading for board exams?",
        answer: "They force recall, which is a better signal than simply recognizing highlighted pages.",
      },
      {
        question: "How should weak areas affect the next study session?",
        answer: "They should shape the next review block so you spend time on the concepts you keep missing.",
      },
    ],
  },
  "how-to-turn-reviewers-into-practice-questions": {
    summary: [
      "Reviewer notes become more effective when they are broken into smaller topic blocks and turned into questions.",
      "Those questions then help you spot which concepts are still weak and worth another review pass.",
    ],
    practiceQuestions: [
      {
        question: "Why break a reviewer into smaller topics before generating questions?",
        answer: "Smaller topic blocks produce clearer questions and more useful weak-area feedback.",
      },
      {
        question: "What do practice questions tell you that rereading does not?",
        answer: "They reveal whether you can explain the concept without looking at the reviewer.",
      },
      {
        question: "What does it mean when the same topic keeps showing up as weak?",
        answer: "It needs deeper review and should become a priority in the next study block.",
      },
    ],
  },
  "how-to-find-weak-areas-before-the-board-exam": {
    summary: [
      "Weak areas are easiest to fix when you spot them early through repeated practice results.",
      "The goal is to review the underlying concept, not just memorize one correct answer.",
    ],
    practiceQuestions: [
      {
        question: "Why is one missed question not enough to identify a true weak area?",
        answer: "A single miss can be random, but repeated misses on the same concept show a real pattern.",
      },
      {
        question: "Why should you review the concept instead of only the correct answer?",
        answer: "Concept review leads to stronger recall and stops the same weakness from returning later.",
      },
      {
        question: "How should weak areas be used after practice?",
        answer: "They should become the next review queue for your reviewer topics and practice targets.",
      },
    ],
  },
  "how-teachers-can-use-notelib-to-create-quizzes": {
    summary: [
      "NoteLib can speed up quiz drafting by turning lesson notes into a first draft of review content and questions.",
      "Teachers still refine the final output, but the first draft reduces repetitive setup work.",
    ],
    practiceQuestions: [
      {
        question: "Why start with lesson notes or review material you already teach?",
        answer: "Using familiar material keeps the generated outputs aligned with your lesson objectives.",
      },
      {
        question: "How should teachers treat the generated quiz output?",
        answer: "As a working draft that saves time, not as a replacement for teacher judgment.",
      },
      {
        question: "Why is it useful to reuse the same lesson note for reviewer support?",
        answer: "It lets one source create both review content and quiz material during busy weeks.",
      },
    ],
  },
  "how-to-turn-lesson-notes-into-quiz-questions": {
    summary: [
      "A lesson note already contains the structure of a quiz when it stays focused on one objective.",
      "The fastest workflow is to generate question drafts, check the key concepts, and then adjust for class level.",
    ],
    practiceQuestions: [
      {
        question: "Why should a note focus on one lesson objective at a time?",
        answer: "A focused note produces clearer questions and makes the quiz easier to balance.",
      },
      {
        question: "What do key concepts help teachers review before finalizing a quiz?",
        answer: "They show whether the quiz is covering the right parts of the lesson.",
      },
      {
        question: "What should happen after question drafts are generated?",
        answer: "Teachers should adjust tone, depth, and wording so the quiz fits the class level.",
      },
    ],
  },
  "how-to-create-reviewer-materials-faster": {
    summary: [
      "Reviewer creation gets faster when teachers start from lesson notes they already use.",
      "Using the same workflow for summaries, key concepts, and quiz prep creates a more consistent review package for students.",
    ],
    practiceQuestions: [
      {
        question: "Why are existing lesson notes a strong starting point for reviewer materials?",
        answer: "They already contain the content structure, so less time is spent building from scratch.",
      },
      {
        question: "How does one workflow help with both reviewer creation and quiz prep?",
        answer: "It lets the same material support review content and question drafting.",
      },
      {
        question: "What is the benefit of a fast first draft?",
        answer: "It leaves more time for refinement, examples, and class-specific context.",
      },
    ],
  },
  "what-is-active-recall": {
    summary: [
      "Active recall means retrieving information without looking first, which is why it feels harder than rereading.",
      "That extra effort is useful because it trains memory and makes weak areas easier to spot.",
    ],
    practiceQuestions: [
      {
        question: "What is the difference between recall and recognition?",
        answer: "Recognition feels familiar when you see the notes, but recall means you can explain the idea without looking.",
      },
      {
        question: "Why do questions make review active?",
        answer: "They force the brain to retrieve information instead of passively seeing it again.",
      },
      {
        question: "Do active recall sessions need to be long?",
        answer: "No. Short sessions built around questions and weak areas are often enough.",
      },
    ],
  },
  "why-practice-questions-are-effective": {
    summary: [
      "Practice questions do more than produce scores; they reveal weak understanding and guide the next review step.",
      "They also make exam conditions feel more familiar by training retrieval under pressure.",
    ],
    practiceQuestions: [
      {
        question: "What can a question reveal in a few seconds?",
        answer: "It can reveal whether a concept still feels weak or confusing.",
      },
      {
        question: "How do practice questions help review stay focused?",
        answer: "They show which topics need attention so you do not keep repeating what you already know.",
      },
      {
        question: "Why do practice questions build exam confidence?",
        answer: "They make retrieving information under pressure feel more familiar before the real exam.",
      },
    ],
  },
  "how-to-study-smarter": {
    summary: [
      "Studying smarter means using notes, summaries, quizzes, and weak concepts as one continuous review loop.",
      "The goal is to spend less time on passive rereading and more time on the parts that improve recall.",
    ],
    practiceQuestions: [
      {
        question: "Why do clearer notes matter in a smarter study loop?",
        answer: "Focused notes are easier to review, summarize, and turn into questions.",
      },
      {
        question: "What changes when you turn review into practice?",
        answer: "Questions help measure whether you really know the lesson instead of only recognizing it.",
      },
      {
        question: "How should weak concepts shape the next session?",
        answer: "They should tell you where to start so each new session improves the areas that still need work.",
      },
    ],
  },
};

function buildLearnGuide(guide: LearnGuideSeed): LearnGuide {
  const articleContent = learnGuideArticleContentBySlug[guide.slug];

  if (!articleContent) {
    throw new Error(`Missing Learn article content for guide: ${guide.slug}`);
  }

  return {
    ...guide,
    summary: articleContent.summary,
    practiceQuestions: articleContent.practiceQuestions,
  };
}

export const learnGuides: LearnGuide[] = learnGuideSeeds.map(buildLearnGuide);

export function getLearnGuideBySlug(slug: string): LearnGuide | null {
  return learnGuides.find((guide) => guide.slug === slug) ?? null;
}

export function getLearnGuidesByCategory(category: LearnGuideCategory): LearnGuide[] {
  return learnGuides.filter((guide) => guide.category === category);
}
