#!/usr/bin/env python3
"""Build a Review Set shaping workbook (.xlsx) from a tab-separated plan file.

    python3 build_review_set_workbook.py <input.tsv> <output.xlsx> "<Review Set title>" "<description>"

Requires openpyxl. The repo's Python is externally managed, so use a venv:
    python3 -m venv /tmp/xlsxvenv && /tmp/xlsxvenv/bin/pip install openpyxl
    /tmp/xlsxvenv/bin/python docs/curriculum/build_review_set_workbook.py ...

INPUT COLUMNS (tab-separated, header row required, order irrelevant):
    plan_no            integer, 1-based; groups rows into Subject Plan sheets
    subject_plan       plan title, emoji allowed; must be constant per plan_no
    plan_description   one sentence; must be constant per plan_no; printed ONCE per sheet
    section            section name; repeats across its rows, printed once per group
    note_title         the note
    note_subject       canonical Subject metadata (NOT the section name)
    domain_context     enum value, or "(unset)"
    applicable_programs  REQUIRED, comma-separated catalog program names, filled on EVERY row.
                       The Domain Context sheet aggregates it per (note subject, Domain Context)
                       and prints the union for that pair. That union is what makes the two hard
                       Domain Context rules checkable instead of merely stated: which (unset) rows
                       are legal, and which notes gain a second program on reuse and therefore now
                       REQUIRE an explicit Domain Context.
                       ⚠️ Legacy plan files predating this rule (civil-engineering, let) lack the
                       column and are refused until it is backfilled. For LET that refusal is the
                       point: rebuilding it from its own .tsv silently DROPS the Applicable
                       Programs column its committed workbook already shows.
    status             Existing | Reuse | New | Excluded | Unmapped
                       Unmapped = a target shape not yet reconciled against production. Use it
                       when reshaping a set whose notes already exist but have not been matched
                       title-by-title. It is honest; guessing "New" is not.

    academic_term      OPTIONAL. The Academic Term of the Subject Plan (e.g. "First Semester"); must
                       be constant per plan_no, like subject_plan. Leave the column out, or blank on
                       EVERY plan, for a Study Plan with no terms (the flat Subject list -- every
                       existing Review Set). It is all-or-nothing PER STUDY PLAN: a file where only
                       some plans carry a term is refused, naming the plans that lack one. There is
                       no term-order column: the order is DERIVED from the first-seen order of the
                       labels in the file (row order is authoritative) and printed in the workbook so
                       a reversed sequence is visible before a curator enters the terms in the Year
                       builder. The curator types the label exactly as printed; the builder snaps
                       spelling variants to the existing label.
                       ⚠️ One plan file is ONE Study Plan (one root), so the check is per file.
                       This column is authoring input for a curator; it does NOT touch any Note and
                       is never merged into applicable_programs (ADR-001, ADR-003).

OPTIONAL SIDECAR: `<input>-policy.tsv` (columns: topic, decision; first row is the header pair
printed bold) renders beside the Domain Context table as the set's Applicable Programs policy.
Use it for decisions that govern the column but are not per-note -- e.g. which program families a
General Education note should and should not carry. Without it, no policy block is drawn.

Row ORDER is authoritative: sections appear in first-seen order, notes in file order.
Nothing is sorted, so the strategist's sequencing survives into the workbook.

See docs/curriculum/review-set-workbook-spec.md for why each sheet exists.
"""
import sys, csv, collections, os, re
from openpyxl import Workbook
from openpyxl.styles import Font, Alignment, PatternFill, Border, Side

STATUS_FILL = {"Existing":"D5E8D4","Reuse":"DAE8FC","New":"FFF2CC","Excluded":"F8CECC","Unmapped":"FFE6CC"}
DC_FILL = {"(unset)":"EAEAEA","ENGINEERING_SCIENCES":"E1D5E7","ENGINEERING_MATHEMATICS":"D5E8D4",
           "CIVIL_ENGINEERING":"FFE6CC","PROFESSIONAL_PRACTICE_AND_REGULATION":"DAE8FC",
           "ENGINEERING_MATHEMATICS ":"D5E8D4"}
HDR = PatternFill("solid", fgColor="44546A")
_T = Side(style="thin", color="D0D0D0")
BOX = Border(left=_T, right=_T, top=_T, bottom=_T)
EXCLUDED = "Excluded"
TERM_LABEL_MAX_LENGTH = 60          # mirrors note_collections.term_label VARCHAR(60)
TERM_NOT_SPECIFIED_LABEL = "Term not specified"   # the runtime heading for unplaced Subjects; never a stored term


