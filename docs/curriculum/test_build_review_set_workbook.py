#!/usr/bin/env python3
"""Tests for build_review_set_workbook.py's academic_term handling (v0.160.0, ADR-003).

    /tmp/xlsxvenv/bin/python -m unittest docs/curriculum/test_build_review_set_workbook.py

The load-bearing test is the no-term one: a plan file with no academic_term must build a workbook
with NO term UI anywhere, so the five live Review Sets regenerate exactly as before.
"""
import importlib.util
import os
import subprocess
import sys
import tempfile
import unittest

import openpyxl

HERE = os.path.dirname(os.path.abspath(__file__))
BUILDER = os.path.join(HERE, "build_review_set_workbook.py")
spec = importlib.util.spec_from_file_location("build_review_set_workbook", BUILDER)
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)

BASE = ["plan_no", "subject_plan", "plan_description", "section", "note_title", "note_subject",
        "domain_context", "applicable_programs", "status"]


def row(plan_no, plan, note, term=None, **over):
    r = {"plan_no": str(plan_no), "subject_plan": plan, "plan_description": f"About {plan}",
         "section": "Week 1", "note_title": note, "note_subject": "Computer Science",
         "domain_context": "(unset)", "applicable_programs": "BS Computer Science", "status": "New"}
    if term is not None:
        r["academic_term"] = term
    r.update(over)
    return r


def sheet_text(path):
    wb = openpyxl.load_workbook(path)
    return {ws.title: [[c.value for c in r] for r in ws.iter_rows()] for ws in wb.worksheets}


class ResolveTermsTest(unittest.TestCase):
    def test_absent_column_means_no_terms(self):
        self.assertEqual(builder.resolve_terms([row(1, "A", "n1"), row(2, "B", "n2")], "Year"), ({}, {}))

    def test_column_blank_on_every_plan_means_no_terms(self):
        rows = [row(1, "A", "n1", term=""), row(2, "B", "n2", term="  ")]
        self.assertEqual(builder.resolve_terms(rows, "Year"), ({}, {}))

    def test_all_assigned_derives_order_from_first_seen_file_order(self):
        rows = [row(1, "A", "n1", "Second Semester"), row(2, "B", "n2", "First Semester"),
                row(3, "C", "n3", "Second Semester"), row(3, "C", "n4", "Second Semester")]
        plan_terms, order = builder.resolve_terms(rows, "Year")
        self.assertEqual(plan_terms, {1: "Second Semester", 2: "First Semester", 3: "Second Semester"})
        self.assertEqual(order, {"Second Semester": 1, "First Semester": 2})

    def test_partial_assignment_is_refused_naming_the_study_plan_and_unassigned_plans(self):
        rows = [row(1, "Programming I", "n1", "First Semester"), row(2, "Algorithms", "n2", ""),
                row(3, "Discrete Math", "n3", "")]
        with self.assertRaises(builder.TermValidationError) as ctx:
            builder.resolve_terms(rows, "BSCS Year 1")
        message = str(ctx.exception)
        self.assertIn("BSCS Year 1", message)
        self.assertIn("1 of 3", message)
        self.assertIn("plan 2 ('Algorithms')", message)
        self.assertIn("plan 3 ('Discrete Math')", message)
        self.assertNotIn("Programming I", message.split("Unassigned:")[1])

    def test_inconsistent_term_within_one_plan_is_refused(self):
        rows = [row(1, "A", "n1", "First Semester"), row(1, "A", "n2", "Second Semester")]
        with self.assertRaisesRegex(builder.TermValidationError, "constant per plan_no.*plan 1"):
            builder.resolve_terms(rows, "Year")

    def test_a_blank_row_inside_a_termed_plan_is_refused_as_inconsistent(self):
        rows = [row(1, "A", "n1", "First Semester"), row(1, "A", "n2", "")]
        with self.assertRaisesRegex(builder.TermValidationError, "constant per plan_no"):
            builder.resolve_terms(rows, "Year")

    def test_label_over_sixty_characters_is_refused_but_sixty_is_allowed(self):
        builder.resolve_terms([row(1, "A", "n1", "x" * 60)], "Year")
        with self.assertRaisesRegex(builder.TermValidationError, "61 characters"):
            builder.resolve_terms([row(1, "A", "n1", "x" * 61)], "Year")

    def test_length_counts_utf16_units_like_the_backend_so_emoji_are_not_undercounted(self):
        builder.resolve_terms([row(1, "A", "n1", "\U0001F600" * 30)], "Year")           # 60 UTF-16 units
        with self.assertRaisesRegex(builder.TermValidationError, "62 characters"):
            builder.resolve_terms([row(1, "A", "n1", "\U0001F600" * 31)], "Year")     # 31 code points, 62 units

    def test_reserved_term_not_specified_is_refused_in_any_case_and_spacing(self):
        with self.assertRaisesRegex(builder.TermValidationError, "reserved"):
            builder.resolve_terms([row(1, "A", "n1", " TERM  not specified ")], "Year")

    def test_spellings_that_differ_only_in_case_or_spacing_are_refused(self):
        rows = [row(1, "A", "n1", "First Semester"), row(2, "B", "n2", "first  semester")]
        with self.assertRaisesRegex(builder.TermValidationError, "differ only in case or spacing"):
            builder.resolve_terms(rows, "Year")


