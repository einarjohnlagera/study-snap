#!/usr/bin/env python3
"""Build the BSIS Year 1 collection BUILD WORKBOOK (xlsx): every placement with its exact Production Note ID, one sheet per
Subject Plan to build from, a tracker, the twelve new Notes with their ids/packs/review results, and the decision log.
Read-only on everything: it only reads the TSV, the id manifest, the body manifest, the review template and the decision log.
Ids are never typed: they come from bsis-year1-reuse-id-manifest.tsv (regenerate that first with bsis_phase0_generate.py).
Requires openpyxl.   python3 tools/build_collection_workbook.py --repo <repo root> --out <xlsx path>"""
import argparse, collections, csv, re
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill, Border, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation

ap = argparse.ArgumentParser(); ap.add_argument('--repo', required=True); ap.add_argument('--out', required=True); a = ap.parse_args()
R = a.repo.rstrip('/') + '/'; D = R + 'docs/claude-plans/bsis-year1/'
rows = list(csv.DictReader(open(R + 'docs/curriculum/bsis-year1-shaping.tsv', encoding='utf-8'), delimiter='\t'))
man = list(csv.DictReader(open(D + 'bsis-year1-reuse-id-manifest.tsv', encoding='utf-8'), delimiter='\t'))
body = {r['title']: r for r in csv.DictReader(open(D + 'bsis-year1-fis-body-manifest.tsv', encoding='utf-8'), delimiter='\t')}
tmpl = open(D + 'bsis-year1-fis-study-pack-review-template.md', encoding='utf-8').read()
packs = {}
for m in re.finditer(r"^\| 2026-11-06 \| (N\d+) \| \*\*([^|]*?)\*\*[^|]*\|.*?Pack `([0-9a-f-]{36})` accepted \|$", tmpl, re.M):
    packs[m.group(1)] = (m.group(3), m.group(2).strip())
assert len(packs) == 12, packs.keys()
assert len(rows) == len(man) == 199
for r, m in zip(rows, man):
    assert (r['subject_plan'], r['section'], r['note_title'], r['status']) == (m['plan'], m['section'], m['note_title'], m['status'])
    r['note_id'] = m['note_id']; r['_bucket'] = m['bucket']; r['_change'] = m['proposed_change']; r['_flag'] = m.get('reuse_flag', ''); r['_trigger'] = m.get('reuse_trigger', '')
placed = [r for r in rows if r['status'] != 'Excluded']
assert len(placed) == 194 and all(r['note_id'] for r in placed) and len({r['note_id'] for r in placed}) == 194

HF = Font(bold=True, color='FFFFFF'); HFILL = PatternFill('solid', fgColor='1F3864'); BAND = PatternFill('solid', fgColor='EAF0FA')
NEWFILL = PatternFill('solid', fgColor='E2F0D9'); EXFILL = PatternFill('solid', fgColor='F8CBAD'); SECFILL = PatternFill('solid', fgColor='D9E1F2')
MONO = Font(name='Consolas', size=10); WRAP = Alignment(wrap_text=True, vertical='top'); thin = Side(style='thin', color='BFBFBF'); BOX = Border(top=thin, bottom=thin, left=thin, right=thin)
def hdr(ws, cols, widths):
    ws.append(cols)
    for i, w in enumerate(widths, 1):
        c = ws.cell(1, i); c.font = HF; c.fill = HFILL; c.alignment = Alignment(wrap_text=True, vertical='center'); ws.column_dimensions[get_column_letter(i)].width = w
    ws.freeze_panes = 'A2'; ws.row_dimensions[1].height = 30
def mono(ws, col, first=2):
    for r in range(first, ws.max_row + 1): ws.cell(r, col).font = MONO

wb = Workbook()
# ---------- README
ws = wb.active; ws.title = 'README'
plans = collections.OrderedDict()
for r in rows:
    p = plans.setdefault(int(r['plan_no']), dict(title=r['subject_plan'], term=r['academic_term'], desc=r['plan_description'], secs=collections.OrderedDict(), re=0, new=0, ex=0))
    if r['status'] == 'Excluded': p['ex'] += 1; continue
    p['secs'].setdefault(r['section'], []).append(r); p['re' if r['status'] == 'Reuse' else 'new'] += 1
