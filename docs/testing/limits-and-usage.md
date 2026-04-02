# limits-and-usage.md - Testing Notes

Verify these cases whenever Study Pack quota logic changes:

- if the Study Pack limit is `5` and usage is `4`, generation is still allowed
- if the Study Pack limit is `5` and usage is `5`, generation is blocked
- warning banners and blocking use the same effective Study Pack usage count
- Free near-limit messaging appears when `studyPacksRemaining` is `2` or `1`
- Premium near-limit messaging appears when `studyPacksRemaining` is `2` or `1`
- near-limit messaging shows the actual remaining-credit count
- when remaining Study Packs reaches `0`, `Generate Study Pack` stays enabled and opens the plan-specific limit modal on click
- Free limit modal shows `Upgrade to Premium`, `Maybe Later`, and `View My Plan`
- Premium limit modal shows `Upgrade Plan`, `Get More Study Packs`, and `Maybe Later`
- saving a note does not consume Study Pack quota
- a failed Study Pack generation does not consume quota
- retrying after a failed generation does not double-count usage
- Create Note and Note Detail both use the same Study Pack metadata suggestion flow
- deleting a note or Study Pack does not retroactively change already-recorded quota usage for the current billing cycle
