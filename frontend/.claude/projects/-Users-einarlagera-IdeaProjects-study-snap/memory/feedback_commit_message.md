---
name: Commit message after every prompt
description: Always provide a commit message at the end of every completed prompt that results in code or doc changes, following AGENTS.md format
type: feedback
---

Always end every completed task response with a suggested commit message block.

**Why:** AGENTS.md rule — "After every completed prompt/task that results in code or doc changes, always include a suggested commit message in the final response." User explicitly reinforced this.

**How to apply:** Format as a plain-text fenced block:
- first line: `type: concise subject`
- following lines: flat `- ` bullets with 3–5 high-signal changes

Types: `feat`, `fix`, `style`, `refactor`, `docs`, `test`, `chore`