lines = [
 ('BS Information Systems — 1st Year: collection build workbook', ''),
 ('Status', 'DRAFT for the owner, 2026-11-06. Generated, not hand-edited. Nothing here writes to production.'),
 ('Purpose', 'The id-bearing sheet to BUILD the collection from: every one of the 194 placements with its exact Production Note ID, in teaching order, one sheet per Subject Plan. (The target-shape workbook docs/curriculum/bsis-year1-study-plan-target-shape.xlsx is the curriculum design view and carries no ids.)'),
 ('Root (already created, private)', '\U0001F5C4️ BS Information Systems — 1st Year  |  course/program Information Systems  |  level College  |  10 plans, terms 5 + 5 (frozen at publication)'),
 ('How to use', '1) Open the sheet for the plan you are building ("1 ..." to "10 ..."). 2) Add each Note BY ID, in row order, with the Section label EXACTLY as written (copy it). 3) Mark the Added column. 4) Tell Claude; it verifies read-only (id, plan, section, order, duplicates, Excluded Notes).'),
 ('Reuse by id, never by title', 'Title-matching copies exist for reused Notes. Only the id on these sheets is the canonical one (already verified in production 2026-11-06: title, Subject, Domain Context, College, PUBLIC, one Study Pack, Information Systems on all).'),
 ('Placements', '194 = 182 shared Notes + 12 new Fundamentals of IS Notes. 5 Notes are EXCLUDED and must not be added (see the Excluded sheet).'),
 ('The 12 new Notes', 'Created by the owner, PUBLIC, Study Pack generated and reviewed (all PASS). Subject Fundamentals of Information Systems; Domain Context unset; Applicable Programs Information Systems only (taxonomy review closed, decision D-52).'),
 ('Regenerate', 'python3 tools/bsis_phase0_generate.py ... then python3 tools/build_collection_workbook.py --repo . --out <xlsx>  (needs openpyxl). Edit the TSV and regenerate; never edit this file by hand.'),
 ('Sheets', 'README | Subject Plans | Build Tracker | one sheet per plan | FIS Notes (12 new) | Excluded | Decision Log'),
]
for a1, b1 in lines: ws.append([a1, b1])
ws['A1'].font = Font(bold=True, size=14)
for r in range(2, ws.max_row + 1): ws.cell(r, 1).font = Font(bold=True); ws.cell(r, 2).alignment = WRAP
ws.column_dimensions['A'].width = 30; ws.column_dimensions['B'].width = 130

# ---------- Subject Plans
ws = wb.create_sheet('Subject Plans')
hdr(ws, ['#', 'Academic term', 'Term order', 'Subject Plan (title)', 'Description', 'Sections', 'Placements', 'Reuse', 'New', 'Excluded', 'Course/program', 'Level'], [5, 16, 7, 38, 80, 9, 11, 7, 6, 9, 20, 8])
for n, p in plans.items():
    ws.append([n, p['term'], 1 if p['term'].startswith('First') else 2, p['title'], p['desc'], len(p['secs']), p['re'] + p['new'], p['re'], p['new'], p['ex'], 'Information Systems', '(empty)'])
for r in range(2, ws.max_row + 1):
    for c in (4, 5): ws.cell(r, c).alignment = WRAP
ws.append([]); ws.append(['', '', '', 'TOTAL', '', '', sum(p['re'] + p['new'] for p in plans.values()), sum(p['re'] for p in plans.values()), sum(p['new'] for p in plans.values()), sum(p['ex'] for p in plans.values())])

# ---------- Build Tracker (all placements, then excluded)
ws = wb.create_sheet('Build Tracker')
hdr(ws, ['Plan #', 'Subject Plan', 'Term', 'Section (exact label)', 'Seq in plan', 'Note title', 'Production Note ID', 'Status', 'Applicable Programs (target)', 'Domain Context', 'Added (owner)', 'Verified (Claude)'], [7, 34, 15, 38, 8, 60, 38, 9, 44, 18, 14, 14])
for n, p in plans.items():
    seq = 0
    for sec, rs in p['secs'].items():
        for r in rs:
            seq += 1
            ws.append([n, p['title'], p['term'], sec, seq, r['note_title'], r['note_id'], r['status'], r['applicable_programs'], r['domain_context'], '', ''])
            if r['status'] == 'New':
                for c in range(1, 13): ws.cell(ws.max_row, c).fill = NEWFILL
for r in rows:
    if r['status'] == 'Excluded':
        ws.append([int(r['plan_no']), plans[int(r['plan_no'])]['title'], plans[int(r['plan_no'])]['term'], '—', '', r['note_title'], '', 'EXCLUDED', r['applicable_programs'], r['domain_context'], 'DO NOT ADD', ''])
        for c in range(1, 13): ws.cell(ws.max_row, c).fill = EXFILL
mono(ws, 7)
ws.auto_filter.ref = 'A1:L%d' % ws.max_row
dv = DataValidation(type='list', formula1='"added,skip,problem"', allow_blank=True); ws.add_data_validation(dv); dv.add('K2:K%d' % ws.max_row)