class TermValidationError(ValueError):
    """A curator-facing, actionable refusal of an invalid academic_term column."""


def _head(ws, row, labels):
    for i, h in enumerate(labels, 1):
        c = ws.cell(row=row, column=i, value=h)
        c.font = Font(bold=True, color="FFFFFF"); c.fill = HDR; c.border = BOX


def _banner(ws, cell, text, span, height, **font):
    ws[cell] = text
    ws[cell].alignment = Alignment(wrap_text=True, vertical="top")
    if font: ws[cell].font = Font(**font)
    ws.merge_cells(span); ws.row_dimensions[int(cell[1:])].height = height


def _widths(ws, widths):
    for col, w in zip("ABCDEFGH", widths):
        ws.column_dimensions[col].width = w


def _norm_term(label):
    return re.sub(r"\s+", " ", label.strip()).lower()


def resolve_terms(rows, study_plan_title):
    """Validate the OPTIONAL academic_term column and derive the term order.

    Returns (plan_terms, term_order): {plan_no: label} and {label: order}. Both are EMPTY when the
    Study Plan uses no terms, which is the state that must leave the workbook byte-for-byte what it
    was before the column existed. Raises TermValidationError otherwise.

    Rules (ADR-003 / plan §12.1): within one Study Plan academic_term is either unused for every
    Subject Plan or assigned to every Subject Plan. Partial assignment is invalid authoring input.
    The runtime still renders a mixed Year defensively; this is the authoring-side gate.
    """
    if not rows or "academic_term" not in rows[0]:
        return {}, {}
    by_plan = collections.OrderedDict()          # plan_no -> (title, set of labels seen)
    for r in rows:
        entry = by_plan.setdefault(int(r["plan_no"]), (r["subject_plan"], set()))
        entry[1].add((r.get("academic_term") or "").strip())
    for pno, (title, labels) in by_plan.items():
        if len(labels) > 1:
            shown = ", ".join(repr(x) if x else "(blank)" for x in sorted(labels))
            raise TermValidationError(
                f"academic_term must be constant per plan_no, but plan {pno} ({title!r}) has {shown}. "
                "Use one value on every row of the plan.")
    plan_terms = {pno: next(iter(labels)) for pno, (_, labels) in by_plan.items()}
    plan_terms = {pno: lab for pno, lab in plan_terms.items() if lab}
    if not plan_terms:
        return {}, {}
    if len(plan_terms) != len(by_plan):
        missing = [f"plan {pno} ({title!r})" for pno, (title, _) in by_plan.items() if pno not in plan_terms]
        raise TermValidationError(
            f"Study Plan {study_plan_title!r}: academic_term is assigned to {len(plan_terms)} of "
            f"{len(by_plan)} Subject Plans. Academic Term is either unused for all Subject Plans or "
            f"assigned to all of them; partial assignment is invalid. Unassigned: {'; '.join(missing)}. "
            "Fill academic_term for those plans, or clear it on every plan to use the flat Subject list.")
    seen = {}
    order = collections.OrderedDict()
    for pno in by_plan:
        label = plan_terms[pno]
        units = len(label.encode("utf-16-le")) // 2   # Java String.length() and the input maxLength count UTF-16 units
        if units > TERM_LABEL_MAX_LENGTH:
            raise TermValidationError(
                f"academic_term {label!r} on plan {pno} is {units} characters; the maximum is "
                f"{TERM_LABEL_MAX_LENGTH}.")
        if _norm_term(label) == _norm_term(TERM_NOT_SPECIFIED_LABEL):
            raise TermValidationError(
                f"academic_term on plan {pno} is {TERM_NOT_SPECIFIED_LABEL!r}, which is reserved for "
                "Subjects with no term and can never be a stored term.")
        key = _norm_term(label)
        if key in seen and seen[key] != label:
            raise TermValidationError(
                f"academic_term spellings {seen[key]!r} and {label!r} differ only in case or spacing "
                "and would be folded into one term by the builder. Use one spelling.")
        seen[key] = label
        order.setdefault(label, len(order) + 1)
    return plan_terms, dict(order)


def sheet_name(plan_no, title):
    """Excel caps sheet names at 31 chars and forbids : \\ / ? * [ ]."""
    clean = "".join(ch for ch in title if ch not in ':\\/?*[]').strip()
    clean = "".join(ch for ch in clean if ch.isascii()).strip()
    return f"{plan_no} {clean}"[:31]


