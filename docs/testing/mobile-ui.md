# mobile-ui.md - Testing Notes

Verify these cases on mobile-sized viewports:

- major action buttons show icon + text, not icon-only
- primary actions remain readable in both light and dark mode
- related action groups stack cleanly when horizontal space is limited
- Note Detail `Summary` and `Quiz` tabs show text labels on mobile
- switching Note Detail tabs does not jump the page to the top
- switching Note Detail tabs keeps the user near the tab content area
- switching Note Detail tabs does not flash a loading state or refetch the note on mobile
- Note Editor mobile Generate CTA stays centered and readable
- global `Send Feedback` launcher does not overlap the mobile Note Editor CTA
- mobile sticky CTA area stays compact and does not consume excessive vertical space
- Dashboard quiz and creation actions show text labels
- Library/Public Library/Public Profile/Public Note actions show text labels
- Profile and Settings save/navigation actions show text labels
- icon-only exceptions are limited to small utility controls such as edit/delete/back/menu/theme/avatar
