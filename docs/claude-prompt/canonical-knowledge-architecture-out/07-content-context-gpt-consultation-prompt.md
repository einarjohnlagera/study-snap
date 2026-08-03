# Content Context taxonomy — consultation prompt for product UX GPT

Paste everything below the line. Written in the owner's voice, self-contained — no NoteLib context assumed.

**One thing to watch in the reply:** GPT cannot authoritatively verify PRC board syllabi and may produce confident-sounding subject groupings that are wrong. The prompt asks it to separate what it is confident about from what needs the real syllabus checked. Hold it to that.

---

# NoteLib — Content Context Taxonomy Decision

I need help finalizing one specific decision. Please treat this as a **product architecture discussion**, not an implementation request.

## Context you need

NoteLib is a notes-first study workspace. Users capture Notes; each Note can generate an AI Study Pack (summary, key concepts, flashcards, memorization cards) and practice quizzes. We also publish curated **Official Review Sets** — hierarchical collections that assemble Notes into a study journey. Structure:

```
Civil Engineering Review Set        (root collection)
└── Engineering Mathematics         (subject-plan child collection)
    └── individual Notes            (subject = Algebra, Trigonometry, Calculus…)
```

We have just ratified an architectural change. **This part is decided and is not what I want re-litigated.** Until now every Note had a single `Course / Program` field, and that one field was doing five different jobs at once: it was the LLM's authoritative domain constraint during generation, the private library filter, the public library filter and search key, the exam-hub mapping key, and the note card badge. Those jobs want different cardinality, and it broke as soon as we tried to expand into engineering — where one Algebra subject is applicable to eleven Philippine engineering programs.

The ratified model splits it into four independent axes:

| Axis | Cardinality | Owns |
|---|---|---|
| **Subject** | one | *what* the note is about (Algebra) |
| **Content Context** | one | ***how* it is authored** — the LLM's domain constraint |
| **Note Learner Level** | one | *how deep* — the educational level it was authored for |
| **Applicable Programs** | many | *where* it appears — discovery and filtering only, never reaches a prompt |

## What triggered this, concretely

Under the Civil Engineering Review Set's **Engineering Mathematics** subject plan, my next authoring step was to create topic Notes for the **Algebra** subject. I stopped before creating them. Those exact Notes will also be needed by Mechanical, Electrical, Electronics, Computer, Industrial, Chemical, Mining, Agricultural, Geodetic and Sanitary Engineering — and authoring them under a one-program-per-note model commits me to duplicating them ten more times, along with their Study Packs, question pools, flashcards, and all future maintenance.

So this is about **not creating the duplication in the first place**, decided at the moment of authoring.

## The decision I need help with

**What should the Content Context value set be?**

This is the highest-stakes part, because Content Context is substituted directly into the generation prompt where the program name currently sits:

```
Course / Program: {VALUE}
Domain constraint: treat the {VALUE} above as the authoritative academic domain. All content,
terminology, examples, and question framing must belong to that domain. Do not blend in
material from unrelated disciplines.
Content calibration: use the {VALUE} above to set depth, vocabulary, terminology, and examples.
```

That instruction is why the original sketch of this idea — values like "Engineering Foundation", "Business Foundation", "Professional Education" — worries me now. "Engineering Foundation" is a **weaker** domain constraint than the "Civil Engineering" it would replace. It is vague about treatment, so it could make generated content *worse*, generic rather than canonical. This is the one way the new architecture could regress quality.

### The rule I'm proposing

> **Content Context is the coarsest label under which the note's treatment is identical.**

Not the program. Not the subject plan. The level at which curricula genuinely share the same treatment. Consequence: the value set is a deliberate **mix** of shared-bundle names and program names.

| Note | Treatment identical across… | Content Context |
|---|---|---|
| Algebra topics | all 11 engineering programs | `Engineering Mathematics` |
| Strength of Materials | most engineering programs | `Engineering Sciences` |
| Structural Analysis | Civil Engineering only | `Civil Engineering` |
| Building Utilities | Architecture only | `Architecture` |

My test for any candidate value: **would a competent author, given only this value plus a subject, produce the intended note?** "Engineering Mathematics + Algebra" passes. "Engineering Foundation + Algebra" does not.

### The 10 values I'm proposing

**Tier 1 — needed to unblock Civil Engineering now**
- `Engineering Mathematics` — Algebra, Trigonometry, Analytic Geometry, Calculus, Differential Equations, Probability & Statistics, Engineering Economics → all 11 engineering programs
- `Engineering Sciences` — Strength of Materials, Statics, Dynamics, Fluid Mechanics, Thermodynamics, Engineering Materials → most engineering programs
- `Civil Engineering` — Structural Analysis & Design, Hydraulics, Geotechnical, Surveying, Transportation, Construction Management → Civil only

**Tier 2 — covers our existing published content**
- `General Education` — Biology, Physics, Chemistry, Science, Earth Science, Environmental Science, pre-college Mathematics, English, Filipino, Social Studies
- `Professional Education` — Curriculum Development, Assessment of Learning, Educational Psychology, the Teaching Profession
- `Nursing` — Med-Surg, Psychiatric, Pediatric, Maternal & Child, Fundamentals, nursing-framed Pharmacology
- `Health Sciences Foundation` — Anatomy & Physiology, Biochemistry, Microbiology, generic Pharmacology
- `Accountancy` — Financial Accounting & Reporting, Taxation, Auditing, Management Advisory Services, Regulatory Framework & Business Law, Financial Management
- `Architecture` — National Building Code, Building Technology, Architectural Design, Building Utilities, Site Planning
- `Computing` — Data Structures, OOP, AI foundations, Software Engineering practice

