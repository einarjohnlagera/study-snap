# Explore — NoteLib Feature Context

## Purpose

`/explore` is NoteLib's composite discovery page for both anonymous visitors and authenticated members. It answers what useful material exists before the learner owns it, while reusing the established source surfaces rather than creating a second catalog implementation.

Explore composites:

- the full admin-published Official Study Plan catalog already served at `/collections/published`
- the public-note discovery experience already served at `/public/library`
- a bounded pointer to the separate Exam Hub index at `/exam`

The source routes remain independent. Explore does not replace, redirect, or redefine `/collections/published`, `/public/library`, any subject landing page, or any public-note detail page.

## Tabs and URL State

Explore has two tabs:

1. `Official {profile-aware plural}` for published plans; this is the default and omits `?tab=`.
2. `Notes`, selected with `?tab=notes`.

Authenticated labels continue to use `getCollectionLabels(profileType)`. Anonymous visitors deliberately use the existing `STUDENT` vocabulary, so the first tab reads `Official Study Plans`, not the null-profile fallback `Official Collections`. Unknown tab values continue to resolve to the default Review Sets tab.

All existing filters and pointer-origin query parameters remain URL-driven. Explore's page-view and tab-switch analytics retain their existing firing conditions and metadata keys; anonymous views are segmented later through `analytics_events.user_id IS NULL`.

## Anonymous and Authenticated Behavior

Both audiences receive the same page structure, default tab, full published-plan catalog, public plan previews, public notes, filters, and Exam Hub pointer. Authentication changes action capability, not browseable content:

- authenticated members retain existing Start/Continue behavior and adopted-plan matching
- anonymous visitors retain the Adopt affordance as `Sign in to adopt`
- clicking that affordance stores discovery intent and enters signup; it never calls an authenticated adopt endpoint while signed out

The public note and facet calls use anonymous-safe endpoints. Facet requests are settled independently, so a failed Subject, Course / Program, Authored Depth, or Tag request degrades that filter to an empty list rather than blanking or redirecting the page. Published-plan public reads likewise use public fetches; private profile and owned-collection reads are skipped when no auth user exists.

## Discovery Intent Lifecycle

`notelib-discovery-intent` is a short-lived client cookie modeled on `notelib-exam-intent`:

- payload: published plan id, Goal-vs-leaf plan shape, and the current safe `/explore` query context
- attributes: `max-age=1800`, `SameSite=Strict`, `path=/`
- set: immediately when an anonymous Explore visitor clicks Adopt
- consume: by the Dashboard handoff after verification and onboarding — mounted **inside Dashboard's loaded branch**, so it cannot run before that page's own auth/onboarding guard
- clear: before the adopt request, ensuring remounts and second passes cannot replay it

Successful adoption uses the existing authenticated Goal or Study Plan action and lands on the resulting private collection. If the source was unpublished, deleted, or otherwise unavailable, the cleared intent returns the learner to the preserved Explore context with a normal catalog notice. Malformed or partial values are cleared without throwing. If cookies are blocked, signup still proceeds and only automatic adoption is lost.

When both discovery and exam intent exist, discovery wins — a clicked Adopt is the newer and more specific requested action. The handoff clears the **discovery** cookie before resuming (that ordering is what makes consumption one-shot), and clears the **exam** cookie only after the adoption succeeds, so a failed adoption does not cost the visitor an exam prompt they were entitled to.

## Canonical and Structured Data Relationship

`/explore` is self-canonical and emits a `CollectionPage` whose identity is the composite `NoteLib Explore — Official Study Plans and Public Notes`. Its metadata description and social cards describe both source types.

`/public/library` remains self-canonical and keeps its existing notes-only `CollectionPage` identity. These are distinct collection claims: Explore is the cross-source discovery destination, while Public Library is the canonical route family for public notes, subject landings, and note details. No Public Library redirect ships in this release.

`robots.ts` does not disallow `/explore` because anonymous rendering and complete metadata ship atomically in one production merge. If those pieces are ever split across separate `main` deploys, the incomplete deployment must temporarily disallow `/explore` until the final piece lands.
