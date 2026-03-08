# CONTEXT_INDEX.md

This file explains how the Study Snap docs were reorganized.

## Goal of the refactor

The original docs contained valuable information but had become bloated and overlapping.
This refactor preserves the original context while redistributing it into clearer categories:

- product
- architecture
- data model
- features
- AI prompts
- agent rules

## Preservation guarantee

Nothing from the original uploaded context was intentionally discarded.

To make that practical and auditable:
- original source files are preserved under `/legacy`
- refactored files reorganize the same knowledge into cleaner destinations
- new feature files add clarified decisions from later discussions, especially around user accounts

## Main mapping

### Original → Refactored
- `README.md` → `README.md`
- `SPEC.md` → `docs/product/SPEC.md`
- `ROADMAP.md` → `docs/product/ROADMAP.md`
- `ARCHITECTURE.md` → `docs/architecture/ARCHITECTURE.md`
- `ARCHITECTURE.md` data model sections → `docs/architecture/DATA_MODEL.md`
- `PROMPTS.md` → `docs/ai/PROMPTS.md`
- `AGENTS.md` → `AGENTS.md`
- `PROJECT_CONTEXT.md` cross-cutting product and feature context → distributed across product, architecture, and features
- `STUDY_SNAP_USER_ACCOUNTS_CONTEXT_V2.md` → `docs/features/user-accounts.md`
- `CODEX_PROMPT_STUDY_SNAP_USER_ACCOUNTS_V2.md` → `docs/ai/CODEX_PROMPT_USER_ACCOUNTS.md`

## Suggested usage

### For product decisions
Read:
- `README.md`
- `docs/product/SPEC.md`
- `docs/product/ROADMAP.md`

### For implementation
Read:
- `AGENTS.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/DATA_MODEL.md`

### For feature work
Read the relevant file under `docs/features/`

### For LLM/prompt work
Read:
- `docs/ai/PROMPTS.md`

### For historical verification
Read:
- `/legacy/*`
