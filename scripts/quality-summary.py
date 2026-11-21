#!/usr/bin/env python3
"""Summarize PMD, CPD and SpotBugs reports as Markdown.

Run first: mvn -Pquality compile pmd:pmd pmd:cpd spotbugs:spotbugs
Usage: python3 scripts/quality-summary.py
Report only: never fails the build.
"""
import os
import sys
import xml.etree.ElementTree as ET
from collections import Counter

PMD = "target/pmd.xml"
CPD = "target/cpd.xml"
SPOTBUGS = "target/spotbugsXml.xml"


def load(path):
    if not os.path.exists(path):
        return None
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return None
    for el in root.iter():  # drop XML namespaces
        if "}" in el.tag:
            el.tag = el.tag.split("}", 1)[1]
    return root


def short(path):
    path = path.replace("\\", "/")
    marker = "com/hrmanager/"
    return path.split(marker, 1)[1] if marker in path else os.path.basename(path)


def table(title, header, rows):
    out = ["", title, "", "| %s |" % " | ".join(header),
           "|%s|" % "|".join("---" for _ in header)]
    out += ["| %s |" % " | ".join(str(c) for c in r) for r in rows]
    return out


out = ["## Backend code quality (PMD / CPD / SpotBugs)"]

pmd = load(PMD)
if pmd is None:
    out.append("\n_PMD: no report._")
else:
    by_rule, by_file = Counter(), Counter()
    for f in pmd.iter("file"):
        for v in f.iter("violation"):
            by_rule[v.get("rule")] += 1
            by_file[short(f.get("name", ""))] += 1
    out.append("\n**PMD**: %d findings" % sum(by_rule.values()))
    if by_rule:
        out += table("Top rules:", ["Rule", "Count"], by_rule.most_common(8))
        out += table("Top files:", ["File", "Count"], by_file.most_common(8))

cpd = load(CPD)
if cpd is None:
    out.append("\n_CPD: no report._")
else:
    dups = sorted(cpd.iter("duplication"), key=lambda d: -int(d.get("lines", 0)))
    out.append("\n**CPD (duplicate code)**: %d blocks" % len(dups))
    rows = []
    for d in dups[:8]:
        where = ", ".join("%s:%s" % (short(f.get("path", "")), f.get("line"))
                          for f in d.iter("file"))
        rows.append((d.get("lines"), where))
    if rows:
        out += table("Largest:", ["Lines", "Locations"], rows)

sb = load(SPOTBUGS)
if sb is None:
    out.append("\n_SpotBugs: no report._")
else:
    bugs = list(sb.iter("BugInstance"))
    out.append("\n**SpotBugs**: %d findings" % len(bugs))
    by_type = Counter((b.get("category"), b.get("type")) for b in bugs)
    if by_type:
        out += table("Top bug types:", ["Category", "Type", "Count"],
                     [(c, t, n) for (c, t), n in by_type.most_common(8)])
    rows = []
    for b in sorted(bugs, key=lambda b: int(b.get("priority", 3)))[:8]:
        sl = b.find("SourceLine")
        loc = "%s:%s" % (short(sl.get("sourcepath", "")), sl.get("start")) if sl is not None else "-"
        rows.append((b.get("priority"), b.get("type"), loc))
    if rows:
        out += table("Highest priority (1 = high):", ["Priority", "Type", "Location"], rows)

out += ["", "Raw reports: `target/pmd.xml`, `target/cpd.xml`, `target/spotbugsXml.xml`."]
print("\n".join(out))
