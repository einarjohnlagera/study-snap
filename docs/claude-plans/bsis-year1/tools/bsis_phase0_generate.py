#!/usr/bin/env python3
"""BSIS Year 1 Phase 0 generator (documentation only; never touches production).

Reads:
  docs/curriculum/bsis-year1-shaping.tsv                       (placements, plans, sections, terms)
  <bsit-manifest>                                              (exact Note ids already verified for the shared Notes; --bsit-manifest)
  docs/claude-plans/bsis-year1/authored-bodies/fis-n*.md       (the twelve approved bodies)
  docs/claude-plans/bsis-year1/bsis-year1-fis-created-ids.tsv  (OPTIONAL: title<TAB>note_id for the New Notes, written by the owner/Claude AFTER creation)

Writes (all under docs/claude-plans/bsis-year1/):
  bsis-year1-reuse-id-manifest.tsv, bsis-year1-fis-body-manifest.tsv,
  bsis-year1-collection-run-sheet.md, bsis-year1-collection-verify-read.sql

Ids are never typed by hand: after the twelve Notes exist, put their ids in bsis-year1-fis-created-ids.tsv
and re-run this script; the manifest, run sheet and verification SQL are regenerated with the real ids.

Usage:  python3 tools/bsis_phase0_generate.py --repo <repo root> --bsit-manifest <path to bsit-year1-reuse-id-manifest.tsv>
"""
import argparse, collections, csv, glob, hashlib, os, re, sys

ap = argparse.ArgumentParser()
ap.add_argument('--repo', required=True)
ap.add_argument('--bsit-manifest', required=True)
a = ap.parse_args()
R = a.repo.rstrip('/') + '/'
OUT = R + 'docs/claude-plans/bsis-year1/'
SUBJECT_FIS = 'Fundamentals of Information Systems'
ROOT_TITLE_TAIL = 'BS Information Systems — 1st Year'   # the emoji prefix, if any, is an owner decision; queries match the tail

rows = list(csv.DictReader(open(R + 'docs/curriculum/bsis-year1-shaping.tsv', encoding='utf-8'), delimiter='\t'))
bsit = list(csv.DictReader(open(a.bsit_manifest, encoding='utf-8'), delimiter='\t'))
bmap = {(m['plan'], m['section'], m['note_title']): m for m in bsit}

created = {}
cp = OUT + 'bsis-year1-fis-created-ids.tsv'
if os.path.exists(cp):
    for r in csv.DictReader(open(cp, encoding='utf-8'), delimiter='\t'):
        created[r['title']] = r['note_id'].strip()

# Reuse flags (decision D-52, 2026-11-06): recorded with a trigger, not gated. Keyed by body code.
REUSE_FLAGS = {'N12': 'candidate (moderate)', 'N1': 'candidate (low)', 'N2': 'candidate (low), not approved', 'N3': 'candidate (low)'}
REUSE_TRIGGER = ('Revisit when a future Study Journey or Review Set has a specific learning objective this Note might satisfy unchanged: '
                 'compare the full body to the objective; confirm depth and framing; check the Study Pack; decide; '
                 'if a second Applicable Program is approved, first establish an explicit Domain Context (ADR-001).')

# the six Notes that gain Information Systems (Applicable Programs), by id (from the Phase B scripts)
IS_FIVE = {'5e2a6baa-791f-4bfb-9b1b-b4317623cd0b', '556e7dbe-c43f-41f2-8d3f-fd2c4434b9e1', '769cd008-4a48-4ac0-b47d-620cbc23ffc0',
           '3b53ce07-08db-4fac-b4fe-8525462d74a5', '6256e54b-1501-44cf-abf3-88b2b4d67a0e'}
IS_HELD = '45133a32-4440-4277-8b50-931eaec3788f'

# ---------- bodies ----------
def norm(s):
    s = s.replace('\r\n', '\n')
    s = re.sub(r'[ \t]+(?=\n)', '', s)
    return s.rstrip(' \t\r\n')
