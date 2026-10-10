#!/usr/bin/env python3
"""Measure the two viable sources so the choice is evidence-based, not assumed."""
import csv
import io
import json
import os
import sys

CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cache")


def measure_ecdict():
    path = os.path.join(CACHE, "ecdict.csv")
    total = 0
    cet4 = cet6 = both = 0
    c4_phon = c6_phon = 0
    c4_trans = c6_trans = 0
    c4_pos = c6_pos = 0
    tags_seen = {}
    samples = {"cet4": [], "cet6": []}
    with open(path, "r", encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            total += 1
            tags = (row.get("tag") or "").split()
            for t in tags:
                tags_seen[t] = tags_seen.get(t, 0) + 1
            is4 = "cet4" in tags
            is6 = "cet6" in tags
            if is4:
                cet4 += 1
                if (row.get("phonetic") or "").strip():
                    c4_phon += 1
                if (row.get("translation") or "").strip():
                    c4_trans += 1
                if (row.get("pos") or "").strip():
                    c4_pos += 1
                if len(samples["cet4"]) < 3:
                    samples["cet4"].append({k: row[k] for k in ("word", "phonetic", "translation", "pos", "tag")})
            if is6:
                cet6 += 1
                if (row.get("phonetic") or "").strip():
                    c6_phon += 1
                if (row.get("translation") or "").strip():
                    c6_trans += 1
                if (row.get("pos") or "").strip():
                    c6_pos += 1
                if len(samples["cet6"]) < 3:
                    samples["cet6"].append({k: row[k] for k in ("word", "phonetic", "translation", "pos", "tag")})
            if is4 and is6:
                both += 1
    print("=== ECDICT ecdict.csv")
    print(f"rows total            : {total}")
    print(f"tag cet4              : {cet4}  (phonetic {c4_phon}, translation {c4_trans}, pos {c4_pos})")
    print(f"tag cet6              : {cet6}  (phonetic {c6_phon}, translation {c6_trans}, pos {c6_pos})")
    print(f"tag cet4 AND cet6     : {both}")
    top = sorted(tags_seen.items(), key=lambda kv: -kv[1])[:14]
    print(f"top exam tags         : {top}")
    for key, rows in samples.items():
        print(f"--- sample {key}")
        for r in rows:
            print("   ", json.dumps(r, ensure_ascii=False))


def measure_kylebing(name, path):
    n = 0
    us = uk = trans = phr = sent = pos = 0
    keys = {}
    samples = []
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            n += 1
            for k in obj:
                keys[k] = keys.get(k, 0) + 1
            if (obj.get("us") or "").strip():
                us += 1
            if (obj.get("uk") or "").strip():
                uk += 1
            t = obj.get("translations") or []
            if t:
                trans += 1
                if any((x.get("type") or "").strip() for x in t):
                    pos += 1
            if obj.get("phrases"):
                phr += 1
            if obj.get("sentences"):
                sent += 1
            if len(samples) < 3:
                samples.append(obj)
    print(f"=== KyleBing {name}")
    print(f"lines          : {n}")
    print(f"has us         : {us}")
    print(f"has uk         : {uk}")
    print(f"has translations: {trans}  (with type/pos: {pos})")
    print(f"has phrases    : {phr}")
    print(f"has sentences  : {sent}")
    print(f"keys seen      : {keys}")
    for s in samples:
        print("   sample:", json.dumps({k: s[k] for k in list(s)[:6]}, ensure_ascii=False)[:500])


if __name__ == "__main__":
    measure_ecdict()
    print()
    measure_kylebing("四级 (CET4)", os.path.join(CACHE, "KyleBing-3-CET4-sentence.jsonl"))
    print()
    measure_kylebing("六级 (CET6)", os.path.join(CACHE, "KyleBing-4-CET6-sentence.jsonl"))
