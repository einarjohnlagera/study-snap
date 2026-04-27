# CONTEXT_INDEX.md

This file explains how NoteLib docs are organized.

## Goal

Preserve legacy context while keeping current implementation guidance clear and non-duplicative.

## Preservation

- Original source documents are retained under `docs/legacy/`.
- Active documentation is maintained under `README.md` and `docs/`.

## Mapping

- `README.md` -> `README.md`
- `SPEC.md` -> `docs/product/SPEC.md`
- `ROADMAP.md` -> `docs/product/ROADMAP.md`
- `ARCHITECTURE.md` -> `docs/architecture/ARCHITECTURE.md`
- architecture data-model sections -> `docs/architecture/DATA_MODEL.md`
- `PROMPTS.md` -> `docs/ai/PROMPTS.md`
- `AGENTS.md` -> `AGENTS.md`
- `PROJECT_CONTEXT.md` -> `docs/PROJECT_CONTEXT.md`
- `STUDY_SNAP_USER_ACCOUNTS_CONTEXT_V2.md` -> `docs/features/authentication.md`
- `CODEX_PROMPT_STUDY_SNAP_USER_ACCOUNTS_V2.md` -> `docs/ai/CODEX_PROMPT_USER_ACCOUNTS.md`

## Suggested Reading Order

For product decisions:

- `README.md`
- `docs/product/SPEC.md`
- `docs/product/ROADMAP.md`

For implementation:

- `AGENTS.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/DATA_MODEL.md`

For feature work:

- relevant file under `docs/features/`
- onboarding flow (v0.11.0): `docs/features/onboarding.md`
- learning loop positioning: `docs/product/SPEC.md` and `docs/PROJECT_CONTEXT.md`
- Generate Note from topic: `docs/product/SPEC.md` and `docs/features/study-pack-generation.md`

For prompt work:

- `docs/ai/PROMPTS.md`

For historical cross-checking:

- `docs/legacy/*`
