# Computing Domain Context — Final Decision Package (Workstream A, closing)

**Status:** Direction owner-approved 2026-09-30; this package is the ratification-ready close-out. **No code, no ADR file, no production writes, no note generation.** This closes Workstream A — the item `bscs-year1-decision-log.md` rows D-13 and D-34 are blocked on.

**⚠️ CORRECTED 2026-09-30, same day, before implementation — two owner corrections applied in `v0.164.0`, not written back into the sections below (they are preserved as originally drafted):**

1. **§5/§9/§10 are wrong on the 34 Discrete Structures I rows** (the package itself miscounts these as 33, of a then-133-row total; the corrected total is 136, of which 34 are Discrete Structures I). They do **not** get `applicable_programs` extended to IT/IS/SE. Domain Context = `Computing`; Applicable Programs stays `Computer Science` only. This was an error in this package, not a follow-up action anyone owes.
2. **§4/§5/§6's 133/136 count is stale.** The BSCS Year 1 corpus is **136/136** classified — all 3 originally-`AMBIGUOUS` rows (§6) were resolved to `COMPUTING_SHARED` by the owner before this correction was written, not left pending.

The ADR-001 amendment and `RELEASES.md`/`ROADMAP.md` entries for `v0.164.0` cite the corrected numbers above, not the sections below.

**Backing documents** (full reasoning trail, do not re-derive): `docs/claude-plans/computing-domain-context-decision.md` (the verified audit + product-UX recommendation) and `docs/claude-plans/computing-domain-context-product-ux-tightening-prompt.md` (external review scoping). This package supersedes those for *action* — read them only for *why*.

---

## 1. Final `Computing` definition

> `Computing` is the shared authoring tradition for knowledge in which computation, information, algorithms, software, or computational systems are themselves the object of study, and where the exact canonical Note can legitimately serve learners across sibling computing programs without changing its academic treatment.

**Enum:** `COMPUTING` · **Label:** `Computing` · **`quantitative`:** `false` (mixed quantitative/non-quantitative content; `false` is the safe, corrigible default — see the backing document §5 for why a `true` flag would be a permanent, unrepairable-per-note commitment).

`Computing` is explicitly **not**: another name for Computer Science · a marker for every Note in a BSCS curriculum · a generic "technical" context · assigned merely because several computing programs are tagged · a replacement for General Education, Engineering Mathematics, or Engineering Sciences · a replacement for single-program specialization.

---

## 2. The two-test curator decision rule (operative, per Note)

**Test 1 — Knowledge-domain test:** Is computation, information, algorithms, software, or computational systems themselves the object of study?
- NO → not `Computing`. Use the honest existing Domain Context, or the existing fallback rules.
- YES → Test 2.

**Test 2 — Canonical-treatment / sibling test:** Could this exact canonical Note, unchanged in framing, depth, and explanation, legitimately serve at least one sibling computing program?
- YES → `Computing`.
- NO → specialization. Domain Context stays NULL; the program-name fallback applies.

**Binding constraint: multi-program tagging alone must never imply `Computing`, and course/Subject-Plan membership never determines Domain Context.** Decided per Note, always — never inferred from `applicable_programs`, never from which course a Note happens to sit in.

---

## 3. Inclusion / exclusion, with worked examples

**Shared foundation (passes both tests):** Variables and Data Types; Conditional Statements; Functions and Parameters; Arrays; fundamental Data Structures; Algorithmic Problem Solving; binary/data representation; Computer Systems fundamentals; discrete logic explicitly treated for computation. Reusable across CS, IT, Information Systems, Software Engineering, and (where treatment genuinely fits) software-facing Computer Engineering.

**Computer Science specialization (fails Test 2):** Automata and Formal Languages, Theory of Computation, Computability, deeper CS-specific formalism. Not `Computing` merely because it's computing-related. → `Applicable Programs: Computer Science`, `Domain Context: NULL`, CS fallback.

**Other sibling specialization (fails Test 2, same treatment):** IT systems/network-administration practice; IS business-process/enterprise treatment; Computer Engineering circuit/device analysis. Stays on the relevant sibling fallback, not `Computing`.

**Subject-Plan overlap is not the reuse test.** A Subject Plan can be mostly `Computing` with one CS-specific Note inside it (e.g. an advanced treatment inside Data Structures and Algorithms), or mostly specialized with one reusable Note inside it (e.g. a shared math prerequisite inside an Automata plan). Judge every Note independently.

**Learner destination does not determine Domain Context — the binding negative test case:** Precalculus (Algebra and Trigonometry) is a BSCS Year 1 prerequisite but is **not** `Computing`. Its content (algebra, trigonometry) is classified by its actual authoring tradition, not by who's about to read it. (Precalculus itself is out of this package's scope per owner direction — see §10.)

---

## 4. BSCS Year 1 classification results (the pre-ratification corpus gate, §7 of the owner's direction)

All 136 currently-unresolved rows in `docs/curriculum/bscs-year1-preliminary-shaping.NOT-IMPLEMENTATION-READY.tsv` (`domain_context = PENDING_PRODUCT_UX_DECISION`) were classified against the two-test rule, by Note title and content, not by subject-plan name or current tagging.

