#!/usr/bin/env python3
"""Validate the generated word packs with measured numbers and write the report data.

Read-only with respect to the assets: it measures, it does not repair.
"""
import csv
import json
import os
import re
import unicodedata
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CACHE = os.path.join(HERE, "cache")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "wordpacks")

SCHEMA_KEYS = {
    "id", "word", "phonetic", "phoneticUs", "phoneticUk", "partOfSpeech", "meanings",
    "exampleSentence", "exampleTranslation", "collocations", "level", "source", "license",
}
PACK_KEYS = {"version", "name", "defaultLevel", "source", "license", "words"}

ASCII_LETTER = re.compile(r"[A-Za-z]")
NON_ASCII_LETTER = re.compile(r"[^\x00-\x7f]")
FILES = {"CET4": "cet4.json", "CET6": "cet6.json"}


def load(name):
    path = os.path.join(ASSETS, name)
    with open(path, "r", encoding="utf-8") as fh:
        text = fh.read()
    return path, text, json.loads(text)


def main():
    report = {}
    raw = {}

    for level, fname in FILES.items():
        path, text, doc = load(fname)
        words = doc["words"]
        raw[level] = words
        sizes = {"bytes": os.path.getsize(path), "chars": len(text)}
        pack_keys_ok = set(doc.keys()) == PACK_KEYS

        # schema conformance
        bad_key_rows = [(i + 1, w.get("id")) for i, w in enumerate(words) if set(w.keys()) != SCHEMA_KEYS]
        missing_key_examples = []
        for i, w in enumerate(words):
            if set(w.keys()) != SCHEMA_KEYS:
                missing_key_examples.append({"index": i + 1, "missing": sorted(SCHEMA_KEYS - set(w.keys())),
                                             "extra": sorted(set(w.keys()) - SCHEMA_KEYS)})
                if len(missing_key_examples) > 5:
                    break

        # word-shape checks
        non_ascii_letter = []
        whitespace = []
        too_short = []
        too_long = []
        no_letter = []
        for w in words:
            word = w["word"]
            if NON_ASCII_LETTER.search(word):
                non_ascii_letter.append(word)
            if any(ch.isspace() for ch in word):
                whitespace.append(word)
            stripped = word.strip()
            if len(stripped) < 2:
                too_short.append(word)
            if len(word) > 32:
                too_long.append(word)
            if not any(ch.isalpha() for ch in word):
                no_letter.append(word)

        # id checks
        ids = [w["id"] for w in words]
        dup_ids = [k for k, v in Counter(ids).items() if v > 1]
        id_format_bad = [w["id"] for w in words if not re.fullmatch(rf"{level.lower()}-\d{{4,}}", w["id"])]
        id_stable = all(w["id"] == f"{level.lower()}-{i + 1:04d}" for i, w in enumerate(words))

        # in-list duplicates (case-insensitive)
        lower = [w["word"].lower() for w in words]
        lower_counts = Counter(lower)
        dup_lower = sorted([k for k, v in lower_counts.items() if v > 1])

        # exact duplicates too
        exact_counts = Counter(w["word"] for w in words)
        dup_exact = sorted([k for k, v in exact_counts.items() if v > 1])

        # empty-field coverage
        n = len(words)
        empties = {}
        for field in ("phonetic", "phoneticUs", "phoneticUk", "partOfSpeech", "exampleSentence",
                      "exampleTranslation"):
            c = sum(1 for w in words if not w[field])
            empties[field] = {"empty": c, "present": n - c, "emptyPct": round(c * 100.0 / n, 2)}
        c = sum(1 for w in words if not w["meanings"])
        empties["meanings"] = {"empty": c, "present": n - c, "emptyPct": round(c * 100.0 / n, 2)}
        c = sum(1 for w in words if not w["collocations"])
        empties["collocations"] = {"empty": c, "present": n - c, "emptyPct": round(c * 100.0 / n, 2)}
        levels_ok = all(w["level"] == level for w in words)

        report[level] = {
            "file": fname,
            "sizeBytes": sizes["bytes"],
            "sizeKiB": round(sizes["bytes"] / 1024.0, 1),
            "sizeMiB": round(sizes["bytes"] / (1024.0 * 1024.0), 3),
            "packKeysOk": pack_keys_ok,
            "declaredLevel": doc["defaultLevel"],
            "allRowsLevelMatch": levels_ok,
            "count": n,
            "badKeyRows": len(bad_key_rows),
            "missingKeyExamples": missing_key_examples,
            "nonAsciiLetter": {"count": len(non_ascii_letter), "sample": non_ascii_letter[:10]},
            "whitespace": {"count": len(whitespace), "sample": whitespace[:10]},
            "tooShort": {"count": len(too_short), "sample": too_short[:10]},
            "tooLong": {"count": len(too_long), "sample": too_long[:10]},
            "noLetter": {"count": len(no_letter), "sample": no_letter[:10]},
            "duplicateIds": dup_ids,
            "idFormatBad": len(id_format_bad),
            "idStableSequential": id_stable,
            "duplicateLowerInList": {"count": len(dup_lower), "words": dup_lower},
            "duplicateExactInList": {"count": len(dup_exact), "words": dup_exact},
            "empties": empties,
            "maxMeanings": max(len(w["meanings"]) for w in words),
            "meaningsTotal": sum(len(w["meanings"]) for w in words),
        }

    # cross-list overlap
    set4 = {w["word"].lower() for w in raw["CET4"]}
    set6 = {w["word"].lower() for w in raw["CET6"]}
    overlap = sorted(set4 & set6)
    report["overlap"] = {
        "count": len(overlap),
        "first20": overlap[:20],
        "onlyCet4": len(set4 - set6),
        "onlyCet6": len(set6 - set4),
        "union": len(set4 | set6),
    }

    # completeness proof: every source row carrying the tag is present in the pack
    source = {}
    tag_rows = {"cet4": [], "cet6": []}
    with open(os.path.join(CACHE, "ecdict.csv"), encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            tags = (row.get("tag") or "").split()
            for level_tag, level in (("cet4", "CET4"), ("cet6", "CET6")):
                if level_tag in tags:
                    tag_rows[level_tag].append(row["word"])
    for level, level_tag in (("CET4", "cet4"), ("CET6", "cet6")):
        src_words = tag_rows[level_tag]
        src_lower = {w.lower() for w in src_words}
        pack_lower = {w["word"].lower() for w in raw[level]}
        source[level] = {
            "sourceRowsWithTag": len(src_words),
            "sourceDistinctLowercase": len(src_lower),
            "packWords": len(raw[level]),
            "sourceNotInPack": sorted(src_lower - pack_lower),
            "packNotInSource": sorted(pack_lower - src_lower),
        }
    report["completeness"] = source

    with open(os.path.join(HERE, "validation_report.json"), "w", encoding="utf-8") as fh:
        json.dump(report, fh, ensure_ascii=False, indent=2)

    # console summary
    for level in ("CET4", "CET6"):
        r = report[level]
        print(f"== {level} {r['file']}: {r['count']} words, {r['sizeBytes']} bytes, "
              f"keys_ok={r['packKeysOk']}, bad_rows={r['badKeyRows']}, level_match={r['allRowsLevelMatch']}")
        print(f"   dup ids={len(r['duplicateIds'])} id_format_bad={r['idFormatBad']} id_stable={r['idStableSequential']}")
        print(f"   dup in list lower={r['duplicateLowerInList']['count']} exact={r['duplicateExactInList']['count']}")
        print(f"   non-ascii-letter={r['nonAsciiLetter']['count']} whitespace={r['whitespace']['count']} "
              f"short={r['tooShort']['count']} long={r['tooLong']['count']} no-letter={r['noLetter']['count']}")
        print(f"   empties: " + ", ".join(
            f"{k}={v['empty']}({v['emptyPct']}%)" for k, v in r["empties"].items()))
    print(f"== overlap: {report['overlap']['count']} (only CET4 {report['overlap']['onlyCet4']}, "
          f"only CET6 {report['overlap']['onlyCet6']}, union {report['overlap']['union']})")
    for level in ("CET4", "CET6"):
        s = report["completeness"][level]
        print(f"== completeness {level}: source rows {s['sourceRowsWithTag']}, distinct {s['sourceDistinctLowercase']}, "
              f"pack {s['packWords']}, missing {len(s['sourceNotInPack'])}, extra {len(s['packNotInSource'])}")
    total = sum(report[l]["sizeBytes"] for l in ("CET4", "CET6")) + os.path.getsize(
        os.path.join(ASSETS, "PACK_INFO.json"))
    print(f"== total asset bytes: {total} ({round(total / 1024.0 / 1024.0, 3)} MiB)")


if __name__ == "__main__":
    main()
