# 06 — Terminology Rename Map (Product Language: Sell Outcomes, Not AI)

Planning output only. This is a rename MAP for a later implementation session — no copy has been changed.

## Decisions carried forward

**Policy — where "AI" MAY appear (descriptive prose only, never as part of a feature name):**
- Landing page and `/how-it-works` (especially the differentiation section, which explicitly compares NoteLib against "generic AI tools" — that comparison stays)
- Marketing pages, SEO metadata, Open Graph copy
- Pricing page *prose* (e.g. trust/positioning sentences) — but plan feature rows keep outcome names (already true: "Summary + Key Concepts", "Topic note generation")
- Learn articles and blog posts (public education content)
- Documentation and legal (privacy page "AI Processing" section stays — transparency requirement)

**Policy — where "AI" is BANNED:**
- Feature names, everywhere (including marketing — a feature is named by its outcome even when marketed)
- Nav / menu labels, buttons, CTAs, modal titles
- Empty states, success/status messages, loading copy
- Upgrade prompts and paywall copy (`getUpgradeCtas` labels + `lib/paywall-content.ts` bodies)
- Onboarding flow (all 5 steps)
- In-app help guides: mechanism may be described ("NoteLib reads your note"), never branded ("The AI reads your note")
- Guidance tips (`pickActiveGuidance`), tooltips, toasts

**Rule of thumb:** the student should feel "this app knows what I should study next" — the word "AI" adds nothing in-product and cheapens the premium, calm tone. In allowed zones, "AI" appears only as an adjective in prose ("AI-powered feedback"), never as a noun-brand ("the AI") and never in a name.

**Top renames (full table below):**
1. "AI Suggestions" modal (note editor) → **"Suggested Details"**; AI Title/Subject/Tags → Suggested Title/Subject/Tags — static, single file, highest-visibility violation
2. "AI Critique" (Interview Practice feature name, landing badge, Learn guide titles, paywall body) → **"Answer Critique"** — feature-name rename, so it applies even in allowed marketing zones
3. Help-guide mechanism copy ("The AI reads your note", "AI-generated overview", "by the AI") → NoteLib-as-actor phrasing — static
4. Brief's canonical names confirmed: any "AI Review Set Builder"-style surface → profile-aware **"New {Study Plan / Review Set / Lesson Plan / Collection}"** via `getCollectionLabels().newCtaLabel` — never a universal string; "AI Note Generator" → **Create Notes / Topic Notes**; "AI Quiz Generator" → **Practice Quiz** (mode names stay in `exam-mode-visibility.ts`); "AI Flashcards" → **Key Concepts** (already shipped); "AI Companion" → **Learning Companion** (inline refs via `companionSingular`)

**Infra rule (hard constraint):** collection-shaped names route through `getCollectionLabels` (frontend/lib/collection-labels.ts), upgrade copy through `getUpgradeCtas` (frontend/src/config/plans.ts), quiz-mode names through `getAvailableExamModes` (frontend/lib/exam-mode-visibility.ts). A rename hardcoding a universal string on any of these surfaces is invalid.

---

## Canonical rename map

Legend for **Routing**: `getCollectionLabels` = profile-aware collection labels; `getUpgradeCtas` = upgrade CTA config; `exam-mode-visibility` = quiz mode cards; `static` = plain copy in one component/config file.