bodies = {}
for f in sorted(glob.glob(OUT + 'authored-bodies/fis-n*.md')):
    t = open(f, encoding='utf-8').read()
    m = re.search(r"```text\n(.*?)\n```", t, re.S)
    body = m.group(1)
    title = body.split('\n', 1)[0].strip()
    n = int(re.search(r'fis-n(\d+)-', os.path.basename(f)).group(1))
    nb = norm(body)
    bodies[title] = dict(code='N%d' % n, file=os.path.basename(f), len=len(body), nlen=len(nb), md5=hashlib.md5(nb.encode('utf-8')).hexdigest(), words=len(body.split()))

# ---------- placements / manifest ----------
for r in rows:
    r['_k'] = (r['subject_plan'], r['section'], r['note_title'])
mf = []
for r in rows:
    base = dict(plan_no=r['plan_no'], plan=r['subject_plan'], section=r['section'], note_title=r['note_title'])
    if r['status'] == 'Reuse':
        m = bmap[r['_k']]
        assert m['note_id'] and m['status'] == 'Reuse', r['_k']
        nid = m['note_id']
        if nid in IS_FIVE:
            bucket, change, ver = 'Reuse, tag pending (Phase 1)', '+Information Systems', 'Tag via bsis-year1-is-applicability.sql (owner), then re-verify; content PASS'
        elif nid == IS_HELD:
            bucket, change, ver = 'Reuse, tag HELD (Phase 1)', '+Information Systems', 'RA 10364 correction first (bsis-year1-global-citizenship-ra10364-correction.sql), then the held tag script'
        else:
            bucket, change, ver = 'Shared Reuse (no change)', '', 'Production 2026-11-06: title, Subject, Domain Context, College, PUBLIC and one Study Pack all equal the TSV; id from the BSIT manifest'
        mf.append(dict(base, note_id=nid, status='Reuse', bucket=bucket, proposed_change=change, verification=ver))
    elif r['status'] == 'New':
        b = bodies[r['note_title']]
        nid = created.get(r['note_title'], '')
        flag = REUSE_FLAGS.get(b['code'], 'none')
        mf.append(dict(base, note_id=nid, status='New', bucket='New (%s), %s' % (b['code'], 'created' if nid else 'not yet created'),
                       proposed_change='create PRIVATE, then set PUBLIC before joining the plan',
                       verification='Body from authored-bodies/%s: %d chars, normalized md5 %s' % (b['file'], b['len'], b['md5']),
                       reuse_flag=flag, reuse_trigger=REUSE_TRIGGER if flag != 'none' else ''))
    else:
        mf.append(dict(base, note_id='', status='Excluded', bucket='Excluded', proposed_change='', verification='Do not add to any plan'))
assert len(mf) == len(rows) == 199
mh = ['plan_no', 'plan', 'section', 'note_title', 'note_id', 'status', 'bucket', 'proposed_change', 'verification', 'reuse_flag', 'reuse_trigger']
with open(OUT + 'bsis-year1-reuse-id-manifest.tsv', 'w', newline='', encoding='utf-8') as f:
    w = csv.DictWriter(f, fieldnames=mh, delimiter='\t', lineterminator='\n', restval=''); w.writeheader(); w.writerows(mf)

# ---------- plans ----------
plans = collections.OrderedDict()
for r in rows:
    p = plans.setdefault(int(r['plan_no']), dict(title=r['subject_plan'], desc=r['plan_description'], term=r['academic_term'], sections=collections.OrderedDict(), excl=[]))
    if r['status'] == 'Excluded':
        p['excl'].append(r['note_title']); continue
    p['sections'].setdefault(r['section'], []).append(r)
TERM_ORDER = {'First Semester': 1, 'Second Semester': 2}
placed = [r for r in rows if r['status'] != 'Excluded']
assert len(placed) == 194 and collections.Counter(r['status'] for r in rows) == {'Reuse': 182, 'New': 12, 'Excluded': 5}

