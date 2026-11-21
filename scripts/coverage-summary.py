#!/usr/bin/env python3
"""Summarize JaCoCo coverage as Markdown.

Usage: python3 scripts/coverage-summary.py [jacoco.csv]
CI appends the output to the job summary; locally it prints to stdout.
Report only: never fails the build.
"""
import csv
import os
import sys
from collections import OrderedDict

path = sys.argv[1] if len(sys.argv) > 1 else "target/site/jacoco/jacoco.csv"
if not os.path.exists(path):
    print("## Backend coverage\n\n_No JaCoCo report found (%s)._" % path)
    sys.exit(0)

with open(path, newline="", encoding="utf-8") as f:
    rows = list(csv.DictReader(f))


def ints(r, k):
    return int(r[k])


def pct(covered, missed):
    total = covered + missed
    return "%5.1f%%" % (100.0 * covered / total) if total else "    - "


def agg(rs):
    return {k: sum(ints(r, k) for r in rs) for k in
            ("LINE_COVERED", "LINE_MISSED", "BRANCH_COVERED", "BRANCH_MISSED")}


def line(label, a):
    return "| %s | %s | %s | %d |" % (
        label,
        pct(a["LINE_COVERED"], a["LINE_MISSED"]),
        pct(a["BRANCH_COVERED"], a["BRANCH_MISSED"]),
        a["LINE_MISSED"])


def module(r):
    parts = r["PACKAGE"].split(".")
    return parts[2] if len(parts) > 2 else "(root)"


modules = OrderedDict()
for r in rows:
    modules.setdefault(module(r), []).append(r)

out = ["## Backend coverage (JaCoCo)", "",
       "| Scope | Line | Branch | Missed lines |", "|---|---|---|---|",
       line("**Total**", agg(rows))]
for name in sorted(modules):
    out.append(line(name, agg(modules[name])))

worst = sorted((r for r in rows if ints(r, "LINE_MISSED") > 0),
               key=lambda r: -ints(r, "LINE_MISSED"))[:8]
out += ["", "Most missed lines:", "",
        "| Class | Line | Branch | Missed lines |", "|---|---|---|---|"]
for r in worst:
    out.append(line("%s.%s" % (module(r), r["CLASS"]), agg([r])))
out += ["", "Full HTML report: download the `jacoco-report` artifact of this run, "
        "or open `target/site/jacoco/index.html` locally."]
print("\n".join(out))
