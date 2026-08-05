# Slice 3 Program Family expansion — consultation prompt for product UX GPT

Paste everything below the line. Written in the owner's voice, self-contained — no NoteLib context assumed.

**Three things to watch in the reply:**

1. **GPT cannot authoritatively verify PRC board syllabi.** It may produce confident-sounding program groupings that are wrong. The prompt asks it to separate what it is confident about from what needs the real syllabus checked. Hold it to that — this is the same failure mode `07` was written to guard against, and the reason that gate still exists.
2. **The prompt deliberately states a house recommendation** (unconditional expansion) and asks GPT to argue against it. If the reply simply agrees, push back once — agreement that costs nothing is not a second opinion.
3. **Question 2 is the one most likely to be answered lazily.** "Add a health sciences family" is easy to say; the useful answer is whether family expansion earns its complexity *at all* at NoteLib's current catalog size.

---

# NoteLib — Program Family expansion: four decisions

I need a second opinion on four product-architecture decisions. Please treat this as a **product architecture discussion**, not an implementation request. I am not asking for code.

## Context you need

NoteLib is a notes-first study workspace for Philippine learners, with a strong board-exam-review segment. Users capture **Notes**; each Note can generate an AI Study Pack and practice quizzes. We also publish curated **Official Review Sets** — hierarchical collections assembling Notes into a study journey:

```
Civil Engineering Review Set        (root collection)
└── Engineering Mathematics         (subject-plan child collection)
    └── individual Notes            (subject = Algebra, Trigonometry, Calculus…)
```

### The architecture this sits inside — decided, not up for re-litigation

A Note used to carry a single free-text `Course / Program` field doing five jobs at once. We split it into four independent axes:

| Axis | Cardinality | Owns |
|---|---|---|
| **Subject** | one | *what* the note is about (Algebra) |
| **Domain Context** | one | ***how* it is authored** — the only domain constraint sent to the LLM |
| **Note Learner Level** | one | *how deep* it is authored |
| **Applicable Programs** | **many** | ***where* it appears** — discovery only, never reaches a prompt |

The motivating case: one Algebra note is applicable to many engineering programs. Under one-program-per-note we would have to duplicate it per program, so authoring was deliberately halted rather than create that duplication.

**Applicable Programs now works.** Notes carry explicit program rows; curators (Teacher/Admin) add and remove them; and library filters, facets, card badges, and public search all read them. A note applicable to three programs is discoverable under all three. That shipped and is live in our release branch.

### The one piece left, and what it is

**Program Families** are an **authoring shortcut**. Instead of ticking five programs one by one, an author picks a family (e.g. `Engineering`) and the system fills in explicit program rows at save time — which the author can then edit or trim. The rows are always explicit; applicability is never inferred from a family when reading. That rule is settled.

What is *not* settled is what a family should expand to. That is a curriculum question, and it is the last thing blocking this work.

### The numbers you need, because they are smaller than they sound

Our program catalog holds **21 programs**, and exactly **one** family:

- **`Engineering` family:** Civil Engineering, Electrical Engineering, Mechanical Engineering — **3 members.**
- **No family:** Accountancy, Architecture, Aviation, Business Administration, Criminology, Education, Information Technology, Law, Medicine, Nursing, Pharmacy, Physical Therapy, Psychology, Radiologic Technology, Senior High – ABM, Senior High – HUMSS, Senior High – STEM, Special Needs Education – Generalist.

Our own planning documents phrase the open question as *"is Engineering Sciences shared by all **11** engineering programs, or a subset?"* — but that "11" was reasoning about Philippine engineering education generally. **Our catalog has 3.** So today, selecting `Engineering` would fill in three rows.

The catalog deliberately **excludes** 11 further values that exist as legacy text on notes — bare school levels, exam goals, bare subjects, and the word "Engineering" itself as a family rather than a program. Exclusion is intentional; those notes keep their text label and simply get no structured program row.

## The four decisions

### 1. Do we add more engineering programs to the catalog?

