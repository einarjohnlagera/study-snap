#!/usr/bin/env python3
"""Journey-agnostic owner-facing target-shape workbook generator (documentation-only; first used for BSCpE Year 1).

    python3 build_journey_workbook.py --recon bscpe-year1-reconciled-target-shape.tsv \
        --shaping ../../curriculum/bscpe-year1-shaping.tsv --metadata bscpe-year1-publication-metadata.json \
        --out ../../curriculum/bscpe-year1-study-plan-target-shape.xlsx

--shaping is an OUTPUT: the 10-column builder TSV, written from the reconciled TSV and the metadata (Subject Plan names
carry their emoji, the established convention of the BSCS, BSIT and BSIS workbooks; Note titles never do).

Needs openpyxl (use a venv). It never edits a workbook by hand: it
  1. writes the builder TSV and runs the SHARED builder docs/curriculum/build_review_set_workbook.py on it (standard layout);
  2. rewrites the Overview with a Publication Metadata section, per-plan emoji fields and separate internal notes;
  3. adds the sheets the shared builder cannot carry yet: Sign-off and Readiness, Note Register, Issues and Conditions,
     CpE-style Tag Gaps, Generation Pilot (all driven by the reconciled TSV and the metadata file).
Everything specific to a journey lives in the METADATA JSON, so a future Study Journey needs a new JSON, not new code.
Folding these fields into the shared builder is a change for Release Implementor to Codex (playbook sections 7 and 12).

METADATA JSON (all keys required unless marked optional):
  workbook_title        string, the workbook's own title (internal)
  signoff_date          string, the academic sign-off date
  internal_line         string, printed under the title as INTERNAL (never public): the reference-arrangement disclaimer
  public                {title, description, emoji, course_program, journey_type, terms:[...in order]}
  status                {academic, generation, publication}
  plans                 {"<plan_no>": {"emoji": "...", "emoji_source": "new" | "preserved: <where>" }, ...} one per plan
  authored_depth        {"BOARD_EXAM_REVIEW": [id8,...], "(unset)": [id8,...]}  (everything else reused is COLLEGE)
  tag_program           string, the program whose tag gaps are listed (here "Computer Engineering")
  pilot                 [{plan, title, kind}, ...]  proposed generation pilot (a proposal, never an authorization)
  issues                [{issue, detail, action}, ...]; detail/action may use {untagged} and {flagged}
  internal_notes        [string, ...]  operational warnings, printed separately from the public text
"""
import argparse, csv, json, os, subprocess, sys, collections
from openpyxl import load_workbook
from openpyxl.styles import Font, Alignment, PatternFill
from copy import copy

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
BUILDER = os.path.join(ROOT, "docs/curriculum/build_review_set_workbook.py")
H_FILL = PatternFill("solid", fgColor="1F3864"); H_FONT = Font(bold=True, color="FFFFFF")
STATUS_FILL = {"REUSE_VERIFIED": "E2EFDA", "REUSE_PENDING": "FFF2CC", "NEW": "DDEBF7", "EXCLUDED": "EDEDED"}
WRAP = Alignment(wrap_text=True, vertical="top")


def head(ws, row, labels):
    for c, t in enumerate(labels, 1):
        cell = ws.cell(row=row, column=c, value=t); cell.fill = H_FILL; cell.font = H_FONT
        cell.alignment = Alignment(wrap_text=True, vertical="center")
    ws.freeze_panes = "A%d" % (row + 1)


def widths(ws, ws_widths):
    for i, w in enumerate(ws_widths, 1):
        ws.column_dimensions[ws.cell(row=1, column=i).column_letter].width = w


def write_shaping(rows, meta, path):
    """The shared builder's 10-column input. Plan names get their emoji prefix (separate metadata, one source); Note titles are untouched."""
    m = {"REUSE_VERIFIED": "Reuse", "REUSE_PENDING": "Reuse", "NEW": "New", "EXCLUDED": "Excluded"}
    cols = ["plan_no", "subject_plan", "plan_description", "section", "note_title", "note_subject", "domain_context", "applicable_programs", "status", "academic_term"]
    with open(path, "w", newline="") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n"); w.writerow(cols)
        for r in rows:
            plan = f"{meta['plans'][r['plan_no']]['emoji']} {r['subject_plan']}"
            w.writerow([r["plan_no"], plan, r["plan_description"], r["section"], r["note_title"], r["note_subject"], r["domain_context"] or "(unset)",
                        r["applicable_programs"], m[r["status"]], r["academic_term"]])