# ---------- body manifest ----------
with open(OUT + 'bsis-year1-fis-body-manifest.tsv', 'w', newline='', encoding='utf-8') as f:
    w = csv.writer(f, delimiter='\t', lineterminator='\n')
    w.writerow(['order', 'code', 'section', 'title', 'subject', 'source_file', 'chars', 'normalized_chars', 'normalized_md5', 'note_id'])
    for r in placed:
        if r['status'] != 'New': continue
        b = bodies[r['note_title']]
        w.writerow([len([x for x in placed if x['plan_no'] == '7' and placed.index(x) <= placed.index(r)]), b['code'], r['section'], r['note_title'], r['note_subject'], b['file'], b['len'], b['nlen'], b['md5'], created.get(r['note_title'], '')])

# ---------- run sheet ----------
L = []
L.append('Status: Collection run sheet, DRAFT for the owner, %s. NOTHING here has been done. NOT implementation-ready until the twelve Fundamentals of IS Notes exist (Phase 2 and 3). Generated by `tools/bsis_phase0_generate.py` from the shaping TSV and the exact-ID manifest, not typed by hand. Use with `bsis-year1-collection-creation-checklist.md`.' % '2026-11-06')
L.append('')
L.append('# BSIS Year 1: collection run sheet (add existing Notes by id)')
L.append('')
L.append("**For each Subject Plan, add the Notes below in row order, with the Section label exactly as written.** The 182 shared Notes already exist: do not create or copy any. The id is the production Note id (the copy already in the BSCS and BSIT Years). **If the picker shows several Notes with the same title, choose by this id**; title-matching copies exist. The twelve Fundamentals of Information Systems Notes do **not** exist yet: create them first (checklist `bsis-year1-fis-note-creation-checklist.md`), set each PUBLIC, record the ids in `bsis-year1-fis-created-ids.tsv`, and regenerate this sheet so the id column is filled by the script, never typed. After each plan, tell Claude and it will verify read-only.")
L.append('')
L.append('Tick each plan when its Notes are in.')
for pn, p in plans.items():
    n_place = sum(len(v) for v in p['sections'].values())
    L.append('')
    L.append('## %d. %s: %s (%d Notes)' % (pn, p['title'], p['term'], n_place))
    L.append('')
    L.append('- [ ] Plan created (checklist section 2). - [ ] All %d Notes added. - [ ] Read-only verification passed.' % n_place)
    L.append('')
    L.append('| # | Section | Note title | Note id |')
    L.append('|---|---|---|---|')
    i = 0
    for sec, rs in p['sections'].items():
        for r in rs:
            i += 1
            if r['status'] == 'New':
                b = bodies[r['note_title']]
                nid = created.get(r['note_title'])
                cell = '`%s`' % nid if nid else '**create first (Phase 2/3), then record the id** (%s)' % b['code']
            else:
                cell = '`%s`' % bmap[r['_k']]['note_id']
            L.append('| %d | %s | %s | %s |' % (i, sec, r['note_title'], cell))
    if p['excl']:
        L.append('')
        L.append('**Do not add (Excluded):** ' + '; '.join(p['excl']) + '.')
L.append('')
L.append('## Totals')
L.append('')
L.append('%d placements: %d shared Notes (ids above) plus %d Fundamentals of Information Systems Notes. %d Excluded Notes are not added.' % (len(placed), 182, 12, 5))
L.append('')
open(OUT + 'bsis-year1-collection-run-sheet.md', 'w', encoding='utf-8').write('\n'.join(L))

# ---------- verification SQL ----------
q = lambda s: "'" + s.replace("'", "''") + "'"
ROOTQ = "r.title LIKE %s AND r.parent_collection_id IS NULL" % q('%' + ROOT_TITLE_TAIL)
EXP = []
for r in placed:
    ref = "NULL::uuid" if r['status'] == 'New' and r['note_title'] not in created else "'%s'::uuid" % (created[r['note_title']] if r['status'] == 'New' else bmap[r['_k']]['note_id'])
    EXP.append("    (%s, %s, %s, %s)" % (q(r['subject_plan']), q(r['section']), ref, q(r['note_title'])))
