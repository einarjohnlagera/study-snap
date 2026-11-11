Status: Batch 1 review (N3, N8, N4), 2026-11-06, for owner and Note Strategist review. Document-only: no Note or Study Pack created, no production write, no SQL executed, nothing committed. Batch 2 and Batch 3 are NOT drafted.

> **Update 2026-11-06 (later): Batch 1 is closed.** N3 and N8 were approved with no revisions. N4 was approved with two targeted corrections (transaction completion; online versus batch in the Key Idea), now applied in `authored-bodies/fis-n4-transaction-processing-systems.md`; the table rows and Study Pack notes below that quote the old completion rule or the old Key Idea are superseded by that file and by `bsis-year1-fis-batch2-review.md` section 0. The nine-item owner-review list in the strategist prompt was replaced by the streamlined review workflow (decision log D-28).

# BSIS Year 1: Fundamentals of IS, Batch 1 review (N3, N8, N4)

Drafts: `authored-bodies/fis-n3-roles-in-information-systems-practice.md`, `authored-bodies/fis-n8-operational-managerial-and-strategic-information-needs.md`, `authored-bodies/fis-n4-transaction-processing-systems.md`. Standard applied: the approved calibration references N1 and N2 (revised versions preserved unchanged).

Labels: **FACT** (checked on the text or in a source), **INFERENCE** (my reading; standard textbook content not verified against a specific source), **PROPOSAL**.

| Note | Characters | Words | Quick Recall bullets | Longest sentence |
|---|---|---|---|---|
| N3 | 4,479 | 709 | 5 | under 45 words |
| N8 | 4,668 | 707 | 5 | under 45 words |
| N4 | 4,300 | 693 | 5 | under 45 words |

All three exceed the soft 2,200 to 3,200 guideline, in line with the approved N1 (4,440) and N2 (5,498). No required element was cut and none was padded; each Note has one worked example.