**Tier 3 — deliberately NOT minting contexts yet:** Law, Medicine, Criminology, Psychology, Aviation, Business Administration, Physical Therapy, Civil Service. Each has only 1–7 notes today. My reasoning is that minting a context per thin program is how a curated taxonomy rots back into the free-text field I'm trying to escape — so these fall back to their program name until real content exists.

## My five open questions

1. **Is `Engineering Sciences` genuinely shared across all 11 Philippine engineering programs, or only a subset?** This is the one I most need resolved — it sets the default applicability expansion. It is a curriculum question, not an architecture one.
2. **Does `Engineering Mathematics` need to split by depth** (board-review vs. college coursework), or does the separate Note Learner Level axis handle that adequately with one shared context?
3. **Is one `General Education` context right across Grade School → Junior High → Senior High**, distinguished only by Note Learner Level? Or does the treatment differ by more than depth?
4. **Should Content Context be the single visible badge on note cards** (replacing the current program badge)? I do not want to show twelve program badges per card, and Content Context is single-valued and stable — but it means learners see this vocabulary constantly, so it has to read well to a student, not just to a curator.
5. **`Civil Service` (7 notes)** — its own context, or fold into `General Education`?

## Real data from our production database, for grounding

**Notes per Course/Program** (top values; ~4,280 notes total carry one): Education 1,630 · Nursing 868 · Architecture 837 · Accountancy 532 · **Civil Engineering 211 (197 published, 24 distinct subjects — already our largest published program)** · Information Technology 74 · Pharmacy 29 · Junior High 24 · High School 11 · Electrical Engineering 8 · Civil Service 7 · Mechanical Engineering 7 · Physical Therapy 7 · Senior High – STEM/ABM/HUMSS 11 combined · Grade School 3 · then Law, Medicine, Criminology, Psychology, Aviation, Business Administration, Biology at 1–2 each. **27 distinct values, and an audit found zero spelling/casing duplicates** — the vocabulary is clean, it is the *semantics* that are mixed.

**The field is currently conflating four different kinds of thing** — actual degree programs; **learner levels** (Junior High, High School, Grade School, the three Senior High tracks — 49 notes); a **program family** (`Engineering`, 2 notes); a **subject** (`Biology`, 1 note); and **user goals** (`Professional / Board Exam Review`, `Civil Service`, `Self Study / Personal Learning`). This is the strongest evidence for splitting the axes.

**Subjects already spanning 2+ programs:** Pharmacology {Nursing, Pharmacy} 109 notes · Biology {Education, High School, Junior High, Nursing} 11 · **Strength of Materials {Civil Engineering, Mechanical Engineering} 11** · Algebra {Junior High, Senior High – STEM} 6 · Environmental Science 6 · Physics 5 · Computer Science {IT, Software Engineering} 5 · plus Earth Science, Science, Psychology, Civil Engineering at 2–3 each.

**Duplication has already started.** Strength of Materials holds "Stress and Strain in Strength of Materials" under Civil Engineering and "Stress, Strain, and Material Strength" under Mechanical Engineering — the same knowledge, two notes, two programs. Nine further Civil SoM notes are queued to need a twin per additional engineering program.

**But the reuse rule does discriminate correctly** — worth noting because it is the counter-example. Nursing's 15 Pharmacology notes are genuinely nursing-framed ("Medication Administration Rights in Nursing", "Safe Medication Practices in Nursing") while Pharmacy's single one is generic ("Antibiotics: Mechanism of Action and Resistance"). Separate notes are *correct* there. That is the evidence behind splitting `Nursing` from `Health Sciences Foundation`.

**Current published Review Sets:** 4 comprehensive ones, averaging ~58 notes via hierarchy rollup — CPALE 74, PNLE 63, ALE 52, LET 43 — against a target of several hundred each.

## What I want back

1. **A critique of the rule** ("coarsest label under which treatment is identical"). Is it decidable in practice, or will it produce arguments at the margin? If it fails, what rule is better?
2. **A critique of the 10 values** — wrong granularity anywhere, missing values, values that should merge.
3. **Your position on each of my five questions**, with reasoning.
4. **A judgment on Tier 3.** Is "let thin programs fall back rather than minting a context" right, or does it create a two-class system that confuses curators?
5. **Anything I have missed** about how this vocabulary behaves once learners see it, once a second curator uses it, or as it grows past 10 values.

## Two constraints on your answer

**Do not re-litigate the four-axis architecture.** It is ratified, the code audit is done, and the migration is planned. I need the taxonomy inside it.

**Be explicit about your confidence on curriculum specifics.** Questions 1 and 2 turn on the actual PRC board exam syllabi for Philippine engineering programs. If you are not certain how a given board groups its subject areas, say so plainly and mark it as needing verification against the real syllabus — do not present a plausible grouping as established fact. I would rather get four confident answers and one flagged unknown than five confident-sounding answers where one is invented.
