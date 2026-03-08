# shareable-study-packs.md — Study Snap Feature Context

## Goal

Allow users to create public share links for generated Study Packs.

## Public route
- `/share/[token]`

## Backend endpoints
- `POST /api/studyPack/{id}/share`
- `GET /api/share/{token}`

## Rules
- shared page shows generated content
- raw uploaded image must not be exposed
- raw notes text should be hidden by default
- tokens must be unguessable
- expiration may be added later
- optional view count may be tracked

## Purpose
- enable viral distribution
- make Study Packs easier to share
- support public viewing without exposing private user data

