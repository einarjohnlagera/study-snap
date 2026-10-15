-- Content correction for one public note. OWNER-RUN — Claude does not execute production writes.
-- Written 2026-10-13 (Prod Investigator session) at the owner's request.
--
-- Note:    0c6b0be8-8b7d-41dc-9619-94cab1d52064
-- Title:   Defective Contracts: Rescissible, Voidable, Unenforceable, and Void
-- Owner:   dee4225c-e460-4f89-a6e5-cd43f6dd1972 (ADMIN)
-- State read 2026-10-13: visibility PUBLIC, status GENERATED, 2,758 chars,
--   md5(content) = ed1556bd5984e2ed2648db387a7a77bc, updated_at 2026-10-12T14:51:53.847299Z
--
-- Why: the stored note misclassifies the four defective-contract types (e.g. it calls fraud,
-- violence and intimidation grounds for RESCISSION, which are vitiated-consent grounds for
-- VOIDABLE contracts, and limits unenforceability to missing form). The Study Pack and its
-- quizzes were generated from that text. The replacement text is the owner-supplied correction.
--
-- ⚠️ FORMAT: the owner's correction arrived as Markdown. Full Notes renders note content as PLAIN
-- TEXT (private page: `whitespace-pre-wrap`, private-note-detail-page-client.tsx:3285-3295; public
-- page: split into blocks by blank lines, lib/public-note-text.ts:88). None of this account's 2,002
-- notes contain `##` or `**`, so Markdown would print literally. The text below keeps every word of
-- the correction and converts only the syntax to the house format: emoji section headers, `- `
-- bullets, no `#` and no `**`. The four Memory-guide lines became `- ` bullets.
--
-- Expected: exactly 1 row updated. The guard on the OLD md5 makes this a no-op (0 rows) if the
-- note was edited after 2026-10-13's read — re-read and re-plan in that case rather than forcing it.

BEGIN;

-- 1. Pre-check: expect 1 row, old_md5 = ed1556bd5984e2ed2648db387a7a77bc
SELECT id, title, owner_user_id, md5(content) AS old_md5, length(content) AS old_chars
FROM notes
WHERE id = '0c6b0be8-8b7d-41dc-9619-94cab1d52064';

-- 2. The write. Expect: UPDATE 1
UPDATE notes
SET content = $note$Types of Defective Contracts in Business Transactions

📘 Overview
Under the Philippine Civil Code, defective contracts are classified into four principal categories: rescissible, voidable, unenforceable, and void or inexistent contracts.
Each category has distinct legal grounds, effects, remedies, and rules on ratification. Correct classification is essential in understanding obligations and contracts under the Regulatory Framework for Business Transactions (RFBT).

🧠 Key Idea
The four classifications differ according to the nature of the legal defect:
- Rescissible: Validly entered into but subject to rescission on grounds established by law.
- Voidable: Binding unless annulled because of incapacity or vitiated consent.
- Unenforceable: Cannot be enforced through court action unless ratified, in cases specified by law.
- Void or Inexistent: Null from the beginning and cannot be ratified.

⚔️ Core Details:
1. Rescissible Contracts — Articles 1380–1389
Rescissible contracts are validly entered into but may be rescinded on specific grounds recognized by law.
Examples include:
- Certain contracts entered into by guardians that cause their wards a lesion exceeding one-fourth of the value involved.
- Certain contracts entered into on behalf of absentees involving the same degree of lesion.
- Transactions undertaken in fraud of creditors when the creditors cannot otherwise collect their claims.
- Certain contracts concerning property under litigation without the required knowledge or approval.
- Other contracts specifically declared rescissible by law.
Important characteristics:
- The contract is initially valid.
- Rescission under Article 1383 is a subsidiary remedy.
- The action for rescission is generally subject to a four-year period under Article 1389, with special commencement rules for guardianship and absentees.
Remember: Fraud against creditors may justify rescission, but fraud that vitiates a party's consent generally concerns voidable contracts.

2. Voidable Contracts — Articles 1390–1402
Voidable contracts are binding unless annulled through proper legal action.
Grounds include:
- Incapacity of one contracting party to give consent.
- Consent vitiated by mistake, violence, intimidation, undue influence, or fraud.
Important characteristics:
- The contract remains binding unless annulled.
- It is susceptible of ratification.
- Ratification extinguishes the action for annulment.
- An action for annulment generally must be brought within four years under Article 1391, with the starting point depending on the applicable ground.
Unlike rescissible contracts, voidable contracts do not require economic damage to establish voidability.

