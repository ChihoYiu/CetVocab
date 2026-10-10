#!/usr/bin/env python3
"""Check how reliably a leading part-of-speech marker can be extracted from ECDICT."""
import collections
import csv
import os
import re

CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cache")

POS_RE = re.compile(r"^([A-Za-z]{1,6})\.\s*(.+)$")


def main():
    markers = collections.Counter()
    unmarked = []
    multi_pos = 0
    single_pos = 0
    for level_tag in ("cet4", "cet6"):
        per_entry_markers = collections.Counter()
        n = 0
        with open(os.path.join(CACHE, "ecdict.csv"), encoding="utf-8", newline="") as fh:
            for row in csv.DictReader(fh):
                tags = (row.get("tag") or "").split()
                if level_tag not in tags:
                    continue
                n += 1
                found = set()
                for line in (row.get("translation") or "").split("\n"):
                    line = line.strip()
                    if not line:
                        continue
                    m = POS_RE.match(line)
                    if m:
                        tag = m.group(1).lower()
                        markers[tag] += 1
                        found.add(tag)
                    else:
                        markers["<unmarked>"] += 1
                        if len(unmarked) < 12:
                            unmarked.append(line[:70])
                per_entry_markers[len(found)] += 1
        print(f"[{level_tag}] entries={n} distinct-marker-count histogram={dict(sorted(per_entry_markers.items()))}")
    print("marker frequency:", markers.most_common(40))
    print("unmarked line samples:")
    for s in unmarked:
        print("   ", s)


if __name__ == "__main__":
    main()
