#!/usr/bin/env python3
"""BSCpE Year 1: build the owner-facing target-shape workbook (documentation-only generator).

    python3 build_bscpe_workbook.py

Needs openpyxl (use a venv). It does two things and never edits a workbook by hand:
  1. runs the SHARED builder docs/curriculum/build_review_set_workbook.py on docs/curriculum/bscpe-year1-shaping.tsv
     (the standard layout: Overview, Domain Context, one sheet per Subject Plan, By Subject (bulk generate));
  2. adds five sheets from bscpe-year1-reconciled-target-shape.tsv, which the shared builder cannot carry yet
     (it has no columns for canonical Note id, learning objectives or readiness; see the playbook, sections 7 and 12):
     Sign-off and Readiness, Note Register, Issues and Conditions, CpE Tag Gaps, Generation Pilot.
Edit the TSV and regenerate; never edit the workbook. Anything touching the shared builder goes through
Release Implementor to Codex.
"""
import csv, subprocess, sys, os, collections
from openpyxl import load_workbook
from openpyxl.styles import Font, Alignment, PatternFill

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
SHAPING = os.path.join(ROOT, "docs/curriculum/bscpe-year1-shaping.tsv")
RECON = os.path.join(HERE, "bscpe-year1-reconciled-target-shape.tsv")
BUILDER = os.path.join(ROOT, "docs/curriculum/build_review_set_workbook.py")
OUT = os.path.join(ROOT, "docs/curriculum/bscpe-year1-study-plan-target-shape.xlsx")
TITLE = "BS Computer Engineering: Year 1 Study Journey (target shape)"
DESC = ("A curated Philippine Year 1 foundation for BS Computer Engineering, a NoteLib reference arrangement and not any "
        "institution's prospectus. Academic target shape approved 2026-11-12; generation and publication are not yet authorized.")
SIGNOFF = "2026-11-12"

# Authored Depth currently on each reused calculus Note (read live 2026-11-12); everything else reused is COLLEGE except
# the mechanics Notes (unset). BOARD_EXAM_REVIEW Notes are preserved unchanged, never relabelled.
BOARD = {"6e5a8d7f", "dc08be8d", "d8042c2f", "7bc1214d", "bde223c9", "11b0d8ef", "1074d3cb", "5e0fb264"}
UNSET = {"24e77b20", "7b0df43e", "5768ba6b", "a2a227b7", "2cf5cdd3", "8cab64e3", "b57a7ec2", "9714a5bd", "65b723f6",
         "124f7cd6", "02b797e0", "0f37e7ca", "9e14aecd", "80e2277d", "dac28cb9", "cf662729",
         "0fdee8ee", "435afd7e", "99648408", "50ee257b", "e6b8bb57", "ce9ad9be", "fc0f8a2d", "45e55bac", "a1fb1d72",
         "4239bdee", "9f8f6767", "acd1073a"}
PILOT = [("Fundamentals of Electrical Circuits", "Node-Voltage Analysis", "circuit analysis"),
         ("Chemistry for Engineers", "The Mole and Stoichiometry", "chemistry calculation"),
         ("Physics for Engineers", "Kinematics in One Dimension", "physics calculation")]

H_FILL = PatternFill("solid", fgColor="1F3864"); H_FONT = Font(bold=True, color="FFFFFF")
STATUS_FILL = {"REUSE_VERIFIED": "E2EFDA", "REUSE_PENDING": "FFF2CC", "NEW": "DDEBF7", "EXCLUDED": "EDEDED"}


def head(ws, row, labels):
    for c, t in enumerate(labels, 1):
        cell = ws.cell(row=row, column=c, value=t); cell.fill = H_FILL; cell.font = H_FONT
        cell.alignment = Alignment(wrap_text=True, vertical="center")
    ws.freeze_panes = "A%d" % (row + 1)


def widths(ws, ws_widths):
    for i, w in enumerate(ws_widths, 1):
        ws.column_dimensions[ws.cell(row=1, column=i).column_letter].width = w