3. Unenforceable Contracts — Articles 1403–1408
Unenforceable contracts cannot be enforced through court action unless ratified, as provided by law.
Article 1403 identifies three principal categories:
- Contracts entered into on behalf of another person without authority or beyond the representative's powers.
- Agreements covered by the Statute of Frauds that do not satisfy its applicable evidentiary requirements.
- Contracts in which both parties are incapable of giving consent.
Important characteristics:
- Unenforceability is not limited to missing written formalities.
- Ratification may make the contract enforceable according to the applicable rules.
- Unenforceable contracts cannot be assailed by third persons under Article 1408.

4. Void or Inexistent Contracts — Articles 1409–1422
Void or inexistent contracts are null from the beginning.
Examples under Article 1409 include:
- Contracts with a cause, object, or purpose contrary to law, morals, good customs, public order, or public policy.
- Absolutely simulated or fictitious contracts.
- Contracts whose cause or object did not exist at the time of the transaction.
- Contracts involving objects outside the commerce of persons.
- Contracts contemplating impossible services.
- Contracts whose principal object cannot be ascertained from the parties' intentions.
- Contracts expressly prohibited or declared void by law.
Important characteristics:
- They are void from inception.
- They cannot be ratified.
- Under Article 1410, an action or defense for declaration of inexistence does not prescribe.

5. Rescission vs. Annulment
Rescission under Articles 1380–1389:
- Applies to validly agreed contracts on grounds established by law.
- Often involves legally recognized prejudice or injury.
- Is subsidiary under Article 1383.
Annulment under Articles 1390–1402:
- Applies to voidable contracts.
- Concerns incapacity or defects affecting consent.
- May be prevented by valid ratification.
These remedies should not be treated as interchangeable.
Additional distinction: Rescission or resolution under Article 1191 for breach of reciprocal obligations is a separate legal concept from rescission of rescissible contracts under Articles 1380–1389.

🎯 Why It Matters:
- Accurate classification: Determines the appropriate legal remedy.
- Contract enforcement: Clarifies whether an agreement remains binding or can be enforced.
- Business transactions: Helps identify legal risks in contractual arrangements.
- Professional practice: Supports sound understanding of obligations and contracts for CPALE preparation.
- Dispute resolution: Distinguishes rescission, annulment, ratification, and declarations of nullity.

🧠 Quick Recall:
- Rescissible — Valid contract subject to rescission on specified legal grounds, often involving prejudice.
- Voidable — Binding unless annulled because of incapacity or vitiated consent.
- Unenforceable — Cannot be enforced through court action unless ratified, in cases recognized by law.
- Void — Null from the beginning and incapable of ratification.
Memory guide:
- RESCISSIBLE → Recognized prejudice / statutory grounds
- VOIDABLE → Defective consent / incapacity
- UNENFORCEABLE → Court enforcement barred unless ratified
- VOID → No legal validity from inception

Legal Basis: Republic Act No. 386, Civil Code of the Philippines, Articles 1380–1422.$note$,
    updated_at = now()
WHERE id = '0c6b0be8-8b7d-41dc-9619-94cab1d52064'
  AND owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
  AND md5(content) = 'ed1556bd5984e2ed2648db387a7a77bc';

-- 3. Verify before committing: expect new_md5 = 4ba328a584b1034ee62fd3535fe4089e, new_chars = 6043
SELECT md5(content) AS new_md5, length(content) AS new_chars, updated_at
FROM notes
WHERE id = '0c6b0be8-8b7d-41dc-9619-94cab1d52064';

-- If step 2 said UPDATE 1 and step 3 matches: COMMIT. Otherwise: ROLLBACK.
COMMIT;

-- 4. AFTER COMMIT — the note text alone does not fix Quick Review.
-- The Study Pack (summary, key concepts, quiz, Quick Review questions) was generated from the OLD
-- text and is not regenerated by this update. In the app, regenerate this note's Study Pack with
-- the STUDY_PACK scope only (the default for POST /notes/{id}/regenerate,
-- RegenerateNoteRequest.java:8). Do NOT use NOTE_AND_STUDY_PACK, which regenerates the note content too —
-- that would overwrite this correction with fresh LLM text. Regeneration also resets this Study
-- Pack's exam question pools (StudyPackService.java:916-918), so Board/Long Exam questions refresh.
--
-- 5. Read-only check after regeneration: summary should no longer call fraud/intimidation grounds
-- for rescission.
-- SELECT sp.updated_at, left(sp.summary, 400) FROM study_packs sp
-- WHERE sp.note_id = '0c6b0be8-8b7d-41dc-9619-94cab1d52064';