| Subject Plan | `COMPUTING_SHARED` | `CS_SPECIFIC` | `NON_COMPUTING` | `AMBIGUOUS` | Total |
|---|---|---|---|---|---|
| Introduction to Computing | 36 | 0 | 0 | 2 | 38 |
| Computer Programming 1 | 35 | 0 | 0 | 0 | 35 |
| Computer Programming 2 | 29 | 0 | 0 | 0 | 29 |
| Discrete Structures I | 33 | 0 | 0 | 1 | 34 |
| **Total** | **133** | **0** | **0** | **3** | **136** |

**Zero rows resolved to `CS_SPECIFIC` or `NON_COMPUTING`.** No automata/computability/theory-of-computation formalism appears anywhere in these four Subject Plans — consistent with "Discrete Structures I" being the introductory course, not a later theory course. This is not a rubber-stamp: the rule was applied to catch exactly that content, and found none to catch.

**Spot-check sample (representative, not exhaustive):** *Recursion: Base Case and Recursive Case* (Programming 1); *Linked Lists: Nodes and Links* (Programming 2); *Boolean Algebra and Digital Logic* (Discrete Structures I); *Number Systems: Decimal, Binary, Octal and Hexadecimal* (Introduction to Computing) — all straightforwardly pass both tests.

---

## 5. Headline count and its one real caveat

**133 of 136 rows genuinely support `Computing`.** This is the actual clause (a) evidence for the ADR amendment — 98% of the currently-blocked corpus, not an undifferentiated placeholder count.

**One separate, distinct finding this classification surfaced, not resolved:** 33 of the 133 `COMPUTING_SHARED` rows — **the entire Discrete Structures I block** — are currently tagged `applicable_programs: Computer Science` only. Ratifying `Computing` does not by itself make these 33 rows correct or generable across siblings; their `applicable_programs` field also needs at least one sibling program (IT/IS/SE) added. **This is a curriculum-authoring decision, not a Domain Context decision, and not this package's to make.** Flagged here as a required follow-up step (§9), owned by the curriculum strategist/curator, not gated on this ratification.

---

## 6. Rows requiring owner/curator judgment (3, in full — none summarized away)

| Subject Plan | Note title | Why ambiguous |
|---|---|---|
| Introduction to Computing | *Analyzing Organizational Computing Solutions* | Title reads as business/organizational-computing framing — may be Information-Systems-specific professional identity (fails Test 2) rather than identical treatment across CS/IT/IS. Not resolvable from the title alone; needs a content read. |
| Introduction to Computing | *Philippine Computing Laws: Data Privacy, Cybercrime and Intellectual Property* | The exact "regulatory content" edge case already named in the backing document as owner-call territory. A separate note (*Ethical, Legal and Social Issues in Computing*) already covers the "Social and Professional Issues" framing that would route this to `Computing`; this one may instead sit closer to `Professional Practice & Regulation` territory. Needs an explicit call, not a default. |
| Discrete Structures I | *Common Logical Fallacies* | Could be formal-logic-adjacent (analyzing informal fallacies via the argument forms just taught → `Computing`) or a generic critical-thinking/GE module that happens to be slotted into this shelf (→ `General Education`). Title alone doesn't distinguish these. |

**Recommended disposition:** hold these 3 rows at `PENDING_PRODUCT_UX_DECISION` pending a curator content read against the two-test rule above — do not default them either direction. They do not block ratifying `Computing` itself; they block only their own 3 rows.

---

## 7. Final ADR-001 amendment recommendation (concise — this is content, not a re-audit)

A new dated section in ADR-001, recorded as an owner decision under the ADR's own clause (b), stating:

