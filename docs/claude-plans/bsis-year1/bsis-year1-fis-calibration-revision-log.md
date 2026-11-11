Status: calibration revision log for N1 and N2 (revision 2), 2026-11-06, for owner and Note Strategist review. Document-only: no Note or Study Pack created, no production write, no SQL executed, nothing committed. N3 to N13 are NOT drafted.

# BSIS Year 1: Fundamentals of IS, calibration revision log (N1 and N2)

Revised drafts: `authored-bodies/fis-n1-information-systems-as-socio-technical-systems.md` and `authored-bodies/fis-n2-data-information-and-knowledge-in-organizations.md`. Earlier source notes and overlap assessment (still valid except where this log says otherwise): `bsis-year1-fis-calibration-review.md`.

Labels: **FACT** (checked on the text or in a source), **INFERENCE**, **PROPOSAL**.

---

## 1. Change log: the owner's required edits

### N1

| Edit | Applied text (exact) | Where |
|---|---|---|
| **A** Avoid absolute functionality claims | "An information system is most effective when its people, processes, data and technology work together, and its results are checked against organizational needs." | Key Idea, first sentence |
| **B** Clarify control and feedback | "Control uses rules, checks and performance measures to keep a system operating as intended. Feedback about its results helps people identify problems and take corrective action, such as correcting data, changing a procedure or adding resources." | Core Details 4 |
| **C** Enrollment input precision | "Students submit enrollment details and payment records (input); the system checks prerequisites and section capacity and computes fees (processing)…" | Core Details 5 |
| **D** Information systems versus computer systems | "These four components describe an information system at the organizational level. A computer system focuses more narrowly on the computing technology that may support it." (the sentence on the technology component being what is usually called information technology is kept) | Core Details 2 |