def build(rows, out, set_title, set_desc, policy=None):
    plan_terms, term_order = resolve_terms(rows, set_title)
    has_terms = bool(plan_terms)
    plans = collections.OrderedDict()
    for r in rows:
        plans.setdefault(int(r["plan_no"]), {"title": r["subject_plan"],
                                             "desc": r["plan_description"],
                                             "sections": collections.OrderedDict()})
        plans[int(r["plan_no"])]["sections"].setdefault(r["section"], []).append(r)

    # a note carried by two plans is ONE canonical note, not a duplicate
    shared = collections.Counter()
    for r in rows:
        if r["status"] != EXCLUDED:
            shared[r["note_title"]] += 1

    wb = Workbook(); ov = wb.active; ov.title = "Overview"
    ov["A1"] = f"{set_title} — target shape"; ov["A1"].font = Font(size=15, bold=True)
    _banner(ov, "A2", set_desc, "A2:H2", 42)
    _banner(ov, "A4", "Status: Existing = already in the set · Reuse = exists elsewhere, add it · "
                      "New = needs authoring · Excluded = deliberately held out.  "
                      "See the Domain Context sheet for the two hard rules on that column.",
            "A4:H4", 16, italic=True, color="B06000")
    _head(ov, 6, ["#", "Subject Plan", "Sections", "In set", "Existing", "Reuse", "New", "Unmapped", "Excluded"]
          + (["Academic Term (order)"] if has_terms else []))
    r, tot = 7, collections.Counter()
    for pno, p in plans.items():
        st = collections.Counter(n["status"] for ns in p["sections"].values() for n in ns)
        tot.update(st)
        vals = [pno, p["title"], len(p["sections"]),
                sum(v for k, v in st.items() if k != EXCLUDED),
                st["Existing"], st["Reuse"], st["New"], st["Unmapped"], st[EXCLUDED]]
        if has_terms:
            vals.append(f"{plan_terms[pno]} ({term_order[plan_terms[pno]]})")
        for i, v in enumerate(vals, 1):
            ov.cell(row=r, column=i, value=v).border = BOX
        r += 1
    for i, v in enumerate(["", "TOTAL", "", sum(v for k, v in tot.items() if k != EXCLUDED),
                           tot["Existing"], tot["Reuse"], tot["New"], tot["Unmapped"], tot[EXCLUDED]], 1):
        c = ov.cell(row=r, column=i, value=v); c.font = Font(bold=True); c.border = BOX
    if policy:
        r += 2
        ov.cell(row=r, column=1, value=policy[0][0]).font = Font(bold=True, size=12)
        ov.merge_cells(start_row=r, start_column=1, end_row=r, end_column=9)
        summary = "  ".join(f"{t}: {d}" for t, d in policy[1:])
        _banner(ov, f"A{r + 1}", summary, f"A{r + 1}:I{r + 1}", 44, italic=True, color="666666")
    _widths(ov, [5, 40, 9, 8, 9, 8, 7, 10, 9]); ov.freeze_panes = "A7"
    if has_terms:
        ov.column_dimensions["J"].width = 26

    dc = wb.create_sheet("Domain Context")
    dc["A1"] = "Domain Context by Subject"; dc["A1"].font = Font(size=14, bold=True)
    _banner(dc, "A2", "RULE 1 — (unset) is only legal on a note with ONE Applicable Program. "
            "NoteApplicableProgramsService rejects a save with 2+ programs and no Domain Context "
            "(MultiProgramDomainContextRequiredException).", "A2:D2", 44, color="B06000", bold=True)
    _banner(dc, "A3", "RULE 2 — Existing and Reuse notes ALREADY have a Domain Context. The value here is a "
            "recommendation, not a blank field; changing it is a separate decision from placing the note, and "
            "it affects FUTURE generation only.", "A3:D3", 44, color="B06000", bold=True)
    _banner(dc, "A4", "NOTE — (unset) falls back to the program name. If that name matches no quantitative "
            "keyword, computation guidance stays OFF for every quiz generated from the note.", "A4:D4", 30,
            italic=True, color="666666")
    # Applicable Programs is REQUIRED as of 2026-09-10 and main() refuses a file without it, so
    # has_programs is always True on the supported path. The branch stays only so build() is safe to
    # call directly from a future script that has not run main()'s validation -- it is DEFENSIVE, not
    # a live option.
    # ⚠️ THIS COMMENT PREVIOUSLY READ "OPTIONAL ... so a plan file without it (the CE set) still
    # builds", which the same change made FALSE: the CE set no longer builds, by design. Corrected
    # rather than left, because a stale comment beside a dead branch is how the next reader concludes
    # the column is optional and re-introduces the loss this rule exists to prevent.
    # Added 2026-09-04 -- the column previously existed only as a HAND-EDIT of the .xlsx, which
    # regenerating silently destroyed. Generating it from the plan file is what makes the workbook
    # safe to rebuild.
    has_programs = "applicable_programs" in rows[0]
    _head(dc, 6, ["Note subject", "Domain Context", "Notes in set"]
          + (["Applicable Programs"] if has_programs else []))
    per = collections.OrderedDict()
    progs = collections.defaultdict(dict)   # dict preserves first-seen order; set would alphabetise
    for r_ in rows:
        key = (r_["note_subject"], r_["domain_context"])
        per.setdefault(key, 0)
        per[key] += 1
        if has_programs:
            for prog in (r_.get("applicable_programs") or "").split(","):
                if prog.strip():
                    progs[key][prog.strip()] = None
    r = 7
    for (subj, v), n in sorted(per.items()):
        vals = [subj, v, n] + ([", ".join(progs[(subj, v)])] if has_programs else [])
        for i, x in enumerate(vals, 1):
            c = dc.cell(row=r, column=i, value=x); c.border = BOX
            if i == 2:
                c.fill = PatternFill("solid", fgColor=DC_FILL.get(v, "FFFFFF")); c.font = Font(bold=True)
            if i == 4:
                c.alignment = Alignment(wrap_text=True, vertical="top")
        r += 1
    # Per-set Applicable Programs policy, from <input>-policy.tsv. It sits BESIDE the table rather
    # than under it because it governs the column next to it. Optional: sets without a policy file
    # simply have no F/G block. This exists because the block was originally a HAND-EDIT of the
    # .xlsx -- the same way the Applicable Programs column itself started -- and regeneration
    # destroyed it. Anything a curator writes into the workbook must have a file that regenerates it.
    if policy:
        for i, (topic, decision) in enumerate(policy):
            tc = dc.cell(row=1 + i, column=6, value=topic)
            vc = dc.cell(row=1 + i, column=7, value=decision)
            tc.border = BOX; vc.border = BOX
            vc.alignment = Alignment(wrap_text=True, vertical="top")
            if i == 0:
                tc.font = Font(bold=True, color="FFFFFF"); tc.fill = HDR
                vc.font = Font(bold=True, color="FFFFFF"); vc.fill = HDR
            else:
                tc.font = Font(bold=True)
        dc.column_dimensions["F"].width = 30
        dc.column_dimensions["G"].width = 96
    _widths(dc, [34, 40, 14, 64]); dc.freeze_panes = "A7"

    for pno, p in plans.items():
        ws = wb.create_sheet(sheet_name(pno, p["title"]))
        ws["A1"] = p["title"]; ws["A1"].font = Font(size=14, bold=True); ws.merge_cells("A1:G1")
        _banner(ws, "A2", p["desc"], "A2:G2", 42)
        if has_terms:
            label = plan_terms[pno]
            ws["A3"] = (f"Academic Term: {label} (order {term_order[label]}, derived from file order; "
                        "enter terms in the Year builder in this order)")
            ws["A3"].font = Font(bold=True, color="44546A")
            ws.merge_cells("A3:G3")
        _head(ws, 4, ["Section", "#", "Note title", "Note subject", "Domain Context", "Status", "Flags",
                      "Applicable Programs"])
        r = 5
        for sec, notes in p["sections"].items():
            for i, n in enumerate(notes):
                flags = []
                if shared[n["note_title"]] > 1:
                    flags.append("same canonical note in another plan — do not duplicate")
                if n["status"] == "Unmapped":
                    flags.append("existence not yet checked — reconcile against production")
                elif n["status"] in ("Reuse", "Existing"):
                    flags.append("context already set — verify before changing")
                elif n["domain_context"] == "(unset)":
                    flags.append("unset requires a SINGLE applicable program")
                vals = [sec if i == 0 else "", i + 1, n["note_title"], n["note_subject"],
                        n["domain_context"], n["status"], " · ".join(flags),
                        n.get("applicable_programs", "")]
                for ci, x in enumerate(vals, 1):
                    c = ws.cell(row=r, column=ci, value=x); c.border = BOX
                    if ci == 1: c.font = Font(bold=True)
                    if ci == 2: c.alignment = Alignment(horizontal="center")
                    if ci == 5: c.fill = PatternFill("solid", fgColor=DC_FILL.get(x, "FFFFFF"))
                    if ci == 6 and x in STATUS_FILL: c.fill = PatternFill("solid", fgColor=STATUS_FILL[x])
                    if ci == 7 and flags: c.font = Font(italic=True, color="B06000", size=9)
                    if ci == 8: c.alignment = Alignment(wrap_text=True, vertical="top")
                r += 1
            r += 1
        _widths(ws, [34, 4, 60, 30, 34, 11, 58, 52]); ws.freeze_panes = "A5"

    bs = wb.create_sheet("By Subject (bulk generate)")
    bs["A1"] = "Generation batches — Bulk Generate applies ONE subject and ONE Domain Context per batch"
    bs["A1"].font = Font(size=13, bold=True); bs.merge_cells("A1:F1")
    _banner(bs, "A2", "Only 'New' and 'Unmapped' notes appear. Each block is one Bulk Generate run: set the Subject and Domain "
            "Context shown, paste the titles as topics.", "A2:F2", 16, italic=True, color="666666")
    _head(bs, 4, ["Note subject", "Domain Context", "New notes", "Subject Plan", "Section", "Note title",
                  "Applicable Programs"])
    by = collections.defaultdict(list)
    for pno, p in plans.items():
        for sec, notes in p["sections"].items():
            for n in notes:
                if n["status"] in ("New", "Unmapped"):
                    by[(n["note_subject"], n["domain_context"])].append(
                        (p["title"], sec, n["note_title"], n.get("applicable_programs", "")))
    r = 5
    for key in sorted(by, key=lambda k: (-len(by[k]), k[0])):
        subj, v = key
        for i, (pl, sec, t, ap) in enumerate(by[key]):
            vals = [subj if i == 0 else "", v if i == 0 else "", len(by[key]) if i == 0 else "", pl, sec, t, ap]
            for ci, x in enumerate(vals, 1):
                c = bs.cell(row=r, column=ci, value=x); c.border = BOX
                if ci in (1, 3): c.font = Font(bold=True)
                if ci == 2 and i == 0:
                    c.fill = PatternFill("solid", fgColor=DC_FILL.get(v, "FFFFFF")); c.font = Font(bold=True)
            r += 1
        r += 1
    _widths(bs, [32, 34, 10, 36, 32, 60, 52]); bs.freeze_panes = "A5"

    wb.save(out)
    return plans, tot, by


