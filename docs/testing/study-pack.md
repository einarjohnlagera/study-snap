# study-pack.md - Testing Notes

## Async note-owned generation

- Note Editor `Generate Study Pack` saves the note before calling generation.
- after generation is queued, Note Editor redirects to Note Detail with `generating=1` and the appropriate `tab`.
- Note Detail renders the `GENERATING` state with friendly loading copy and placeholder Study Pack content.
- Note Detail polling stops when the note reaches `STUDY_PACK_READY`.
- Note Detail polling stops when the note reaches `FAILED`.
- `FAILED` renders a friendly recovery card and `Retry Generation`.
- retry queues generation again from the saved note.
- failed generation and failed retries do not increment Study Pack quota.
- note content remains saved and visible in `Full Notes` after a failed generation.
- AI title/subject/tag suggestions still appear after async generation reaches `STUDY_PACK_READY`.

## Subject sanitization (covered in `SubjectSanitizerTest` and `OpenAiLlmStudyPackServiceTest`)

### stripSubtopicSuffix behavior
- Domain-only input returns unchanged: `"Biology"` → `"Biology"`
- Em-dash combined value stripped to domain: `"Biology – Cell Division"` → `"Biology"`
- Colon-separated combined value stripped: `"Physics: Electricity"` → `"Physics"`
- Multi-word domain preserved: `"Computer Science"` → `"Computer Science"`, `"Criminal Law – Crimes Against Persons"` → `"Criminal Law"`
- Separator at position 0 (empty left side) returns null: `" – Cell Division"` → `null`
- Null input returns null

### Service-level subject enforcement
- Domain-only subjects pass through unchanged: `"Biology"`, `"Engineering"`, `"Medicine"` etc.
- Combined domain-topic is stripped before saving: `"Electrical Engineering – Ohm's Law"` → `"Electrical Engineering"`
- Overlong combined subject is also stripped (not word-count-trimmed): `"Electrical Engineering – Voltage Current Resistance and Power"` → `"Electrical Engineering"`
- Empty/blank subject throws `LLM_INVALID_OUTPUT`
- Broad domain labels (`Engineering`, `Medicine`, `Law`, `Business`, `Education`) are now **valid** and pass without retry

### Edge cases tested
| Input | After normalizeForStorage | After stripSubtopicSuffix | Final result |
|-------|--------------------------|--------------------------|--------------|
| `"Biology - Photosynthesis"` | `"Biology – Photosynthesis"` | `"Biology"` | `"Biology"` |
| `"Physics: Electricity"` | `"Physics: Electricity"` | `"Physics"` | `"Physics"` |
| `"Mathematics – Derivatives"` | `"Mathematics – Derivatives"` | `"Mathematics"` | `"Mathematics"` |
| `"  Biology  "` | `"Biology"` | `"Biology"` (no separator) | `"Biology"` |
| `"Engineering"` | `"Engineering"` | `"Engineering"` | `"Engineering"` (valid domain) |
| `"   "` | `null` | — | `LLM_INVALID_OUTPUT` |

## Key concept sanitization (covered in `KeyConceptSanitizerTest`)

- Filler prefixes stripped: `"relationship between voltage and current"` → `"voltage and current"`
- Overlong concepts truncated to `MAX_KEY_CONCEPT_WORDS` (4)
- Single-word and within-limit concepts returned unchanged
- Null/blank inputs return null
- Repair never blocks study pack creation (hard-truncate as last resort)

## Quiz concept sanitization

- Max 3 words per concept (`MAX_QUIZ_CONCEPT_WORDS`)
- Filler prefix stripping + truncation
- Repair or null for overlong → throws `LLM_INVALID_OUTPUT` only after all repair attempts fail
