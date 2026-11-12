#!/usr/bin/env python3
"""Print one exact field for the BSIS collection build, so nothing is retyped (emoji variation selectors matter).
  python3 tools/copy_plan_field.py root-title | pbcopy
  python3 tools/copy_plan_field.py root-desc  | pbcopy
  python3 tools/copy_plan_field.py 7 title    | pbcopy      # plan number 1..10; fields: title, desc, term, sections
Source: docs/curriculum/bsis-year1-shaping.tsv and the owner-approved root text (decision log D-17, D-53). Read-only."""
import csv, os, sys
ROOT_TITLE = '\U0001F5C4️ BS Information Systems — 1st Year'
ROOT_DESC = ("Build your first-year foundations in computing, information systems, mathematics, and General Education through a curated Study Journey based on selected subjects in a Philippine BS Information Systems degree.\n\n"
             "It follows a NoteLib reference curriculum rather than any single university's prospectus. Course names, semester placement, and sequence may differ across institutions.\n\n"
             "Physical Education and NSTP, while included in many degree programs, fall outside this Notes-based journey because they primarily involve physical activities, community service, or practical participation.")
a = sys.argv[1:] 
if not a: sys.exit(__doc__)
if a[0] == 'root-title': sys.stdout.write(ROOT_TITLE); sys.exit()
if a[0] == 'root-desc': sys.stdout.write(ROOT_DESC); sys.exit()
here = os.path.dirname(os.path.abspath(__file__))
rows = list(csv.DictReader(open(here + '/../../../curriculum/bsis-year1-shaping.tsv', encoding='utf-8'), delimiter='\t'))
pr = [r for r in rows if r['plan_no'] == a[0]]
if not pr or len(a) < 2: sys.exit('usage: copy_plan_field.py <1..10> title|desc|term|sections')
f = a[1]
if f == 'title': sys.stdout.write(pr[0]['subject_plan'])
elif f == 'desc': sys.stdout.write(pr[0]['plan_description'])
elif f == 'term': sys.stdout.write(pr[0]['academic_term'])
elif f == 'sections':
    seen = []
    for r in pr:
        if r['status'] != 'Excluded' and r['section'] not in seen: seen.append(r['section'])
    sys.stdout.write('\n'.join(seen))
else: sys.exit('field must be title, desc, term or sections')
