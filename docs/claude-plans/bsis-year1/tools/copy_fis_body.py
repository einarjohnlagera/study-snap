#!/usr/bin/env python3
"""Print one approved Fundamentals of IS body (exactly the fenced block, without the ``` lines) to stdout,
checking it against bsis-year1-fis-body-manifest.tsv first. Nothing is written anywhere.

  python3 tools/copy_fis_body.py N1 | pbcopy        # body -> clipboard (macOS)
  python3 tools/copy_fis_body.py N1 --title         # prints the exact Note title
Run from docs/claude-plans/bsis-year1/ . Exit code 2 (and no body on stdout) if the file no longer matches the manifest."""
import csv, glob, hashlib, os, re, sys

code = sys.argv[1].upper() if len(sys.argv) > 1 else ''
here = os.path.dirname(os.path.abspath(__file__)) + '/..'
rows = {r['code']: r for r in csv.DictReader(open(here + '/bsis-year1-fis-body-manifest.tsv', encoding='utf-8'), delimiter='\t')}
if code not in rows:
    sys.exit('usage: copy_fis_body.py N1..N13 [--title]  (known: ' + ', '.join(sorted(rows)) + ')')
r = rows[code]
text = open(glob.glob(here + '/authored-bodies/' + r['source_file'])[0], encoding='utf-8').read()
body = re.search(r"```text\n(.*?)\n```", text, re.S).group(1)
norm = re.sub(r'[ \t]+(?=\n)', '', body.replace('\r\n', '\n')).rstrip(' \t\r\n')
ok = len(body) == int(r['chars']) and hashlib.md5(norm.encode('utf-8')).hexdigest() == r['normalized_md5'] and body.split('\n', 1)[0] == r['title']
if not ok:
    sys.exit('REFUSED: %s no longer matches the body manifest; regenerate the manifest or restore the file.' % code)
if '--title' in sys.argv:
    print(r['title'])
else:
    sys.stderr.write('%s: %s chars, md5 %s OK, first line equals the title\n' % (code, len(body), r['normalized_md5']))
    sys.stdout.write(body)