class BuildOutputTest(unittest.TestCase):
    def build(self, rows):
        out = os.path.join(tempfile.mkdtemp(), "out.xlsx")
        builder.build(rows, out, "BSCS Year 1", "desc")
        return sheet_text(out)

    def test_no_terms_leaves_no_term_ui_in_the_workbook(self):
        sheets = self.build([row(1, "A", "n1"), row(2, "B", "n2")])
        flat = repr(sheets)
        self.assertNotIn("Academic Term", flat)
        self.assertEqual(len(sheets["Overview"][5]), 9)          # header row keeps its 9 columns
        self.assertIsNone(sheets["1 A"][2][0])                   # row 3 of a plan sheet stays empty

    def test_terms_add_an_overview_column_and_a_plan_sheet_line(self):
        rows = [row(1, "A", "n1", "First Semester"), row(2, "B", "n2", "Second Semester"),
                row(3, "C", "n3", "First Semester")]
        sheets = self.build(rows)
        overview = sheets["Overview"]
        self.assertEqual(overview[5][9], "Academic Term (order)")
        self.assertEqual([overview[6][9], overview[7][9], overview[8][9]],
                         ["First Semester (1)", "Second Semester (2)", "First Semester (1)"])
        self.assertIn("First Semester (order 1", sheets["1 A"][2][0])
        self.assertIn("Second Semester (order 2", sheets["2 B"][2][0])

    def test_a_refused_file_writes_no_workbook_and_main_exits_with_the_message(self):
        d = tempfile.mkdtemp()
        tsv = os.path.join(d, "plan.tsv")
        out = os.path.join(d, "out.xlsx")
        rows = [row(1, "A", "n1", "First Semester"), row(2, "B", "n2", "")]
        with open(tsv, "w", encoding="utf-8") as f:
            f.write("\t".join(BASE + ["academic_term"]) + "\n")
            for r in rows:
                f.write("\t".join(r.get(c, "") for c in BASE + ["academic_term"]) + "\n")
        proc = subprocess.run([sys.executable, BUILDER, tsv, out, "BSCS Year 1", "d"],
                              capture_output=True, text=True)
        self.assertNotEqual(proc.returncode, 0)
        self.assertIn("academic_term is invalid", proc.stderr)
        self.assertIn("plan 2 ('B')", proc.stderr)
        self.assertFalse(os.path.exists(out))


if __name__ == "__main__":
    unittest.main()