# ---------- per-plan sheets
short = {1: 'Intro to Computing', 2: 'Computer Programming 1', 3: 'Math in the Modern World', 4: 'Understanding the Self', 5: 'Art Appreciation', 6: 'Computer Programming 2', 7: 'Fundamentals of IS', 8: 'Readings in PH History', 9: 'Purposive Communication', 10: 'The Contemporary World'}
for n, p in plans.items():
    ws = wb.create_sheet(('%d %s' % (n, short[n]))[:31])
    ws.append([p['title'] + '  —  ' + p['term'] + '  —  ' + str(p['re'] + p['new']) + ' Notes  (course/program Information Systems; level empty)'])
    ws['A1'].font = Font(bold=True, size=12)
    ws.append(['Description: ' + p['desc']]); ws['A2'].alignment = WRAP; ws.merge_cells('A2:E2'); ws.row_dimensions[2].height = 48
    ws.append(['#', 'Section label (copy exactly)', 'Note title', 'Production Note ID (add by id)', 'Added'])
    for i, w in enumerate([5, 44, 70, 40, 12], 1):
        c = ws.cell(3, i); c.font = HF; c.fill = HFILL; ws.column_dimensions[get_column_letter(i)].width = w
    seq = 0
    for sec, rs in p['secs'].items():
        ws.append(['', sec + '   (' + str(len(rs)) + ' Notes)', '', '', '']); sr = ws.max_row
        for c in range(1, 6): ws.cell(sr, c).fill = SECFILL; ws.cell(sr, c).font = Font(bold=True)
        for r in rs:
            seq += 1; ws.append([seq, sec, r['note_title'], r['note_id'], '']); rr = ws.max_row
            ws.cell(rr, 4).font = MONO
            if r['status'] == 'New':
                for c in range(1, 6): ws.cell(rr, c).fill = NEWFILL
    ws.freeze_panes = 'A4'
    d2 = DataValidation(type='list', formula1='"added,problem"', allow_blank=True); ws.add_data_validation(d2); d2.add('E4:E%d' % ws.max_row)
    if p['ex']:
        ws.append([]); ws.append(['', 'EXCLUDED — do not add: ' + '; '.join(r['note_title'] for r in rows if r['status'] == 'Excluded' and int(r['plan_no']) == n)])
        ws.cell(ws.max_row, 2).fill = EXFILL

# ---------- FIS Notes
ws = wb.create_sheet('FIS Notes (12 new)')
hdr(ws, ['Order', 'Code', 'Section', 'Title', 'Production Note ID', 'Chars', 'Normalized body md5', 'Study Pack ID', 'Pack review', 'Reuse flag (D-52)', 'Domain Context', 'Applicable Programs', 'Visibility', 'Reuse trigger'], [6, 6, 36, 62, 38, 7, 34, 38, 12, 26, 14, 20, 11, 60])
for r in placed:
    if r['status'] != 'New': continue
    b = body[r['note_title']]
    ws.append([b['order'], b['code'], r['section'], r['note_title'], r['note_id'], int(b['chars']), b['normalized_md5'], packs[b['code']][0], packs[b['code']][1], r['_flag'] or 'none', 'unset', 'Information Systems', 'PUBLIC', r['_trigger']])
for r in range(2, ws.max_row + 1):
    for c in (5, 7, 8): ws.cell(r, c).font = MONO
    ws.cell(r, 4).alignment = WRAP; ws.cell(r, 14).alignment = WRAP

# ---------- Excluded
ws = wb.create_sheet('Excluded')
hdr(ws, ['Plan #', 'Subject Plan', 'Note title (do NOT add)', 'Why'], [7, 38, 70, 80])
WHY = {}
for r in rows:
    if r['status'] == 'Excluded':
        why = ('Foundation-mathematics Note, held out as in the BSCS and BSIT Years (Phase A audit: "the same foundation-math and teacher-education Notes BSCS and BSIT held out").' if r['plan_no'] == '3'
               else 'Teacher-education Note, held out as in the BSCS and BSIT Years (Phase A audit: "the same foundation-math and teacher-education Notes BSCS and BSIT held out").')
        ws.append([int(r['plan_no']), r['subject_plan'], r['note_title'], why])

# ---------- Decision Log
ws = wb.create_sheet('Decision Log')
hdr(ws, ['ID', 'Decision', 'Date', 'Source / evidence'], [7, 150, 18, 40])
for m in re.finditer(r"^\| (D-\d+) \| (.*) \| ([^|]+) \| ([^|]*) \|$", open(D + 'bsis-year1-decision-log.md', encoding='utf-8').read(), re.M):
    ws.append([m.group(1), m.group(2).replace('**', ''), m.group(3).strip(), m.group(4).strip()])
for r in range(2, ws.max_row + 1): ws.cell(r, 2).alignment = WRAP
wb.save(a.out)
print('saved', a.out, [s.title for s in wb.worksheets])
