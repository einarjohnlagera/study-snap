# mobile-ui.md - Testing Notes

Verify these cases on mobile-sized viewports:

- major action buttons show icon + text, not icon-only
- primary actions remain readable in both light and dark mode
- related action groups stack cleanly when horizontal space is limited
- public mobile navbar keeps theme toggle in the header utility group instead of inside the CTA stack
- public mobile menu keeps nav links separate from `Login` and `Get Started`
- public mobile menu does not duplicate the theme toggle or visible `Get Started` CTA while the menu is open
- Note Detail `Summary`, `Key Concepts`, `Quiz`, and `Full Notes` tabs show text labels on mobile
- the `View Full Notes →` CTA is visible inside the `Summary` view on mobile
- switching Note Detail tabs does not jump the page to the top
- switching Note Detail tabs keeps the user near the tab content area
- switching Note Detail tabs does not flash a loading state or refetch the note on mobile
- the four-tab row remains scrollable/readable on mobile instead of becoming cramped
- `Full Notes` remains readable on mobile for long note bodies
- Note Editor mobile Generate CTA stays centered and readable
- global `Send Feedback` launcher does not overlap the mobile Note Editor CTA
- mobile sticky CTA area stays compact and does not consume excessive vertical space
- mobile `Course / Program` autocomplete sheets stay tappable and scrollable while typing
- typing into mobile `Course / Program` fields filters suggestions in place instead of keeping the full list open
- the custom `Use "..."` action stays below matching saved suggestions on mobile
- Dashboard quiz and creation actions show text labels
- Library/Public Library/Public Profile/Public Note actions show text labels
- Profile and Settings save/navigation actions show text labels
- icon-only exceptions are limited to small utility controls such as edit/delete/back/menu/theme/avatar
- Library and Public Library keep `Filter` and `Sort` inside a mobile sheet/modal instead of always-visible controls.
- Library and Public Library mobile filter sheets include the newer metadata filters (`Course / Program`, and Public Library `Learner Level` when available) without overcrowding the base layout.
- Library, Public Library, and Public Profile note cards remain action-free on mobile and open note detail from the whole card.
- Public Library and Public Profile note cards keep the note-content preview readable on mobile and keep `views` / `copies` on a compact metrics row that does not wrap awkwardly.