def main():
    if len(sys.argv) != 5:
        print(__doc__); sys.exit(1)
    src, out, title, desc = sys.argv[1:5]
    with open(src, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f, delimiter="\t"))
    required = {"plan_no","subject_plan","plan_description","section","note_title",
                "note_subject","domain_context","status","applicable_programs"}
    missing = required - set(rows[0])
    if missing:
        hint = ""
        if "applicable_programs" in missing:
            hint = ("\n\napplicable_programs is REQUIRED on every plan file. The Domain Context sheet "
                    "aggregates it per (note subject, Domain Context); without it the two hard Domain "
                    "Context rules cannot be checked, only recited. Backfill it from production "
                    "(one program name per note, comma-separated for shared notes) and re-run. "
                    "Building without it is how the column was silently lost twice.")
        sys.exit(f"input is missing required columns: {sorted(missing)}{hint}")
    blank = [r["note_title"] for r in rows if not (r.get("applicable_programs") or "").strip()]
    if blank:
        sys.exit(f"applicable_programs is empty on {len(blank)} row(s), first: {blank[0]!r}. "
                 "Every row must name at least one catalog program.")
    bad = {r["status"] for r in rows} - set(STATUS_FILL)
    if bad:
        sys.exit(f"unknown status values: {sorted(bad)} (allowed: {sorted(STATUS_FILL)})")
    policy_path = re.sub(r"\.tsv$", "", src) + "-policy.tsv"
    policy = None
    if os.path.exists(policy_path):
        with open(policy_path, encoding="utf-8-sig", newline="") as f:
            policy = [tuple(r[:2]) for r in csv.reader(f, delimiter="\t") if r and any(x.strip() for x in r)]
        print(f"policy: {len(policy) - 1} decision(s) from {os.path.basename(policy_path)}")
    try:
        plans, tot, by = build(rows, out, title, desc, policy)
    except TermValidationError as err:
        sys.exit(f"academic_term is invalid: {err}")
    print(f"saved {out}")
    print(f"{len(plans)} plans · {len(rows)} rows · "
          + " · ".join(f"{k}={v}" for k, v in sorted(tot.items())))
    print(f"{len(by)} bulk-generate batches")


if __name__ == "__main__":
    main()