def validate(meta, rows):
    plans = collections.OrderedDict((r["plan_no"], r["subject_plan"]) for r in rows)
    terms = list(collections.OrderedDict.fromkeys(r["academic_term"] for r in rows))
    pub = meta["public"]
    if pub["terms"] != terms:
        sys.exit(f"metadata terms {pub['terms']} do not match the TSV terms in order {terms}")
    if set(meta["plans"]) != set(plans):
        sys.exit(f"metadata plans {sorted(meta['plans'])} do not match TSV plans {sorted(plans)}")
    if not pub["title"].startswith(pub["emoji"]):
        sys.exit("public title must start with the overall emoji")
    for k in ("academic", "generation", "publication"):
        if not meta["status"].get(k): sys.exit(f"metadata status.{k} is empty")
    return plans, terms


def depth_of(r, meta):
    i = r["canonical_note_id"][:8]
    if r["status"] == "NEW": return "COLLEGE (proposed; confirm at generation)"
    if i in meta["authored_depth"].get("BOARD_EXAM_REVIEW", []): return "BOARD_EXAM_REVIEW (preserved)"
    if i in meta["authored_depth"].get("(unset)", []): return "(unset) (preserved)"
    return "COLLEGE"


def gen_ready(r):
    s, v = r["status"], r["verification_status"]
    if s == "NEW":
        return "PILOT FIRST: NEEDS_DECISION (worked-method depth)" if "NEEDS_DECISION" in v else "READY once the owner authorizes the batch"
    if s == "REUSE_PENDING": return "BLOCKED: content defect (owner-run fix or replacement)"
    if s == "REUSE_VERIFIED": return "n/a (reuse)"
    return "DEFERRED (not a generation candidate)" if "sign-off" in v else "n/a (excluded)"


def shift_down(ws, first_row, k):
    """Move every row from first_row down by k rows, keeping merged ranges, row heights and the freeze pane."""
    merged = [str(m) for m in ws.merged_cells.ranges if m.min_row >= first_row]
    for m in merged: ws.unmerge_cells(m)
    heights = {r: ws.row_dimensions[r].height for r in range(first_row, ws.max_row + 1)}
    last_col = ws.max_column
    ws.move_range("A%d:%s%d" % (first_row, ws.cell(row=1, column=last_col).column_letter, ws.max_row), rows=k)
    for r in sorted(heights, reverse=True):
        ws.row_dimensions[r + k].height = heights[r]
    for r in range(first_row, first_row + k): ws.row_dimensions[r].height = None
    for m in merged:
        a, b = m.split(":"); import re
        ra = re.match(r"([A-Z]+)(\d+)", a); rb = re.match(r"([A-Z]+)(\d+)", b)
        ws.merge_cells("%s%d:%s%d" % (ra.group(1), int(ra.group(2)) + k, rb.group(1), int(rb.group(2)) + k))