| Current term (location) | Recommended term | Surface | Routing |
|---|---|---|---|
| "AI Suggestions" (modal title, `components/notes/ai-suggestion-modal.tsx:204`) | "Suggested Details" | Note editor — metadata suggestion modal | static |
| "AI Title" / "Use AI Title" (same file, :232, :245) | "Suggested Title" / "Use Suggested Title" | Note editor modal | static |
| "AI Subject" / "Use AI Subject" (:256, :269) | "Suggested Subject" / "Use Suggested Subject" | Note editor modal | static |
| "AI Tags" / "Merge My Tags + AI Tags" / "Use AI Tags Only" (:281, :299, :306) | "Suggested Tags" / "Merge My Tags + Suggested Tags" / "Use Suggested Tags Only" | Note editor modal | static |
| "AI Critique" (feature name — `components/landing/profile-learning-section.tsx:144` badge; concept used in Interview Practice) | "Answer Critique" | Landing profile section, Interview Practice results, plan features | static (landing) — feature name applies everywhere |
| "per-answer AI feedback" (`profile-learning-section.tsx:139`) | "per-answer critique" | Landing profile section | static |
| "…get AI critique after every answer…" (`lib/paywall-content.ts:176`) | "…get detailed critique after every answer…" | Interview Practice paywall body | static — but centralized in `paywall-content.ts`; CTA label stays `getUpgradeCtas("interview-practice")`, already AI-free ("Unlock Interview Practice") |
| "How to Use AI Critique in Interview Practice" + body refs (`lib/learn-guides.ts:375-378, 732, 737`) | "How to Get More from Answer Critique in Interview Practice"; body: "Answer critique…" (prose may keep one "AI-powered" adjective — Learn is an allowed zone, but the feature *name* follows the rename) | Learn guide (public) | static |
| "The AI reads your note and builds the pack…" (`components/help/study-packs-guide.tsx:17`) | "NoteLib reads your note and builds the pack…" | In-app Help — Study Packs guide | static |
| "An AI-generated overview of your note…" (`study-packs-guide.tsx:28`) | "An automatic overview of your note condensed into the key points." | In-app Help | static |
| "Auto-extracted from your note content by the AI" (`study-packs-guide.tsx:40`) | "Pulled automatically from your note content" | In-app Help | static |
| "…see targeted AI critique on each answer." (`components/help/professional-guide.tsx:37`) | "…see targeted critique on each answer." | In-app Help — Professional guide | static |
| "Read the AI critique even on correct answers…" (`professional-guide.tsx:52`) | "Read the critique even on correct answers…" | In-app Help | static |
| "AI Review Set Builder" (brief example — any collection-creation surface) | "New Review Set" / "New Study Plan" / "New Lesson Plan" / "New Collection" — i.e. `getCollectionLabels(profileType).newCtaLabel`; page-level framing may use "Create your {singular}" | Collection create CTA, empty states, builder headers | **getCollectionLabels** — the brief's "Create Review Plan" is the BOARD_EXAM rendering only; a universal string here is invalid |
| "AI Note Generator" (brief example) | "Create Notes" (action) / "Topic Notes" (feature noun). Onboarding already ships "Generate a note" / "Generate Note" — keep; plans row "Topic note generation" — keep | Note creation entry points, onboarding Step 3, plan comparison | static (plan row lives in `plans.ts` PLAN_COMPARISON_ROWS but is not profile/CTA infra) |
| "AI Quiz Generator" (brief example) | "Practice Quiz" as the generic action; specific mode names come from `getAvailableExamModes` ("Challenge Quiz", "Board Exam Mode", "Long Exam Mode", "Certification Review", "Full Practice Exam") — all already AI-free | Quiz creation entry points, mode picker | **exam-mode-visibility** for mode names; static for generic entry-point copy |
| "AI Flashcards" (brief example) | "Key Concepts" — already the shipped name (`plans.ts`, onboarding Step 4 preview, Free plan feature) | Study Pack section, plan features | static — confirmed, no work needed; listed to lock the canonical name |
| "AI Companion" (brief example) | "Learning Companion" (full name in intro/static surfaces); inline collection-context references use `getCollectionLabels(profileType).companionSingular` (currently "Companion" for all profiles) | Companion feature surfaces, mentor tips | **getCollectionLabels** (`companionSingular`) for inline refs; static for the standalone feature name |
| "Not just AI output — structured for real learning…" (`app/page.tsx:131`) | **Keep** — differentiation against generic AI tools is an allowed, intentional use per `docs/features/landing.md` | Landing differentiation | static — no change |
| "4. AI Processing" (`app/privacy/page.tsx:60`) | **Keep** — legal transparency | Privacy page | static — no change |

### Already-clean infra (verified, no renames needed — do not regress)

| Surface | Status |
|---|---|
| `getUpgradeCtas` — all CTA labels ("Unlock Interview Practice", "Get More Study Packs", "Unlock Board Exam Mode", teacher contexts…) | AI-free; outcome-first. Any *new* upgrade copy must be added here, never inline |
| `getAvailableExamModes` / `resolvePlanPremiumExamMode` — all mode labels and descriptions | AI-free |
| `getCollectionLabels` / `getCollectionTerminalAction` — all profile label sets, empty states, terminal actions | AI-free |
| `plans.ts` PLANS + comparison rows, pass-framing constants | AI-free |
| Onboarding flow copy per `docs/features/onboarding.md` ("Building your Study Pack...", "You just started your study loop.") | AI-free |

## Blast radius for the implementation session

**Plain static copy (low risk, single-file edits):**
- `components/notes/ai-suggestion-modal.tsx` — 8 strings. Optional non-copy follow-up: rename the file/component itself (`suggestion-modal.tsx`) — flag as a separate mechanical change, not required for the copy fix.
- `components/help/study-packs-guide.tsx`, `components/help/professional-guide.tsx` — 5 strings total.
- `components/landing/profile-learning-section.tsx` — 2 strings.
- `lib/paywall-content.ts` — 1 string (centralized paywall config; check siblings in the same file for other "AI" bodies while there).
- `lib/learn-guides.ts` — guide title + ~3 body strings; body prose may retain one descriptive "AI-powered" since Learn is an allowed zone, but the feature name becomes "Answer Critique".

**Label infrastructure (higher blast radius — touch only if a surface currently bypasses it):**
- No rename in this map requires *changing* `getCollectionLabels`, `getUpgradeCtas`, or `exam-mode-visibility.ts` values — they are already AI-free. The infra work, if any, is **re-routing**: if the implementation session finds a collection-creation or upgrade surface hardcoding its own string (e.g. a literal "AI Review Set Builder" header), the fix is to consume the existing infra field (`newCtaLabel`, `singular`, `companionSingular`, `getUpgradeCtas(...)`), not to add a parallel constant.
- "Answer Critique" as a feature name spans landing + paywall + Learn + help — 4 files, same string. Recommend a single exported constant (e.g. in a shared copy module) if the implementation session wants drift protection, but that is a nice-to-have, not required.

**Consistency guard for future copy:** new feature names are outcome nouns (Key Concepts, Answer Critique, Topic Notes, Practice Quiz); actions are verb-first without "AI" ("Create", "Build", "Generate a note" is acceptable — "generate" describes the action, not the technology). "Curation, never generation" stays intact: nothing in this map renames a curator/assistant surface in a way that implies students receive auto-generated curriculum — the Internal Curator vs. Learning Companion split is unaffected.