EXPV = ",\n".join(EXP)
MATCH = "((e.ref_id IS NOT NULL AND e.ref_id = a.note_id) OR (e.ref_id IS NULL AND a.title = e.title AND a.subject = %s))" % q(SUBJECT_FIS)
NORM = r"md5(rtrim(regexp_replace(replace(n.content, E'\r\n', E'\n'), E'[ \t]+\n', E'\n', 'g'), E' \t\r\n'))"
FIS = []
for r in placed:
    if r['status'] != 'New': continue
    b = bodies[r['note_title']]
    FIS.append("    (%s, %s, %d, %s)" % (q(b['code']), q(r['note_title']), b['nlen'], q(b['md5'])))
FISV = ",\n".join(FIS)
six = ["'%s'::uuid" % i for i in sorted(IS_FIVE | {IS_HELD})]

SQL = rf"""-- ============================================================================
-- BSIS Year 1: read-only verification queries for the Fundamentals of IS Notes and the collection build
-- Status: DRAFT, 2026-11-06. EVERY STATEMENT IS A SELECT. Nothing writes. Safe to run against production.
-- Generated by tools/bsis_phase0_generate.py from the shaping TSV, the exact-ID manifest and the twelve authored bodies.
-- Expected placements: {len(placed)} ({182} shared + {12} Fundamentals of Information Systems); {5} Excluded Notes must not appear.
--
-- PHASE DEPENDENCIES (read before running; a query run too early reports failures that are really "not yet"):
--   P0  before anything:        run F0 and S1 to confirm the starting state.
--   P1  after Phase 1 (owner):  S1 must show exactly the five tagged Notes plus the held Note carrying Information Systems.
--                               BEFORE Phase 1, V5/V5b report exactly 6 Notes missing Information Systems; that is expected, not a defect.
--   P2  after Notes are created: F1 and F2 (twelve Notes; F2 expects PRIVATE until they are set PUBLIC for plan membership).
--   P3  after the root and plans exist: V1 to V3, V6.
--   P4  before publication:      V4, V4b, V5, V5b, V7 must all be clean.
--   P5  after publication:       V8.
-- Root title: queries match the title TAIL "{ROOT_TITLE_TAIL}" so they work with or without an emoji prefix (owner decision).
-- The normalized body hash ignores CRLF vs LF and trailing spaces; a mismatch means "inspect the difference", not "wrong Note".
-- ============================================================================

-- F0 (P0): the Information Systems catalog row. Expect exactly 1 row, id 338d3e66-fee4-4400-afa7-0af0931444c8, active.
SELECT id::text, name, is_active FROM course_programs WHERE name = 'Information Systems';

-- S1 (P0/P1): Phase 1 state of the six shared Notes that gain Information Systems.
-- Before Phase 1: has_is = false on all six. After the RA 10364 correction and the tag scripts: has_is = true on all six.
SELECT n.id::text, n.title,
       EXISTS (SELECT 1 FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id WHERE x.note_id = n.id AND cp.name = 'Information Systems') AS has_is,
       n.visibility::text AS visibility
FROM notes n WHERE n.id IN ({", ".join(six)})
ORDER BY n.title;

-- S2 (P1): the Global Citizenship correction (RA 10364). Expect after the correction: fixed = true, old_gone = true, length 3516, md5 9e3e316db280f70073ca1ce485abd01b. Before it: fixed = false, md5 834de9de0e5296a782855611eed736f7.
SELECT id::text, length(content) AS len, md5(content) AS md5, position('in 2013;' in content) > 0 AS fixed, position('in 2012' in content) = 0 AS old_gone, updated_at
FROM notes WHERE id = '{IS_HELD}';

-- F1 (P2): each of the twelve titles exists exactly once. Expect found = 1 and found_with_subject = 1 on every row.
WITH exp(code, title, nlen, nmd5) AS (VALUES
{FISV}
)
SELECT e.code, e.title,
       (SELECT count(*) FROM notes n WHERE n.title = e.title) AS found,
       (SELECT count(*) FROM notes n WHERE n.title = e.title AND n.subject = {q(SUBJECT_FIS)}) AS found_with_subject
FROM exp e ORDER BY e.code;

-- F2 (P2/P3): the twelve Notes' metadata and body. Returns ONE ROW PER NOTE with a flag per check; every flag should be OK.
-- visibility: PRIVATE right after creation is expected (flag shows PRIVATE); it must be PUBLIC before the plan is published (V4).
-- study_pack / status: after the Study Pack is generated with confirmation, expect packs = 1 and status GENERATED.
WITH exp(code, title, nlen, nmd5) AS (VALUES
{FISV}
)
SELECT e.code, n.id::text AS note_id,
       CASE WHEN n.subject = {q(SUBJECT_FIS)} THEN 'OK' ELSE 'SUBJECT:' || coalesce(n.subject, 'null') END AS subject,
       CASE WHEN n.domain_context IS NULL THEN 'OK' ELSE 'DC:' || n.domain_context::text END AS domain_context_unset,
       CASE WHEN n.learner_level::text = 'COLLEGE' THEN 'OK' ELSE 'LEVEL:' || coalesce(n.learner_level::text, 'null') END AS level,
       CASE WHEN p.progs = 'Information Systems' THEN 'OK' ELSE 'PROGRAMS:' || coalesce(p.progs, 'none') END AS programs_is_only,
       n.visibility::text AS visibility,
       n.status::text AS status,
       (SELECT count(*) FROM study_packs sp WHERE sp.note_id = n.id) AS packs,
       length(n.content) AS content_len, e.nlen AS expected_len,
       CASE WHEN {NORM} = e.nmd5 THEN 'OK' ELSE 'BODY DIFFERS (inspect)' END AS normalized_body
FROM exp e
LEFT JOIN notes n ON n.title = e.title AND n.subject = {q(SUBJECT_FIS)}
LEFT JOIN LATERAL (SELECT string_agg(cp.name, ', ' ORDER BY cp.name) AS progs FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id WHERE x.note_id = n.id) p ON true
ORDER BY e.code;

-- V1 (P3): the root and its children, in creation order.
-- Expect: 1 root and 10 children; titles exact; PRIVATE until publication; term_label/term_order set on ALL ten or on none
-- (planned: plans 1-5 First Semester = 1, plans 6-10 Second Semester = 2); course_program Information Systems; root learner_level COLLEGE, plans NULL (the BSCS and BSIT convention).
SELECT c.title, c.visibility::text AS visibility, c.course_program, c.learner_level::text AS learner_level, c.term_label, c.term_order,
       (c.parent_collection_id IS NULL) AS is_root, c.sibling_position, c.created_at
FROM note_collections c
WHERE c.title LIKE {q('%' + ROOT_TITLE_TAIL)}
   OR c.parent_collection_id IN (SELECT id FROM note_collections WHERE title LIKE {q('%' + ROOT_TITLE_TAIL)} AND parent_collection_id IS NULL)
ORDER BY is_root DESC, c.sibling_position NULLS FIRST, c.created_at;

-- V1b (P3): exactly ONE root with this title tail (a duplicate would orphan children). Expect 1.
SELECT count(*) AS roots_with_title FROM note_collections r WHERE {ROOTQ};

-- V2 (P3, after each plan): note and section counts per Subject Plan, sections in teaching order (lowest position first).
SELECT c.title AS subject_plan, i.label AS section, count(*) AS notes, min(i.position) AS first_position
FROM note_collections r
JOIN note_collections c ON c.parent_collection_id = r.id
JOIN note_collection_items i ON i.collection_id = c.id
WHERE {ROOTQ}
GROUP BY c.title, i.label
ORDER BY c.title, min(i.position);

-- V2b (P3): section labels that differ only by case or spacing within a plan (a typo creates a second section). Expect 0 rows.
SELECT c.title AS subject_plan, lower(regexp_replace(trim(i.label), '\s+', ' ', 'g')) AS folded, count(DISTINCT i.label) AS spellings, string_agg(DISTINCT i.label, ' | ') AS labels
FROM note_collections r
JOIN note_collections c ON c.parent_collection_id = r.id
JOIN note_collection_items i ON i.collection_id = c.id
WHERE {ROOTQ}
GROUP BY c.title, folded HAVING count(DISTINCT i.label) > 1;

-- V3 (P3): MISSING and UNEXPECTED placements in ONE query (id, plan and section must all match; the New Notes match by exact title + Subject
-- until their ids are recorded). Expect 0 rows when the build is complete.
WITH expected(plan, section, ref_id, title) AS (VALUES
{EXPV}
), actual AS (
  SELECT c.title AS plan, i.label AS section, i.note_id, n.title, n.subject
  FROM note_collections r
  JOIN note_collections c ON c.parent_collection_id = r.id
  JOIN note_collection_items i ON i.collection_id = c.id
  JOIN notes n ON n.id = i.note_id
  WHERE {ROOTQ}
)
SELECT 'MISSING' AS kind, e.plan, e.section, e.title FROM expected e
 WHERE NOT EXISTS (SELECT 1 FROM actual a WHERE a.plan = e.plan AND a.section = e.section AND {MATCH})
UNION ALL
SELECT 'UNEXPECTED', a.plan, a.section, a.title FROM actual a
 WHERE NOT EXISTS (SELECT 1 FROM expected e WHERE a.plan = e.plan AND a.section = e.section AND {MATCH})
ORDER BY 1, 2, 3, 4;

-- V3c (P3): a Note placed twice anywhere in the Year. Expect 0 rows.
SELECT n.title, count(*) AS placements
FROM note_collections r
JOIN note_collections c ON c.parent_collection_id = r.id
JOIN note_collection_items i ON i.collection_id = c.id
JOIN notes n ON n.id = i.note_id
WHERE {ROOTQ}
GROUP BY n.id, n.title HAVING count(*) > 1;

-- V3d (P3): the Excluded Notes must not be present. Expect 0 rows.
SELECT c.title AS plan, n.title
FROM note_collections r
JOIN note_collections c ON c.parent_collection_id = r.id
JOIN note_collection_items i ON i.collection_id = c.id
JOIN notes n ON n.id = i.note_id
WHERE {ROOTQ} AND n.title IN ({", ".join(q(t) for p in plans.values() for t in p['excl'])});

-- V4 (P4): visibility of every member Note. Expect only PUBLIC ({len(placed)} rows total) before publication.
SELECT n.visibility::text AS visibility, count(*) AS notes
FROM note_collections r
JOIN note_collections c ON c.parent_collection_id = r.id
JOIN note_collection_items i ON i.collection_id = c.id
JOIN notes n ON n.id = i.note_id
WHERE {ROOTQ}
GROUP BY n.visibility;

-- V4b (P4): the Notes that are NOT public, by plan. Expect 0 rows. (A Fundamentals of IS Note still PRIVATE blocks publication.)
SELECT c.title AS plan, i.label AS section, n.title, n.visibility::text AS visibility
FROM note_collections r
JOIN note_collections c ON c.parent_collection_id = r.id
JOIN note_collection_items i ON i.collection_id = c.id
JOIN notes n ON n.id = i.note_id
WHERE {ROOTQ} AND n.visibility::text <> 'PUBLIC'
ORDER BY c.title, i.position;

-- V5 (P4): every member Note carries Information Systems. Expect missing_is = 0 AFTER Phase 1.
-- BEFORE Phase 1 expect exactly 6 (the five tagged Notes and Global Citizenship); that is "not yet", not a defect.
SELECT count(*) AS notes, count(*) FILTER (WHERE NOT EXISTS (
         SELECT 1 FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id
          WHERE x.note_id = n.id AND cp.name = 'Information Systems')) AS missing_is
FROM note_collections r
JOIN note_collections c ON c.parent_collection_id = r.id
JOIN note_collection_items i ON i.collection_id = c.id
JOIN notes n ON n.id = i.note_id
WHERE {ROOTQ};

-- V5b (P4): which Notes lack Information Systems. Expect 0 rows after Phase 1.
SELECT c.title AS plan, n.title
FROM note_collections r
JOIN note_collections c ON c.parent_collection_id = r.id
JOIN note_collection_items i ON i.collection_id = c.id
JOIN notes n ON n.id = i.note_id
WHERE {ROOTQ}
  AND NOT EXISTS (SELECT 1 FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id WHERE x.note_id = n.id AND cp.name = 'Information Systems')
ORDER BY c.title, n.title;

-- V6 (P3): Subject Plan term, program and item counts. Expect 10 rows, terms coherent, course_program Information Systems.
SELECT c.title, c.term_label, c.term_order, c.course_program, c.learner_level::text AS learner_level, c.visibility::text AS visibility, count(i.id) AS items
FROM note_collections r
JOIN note_collections c ON c.parent_collection_id = r.id
LEFT JOIN note_collection_items i ON i.collection_id = c.id
WHERE {ROOTQ}
GROUP BY c.id, c.title, c.term_label, c.term_order, c.course_program, c.learner_level, c.visibility, c.sibling_position
ORDER BY c.sibling_position;

-- V7 (P4): every member Note that is a Fundamentals of IS Note is PUBLIC, has exactly one Study Pack and status GENERATED. Expect 0 rows.
SELECT n.title, n.visibility::text AS visibility, n.status::text AS status, (SELECT count(*) FROM study_packs sp WHERE sp.note_id = n.id) AS packs
FROM note_collections r
JOIN note_collections c ON c.parent_collection_id = r.id
JOIN note_collection_items i ON i.collection_id = c.id
JOIN notes n ON n.id = i.note_id
WHERE {ROOTQ} AND n.subject = {q(SUBJECT_FIS)}
  AND (n.visibility::text <> 'PUBLIC' OR n.status::text <> 'GENERATED' OR (SELECT count(*) FROM study_packs sp WHERE sp.note_id = n.id) <> 1);

-- V7b (P4): all {len(placed)} members: Study Pack present and status GENERATED. Expect bad = 0.
SELECT count(*) AS notes, count(*) FILTER (WHERE n.status::text <> 'GENERATED' OR (SELECT count(*) FROM study_packs sp WHERE sp.note_id = n.id) <> 1) AS bad
FROM note_collections r
JOIN note_collections c ON c.parent_collection_id = r.id
JOIN note_collection_items i ON i.collection_id = c.id
JOIN notes n ON n.id = i.note_id
WHERE {ROOTQ};

-- V8 (P5, after publication): the Year and its ten plans are public; terms stay as set.
SELECT c.title, c.visibility::text AS visibility, c.published_at, c.term_label, c.term_order
FROM note_collections c
WHERE c.title LIKE {q('%' + ROOT_TITLE_TAIL)}
   OR c.parent_collection_id IN (SELECT id FROM note_collections WHERE title LIKE {q('%' + ROOT_TITLE_TAIL)} AND parent_collection_id IS NULL)
ORDER BY (c.parent_collection_id IS NULL) DESC, c.sibling_position NULLS FIRST;

-- V9 (any time): the BSCS and BSIT Years are untouched. Shows every root whose title mentions BSCS or BS Information Technology, its visibility and child count.
-- Compare with the previous reading; this build must not change them.
SELECT c.title, c.visibility::text AS visibility, c.course_program,
       (SELECT count(*) FROM note_collections k WHERE k.parent_collection_id = c.id) AS child_plans,
       (SELECT count(*) FROM note_collection_items i JOIN note_collections k ON k.id = i.collection_id WHERE k.parent_collection_id = c.id) AS items
FROM note_collections c
WHERE c.parent_collection_id IS NULL AND (c.title ILIKE '%BSCS%' OR c.title ILIKE '%Information Technology%')
ORDER BY c.title;
"""
open(OUT + 'bsis-year1-collection-verify-read.sql', 'w', encoding='utf-8').write(SQL)
print('manifest', len(mf), 'placed', len(placed), 'bodies', len(bodies), 'created ids', len(created), 'sql chars', len(SQL))
