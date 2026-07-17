# docs/gpt-contexts/

Paste-ready context documents for handing NoteLib context to an external GPT session. These are
snapshots/briefs, not source-of-truth for implementation — for that, always defer to `AGENTS.md`,
`RELEASES.md`, `docs/product/ROADMAP.md`, and the relevant `docs/features/*.md`.

| File | Use for |
|---|---|
| `GPT_CONTEXT.md` | Full product/engineering context handoff — app model, current release, roadmap state, non-negotiable rules. Paste as the first message in a new product/engineering GPT session. Update on every version ship or roadmap shift. |
| `NOTES_AND_COLLECTIONS_CONTEXT.md` | Narrower structural reference: Note fields, subject/courseProgram taxonomy, Bulk Generate metadata, and how Note Collections (Study Plans) vs. query-filtered groupings (Exam Hub, Public Library, Saved Filters) work. Paste when the conversation is specifically about Library/notes/collection structure. |
| `NOTELIB_PRODUCT_DECISIONS.md` | Standing role/instruction prompt for a GPT acting as long-term PM/UX strategist. |
| `MARKETING_STRATEGY.md` | Current marketing strategy and source of truth for channel strategy. |
| `FACEBOOK_GROUP_STRATEGY.md` | Facebook-group-specific organic growth strategy. |
| `NOTELIB_SOCIAL_CONTEXT_v1.0.md` | Content format and writing style rules for social posts. |
| `GPT_MARKETER_PROMPT.txt` | Session-starter prompt for a marketing-focused GPT session. |
