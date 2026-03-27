export type LearnGuideSection = {
  heading: string;
  paragraphs: string[];
};

export type LearnGuide = {
  slug: string;
  title: string;
  description: string;
  intro: string;
  sections: LearnGuideSection[];
};

export const learnGuides: LearnGuide[] = [
  {
    slug: "how-to-study-for-board-exams",
    title: "How to Study for Board Exams",
    description: "A practical study method for review season: test yourself, find weak areas, and review what still needs work.",
    intro:
      "Board exams reward recall, not rereading. A stronger study loop is to turn your notes into questions, check what you miss, and spend more time on the topics that still feel weak.",
    sections: [
      {
        heading: "Start with one reliable set of notes",
        paragraphs: [
          "Pick one reviewer, lecture note set, or topic outline and make that your source for the day.",
          "Trying to study from too many materials at once usually leads to rereading without real recall.",
        ],
      },
      {
        heading: "Turn notes into recall practice",
        paragraphs: [
          "After reading a section, quiz yourself from memory. That forces your brain to retrieve information instead of just recognizing it on the page.",
          "Short recall sessions are more useful than long passive rereading sessions when you are preparing for a licensure or board exam.",
        ],
      },
      {
        heading: "Track weak areas and revisit them",
        paragraphs: [
          "When you miss a question, mark the concept instead of just noting the score.",
          "Your next study block should start with those weak areas so your review becomes more targeted over time.",
        ],
      },
      {
        heading: "Repeat with smaller focused sessions",
        paragraphs: [
          "A repeatable loop works better than marathon review days: Notes, quiz, weak areas, review, repeat.",
          "That is the study method NoteLib is designed to support.",
        ],
      },
    ],
  },
  {
    slug: "how-to-use-notelib-for-studying",
    title: "How to Use NoteLib for Studying",
    description: "A simple workflow for new users: create a note, generate a Study Pack, review, and improve your weak topics.",
    intro:
      "NoteLib works best when you use it as a repeatable study loop instead of a one-time generator. The goal is to move from raw notes into active review as quickly as possible.",
    sections: [
      {
        heading: "Create or import your notes",
        paragraphs: [
          "Start with class notes, reviewer notes, or a topic outline. You can type, paste, or import supported files and images.",
          "Keep each note focused enough that the generated Study Pack stays tied to one lesson or topic cluster.",
        ],
      },
      {
        heading: "Generate your Study Pack",
        paragraphs: [
          "Once your note is ready, generate a Study Pack to get a summary, key concepts, and quiz-ready material.",
          "This helps you move from note collection into actual review faster.",
        ],
      },
      {
        heading: "Use Quick Review first",
        paragraphs: [
          "Quick Review is the fastest way to check whether you still remember what you just studied.",
          "Use it right after generation so the material is still fresh.",
        ],
      },
      {
        heading: "Use weak areas to guide the next review",
        paragraphs: [
          "Your missed questions show you what to revisit next.",
          "That makes each next study session more focused than simply starting from page one again.",
        ],
      },
    ],
  },
  {
    slug: "active-recall-study-method",
    title: "What is Active Recall and Why It Works",
    description: "Why self-testing beats passive rereading when you want stronger memory and better exam performance.",
    intro:
      "Active recall means pulling information out of memory without looking at your notes first. It feels harder than rereading because it is actual practice, not just exposure.",
    sections: [
      {
        heading: "Recognition is not the same as recall",
        paragraphs: [
          "A familiar sentence can make you feel prepared even when you cannot explain the idea on your own.",
          "Exams ask you to retrieve what you know, so your review method should train that same skill.",
        ],
      },
      {
        heading: "Questions reveal weak understanding",
        paragraphs: [
          "Quizzes show exactly where you are guessing, mixing concepts, or forgetting definitions.",
          "That feedback is what makes later review sessions more efficient.",
        ],
      },
      {
        heading: "Short review cycles work better",
        paragraphs: [
          "Read, test, review mistakes, then test again later. That repeated cycle strengthens memory more than another passive reread.",
          "You do not need longer study blocks. You need more deliberate retrieval practice.",
        ],
      },
    ],
  },
  {
    slug: "how-teachers-can-use-notelib",
    title: "How Teachers Can Use NoteLib to Create Quizzes",
    description: "Use lesson notes and reviewers to prepare quizzes, question banks, and review material faster.",
    intro:
      "Teachers can use NoteLib as a drafting tool for review material. A lesson note can become a summary, key concepts, and quiz material that is easier to refine for class use.",
    sections: [
      {
        heading: "Start with lesson notes or reviewer material",
        paragraphs: [
          "Upload or paste the material you already use in class so the generated outputs stay aligned with your lesson plan.",
          "This works well for quiz prep, reviewer sheets, and fast recitation prompts.",
        ],
      },
      {
        heading: "Use generated outputs as a starting draft",
        paragraphs: [
          "The goal is not to publish the first draft immediately. It is to speed up the first pass of creating summaries and quiz prompts.",
          "Teachers can then edit the output to match class depth, tone, and difficulty.",
        ],
      },
      {
        heading: "Turn weak areas into revision topics",
        paragraphs: [
          "If students commonly miss the same concept, that topic can become the basis for follow-up review material.",
          "This makes NoteLib useful not only for content creation, but also for spotting where learners need more support.",
        ],
      },
    ],
  },
];

export function getLearnGuideBySlug(slug: string): LearnGuide | null {
  return learnGuides.find((guide) => guide.slug === slug) ?? null;
}