**Optional N2 polish (the owner's proposed Overview sentence): NOT applied.** N2 is preserved as the approved reference. I checked the proposed sentence against the framework and it is consistent: "accurate" corresponds to intrinsic, "relevant" and "timely" to contextual, and "usable" to representational and accessibility. If applied, the Overview would lose the clause "which is why its quality matters"; the owner can approve it as a one-line edit.

---

## 1. Academic accuracy and terminology review

### 1.1 N3: Roles in Information Systems Practice

| Claim | Basis | Label |
|---|---|---|
| The roles around an information system (users, technical specialists, managers, vendors) and their responsibilities; the IS professional's characteristics and responsibilities | IS2020 A3.1.1 C3 (printed pp. 98 to 99): roles and responsibilities of the IS professional (Apply); characteristics (Apply); ethical standards (Apply). CIIT FIS description names these roles. CHED sample outcome is about IS in enterprises | FACT |
| Vendors supply software or services; the organization stays accountable for its own data and procedures | IS2020 C8 lists outsourcing possibilities and models (Understand); the accountability sentence is a standard organizational principle, not from IS2020 | FACT for the first part; **INFERENCE** for accountability |
| Users test whether a system fits, report problems, describe needs; specialists configure and support; managers sponsor and set priorities | Standard IS-textbook role descriptions; no single source read | **INFERENCE** |
| Titles and structures differ between organizations | Common-sense and standard caveat | INFERENCE |
| "Professional codes" pointer | Introduction to Computing's *Professional Societies and Standards in Computing* covers ACM and IEEE codes; N3 only points to them | FACT (the existing Note) |

**Terminology:** "end user", "developer and technical specialist", "manager and sponsor", "vendor" are used consistently in the body, example and Quick Recall. N3 deliberately does not use the Introduction to Computing term "systems analyst" (that Note's careers view), so the two Notes do not blur.

### 1.2 N8: Operational, Managerial and Strategic Information Needs

| Claim | Basis | Label |
|---|---|---|
| Decisions differ in type and need different information; the role of information in decision making; types of decisions; stages of decision making | IS2020 C4 (printed p. 99): role of information in decision making (Understand); types of decisions and techniques for decision making (Apply); stages of decision making and appropriate IS tools (Apply) | FACT |
| **Intelligence, design, choice, implementation** as stages, attributed in the Note as "a model based on Herbert Simon's work" | Search results support Simon's **three** phases (intelligence, design, choice) from *The New Science of Management Decision* (1960) and the interlinked, revisitable nature of the phases. **Implementation as a fourth stage is supported only by secondary sources, and they differ**: one cites Simon's 1977 revision for implementation, another names the fourth phase "review". The Note's wording ("based on Simon's work") deliberately does not credit Simon with the fourth stage. **I did not read Simon's text** | FACT for the three phases and for the looping; **INFERENCE** for the fourth stage's labelling. Owner item |
| **Structured, semi-structured and unstructured decisions** | Search results confirm the three terms come from Gorry and Scott Morton, *A Framework for Management Information Systems*, MIT Sloan Working Paper 510-71 (1971; also where the term "decision support system" first appears, per a secondary account). **The paper's text could not be read**: archive.org refused the connection and the MIT DSpace bitstream returned HTTP 429 (retry after 900 seconds) on two attempts. The Note gives **plain-language definitions and attributes them to no one** | FACT for the bibliographic record; **the definitions are INFERENCE (standard usage), not checked against the paper** |
| **Operational, managerial and strategic** levels | The standard three-level framing associated with Anthony's planning-and-control categories; not named in IS2020 and **not verified against Anthony's text**. The Note uses plain terms and cites no one | INFERENCE |
| **Level and structure are related but not the same** | The Note says only that operational decisions are often more structured and strategic decisions often less so, with a counter-example in each direction. The paper's own statement of how the two dimensions relate was **not read**, so the Note claims no more than "related but not the same" | INFERENCE |
| Example information needs (bus availability and driver schedules; monthly ridership by route and fuel cost against budget; population trends, competitor fares and new-bus cost) | Fictional illustrations | n/a |

**Terminology and consistency:** the four stages appear in order in the body, the example and Quick Recall; the three levels use the same wording in Overview, Core Details and Quick Recall; the Note names none of N2's quality categories and uses the words "inaccurate", "irrelevant", "too late" and "hard to understand" in one sentence only. An earlier wording problem was caught and fixed: a managerial example of an *annual* budget contradicted the definition of managerial decisions as "over weeks or months"; it now reads "routine maintenance spending within an agreed limit".

### 1.3 N4: Transaction Processing Systems

| Claim | Basis | Label |
|---|---|---|
| Transaction Processing Systems are a common type of information system | IS2020 C1 names "Transaction Processing Systems" as a common type (Understand) | FACT |
| A TPS records routine business events; users are mostly operational staff; accuracy and reliability matter | Standard IS-textbook content; no single source read | **INFERENCE** |
| Online (immediate) versus batch processing; validation, authorization and reconciliation controls; corrections recorded rather than deleted; the audit trail | Standard IS and accounting-information concepts; no single source read | **INFERENCE** |
| "A sale is complete only when payment has been accepted and the receipt issued" | A business rule of the **fictional** grocery store, stated as an illustration of "business rules define when a transaction is complete" | n/a (fictional) |

**Terminology:** the Note says "online (immediate) processing" and **avoids "real-time"**, which has a technical meaning not taught here. "Authorization" is used as in the existing *Authentication, Passwords and Access Control* Note (which permissions an identity has). "Validation" is used at the **organizational** level (checking an entry against business rules); the programming-level Note *Input Validation* uses the same word for program input (see overlap). The mislabel caught on review: the daily sales totals were first labelled "output"; they are now correctly "stored records", and the receipt is the output.

---

## 2. Canonical overlap assessment

Existing Notes below were read in full (the Introduction to Computing Notes in the earlier passes) or, for the Notes marked \* in this pass, in their Overview and Core Details. **No existing canonical Note provides equivalent coverage of N3, N8 or N4.**

### 2.1 Against existing Notes

| New Note | Existing Note | What it teaches | Overlap | Finding |
|---|---|---|---|---|
| N3 | *The Computing Industry, Professions and Careers* | Industry segments, software, infrastructure and data roles, entry pathways, professional practice; *systems analyst* once | Roles vocabulary | N3 is not a roles catalog and uses no career content; it frames roles around **one system and how they collaborate**. Distinct |
| N3 | *Professional Societies and Standards in Computing* | ACM and IEEE codes, ethics codes, standards bodies | One sentence | N3 points to "professional codes" only; no ethics framework |
| N3 | *Analyzing Organizational Computing Solutions* | Stakeholder requirements, vendor lock-in, pilots, evaluation | Vendor mention | N3 says vendors supply products and services and that the organization stays accountable; it teaches **no vendor selection or evaluation**. Distinct |
| N3 | *Workplace and Special-Purpose Communication* \*, *Effective Communication: Principles, Process, and Contexts* \* | Audience, channel, formats, feedback, the seven Cs | "Communication" | N3's communication is **role-to-role in an information-system project** (users in work terms, specialists without jargon, a sample before go-live); it teaches no formats or models. Distinct |
| N8 | *Artificial Intelligence and Human Judgment* | Decision support versus automated decision-making; human oversight | "Decision" | N8 never defines decision support; it teaches information needs by decision. Distinct |
| N8 | *Problem-Solving Steps in Programming* \*, *Mathematical Problem Solving and Logical Reasoning* \* | Staged problem solving (analyze to refine; understand, plan, carry out, review) | **Structural similarity** (staged, revisitable) | Different domain and stages; N8's stages describe organizational decisions. No conflict; the "stages can repeat" idea is consistent with both |
| N8 | *Analyzing Organizational Computing Solutions* | Weighted selection among solutions | "Choice" | N8's choice stage mentions no scoring method. Distinct |
| N4 | *Input Validation: Checking Before Trusting Data* \* (Computer Programming 1), *Error Handling Strategies…* \* (Computer Programming 2) | Program-level validation and failure handling | **Shared words** "validation", "errors" | N4's validation and error correction are **business controls** (a positive quantity, a supervisor's void, a correction record), not program code. **Terminology consistent**: both treat invalid input as something to be caught and handled. A learner meets "validation" in two settings; no change proposed |
| N4 | *Authentication, Passwords and Access Control* \* | Authentication versus authorization; role-based access control | "Authorization" | N4 uses authorization in the same sense (who may do what) in one clause |
| N4 | *Coding Schemes: Bar Codes, QR Codes and Check Digits* \* (Mathematics in the Modern World) | UPC-A, check digits | The grocery example scans items | N4 does not explain bar codes. **Quiz caution:** do not ask about check digits in N4 |
| N4 | *Computer Security Fundamentals…* | Integrity and availability | Accuracy and reliability | Different framing (organizational record accuracy); no contradiction |

### 2.2 Against the approved FIS inventory

| Pair | Boundary | Status |
|---|---|---|
| **N3 versus Introduction to Computing** | Collaboration around an organizational system, not computing careers | **Held** |
| N3 versus N12 (development life cycle) and N13 (adoption) | N3 uses a project example but names **no life-cycle stage**; it mentions staff training once, as something the technical team does | **Held with a low watch item:** the single training mention is the only point of contact with N13 |
| **N8 versus N2** | N8 teaches how information is used in decisions; N2 teaches what information is and how quality is judged. N8 names no category | **Held** |
| N8 versus N6 and N5 | No decision-support content; the managerial examples ("monthly ridership by route and fuel cost compared with budget") describe *information needed*, not reports or systems | **Held** |
| **N4 versus N1** | N1 teaches the general processing cycle; N4 uses its labels once and adds TPS-specific knowledge (a transaction as a unit of business, completion rules, online versus batch, validation, authorization, reconciliation, correction records, the audit trail) | **Held** |
| **N4 versus future N5** | N4 records and processes operational transactions; reports appear only as a one-sentence pointer | **Held** |
| N4 versus N9 (enterprise systems) | None | Held |

**No draft needed to be flagged for failing to keep its distinct learning objective.**

---

## 3. Per-Note Domain Context and reuse recommendation (ADR-001)

Rule applied (FACT): unset (Information Systems fallback) for organizational or business-process treatment; `COMPUTING` only for broadly transferable computing knowledge; decided from the authored content, never from plan membership or Bulk Generate; no new value; no added program.

| | N3 | N8 | N4 |
|---|---|---|---|
| Subject / Depth / Programs | Fundamentals of Information Systems / COLLEGE / Information Systems | same | same |
| **Domain Context** | **Unset, provisionally** | **Unset, provisionally** | **Unset, provisionally** |
| **Reuse flag** | **`candidate (low)`** | **`none`** | **`none`** |
| Why it belongs in FIS | The collaboration of users, managers, specialists and vendors around an **organizational** system, with a library example and an organizational accountability point | Organizational levels, decision structure and the information each needs, with a bus-company example; a management and IS framing | Organizational transactions, business rules and controls in a grocery store; explicitly **no** database mechanics |
| Could BSCS or BSIT learners reuse it unchanged? | **Plausibly for BSIT** (IT professionals work with users, managers and vendors); marginal for BSCS. The Note is written for an organizational system, which is IS-framed | **Unlikely**: decision levels and structure are management and IS content | **Unlikely**: business-event treatment; a BSIT learner could use it as context but it is not computing knowledge |
| Ambiguity for the owner | **Moderate.** The roles half is close to "IT project roles", which are broadly relevant. The organizational framing and the vendor-accountability point keep it IS-fallback | **Low** | **Low.** Confirmed: the screen found none of ACID, locking, isolation, concurrency, schema, database, SQL, rollback, commit, primary key or table |
| Equivalent canonical coverage | None | None | None |

**Flagged for taxonomy and reuse review before publication:** **N3** (`candidate (low)`). Per the owner's rule, a flagged Note is not published IS-only until reviewed, and any second program would need an explicit Domain Context first. **N4 is confirmed organizational, as you asked me to check.** No `COMPUTING` assignment is proposed for any Batch 1 Note, none for Bulk Generate convenience.

---

## 4. Study Pack readiness (nothing generated)

Each Note can support five accurate items in the owner's mix (two conceptual, two application or scenario, one distinction or misconception). **Scenario items should use a new but equivalent example, not the worked one**, and each must have one defensibly best answer. Slots and supporting text (not questions):

### N3
1. **Conceptual:** match a responsibility to a role (users describe needs and test fit; specialists configure, test and support; managers sponsor, set priorities and approve; vendors supply software or services) (Core Details 1 to 4). *One-best-answer caution:* use only responsibilities the Note assigns to a single role.
2. **Conceptual:** why the roles must communicate, or what the organization remains responsible for when a vendor supplies a system (Key Idea; Core Details 4 and 7).
3. **Application:** a **new** scenario (for example a fictional clinic introducing online appointment booking) in which a problem is traced to a missing or misunderstood role, such as users who were not consulted (Why It Matters 1; Core Details 7).
4. **Application:** a **new** scenario (for example a fictional school canteen replacing its cash register) asking which communication would have prevented a misunderstanding (specialists explaining options without jargon, showing a sample before go-live) (Core Details 7).
5. **Distinction or misconception:** "a vendor who supplies the system takes over responsibility for the organization's data and procedures", or "every organization has the same job titles" (Core Details 4 and 5).

### N8
1. **Conceptual:** match information needs to a level (detailed, current, internal; summarized, periodic; broader, less frequent, often external) (Core Details 1 to 3).
2. **Conceptual:** the three decision-structure terms and what each means (Core Details 4).
3. **Application:** a **new** scenario (for example a fictional supermarket chain or university) classifying decisions by **level and structure**. *One-best-answer caution:* choose clear cases and avoid the cross-cases the Note itself discusses as exceptions.
4. **Application:** a **new** scenario identifying the decision **stage**, or showing a return to an earlier stage after a poor result (Core Details 6 and 7).
5. **Distinction or misconception:** "all strategic decisions are unstructured" or "operational decisions are always structured", or "the stages always run once, in a fixed order" (Core Details 5 and 6).
- **Avoid asking:** who proposed the stage model or the structure terms; whether Simon's model has three or four stages; any detail about information-quality categories (that is N2).

### N4
1. **Conceptual:** what a business transaction is and what a TPS does and for whom (Core Details 1 and 2).
2. **Conceptual:** online versus batch processing (Core Details 6).
3. **Application:** a **new** scenario (for example a fictional clinic registration desk or school canteen) in which a described check is recognized as validation, authorization or reconciliation. *One-best-answer caution:* validation is checking an entry as it is made; authorization is who may do what; reconciliation compares records with reality at a point such as closing.
4. **Application:** a **new** scenario of a wrong entry and the proper response (a recorded correction with an audit trail, not silent deletion) (Core Details 8).
5. **Distinction or misconception:** recording transactions versus analyzing them (TPS versus reporting), or "errors can simply be deleted" (Core Details 8 and 9; Why It Matters 3 and 4).
- **Avoid asking:** ACID, database concepts, bar-code check digits, any statistic, how "real-time" differs from online.

**Across all three:** vary the correct option's position naturally; no fixed position; no "all of the above".

---

## 5. Material concerns for the owner before the next batch

None blocks Batch 2. In priority order:

1. **Primary sources for N8's terminology are unverified.** The Gorry and Scott Morton paper (the structured, semi-structured and unstructured terms; the managerial-activity levels) could not be read: archive.org refused the connection and MIT DSpace returned HTTP 429 twice. Simon's text was not read, and the fourth stage's labelling varies across secondary sources. The Note therefore uses **plain definitions, attributes the stage model only as "based on Herbert Simon's work", and cites no one for the level or structure terms**. **Action if you want them verified:** download the working paper from the MIT DSpace page (record 1721.1/47936) or retry the bitstream after the 15-minute rate limit, and I will check the definitions and the paper's statement on how structure relates to level. Not a blocker.
2. **N3's reuse ambiguity (moderate).** `candidate (low)`: the roles content is close to general IT-project roles. Keeping it unset is defensible; the taxonomy review before publication decides.
3. **N3 and N13 contact point.** N3 mentions that the technical team "trains staff"; N13 owns adoption, acceptance, resistance and training. One passing mention; flagged so N13's draft does not repeat it.
4. **"Validation" in two settings** (N4 organizational; Input Validation programming). Terminology is consistent; a learner may meet the word twice. No change proposed.
5. **N4, N3 and N8 content is standard textbook material** (INFERENCE): no single course textbook was read. If the owner names a course textbook, I will check the definitions of online and batch processing, reconciliation, the audit trail, and the role descriptions against it.
6. **Length.** All three are about 4,300 to 4,700 characters, matching the approved N1 and N2 range. The owner called the guideline soft; no trimming proposed. Optional trims exist per Note if wanted.
7. **IS 2010.1** remains unread; nothing is attributed to it.

**Recommendation (PROPOSAL):** the three drafts meet the calibration standard and keep their boundaries. The next decision is whether to authorize **Batch 2** (N6, N9, N11, N7, N12), with the same standard, screens and per-Note assessment. N12 should be drafted with explicit care for its boundary with *Analyzing Organizational Computing Solutions* and with N3 (participation) and N13 (adoption).