**Consistency edits I made to keep the Note coherent with A to D (each is within the owner's stated consistency requirement; flagged so you can reject any):**
- **Control can occur at any stage.** Added one sentence after the required control text: "Controls can act at any stage, such as checking data as it is entered or reviewing results afterward." This secures "do not imply that every control occurs only after output."
- **Example labelled consistently with the new control definition.** The old example called only the registrar's report "control". It now reads: "Control appears at several points: the prerequisite and capacity checks, and the registrar's review of a report of full sections and unpaid balances. When that feedback shows a section is full, staff open another one, and when enrollment details were entered wrongly, they correct them (corrective action)." (The old "a form was entered wrongly" no longer matched the changed input wording.)
- **Quick Recall, control.** "Control — the rules, checks and performance measures that keep a system operating as intended; feedback about results helps people identify problems and take corrective action."
- **Absolute wording softened elsewhere in the same spirit as edit A:** Key Idea "a system with excellent technology **can** still fail to serve an organization if…" (was "still fails if…") and Why It Matters "systems **can** fail for non-technical reasons".
- **Kept as instructed:** the socio-technical explanation, the four-component model, the five-stage cycle (named identically in the Core Details, example, Why It Matters and Quick Recall), the enrollment example, the component-mapping bullet, and the technology-change bullet.

### N2

| Edit | Applied text (exact) | Where |
|---|---|---|
| **A** Data definition | "Data are recorded facts, observations or symbols that can be processed or interpreted. In a fictional pharmacy chain, the individual sales records of each medicine at each branch are data." | Core Details 1; Overview; Quick Recall (all three use the same wording) |
| **B** Knowledge definition | "Knowledge is understanding developed from information, experience and context that helps people interpret situations and make decisions." | Overview; Core Details 3; Quick Recall (identical in all three) |
| **C** Replace the rainy-season example | "In the pharmacy, a branch manager notices from previous sales records that demand for a particular product rises during a recurring local event, and uses that experience to plan the next order." (inside the fictional pharmacy chain introduced in Core Details 1) | Core Details 3 |
| **D** Wang and Strong terminology | "Wang and Strong developed a consumer-centered framework for data quality that also helps explain the quality of information used in organizations. It groups quality into four categories: intrinsic, contextual, representational and accessibility." Quick Recall now says "Wang and Strong's data-quality framework groups it into four categories". The Note never calls it an information-quality framework | Core Details 5; Quick Recall |
| **E** Accessibility and access security | "Accessibility concerns whether intended users can obtain information when needed, while access security helps prevent unauthorized use. Both belong to the accessibility category." The pharmacy report example is preserved and mapped to the categories | Core Details 7 and 8 |
| **F** Virtual value chain precision | The five activities are kept in order; added "The activities are not a rigid pipeline: organizations may repeat or combine them." Quick Recall now says "set of five activities" rather than "sequence" | Core Details 11; Quick Recall |

**Consistency and readability edits I made (flagged so you can reject any):**
- Key Idea: "…and from the knowledge people develop to guide their actions" (was "and into knowledge that guides action", which implied data turn directly into knowledge).
- Overview: "Information creates the **most** value when it is good enough to use" (was "only when", an absolute claim).
- **Readability:** the long quality sentence was split; the categories now have one short sentence each (intrinsic, contextual, representational), accessibility has its own bullet (edit E), and the pharmacy report example is three short sentences tied to the categories; the virtual value chain bullet was split into four short sentences. The duplicate "information quality means fit for purpose" bullet was merged into the Wang and Strong bullet. **No sentence in either Note now exceeds 45 words except one list sentence in N1 (component definitions, 49 words).**
- **Kept as instructed:** the pharmacy example, the three-way distinction, the organizational focus, the four categories, the five activities, the value-and-cost explanation.

**Optional items correctly NOT added (owner decisions 4 to 8):** no bits terminology bridge; no virtual-versus-global value chain comparison; no sorting of the four components into social and technical sides; the computer-system distinction is now less categorical; no extra framework.

---

## 2. Final accuracy and internal-consistency check (FACT, run on the final text)

**N1** (4,440 characters, 674 words)
- Five section headers present; **five Quick Recall bullets**; seven Core Details bullets.
- **Control and cycle consistency:** the five stages (input, processing, storage, output, control) are named identically in Core Details 3, the Quick Recall and the example; the Control bullet, the example and the Quick Recall all use rules, checks and performance measures plus feedback and corrective action; **no wording implies control occurs only after output** (screened for "only after", "after the output", "falls short", "compares the output": none).
- **Input wording:** "enrollment details and payment records" used consistently; the stale "form" wording is gone.
- **Absolute claims:** none found ("works only", "still fails", an absolute IS-versus-computer-system boundary: all absent). The only "all" is "all four components".
- Component-mapping bullet and technology-change bullet intact. No scope-disclaimer terms; no hardware, database or program-audience terms; no LaTeX; no real-company name.

**N2** (5,498 characters, 855 words)
- Five section headers present; **five Quick Recall bullets**; twelve Core Details bullets.
- **Definitions identical in the Overview, Core Details and Quick Recall:** data, information and knowledge (checked by string match). Data is **not** defined as unorganized ("not yet organized" is absent); knowledge is **not** defined as information plus experience alone ("information combined with experience" is absent; context is included).
- **Attribution:** "consumer-centered framework for data quality" present; the framework is never named an information-quality framework.
- **Four categories named:** intrinsic, contextual, representational, accessibility; accessibility and access security distinguished and both placed in the accessibility category.
- **Virtual value chain:** the five activities appear in the order gather, organize, select, synthesize, distribute (positions checked inside the bullet); "not a rigid pipeline" present; the word "sequence" is gone; **no** comparison to a global value chain.
- No rainy-season or medicine-demand claim; no bits terminology; no scope-disclaimer terms; no database or program-audience terms; no LaTeX; no real-company name; the pharmacy example is the single example used throughout.
- **Absolute wording:** the only "only" constructions are "not only…" (twice) and none asserts exclusivity.

**Quick Recall versus body:** every Quick Recall definition matches the corresponding Core Details wording in both Notes (checked above).

---

## 3. Final Domain Context and reuse-flag assessment (ADR-001)

| | N1 | N2 |
|---|---|---|
| Subject | Fundamentals of Information Systems | Fundamentals of Information Systems |
| Authored Depth | COLLEGE | COLLEGE |
| Applicable Programs | Information Systems only | Information Systems only |
| **Domain Context** | **Unset, provisionally approved** | **Unset, provisionally approved** |
| **Reuse flag** | **`candidate (low)`** (unchanged) | **`candidate (low), not approved`** (**changed from my earlier `none`, per the owner's decision**) |

- **N1.** The organizational, socio-technical treatment, the enrollment example and the organizational-level view of an information system support the Information Systems fallback. The softened computer-system sentence does not turn it into generic computing knowledge. Future BSIT reuse would be a separate decision and would need an explicit Domain Context first.
- **N2.** Unchanged in substance: the definitions and the quality categories are broadly transferable, but the complete Note is organizational (fitness for users' purposes, the cost and value of information, the virtual value chain, pharmacy examples). Per the owner: **do not split N2, do not add BSCS or BSIT programs, create no new Domain Context value.** Edit A (the broader data definition) and edit B (the broader knowledge definition) made the definitions half slightly **more** generic; I judge that this does not change the unset verdict, because the value discussion and the virtual value chain still carry the Note, but it is the reason the flag stays `candidate (low)`.
- **Canonical overlap:** unchanged from the review: no existing canonical Note provides equivalent coverage. *Components of a Computer System* (computer-system-level input, process, store, output; hardware components) is the closest existing Note to N1; edit D keeps the two consistent ("a computer system focuses more narrowly on the computing technology that may support it"). *Critical Reading and Evaluating Sources* and *Bits, Bytes and Units of Information* remain the two terminology neighbours for N2; the owner decided no bridge sentence.
- **No ambiguity needing a new decision** beyond the two flags above.

---

## 4. Study Pack readiness after the edits (nothing generated)

Each Note can support five accurate items in the owner's mix: **two conceptual, two application or scenario, one distinction or misconception**. Slots and the supporting text (not questions):

**N1**
1. Conceptual: identify the four components and what each means (Overview; Core Details 1).
2. Conceptual: the five processing-cycle stages in order, and what control uses (rules, checks, performance measures) (Core Details 3 and 4).
3. Application: a **new equivalent scenario** (for example a fictional clinic appointment desk or library loan counter) in which a breakdown is traced to a component, or a change in one component forces changes in others (Core Details 5 to 7; Why It Matters 1).
4. Application: a new scenario in which a described check or review is recognized as control and the response as corrective action; **a control that acts before output must be a possible correct answer** (Core Details 4 and 5).
5. Distinction or misconception: an information system versus its technology component, or "excellent technology is enough" (Key Idea; Core Details 2).

**N2**
1. Conceptual: data, information and knowledge definitions (Core Details 1 to 3); **do not imply data must be unorganized; do not present knowledge as exactly information plus experience**.
2. Conceptual: the four Wang and Strong categories and what each means (Core Details 5 and 6).
3. Application: classify a described flaw in a report as a category, including access security as part of accessibility (Core Details 6 to 8).
4. Application: place the steps of a described scenario in the five virtual value chain activities, in understandable terms (Core Details 11 and 12; activities may repeat or combine).
5. Distinction or misconception: data versus information versus knowledge in a described situation, or "more information is always better" (Core Details 2, 3 and 10).

**Avoid asking:** framework publication years; the number or the full list of Wang and Strong dimensions; anything about demand patterns; which component is "social" or "technical"; the origin of "socio-technical"; details not taught. **Key positions:** vary the correct option naturally across the five items; no fixed position; no "all of the above".

---

## 5. Remaining material academic or learner-facing concerns

None is a blocker. In priority order:

1. **Length (N2).** N2 is 5,498 characters, about 17% longer than the first draft, because the required edits add attribution, the accessibility and access-security distinction, the pipeline caveat and a category-mapped example. The owner said the range is soft and not to trim required content. **If you want it shorter, the candidates that cost the least learning are** Core Details 9 (193 characters, partly repeated in Why It Matters 2), Core Details 4 (168) and Why It Matters 4 (185), which together (546 characters) would bring N2 to about 4,950. N1 is 4,440 and has no obvious cut other than the component-mapping bullet the owner asked to keep.
2. **N1 Core Details 1** is a 49-word list sentence (the four component definitions). It reads acceptably as a definition list; it could be split if a learner reviewer finds it dense.
3. **The definitions of data, information and knowledge are now the owner-approved formulations** and are consistent across each Note, but **I have not tied them to a specific cited source** (IS2020 does not define them). Record the owner's chosen source for the drafting artifact if one exists.
4. **IS 2010.1** was not read; nothing is attributed to it.
5. **Reference format.** Wang, R. Y., and Strong, D. M. (1996), *Beyond Accuracy: What Data Quality Means to Data Consumers*, *Journal of Management Information Systems* 12(4), starting page 5 (the end page, 33, is from memory). The brief's title read "...Information Consumers"; the paper's title ends "...Data Consumers". Rayport and Sviokla, *Exploiting the Virtual Value Chain*, Harvard Business Review, November-December 1995 (volume, issue and pages from library-catalog results, not the HBR page). Neither reference appears in the learner-facing text beyond the authors' names.
6. **N2's information definition** ("data organized or processed so that they are meaningful for a purpose") and the new data definition ("can be processed or interpreted") are compatible, but a reviewer may ask whether processed data are still data. The Note answers it by tying information to **meaning for a purpose**; no change proposed.

**Material factual contradiction exposed by the edits:** none found. The edits did not require reopening any of the nine decisions.

**Recommendation (PROPOSAL):** both revised drafts pass the final consistency review. The owner's next decision is whether to authorize the remaining controlled drafting batches (N3, N8, N4; then N6, N9, N11, N7, N12; then N5, N13), with this standard: required-edit style, one named framework per concept, no absolute claims, and the same screens.

---

## 6. Local artifacts updated

The decision log (D-22 to D-24) and the two authored-body files. Not touched: production, N3 to N13, SQL, existing Notes, Study Packs, the shaping TSV and workbook (the plan title and root description were unchanged by this round).
