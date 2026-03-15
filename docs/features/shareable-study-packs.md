# shareable-study-packs.md — Study Snap Feature Context

## Goal

Allow users to create public share links for generated Study Packs.

## Public route
- `/p/[token]`

## Backend endpoints
- `POST /api/study-packs/{id}/share`
- `GET /api/p/{token}`
- `POST /api/p/{token}/remix`

## Rules
- shared page shows generated content
- shared page is read-only
- raw uploaded image must not be exposed
- raw notes text should be hidden by default
- tokens must be unguessable
- owners can generate token links on demand (reuse existing token when present)
- authenticated users can copy/remix shared Study Packs into their own Study Library
- remix must duplicate existing stored Study Pack content without triggering a new LLM generation request
- expiration may be added later
- optional view count may be tracked

## Purpose
- enable viral distribution
- make Study Packs easier to share
- support public viewing without exposing private user data