Philippine engineering has many more board programs (Chemical, Electronics, Industrial, Computer, Geodetic, Sanitary, Mining, Metallurgical, Naval Architecture, Agricultural…). We have three because those are where we have content.

This is a product/expansion call, and it decides whether "8 vs 11 programs" is a real question or a moot one. **Should the catalog lead content (add programs we cannot yet serve, so notes can be marked applicable to them now), or follow it (add a program when we have material for it)?** What does each choice cost a learner who filters by a program with three notes in it?

**One decided precedent bears directly on this, and I do not want advice that contradicts it without knowing it did.** When we built the catalog we deliberately excluded 11 values that exist as text on real notes — and two of those exclusions were programs with actual content: `Software Engineering` (which had notes) and `Computer Science` (which had user profiles but no notes). Both were ruled out as catalog programs pending real curriculum, and their notes keep their text label with no structured row. So the catalog's established philosophy is already **follow, not lead** — a program earns a row by having curriculum behind it, not by being plausible. If you think we should now reverse that for engineering, say so explicitly and give the reason the earlier ruling was wrong, rather than treating it as an open field.

### 2. Should we create more families — and does this feature earn its complexity at our size?

`Engineering` is our only family, with 3 members. Candidate groupings visible in the catalog:

- **Health sciences:** Nursing, Pharmacy, Physical Therapy, Medicine, Radiologic Technology
- **Education:** Education, Special Needs Education – Generalist
- **Senior High:** the three strands (ABM, HUMSS, STEM)

**Please answer the harder version of this question first: at 3-to-5 members per family, is a family shortcut worth building at all**, versus letting curators tick 3 boxes? What would have to be true — catalog size, notes per curator session, how often a note genuinely spans a whole family — for the shortcut to pay for itself? I would rather cut this than ship a shortcut that saves two clicks.

If families *are* worth it, which groupings are real curricular families as opposed to convenient bins? I am suspicious of "Health sciences" specifically: a Nursing pharmacology note and a Medicine pharmacology note may be genuinely different artifacts, not one shared note.

### 3. Should a family expand to *all* its members, or a curated subset?

Our data model currently stores only *membership* — each program optionally points at one family. There is no structure for "family X expands to subset Y."

If expansion means "all members," we build nothing new. If a family should sometimes expand to a subset, we need a new structure and a curation surface to maintain it. **Is the subset case real enough to pay for, or is trim-after-expand sufficient?**

### 4. Should expansion depend on the note's subject?

This is the biggest fork. An early internal taxonomy doc proposed that `Engineering Mathematics` subjects (Algebra, Calculus, Differential Equations) apply to *all* engineering programs, while `Engineering Sciences` subjects (Statics, Dynamics, Thermodynamics, Fluid Mechanics, Strength of Materials) apply to *most but not all* — and warned against assuming they are universal.

So: should picking `Engineering` on an Algebra note fill in a different set than on a Thermodynamics note?

- **Unconditional:** one behavior, no subject logic. The author trims what does not apply.
- **Subject-conditioned:** we maintain a subject→program-subset map. More accurate defaults, but a real curation burden — and it couples two axes we just deliberately separated (*where a note appears* vs *how it is authored*).

**My current preference is unconditional, and I would like you to argue against it.** My reasoning: the family is described as an authoring shortcut, the author sees the filled-in rows immediately and can trim them, and per-note judgment is where applicability should live anyway. The strongest counter I can see is that defaults are sticky — an author in a hurry accepts them, so a wrong default silently becomes wrong data at scale. Tell me if that counter is decisive, or if there is a better one.

## What I want back

1. A recommendation on each of the four, with reasoning I can disagree with — not a summary of the options.
2. **Explicitly separate** what you are confident about as a product/UX matter from what depends on actual PRC board syllabus content you cannot verify. Where a claim needs the real syllabus checked, say so plainly rather than asserting the grouping. I would rather have "this needs checking" than a confident wrong list.
3. If you think the honest answer to question 2 is "don't build this," say that. Cutting the slice is an acceptable outcome and I would rather hear it now.
4. Anything I have not asked that this decision depends on.