def rebuild_overview(wb, meta, rows, plans, terms, cnt):
    ws = wb["Overview"]; pub = meta["public"]
    included = cnt["REUSE_VERIFIED"] + cnt["REUSE_PENDING"] + cnt["NEW"]
    block = [
        ("Public Study Journey title", pub["title"]),
        ("Public description", pub["description"]),
        ("Overall emoji", pub["emoji"]),
        ("Course/Program", pub["course_program"]),
        ("Study Journey type", pub["journey_type"]),
        ("Academic terms, in order", " then ".join(f"{i}. {t}" for i, t in enumerate(terms, 1))),
        ("Subject Plan count", str(len(plans))),
        ("Planned included Note count", f"{included} ({cnt['REUSE_VERIFIED'] + cnt['REUSE_PENDING']} reuse + {cnt['NEW']} new); {cnt['EXCLUDED']} excluded rows are not counted"),
        ("Academic status", meta["status"]["academic"]),
        ("Generation status", meta["status"]["generation"]),
        ("Publication status", meta["status"]["publication"]),
    ]
    k = len(block) + 3  # banner + rows + two blank lines
    shift_down(ws, 4, k)
    ws["A4"] = "PUBLICATION METADATA: what the learner will see once published (public text only; internal notes are at the foot of this sheet)"
    ws.merge_cells("A4:I4"); ws["A4"].fill = H_FILL; ws["A4"].font = H_FONT; ws["A4"].alignment = Alignment(wrap_text=True, vertical="center")
    ws.row_dimensions[4].height = 32
    for i, (label, value) in enumerate(block, 5):
        ws.cell(row=i, column=2, value=label).font = Font(bold=True)
        ws.cell(row=i, column=2).alignment = WRAP
        c = ws.cell(row=i, column=3, value=value); c.alignment = WRAP
        ws.merge_cells("C%d:I%d" % (i, i))
        ws.row_dimensions[i].height = max(18, 15 * (1 + len(value) // 62))
    ws["A2"] = "INTERNAL (never public): " + meta["internal_line"]
    # per-plan emoji fields, as separate columns beside the plan table
    hdr = 6 + k
    ws.freeze_panes = "A%d" % (hdr + 1)
    src = ws.cell(row=hdr, column=10)
    for col, (label, w) in enumerate([("Emoji (separate field)", 12), ("Emoji source", 46)], 11):
        c = ws.cell(row=hdr, column=col, value=label); c.fill = copy(src.fill); c.font = copy(src.font); c.alignment = copy(src.alignment); c.border = copy(src.border)
        ws.column_dimensions[c.column_letter].width = w
    for r in range(hdr + 1, ws.max_row + 1):
        no = ws.cell(row=r, column=1).value
        if no is None or str(no) not in meta["plans"]: continue
        p = meta["plans"][str(no)]; title = plans[str(no)]
        ws.cell(row=r, column=11, value=p["emoji"]); ws.cell(row=r, column=12, value=p["emoji_source"])
        for col in (11, 12): ws.cell(row=r, column=col).alignment = WRAP
    # internal notes, separate from the public text
    r0 = ws.max_row + 2
    ws.cell(row=r0, column=1, value="INTERNAL NOTES (never public): reference-arrangement disclaimer and operational warnings").fill = H_FILL
    ws.cell(row=r0, column=1).font = H_FONT; ws.merge_cells("A%d:I%d" % (r0, r0))
    for i, note in enumerate([meta["internal_line"]] + meta["internal_notes"], r0 + 1):
        c = ws.cell(row=i, column=2, value=note); c.alignment = WRAP; ws.merge_cells("B%d:I%d" % (i, i))
        ws.row_dimensions[i].height = max(18, 15 * (1 + len(note) // 78))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--recon", required=True); ap.add_argument("--shaping", required=True)
    ap.add_argument("--metadata", required=True); ap.add_argument("--out", required=True)
    a = ap.parse_args()
    rows = list(csv.DictReader(open(a.recon), delimiter="\t"))
    meta = json.load(open(a.metadata))
    plans, terms = validate(meta, rows)
    write_shaping(rows, meta, a.shaping)
    subprocess.run([sys.executable, BUILDER, a.shaping, a.out, meta["workbook_title"], meta["internal_line"]], check=True)
    wb = load_workbook(a.out)
    cnt = collections.Counter(r["status"] for r in rows)
    flagged = [r for r in rows if r["status"] == "NEW" and "NEEDS_DECISION" in r["verification_status"]]
    tag = meta["tag_program"]
    untagged = [r for r in rows if r["status"] in ("REUSE_VERIFIED", "REUSE_PENDING") and tag not in r["applicable_programs"]]
    sign = meta["signoff_date"]
    rebuild_overview(wb, meta, rows, plans, terms, cnt)

    # ---- Sign-off and Readiness
    ws = wb.create_sheet("Sign-off and Readiness")
    ws["A1"] = meta["workbook_title"]; ws["A1"].font = Font(bold=True, size=14)
    ws["A2"] = "INTERNAL (never public): " + meta["internal_line"]; ws["A2"].alignment = Alignment(wrap_text=True); ws.merge_cells("A2:D2"); ws.row_dimensions[2].height = 48
    head(ws, 4, ["Gate", "State", "Meaning", "Who decides"])
    gates = [
        ("Academic approval", meta["status"]["academic"],
         "The Subject Plans, semester arrangement, reuse decisions, Domain Contexts and structure. It does NOT approve any Note body, Study Pack or publication.", "GPT Note Strategist; owner approves this workbook"),
        ("Generation readiness", meta["status"]["generation"] + f" ({cnt['NEW'] - len(flagged)} NEW Notes ready once a batch is authorized; {len(flagged)} wait for the pilot)",
         "A NEW row is READY when Subject, Domain Context, programs, Authored Depth (proposed) and objectives are set and the title is at most 160 characters. Bulk Generate takes one Subject, one Domain Context and one program set per batch (see By Subject (bulk generate)). No scope field exists, so the title must fix the scope.", "Owner, per batch"),
        ("Pilot before bulk generation", "REQUIRED, separately authorized",
         "A small pilot with representative calculation-heavy Notes (see Generation Pilot) before any bulk generation of the flagged Notes.", "Owner"),
        ("Publication readiness", meta["status"]["publication"],
         "Publication follows the playbook section 10 checklist: per-plan placement check, program, visibility and pack checks, then the owner's publish. Claude prepares SQL files; the owner runs every production write.", "Owner"),
    ]
    for i, g in enumerate(gates, 5):
        for c, t in enumerate(g, 1): ws.cell(row=i, column=c, value=t).alignment = WRAP
    ws["A10"] = "Counts"; ws["A10"].font = Font(bold=True)
    for i, (k, v) in enumerate([("REUSE_VERIFIED", cnt["REUSE_VERIFIED"]), ("REUSE_PENDING", cnt["REUSE_PENDING"]), ("NEW", cnt["NEW"]), ("EXCLUDED", cnt["EXCLUDED"]),
                                ("Total rows", len(rows)), ("NEW with a generation-depth concern", len(flagged)), (f"Reuse rows without the {tag} tag", len(untagged))], 11):
        ws.cell(row=i, column=1, value=k); ws.cell(row=i, column=2, value=v)
    ws["A19"] = "Generated from the reconciled TSV and the metadata JSON by docs/claude-plans/<journey>/build_journey_workbook.py. The standard sheets come from the shared builder; edit the sources and regenerate, never the workbook."
    ws["A19"].alignment = Alignment(wrap_text=True); ws.merge_cells("A19:D19"); ws.row_dimensions[19].height = 40
    widths(ws, [34, 52, 90, 34])

    # ---- Note Register
    ws = wb.create_sheet("Note Register")
    head(ws, 1, ["Plan", "Subject Plan", "Term", "Section", "#", "Note title", "Canonical Note id", "Note subject", "Domain Context", "Applicable Programs",
                 "Authored Depth", "Status", "Learning objectives", "Verification", "Academic approval", "Generation readiness", "Publication readiness"])
    seq = collections.Counter()
    for r in rows:
        seq[r["plan_no"]] += 1
        pub = "NOT READY (nothing generated, built or authorized)" if r["status"] != "EXCLUDED" else "n/a (excluded)"
        ws.append([int(r["plan_no"]), r["subject_plan"], r["academic_term"], r["section"], seq[r["plan_no"]], r["note_title"], r["canonical_note_id"], r["note_subject"],
                   r["domain_context"], r["applicable_programs"], depth_of(r, meta), r["status"], r["learning_objectives"], r["verification_status"],
                   f"APPROVED {sign}" if r["status"] != "EXCLUDED" else f"EXCLUDED {sign}", gen_ready(r), pub])
        ws.cell(row=ws.max_row, column=12).fill = PatternFill("solid", fgColor=STATUS_FILL[r["status"]])
    for row in ws.iter_rows(min_row=2):
        for c in row: c.alignment = WRAP
    widths(ws, [6, 28, 14, 28, 5, 48, 38, 24, 26, 48, 22, 16, 52, 60, 22, 34, 30])
    ws.auto_filter.ref = ws.dimensions

    # ---- Issues and Conditions
    ws = wb.create_sheet("Issues and Conditions")
    head(ws, 1, ["#", "Issue or condition", "Exact ids and detail", "Action and owner"])
    def fill(text):  # plain replacement, so literal braces in the text (for example LaTeX) survive
        return text.replace("{untagged}", str(len(untagged))).replace("{flagged}", str(len(flagged)))
    for i, it in enumerate(meta["issues"], 1):
        ws.append([i, it["issue"], fill(it["detail"]), fill(it["action"])])
    for row in ws.iter_rows(min_row=2):
        for c in row: c.alignment = WRAP
    widths(ws, [4, 52, 110, 50])

    # ---- Tag Gaps
    ws = wb.create_sheet("CpE Tag Gaps")
    head(ws, 1, ["Subject Plan", "Canonical Note id", "Note title", "Current Applicable Programs", "Decision"])
    for r in untagged:
        ws.append([r["subject_plan"], r["canonical_note_id"], r["note_title"], r["applicable_programs"], "Owner-authorized metadata decision, undecided"])
    for row in ws.iter_rows(min_row=2):
        for c in row: c.alignment = WRAP
    widths(ws, [34, 38, 56, 70, 40])

    # ---- Generation Pilot
    ws = wb.create_sheet("Generation Pilot")
    ws["A1"] = "Proposed generation pilot (separately authorized by the owner; nothing here is authorized)"; ws["A1"].font = Font(bold=True)
    head(ws, 3, ["Type", "Subject Plan", "Note title", "Learning objectives", "Notes"])
    byt = {r["note_title"]: r for r in rows if r["status"] == "NEW"}
    for p in meta["pilot"]:
        ws.append([p["kind"], p["plan"], p["title"], byt[p["title"]]["learning_objectives"], "Subject, Domain Context and program set are identical within a plan, so each is one batch of one Note."])
    last = ws.max_row + 2
    ws.cell(row=last, column=1, value=("Proposed measurements, read-only after generation: body length, whether worked examples appear, whether every objective is covered, whether the math renders, "
                                       f"and the Study Pack structural checks. The pilot decides whether the {len(flagged)} flagged Notes can be generated by title alone or need a scope field (a backend change via Release Implementor to Codex).")).alignment = WRAP
    ws.merge_cells("A%d:E%d" % (last, last)); ws.row_dimensions[last].height = 60
    for row in ws.iter_rows(min_row=4, max_row=3 + len(meta["pilot"])):
        for c in row: c.alignment = WRAP
    widths(ws, [24, 34, 46, 80, 60])

    wb.save(a.out)
    print("saved", a.out, "| sheets:", wb.sheetnames)


if __name__ == "__main__":
    main()
