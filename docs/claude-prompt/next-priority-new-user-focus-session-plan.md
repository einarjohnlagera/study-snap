# Session Plan — What's Next, Given "New Users to Retain, Not Previous Users to Win Back"

> **Purpose.** v0.53.0 (SEO Discoverability: Exam Hub Depth & Organic Attribution) just shipped and
> merged. The product owner has now stated the acquisition/retention posture explicitly, for the second
> time this cycle: *"we're not chasing our previous users anymore, we're now chasing new users to
> retain."* This is a prioritization session, not a feature-design session — the backlog has at least
> two live, unscoped tracks that could plausibly be "next," and this framing changes how they should be
> weighed against each other. Before writing any Codex prompt or kicking off a new version, get a real
> recommendation on sequencing.

## Ground truth this session must respect (already established, don't re-derive)

**Two real candidate tracks exist, and they are not equivalent under the new framing:**

1. **SEO acquisition remainder** (`seo-strategy-out/01-seo-strategy.md`) — top-of-funnel, gets people who
   don't know NoteLib exists into the funnel. v0.53.0 just shipped P4 (exam hub subject breakdown +
   full-inventory path), P5 (`ItemList` JSON-LD), and P6 (organic-referrer attribution). Remaining in this
   track:
   - **P1 — Google Search Console setup.** Non-code ops task (needs domain/DNS access), not something
     Claude Code or Codex can implement.
   - **P3 — exam-named Learn guides** (e.g. content that actually surfaces for "free PNLE notes"-style
     searches). Needs human-verified exam-subject content — cannot be fabricated by an LLM without a
     subject-matter curator; not a pure code task.
   - **Wave 2 Exam Hub candidate: CPALE (Accountancy).** A side-finding from the subject-depth inventory
     (`public-library-seo-expansion-out/02-subject-depth-inventory.sql` query 1): ~100+ notes across
     Accountancy-adjacent subjects (accounting, auditing, taxation, financial management) not covered by
     any current Exam Hub. Needs a product decision confirming real `courseProgram`-level depth clears the
     existing Wave-2 gate (~25–30 notes, see `docs/features/exam-hub.md`) before it can be scoped like the
     original three hubs (ALE/PNLE/LET). This is the one remaining item in this track that is both
     new-user-acquisition-aligned AND immediately actionable as engineering work (unlike P1/P3 above).