def depth_of(r):
    i = r["canonical_note_id"][:8]
    if r["status"] == "NEW": return "COLLEGE (proposed; confirm at generation)"
    if i in BOARD: return "BOARD_EXAM_REVIEW (preserved)"
    if i in UNSET: return "(unset) (preserved)"
    return "COLLEGE"


def gen_ready(r):
    s, v = r["status"], r["verification_status"]
    if s == "NEW":
        if "NEEDS_DECISION" in v: return "PILOT FIRST: NEEDS_DECISION (worked-method depth)"
        return "READY once the owner authorizes the batch"
    if s == "REUSE_PENDING": return "BLOCKED: content defect (owner-run fix or replacement)"
    if s == "REUSE_VERIFIED": return "n/a (reuse)"
    return "DEFERRED (not a generation candidate)" if "sign-off" in v else "n/a (excluded)"


def main():
    rows = list(csv.DictReader(open(RECON), delimiter="\t"))
    subprocess.run([sys.executable, BUILDER, SHAPING, OUT, TITLE, DESC], check=True)
    wb = load_workbook(OUT)
    cnt = collections.Counter(r["status"] for r in rows)
    flagged = [r for r in rows if r["status"] == "NEW" and "NEEDS_DECISION" in r["verification_status"]]
    untagged = [r for r in rows if r["status"] in ("REUSE_VERIFIED", "REUSE_PENDING") and "Computer Engineering" not in r["applicable_programs"]]

    # ---- Sign-off and Readiness
    ws = wb.create_sheet("Sign-off and Readiness")
    ws["A1"] = TITLE; ws["A1"].font = Font(bold=True, size=14)
    ws["A2"] = DESC; ws["A2"].alignment = Alignment(wrap_text=True); ws.merge_cells("A2:D2"); ws.row_dimensions[2].height = 48
    head(ws, 4, ["Gate", "State", "Meaning", "Who decides"])
    gates = [
        ("Academic approval", f"APPROVED {SIGNOFF} (GPT Note Strategist final sign-off, with three row edits applied)",
         "The 11 Subject Plans, semester arrangement, reuse decisions, Domain Contexts and structure. It does NOT approve any Note body, Study Pack or publication.", "GPT Note Strategist; owner approves this workbook"),
        ("Generation readiness", f"NOT AUTHORIZED. {cnt['NEW']} NEW Notes: {cnt['NEW'] - len(flagged)} ready once a batch is authorized; {len(flagged)} must wait for the pilot",
         "A NEW row is READY when Subject, Domain Context, programs, Authored Depth (proposed) and objectives are set and the title is at most 160 characters. Bulk Generate takes one Subject, one Domain Context and one program set per batch (see the By Subject (bulk generate) sheet). No scope field exists, so the title must fix the scope.", "Owner, per batch"),
        ("Pilot before bulk generation", "REQUIRED, separately authorized",
         "A small pilot with one circuit-analysis, one chemistry-calculation and one physics-calculation Note (see Generation Pilot) before any bulk generation of the flagged Notes.", "Owner"),
        ("Publication readiness", "NOT READY",
         "No Note generated, no collection built, no tag decided. Publication follows the playbook section 10 checklist: per-plan placement check, program, visibility and pack checks, then the owner's publish. Claude prepares SQL files; the owner runs every production write.", "Owner"),
    ]
    for i, g in enumerate(gates, 5):
        for c, t in enumerate(g, 1):
            ws.cell(row=i, column=c, value=t).alignment = Alignment(wrap_text=True, vertical="top")
    ws["A10"] = "Counts"; ws["A10"].font = Font(bold=True)
    for i, (k, v) in enumerate([("REUSE_VERIFIED", cnt["REUSE_VERIFIED"]), ("REUSE_PENDING", cnt["REUSE_PENDING"]), ("NEW", cnt["NEW"]),
                                ("EXCLUDED", cnt["EXCLUDED"]), ("Total rows", len(rows)), ("NEW with a generation-depth concern", len(flagged)),
                                ("Reuse rows without the Computer Engineering tag", len(untagged))], 11):
        ws.cell(row=i, column=1, value=k); ws.cell(row=i, column=2, value=v)
    ws["A19"] = ("Generated from bscpe-year1-reconciled-target-shape.tsv by docs/claude-plans/bscpe-year1/build_bscpe_workbook.py. "
                 "The standard sheets come from the shared builder; edit the TSV and regenerate, never the workbook.")
    ws["A19"].alignment = Alignment(wrap_text=True); ws.merge_cells("A19:D19"); ws.row_dimensions[19].height = 40
    widths(ws, [34, 52, 90, 34])

    # ---- Note Register
    ws = wb.create_sheet("Note Register")
    cols = ["Plan", "Subject Plan", "Term", "Section", "#", "Note title", "Canonical Note id", "Note subject", "Domain Context",
            "Applicable Programs", "Authored Depth", "Status", "Learning objectives", "Verification", "Academic approval", "Generation readiness", "Publication readiness"]
    head(ws, 1, cols)
    seq = collections.Counter()
    for r in rows:
        seq[r["plan_no"]] += 1
        pub = "NOT READY (nothing generated, built or authorized)" if r["status"] != "EXCLUDED" else "n/a (excluded)"
        vals = [int(r["plan_no"]), r["subject_plan"], r["academic_term"], r["section"], seq[r["plan_no"]], r["note_title"], r["canonical_note_id"],
                r["note_subject"], r["domain_context"], r["applicable_programs"], depth_of(r), r["status"], r["learning_objectives"],
                r["verification_status"], f"APPROVED {SIGNOFF}" if r["status"] != "EXCLUDED" else f"EXCLUDED {SIGNOFF}", gen_ready(r), pub]
        ws.append(vals)
        fill = PatternFill("solid", fgColor=STATUS_FILL[r["status"]])
        ws.cell(row=ws.max_row, column=12).fill = fill
    for row in ws.iter_rows(min_row=2):
        for c in row: c.alignment = Alignment(wrap_text=True, vertical="top")
    widths(ws, [6, 28, 14, 28, 5, 48, 38, 24, 26, 48, 22, 16, 52, 60, 22, 34, 30])
    ws.auto_filter.ref = ws.dimensions

    # ---- Issues and Conditions
    ws = wb.create_sheet("Issues and Conditions")
    head(ws, 1, ["#", "Issue or condition", "Exact ids and detail", "Action and owner"])
    issues = [
        ("The CHED CMOs 87 and 86 were not readable (HTTP 403); the PCU page was not readable. Only the UE page was read.",
         "Course list, semester split and prerequisites are not verified against the CMO. The arrangement is a NoteLib reference, not a claim that CHED prescribes it.", "Owner may supply the PDFs; sign-off already given on this basis."),
        ("REUSE_PENDING: two Calculus 1 Notes with math-rendering defects (they stay pending)",
         "6e5a8d7f-c113-4153-b8df-5a342a59949e Limits and Continuity in Differential Calculus: doubled backslashes, formulas will not render. "
         "8cab64e3-1c12-42ee-9899-115354d13253 Implicit Differentiation in Differential Calculus: a lost backslash leaves 'rac{dy}{dx}' in the Key Idea. "
         "Both are held by the public CELE Comprehensive Review, so a content correction is an owner-run SQL that also changes that set.", "Owner decides: fix, replace with a NEW Note, or accept. Not done in this workstream."),
        ("Two BOARD_EXAM_REVIEW calculus Notes are reused unchanged", "dc08be8d-e6c7-4ab7-b222-7152e45cdc7b Indefinite Integrals; d8042c2f-022d-4a9f-806e-75e21c27079e Definite Integrals. Authored Depth is NOT relabelled.", "None; preserved by decision."),
        ("Reuse rows without the Computer Engineering program", f"{len(untagged)} rows (see CpE Tag Gaps). Tagging is discovery-only and a separate owner-authorized metadata decision; do not tag automatically.", "Owner decides per Note."),
        ("Generation-depth concern", f"{len(flagged)} NEW Notes need worked methods or calculations that a title-only generator does not carry (the existing calculus Notes are rules-only outlines of about 2,000 characters).", "Run the separately authorized pilot first (Generation Pilot sheet)."),
        ("Deferred, not generation candidates", "Infinite Sequences and Series: Convergence Tests; Sinusoidal Signals and Phasors (EXCLUDED at sign-off). Linked List Operations in C++ (e1828d0c) EXCLUDED to keep a language-neutral DSA introduction.", "Revisit only with scope evidence."),
        ("Overlaps accepted", "583c82dc Algorithms and Their Properties (Big-O) vs c7ae7a94 Comparing Algorithm Efficiency Informally; 2ed3f9cd MMW Logic vs the Discrete Mathematics logic Notes; 5768ba6b derivative applications vs the dedicated related-rates and maxima Notes; d8042c2f Definite Integrals vs 9e14aecd the Fundamental Theorem.", "Accepted at sign-off."),
        ("Not used", "d50a303f-8467-4180-9555-86284cf5adaa 'Republic Act 8981: Philippine Professionalization Act for Engineers' is not in the target shape; its title may misstate the statute (unverified).", "Independent legal verification before any future reuse."),
        ("Programs on NEW rows", "Computer Engineering is the single proposed program; no new Domain Context is introduced. Every selected reuse Note has an explicit Domain Context, so adding a program later does not trigger the ADR-001 precondition.", "Owner authorizes any program change separately."),
    ]
    for i, (a, b, c) in enumerate(issues, 1):
        ws.append([i, a, b, c])
    for row in ws.iter_rows(min_row=2):
        for c in row: c.alignment = Alignment(wrap_text=True, vertical="top")
    widths(ws, [4, 52, 110, 50])

    # ---- CpE Tag Gaps
    ws = wb.create_sheet("CpE Tag Gaps")
    head(ws, 1, ["Subject Plan", "Canonical Note id", "Note title", "Current Applicable Programs", "Decision"])
    for r in untagged:
        ws.append([r["subject_plan"], r["canonical_note_id"], r["note_title"], r["applicable_programs"], "Owner-authorized metadata decision, undecided"])
    for row in ws.iter_rows(min_row=2):
        for c in row: c.alignment = Alignment(wrap_text=True, vertical="top")
    widths(ws, [34, 38, 56, 70, 40])

    # ---- Generation Pilot
    ws = wb.create_sheet("Generation Pilot")
    ws["A1"] = "Proposed generation pilot (separately authorized by the owner; nothing here is authorized)"; ws["A1"].font = Font(bold=True)
    head(ws, 3, ["Type", "Subject Plan", "Note title", "Learning objectives", "Notes"])
    byt = {r["note_title"]: r for r in rows if r["status"] == "NEW"}
    for plan, title, kind in PILOT:
        r = byt[title]
        ws.append([kind, plan, title, r["learning_objectives"], "Subject, Domain Context and program set are identical within a plan, so each is one batch of one Note."])
    ws["A8"] = ("Proposed measurements, read-only after generation: body length, whether worked examples appear, whether every objective is covered, whether the math renders, "
                f"and the Study Pack structural checks. The pilot decides whether the {len(flagged)} flagged Notes can be generated by title alone or need a scope field (a backend change via Release Implementor to Codex).")
    ws["A8"].alignment = Alignment(wrap_text=True); ws.merge_cells("A8:E8"); ws.row_dimensions[8].height = 60
    for row in ws.iter_rows(min_row=4, max_row=6):
        for c in row: c.alignment = Alignment(wrap_text=True, vertical="top")
    widths(ws, [24, 34, 46, 80, 60])

    wb.save(OUT)
    print("saved", OUT, "| sheets:", wb.sheetnames)


if __name__ == "__main__":
    main()
