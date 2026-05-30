# account-profile.md - NoteLib Feature Context

## Public Identity

NoteLib keeps account identity simple and note-centric.

- `displayName` is the human-friendly presentation name.
- `username` is the unique public identity / handle.
- Public attribution and future creator URLs must use `username`, not `displayName`.
- Public pages must never expose email addresses or raw private user IDs.

## Username Rules

Usernames are required for stored users after migration.

- lowercase
- `3-30` characters
- letters, numbers, underscores, and hyphens only
- unique case-insensitively
- reserved route and support names are blocked

Existing users are backfilled automatically from `displayName`, then email prefix fallback, with numeric suffixes for duplicates.

## Profile Editing

The private `/profile` identity card lets users edit username alongside their identity fields.

Helper copy (v0.21.0+):

- **Display Name**: `The name shown on your public notes and profile. Falls back to your first name if left blank.`
- **Username**: `Your @handle on NoteLib — shown on your public profile and next to your public notes.`

Changing username should validate format and uniqueness before save.

## Login

Login accepts either email or username through the same credential field.

UI label:

`Email or username`

Existing email login remains supported.

## Account Management Follow-ups

Do not implement these unless explicitly requested:

- forgot password
- change password
- additional email verification and account-security improvements