1. **Supersession, stated plainly, not silently**: this amendment explicitly supersedes the 2026-09-04 rejection of `Computing`. Quote that rejection's two grounds (invented naming; failed learner-comprehension test) and answer both:
   - **Naming**: `Computing` is the confirmed umbrella term of the ACM/IEEE *Computing Curricula 2020* framework (covering Computer Science, Computer Engineering, Information Systems, Information Technology, Software Engineering), and "Introduction to Computing" is a real, literal shared first-year course title across BSCS/BSIT/BSIS under CHED CMO No. 25, s. 2015 — borrowed vocabulary, not invented.
   - **Comprehension**: the test applies to curators, not learners (ADR-001's own governance section: learners never see this vocabulary). `Computing` names a course every curator will be authoring; the two-test rule above makes the boundary operational.
2. **Naming-rule and composition-matrix clearance**: `Computing` collides with no live catalog program name (confirmed by direct query); it serves four confirmed live sibling programs (CS, IT, Information Systems, Software Engineering) — a stronger position than two of the three most recently ratified values, which each serve only one live program.
3. **Clause (a) evidence, stated with the real number**: 133 of 136 rows in the current BSCS Year 1 authoring corpus classify as genuinely shared computing knowledge under the two-test rule (§4-5 above) — not an assertion, a per-Note classification result. State plainly that this is *planned, not yet authored* corpus, consistent with how the `v0.111.0` amendment's own evidence was framed.
4. **The two-test rule itself** (§2 above), verbatim, as the durable operational boundary — this is what prevents `Computing` from becoming the next `General Engineering` catch-all.
5. **`quantitative = false`**, with the one-line reasoning from §1.
6. A `[CHECKPOINT — due after BSCS Year 1 authoring completes]`: re-count `Computing`-tagged notes across live programs; if the population turns out overwhelmingly single-program in practice (the `Architecture` failure), re-open the value.

**Keep this to roughly one page.** The evidence already exists in this package and its backing document; the ADR amendment cites it, it does not re-derive it.

**Routing:** Claude-direct (doc/ADR writing), on its own branch and PR, per this repo's task-routing table — unchanged from the backing document's §10.2 recommendation for the accompanying code (one enum constant, one frontend array entry, test updates; well under the Codex threshold).

---

## 8. R4 generation-quality pilot plan

**After** `Computing` is ratified and the enum/frontend value ships, **before** bulk-generating the BSCS shared-computing corpus: run the existing R4 verification runbook (`docs/claude-prompt/canonical-knowledge-architecture-out/17-r4-verification-runbook.md`, confirmed present) against five deliberately varied Notes:

1. **History and Evolution of Computing** — conceptual
2. **Variables and Data Types** — programming concept
3. **Functions and Parameters** — programming / executable-example pressure
4. **Propositional Logic for Computing** — discrete mathematics
5. **Algorithmic Problem Solving** — algorithmic reasoning

**Watch for:** inappropriate language-specific assumptions; source code forced into conceptual material; overly generic "computer literacy" treatment; CS-specific framing bleeding into genuinely shared material; failure to recognize computational/discrete reasoning as such.

**Do not preemptively add computing-specific prompt instructions.** Only propose a prompt change if this measured review shows a recurring, demonstrated problem — matching the backing document's §5 and the owner's explicit instruction not to bundle prompt behavior into this ratification.

---

## 9. Changes required in shaping/context documentation

1. `docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md` — add `Computing` as the 13th ratified value in the strategist's fixed list, and add the two-test rule (§2 above) as the operative per-Note instruction, replacing the current blanket "do not propose a new value" language for this one case now that it's ratified.
2. `docs/curriculum/review-set-workbook-spec.md:118-119` — correct the stale claim that the server rejects a *save* with 2+ Applicable Programs and no Domain Context (it doesn't; the check is generation-time only — see the backing document §1). Independent of this ratification; can ship immediately as a docs-only fix.
3. `docs/curriculum/bscs-year1-preliminary-shaping.NOT-IMPLEMENTATION-READY.tsv` — once ratified, the strategist replaces `PENDING_PRODUCT_UX_DECISION` with `COMPUTING` on the 133 confirmed rows (§4), leaves the 3 ambiguous rows pending (§6), and separately extends `applicable_programs` on the 33 Discrete Structures I rows (§5) — three distinct edits, not one bulk find-replace. **Not this package's action; the strategist's, once ratified.**
4. `docs/curriculum/bscs-year1-decision-log.md` — D-13 and D-34 close, citing this package. D-33 (Precalculus + the "technical mathematics for non-engineering programs" sub-question) stays open — see §10.

---

## 10. Remaining blockers before the BSCS TSV is implementation-ready

**Closed by this package:**
- D-13 (Domain Context for computing and technical-math notes, within the main Year TSV) — resolved: `Computing`, per the classification in §4.
- D-34 (Introduction to Computing content borrowed from IT-only notes) — resolved: the two live candidates (Network Security, Human Judgment in AI) can proceed once tagged, since `Computing` is now the honest Domain Context for adding the Computer Science program to an IT-only note whose treatment passes both tests.

**Explicitly NOT closed by this package, and out of its scope by owner direction:**
- **D-33 — Precalculus and the "technical mathematics for non-engineering programs" sub-question.** Precalculus content is not `Computing` (§3's binding negative case) and this package does not classify the separate Precalculus TSV. Whether Discrete-style pure-math content needs its own Domain Context distinct from both `Computing` and the engineering values is a genuinely separate question this package was told not to broaden into. Stays open; D-33's own documented legal fallback (single-program NULL) remains available in the meantime.
- The 3 `AMBIGUOUS` rows (§6) — curator content read required.
- The 33 Discrete Structures I `applicable_programs` extensions (§5) — curriculum-authoring action, not gated on this ratification but required before those rows are actually generable across siblings.
- Every other still-open decision-log row (D-07, D-08, D-11, D-14, D-17, D-21, D-26, D-27, D-35) — unrelated to Domain Context, not touched here.
- The ADR-001 amendment itself has not been written — §7 is a description of its required content, not the content.

**Explicitly out of scope for this package** (per owner §13, carried forward unchanged): the four-axis Note model; the multi-program generation rule; additional computing Domain Contexts; certification/interview Domain-Context or Authored-Depth values; the Applicable Programs model; a Learning Goal / Preparation Goal feature (a separate, parked workstream — the principle "Domain Context is independent of the learner's preparation goal" is recorded per owner §10, nothing more); bulk IT-note migration; Authored Depth changes; BSCS collection creation; corpus generation; any production write.