2. **Retention H1 + H5** (`retention-diagnosis-session-plan.md`) — a commitment device (H1: ask for a
   concrete commitment/schedule at peak motivation, tied to the user's exam date) paired with a
   pre-decided return action (H5: a Unified Next-Step Resolver so returning after inactivity lands on one
   backend-resolved next step instead of five quiz modes each computing their own). **Read this
   carefully: despite living under a doc literally named "retention diagnosis," this track is NOT about
   winning back users who already churned in the past — it is about getting *newly acquired* users to
   come back for a second and third session shortly after signup**, i.e. exactly "new users to retain" as
   the product owner just stated it. Do not conflate this with the separate, genuinely previous-user-
   focused outbound interview track below.
   - **Current gate status, check this plainly:** the Backlog Index's pre-committed decision rule was
     "any positive-or-ambiguous v0.48.0 cohort signal → ship H1+H5 together as one release." That read
     window was estimated as "~late July 2026." Today is within that window and **the read has not yet
     been re-pulled this cycle** (`docs/product/ROADMAP.md` Backlog Index, last reviewed 2026-07-21, notes
     "not yet re-read this cycle, no new production pull run"). This is a factual, checkable gate — not
     something this session can resolve by reasoning alone. Say plainly that the actual next action for
     this track may be "pull the v0.48.0 cohort data" rather than "start building," if that's what the
     gate demands.

**A third track exists but is explicitly the wrong shape for "new users to retain" and should be weighed
accordingly, not silently treated as equivalent to the above two:**

3. **Direct outbound interviews to churned users** (`retention-diagnosis-out/05-interview-script.md`) —
   script already written, ready to send, zero engineering cost. This is *the* genuinely previous-user-
   focused track (its entire purpose is learning from people who already left). Under the product owner's
   restated priority, this is the track most likely to get explicitly deprioritized or held, not the one
   to recommend next — but say so directly rather than ignoring it, since it is real, already-scoped, and
   someone might otherwise assume it's still the default next action.

**What "next" is emphatically not, this cycle:** anything gated on interview signal that hasn't run
(Smart Review Planning, the manual Official-coverage sprint) — both are explicitly conditional on
interview results per the Backlog Index, and the interviews themselves are now the deprioritized track
above. Don't recommend jumping past that dependency.

## What to design

1. **State the reframing in your own words first.** Confirm which of the two real candidate tracks
   (SEO acquisition remainder vs. H1+H5) actually fits "new users to retain," and confirm that the
   outbound-interview track is the one track that does NOT fit it despite living in the same "retention"
   documentation — don't let the shared label ("retention diagnosis") cause you to lump H1+H5 in with the
   churned-user interviews. They are different populations.
2. **Give a real sequencing recommendation between SEO-remainder-as-engineering-work (Wave 2 CPALE hub)
   and H1+H5**, not a hedge. Consider: SEO is proven-shape, low-risk, extends what just shipped, and its
   own gate (Wave-2 depth) is checkable directly from code the product owner already has. H1+H5 is a
   bigger, unshipped bet whose own gate (cohort read) has not yet been checked this cycle and may not even
   be answerable yet (small-sample ambiguity was pre-anticipated). Weigh cost, risk, and whether the gate
   check itself (not the build) is the actual next action for one of them.
3. **Say explicitly what to do about the outbound-interview track** — hold entirely for now, run it
   anyway because it's zero-cost and orthogonal to engineering capacity, or something else — given it's
   the one track that doesn't fit the new-user framing.
4. **Say what to do about P1 (GSC ops) and P3 (Learn guides content)** — both are real backlog items but
   neither is code work Claude Code/Codex can execute; say whether either should be flagged back to the
   product owner as "needs you, not us" now, or can keep waiting.
5. **Give one clear "do this next" verdict** — which single track (or specific narrow slice of a track)
   should the next release actually scope, and what is the first concrete action (a data pull, a product
   decision, a Codex prompt, a scoping session) rather than a vague direction.

## Hard constraints (Fable starts cold, repeat these)

- This is a sequencing/prioritization session, not a feature-design session. Do not design new UI copy,
  question wording, or schema changes here — that's a separate, later step once a track is chosen.
- Do not recommend anything that requires re-litigating already-shipped, locked decisions (the sitemap/
  JSON-LD/robots architecture from the SEO track, the Unified-Next-Step-Resolver-as-H5-infra decision, the
  freeform-only feedback pipeline decision from the most recent feedback session).
- Treat the "new users to retain" instruction as a hard filter on what counts as "next," not just one
  input among several — if a track's primary value proposition is reaching or learning from *already-
  churned* users, name that plainly and weigh it down accordingly.

## Prompt

Full paste-ready prompt: `next-priority-new-user-focus-prompts/01-next-priority-new-user-focus.txt`

## Output

`docs/claude-prompt/next-priority-new-user-focus-out/01-next-priority-new-user-focus.md` (once run)

## Status

Run 2026-07-21 via the `fable` model. Verdict: neither candidate build (CPALE hub, H1+H5) should be
scoped yet — both sit on an unconfirmed gate, and confirming a gate is cheap while building on top of one
isn't. The single next action is re-pulling the v0.48.0 cohort return-rate data for the H1+H5 decision
gate (overdue against the team's own "~late July" pre-commitment); the CPALE depth-count query should run
in parallel since it's free but shouldn't displace the cohort pull as the headline action. Confirms H1+H5
fits "new users to retain" more precisely than the SEO track does, despite sharing a "retention diagnosis"
document with the churned-user interview track — those are different populations. Recommends running the
already-written churned-user interviews anyway (zero engineering cost, doesn't compete for capacity) while
explicitly not treating that population as a priority. Recommends handing P1 (Search Console) and P3
(exam-content guides) back to the product owner now as non-engineering action items rather than leaving
them to sit in the backlog unflagged. Full output:
`next-priority-new-user-focus-out/01-next-priority-new-user-focus.md`.
