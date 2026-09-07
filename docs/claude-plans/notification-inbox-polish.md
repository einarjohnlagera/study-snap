# Notification Inbox Polish — Plan

**Status:** PLAN ONLY — nothing implemented. Written 2026-09-07.
**Target:** v0.131.0. **Scope:** three fixes against the **shipped** `v0.130.0` inbox, plus the
focus-mode expansion they imply.
**Subject:** `frontend/components/notifications/notification-inbox.tsx` (190 lines) and
`frontend/components/app-shell.tsx`.
**Origin:** owner report, 2026-09-07 (screenshot: panel open with a `Close` button, no outside-click
dismissal).

**Routing: Claude Code inline.** One component plus hook calls on the quiz pages. No backend, no
migration, no contract change. **Tier: a single `advisor()` call.**

---

## 1. Close on outside click — and drop the `Close` button

**Current:** the desktop panel can only be closed by its `Close` button (`:172`). The **only**
`addEventListener` in the component is a `matchMedia` listener for `isMobile` (`:41`) — there is no
outside-click handling at all.

**⚠️ The inbox is the odd one out. Every other dropdown in this app already closes on outside click,
including the avatar menu in the same header:**

| Control | Outside-click close |
|---|---|
| Avatar menu | `app-shell.tsx:525` — `contains(event.target)` |
| Theme toggle | `theme-toggle.tsx:125,136` — `mousedown` + `contains` |
| Export dropdown | `export-dropdown-menu.tsx:43` |
| **Notification inbox** | **absent** |

**Ship:** a `mousedown` listener on `document` that closes when the click falls outside the panel
**and** outside the bell — copying the avatar-menu pattern in the same file. **Then remove the
`Close` button**; it exists only because closing was otherwise impossible.

**⚠️ The one real trap: the handler must treat the bell as INSIDE.** If the bell counts as "outside",
the outside-click handler and the toggle (§2) both fire on the same click and the panel reopens or
flickers.

**Also add `Escape` to close the desktop panel.** The mobile path already handles it
(`app-modal.tsx:109-111`); the asymmetry is the same gap.

**⚠️ Mobile needs no change** — that path renders `AppModal`, which already has its own dismissal.
**Do not add a second handler there.**

## 2. The bell is a toggle, not a refresh

**Current:**

```ts
const openInbox = () => {
  setIsOpen(true);
  void loadInbox();
};
```
`:57-60`, wired to the bell's `onClick` at `:158`.

**Clicking an open inbox re-opens it and refetches** — the reported "refresh, not toggle" behaviour.

**Ship:** when open, close and **do not refetch**; when closed, open and load. Matches every sibling
control above.

**⚠️ Do NOT drop the load-on-open.** Opening a closed inbox must still fetch — only the *re-click
while open* skips it.

**⚠️ Accessibility, missing today and cheap to fix here:** the trigger has a hardcoded
`aria-label="Open notifications"` (`:157`) and **no `aria-expanded`**. A toggle owes both — the label
should reflect state, and `aria-expanded` should track `isOpen`.

## 3. Hide the bell while a learner is taking a quiz

**⚠️ It is already hidden — but only for two modes.** The bell renders inside the header at
`app-shell.tsx:643`, and that header is wrapped in `{!isExamFocusActive ? (` (`:628-629`). So focus
mode already removes it:

| Surface | Focus mode today |
|---|---|
| Long Exam | **Yes** — `useExamFocusMode(phase === "running")` |
| Challenge Quiz — **Board Exam mode only** | **Yes** — `isBoardExamMode && phase === "running"` |
| Challenge Quiz (ordinary) · Quick Review · Adaptive Practice · Interview Practice | **No** |
| Shared quiz `/quiz/[token]` | **No** |

**Ship:** `useExamFocusMode(true)` while a quiz is in progress on the remaining surfaces. **No new
mechanism** — the hook, the context and the header gate all exist.

**⚠️ Focus mode hides the WHOLE header** — page title, theme toggle, feedback widget, avatar — plus
the mobile tab bar, not just the bell. That breadth is accepted deliberately (owner, 2026-09-07): a
partially-hidden header is a worse inconsistency than a fully-hidden one, and it matches Board Exam.

**⚠️ EVERY newly-focused surface owes an in-page exit.** Hiding the chrome removes the way out.
Challenge Quiz has a `BackLink` (`:1692`); **Quick Review, Adaptive Practice, Interview Practice and
the shared quiz must each be checked before the hook is added.** A surface that gains focus mode
without an exit **traps the learner** — worse than the bell being visible.

**⚠️ This supersedes a previous owner decision**, already reconciled:
`shared-quiz-recipient-experience-plan.md` §10 recorded *"do NOT expand focus mode to other quiz
modes"*; that deferral was withdrawn on 2026-09-07 and §10 now points here. **Do not re-derive the
narrowing from that file's history.**

---

## 4. Verification guards

1. **Outside click closes** the desktop panel.
2. **⚠️ Clicking the bell while open closes it exactly once** — it must not reopen via the
   outside-click handler. *A fixture that clicks the page body passes while the bell double-fires.*
3. **Re-click does not refetch** — **assert the request count**, not the visible state.
4. **Opening still loads** — a closed → open transition still fetches.
5. **`Escape` closes** the desktop panel.
6. **Bell hidden during a quiz and restored on exit**, including leaving mid-quiz.
7. **Every focused surface has a reachable in-page exit** — assert per surface, not once.

## 5. Anti-drift

- **⚠️ Do NOT let the outside-click handler treat the bell as "outside"** — it fights the toggle.
- **⚠️ Do NOT stop loading the inbox when it OPENS** — only the re-click while open skips the fetch.
- **⚠️ Do NOT add a second dismissal handler to the mobile `AppModal` path.**
- **⚠️ Do NOT give a quiz surface focus mode without an in-page exit.**
- **⚠️ Do NOT change badge semantics** — Announcements still never inflate the number, and a zero
  count still renders no badge.
- **⚠️ Do NOT change read/dismiss semantics** — opening the panel still must not mark everything read.
- **⚠️ No backend change, no migration, no new notification type.**
