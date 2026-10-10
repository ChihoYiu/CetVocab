#!/usr/bin/env python3
"""Build the in-app CET-4 / CET-6 word packs from ECDICT (MIT).

Source : skywind3000/ECDICT, ecdict.csv @ bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b
Licence: MIT (see tools/wordpack/cache/ECDICT-LICENSE)

Every output field is derived mechanically from the source row. Nothing is authored,
translated or rewritten here: if the source has no value for a field the output field is
left empty.
"""
import csv
import json
import os
import re
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CACHE = os.path.join(HERE, "cache")
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "wordpacks")

COMMIT = "bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b"
SOURCE_REPO = "https://github.com/skywind3000/ECDICT"
LICENCE_ID = "MIT"
LICENCE_URL = f"https://raw.githubusercontent.com/skywind3000/ECDICT/{COMMIT}/LICENSE"
DOWNLOADED_AT = "2026-10-10T02:58:00Z"

PACKS = [
    {
        "level": "CET4",
        "tag": "cet4",
        "id_prefix": "cet4",
        "file": "cet4.json",
        "display": "CET-4 词汇（来源：ECDICT）",
        "source": f"skywind3000/ECDICT ecdict.csv (tag=cet4) @ {COMMIT[:7]}",
        "source_url": f"https://raw.githubusercontent.com/skywind3000/ECDICT/{COMMIT}/ecdict.csv",
    },
    {
        "level": "CET6",
        "tag": "cet6",
        "id_prefix": "cet6",
        "file": "cet6.json",
        "display": "CET-6 词汇（来源：ECDICT）",
        "source": f"skywind3000/ECDICT ecdict.csv (tag=cet6) @ {COMMIT[:7]}",
        "source_url": f"https://raw.githubusercontent.com/skywind3000/ECDICT/{COMMIT}/ecdict.csv",
    },
]

# Leading part-of-speech marker, e.g. "vt. ", "n. ", "a. ".
POS_RE = re.compile(r"^([A-Za-z]{1,6})\.\s*(.+)$")

# Canonical spelling for ECDICT's marker vocabulary. Markers are kept verbatim when they
# already carry a trailing dot, otherwise a dot is appended so the UI renders "v." not "v".
POS_CANON = {
    "n": "n.",
    "v": "v.",
    "vt": "vt.",
    "vi": "vi.",
    "a": "a.",
    "adj": "adj.",
    "adv": "adv.",
    "prep": "prep.",
    "pron": "pron.",
    "conj": "conj.",
    "num": "num.",
    "art": "art.",
    "int": "int.",
    "interj": "interj.",
    "aux": "aux.",
    "abbr": "abbr.",
    "pl": "pl.",
    "u": "u.",
    "c": "c.",
}


def strip_control(text: str) -> str:
    """Drop control characters that would corrupt the asset, keep the visible text."""
    return "".join(ch for ch in text if ch == "\t" or unicodedata.category(ch) not in ("Cc", "Cf", "Cs", "Co", "Cn"))


def split_meanings(translation: str):
    """Split an ECDICT `translation` cell into ordered meaning entries.

    ECDICT separates meaning groups with a newline. A few rows contain a literal backslash-n
    instead of a real newline, so both spellings are treated as separators.
    """
    text = translation.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\r", "\n")
    out = []
    for chunk in text.split("\n"):
        chunk = chunk.strip()
        if not chunk:
            continue
        out.append(chunk)
    return out


def parse_meaning(chunk: str):
    """Return (part_of_speech, meaning_text) for one meaning group.

    The POS marker is removed from the meaning text only when it is the single marker for the
    whole entry; that decision is made by the caller, which passes `keep_marker`.
    """
    m = POS_RE.match(chunk)
    if not m:
        return None, chunk
    return m.group(1).lower(), m.group(2).strip()


def build_entry_list(pack):
    """Read ECDICT once and return the raw rows carrying the requested exam tag."""
    rows = []
    path = os.path.join(CACHE, "ecdict.csv")
    with open(path, "r", encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            tags = (row.get("tag") or "").split()
            if pack["tag"] not in tags:
                continue
            rows.append(row)
    return rows


def rows_to_words(pack, rows, stats):
    """Convert raw ECDICT rows into schema words, resolving case-insensitive duplicates."""
    # key -> (word_obj, richness)
    chosen = {}
    order = []
    for row in rows:
        raw_word = strip_control((row.get("word") or "").strip())
        if not raw_word:
            stats["dropped_empty_word"] += 1
            continue

        translation = row.get("translation") or ""
        chunks = split_meanings(translation)
        if not chunks:
            stats["dropped_empty_meanings"] += 1
            continue

        # Resolve the part of speech: only when every group agrees on one marker, and only
        # then is the marker stripped out of the meaning text.
        markers = []
        parsed = []
        for chunk in chunks:
            marker, text = parse_meaning(chunk)
            if marker:
                markers.append(marker)
            parsed.append((marker, text))
        distinct = sorted(set(markers))
        single_marker = distinct[0] if len(distinct) == 1 else None

        meanings = []
        for marker, text in parsed:
            if single_marker is not None:
                text = text.strip()
            else:
                # Mixed markers: keep the group verbatim so no content is lost or guessed.
                text = (f"{marker}. {text}" if marker else text).strip()
            text = strip_control(text)
            if text:
                meanings.append(text)
        if not meanings:
            stats["dropped_empty_meanings"] += 1
            continue

        phonetic_raw = strip_control((row.get("phonetic") or "").strip())
        phonetic = phonetic_raw
        if phonetic:
            stats["phonetic_present"] += 1
            if not (phonetic.startswith("/") and phonetic.endswith("/")):
                phonetic = "/" + phonetic.strip("/") + "/"
                stats["phonetic_slashes_added"] += 1

        pos_value = POS_CANON.get(single_marker, "") if single_marker else ""
        if pos_value:
            stats["pos_present"] += 1

        word_obj = {
            "word": raw_word,
            "phonetic": phonetic,
            "phoneticUs": "",
            "phoneticUk": "",
            "partOfSpeech": pos_value,
            "meanings": meanings,
            "exampleSentence": "",
            "exampleTranslation": "",
            "collocations": [],
            "level": pack["level"],
            "source": pack["source"],
            "license": LICENCE_ID,
        }

        richness = (3 if phonetic else 0) + len(meanings) + (1 if pos_value else 0)
        key = raw_word.lower()
        if key in chosen:
            stats["duplicates_in_list"] += 1
            stats["duplicate_words"].append(raw_word)
            if richness > chosen[key][1]:
                chosen[key] = (word_obj, richness)
                stats["duplicates_replaced"] += 1
        else:
            chosen[key] = (word_obj, richness)
            order.append(key)

    words = []
    for index, key in enumerate(order, start=1):
        obj, _ = chosen[key]
        entry = {"id": f"{pack['id_prefix']}-{index:04d}"}
        entry.update(obj)
        words.append(entry)
    return words


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    coverage = {}
    word_counts = {}
    phon_us = False
    phon_uk = False

    for pack in PACKS:
        stats = {
            "dropped_empty_word": 0,
            "dropped_empty_meanings": 0,
            "duplicates_in_list": 0,
            "duplicates_replaced": 0,
            "duplicate_words": [],
            "phonetic_present": 0,
            "phonetic_slashes_added": 0,
            "pos_present": 0,
        }
        rows = build_entry_list(pack)
        words = rows_to_words(pack, rows, stats)
        pack_obj = {
            "version": 2,
            "name": pack["display"],
            "defaultLevel": pack["level"],
            "source": pack["source"],
            "license": LICENCE_ID,
            "words": words,
        }
        dest = os.path.join(OUT_DIR, pack["file"])
        with open(dest, "w", encoding="utf-8", newline="") as fh:
            json.dump(pack_obj, fh, ensure_ascii=False, separators=(",", ":"))
        size = os.path.getsize(dest)
        total = len(words)
        word_counts[pack["level"]] = total
        coverage[pack["level"]] = {
            "sourceRows": len(rows),
            "words": total,
            "phonetic": sum(1 for w in words if w["phonetic"]),
            "phoneticUs": sum(1 for w in words if w["phoneticUs"]),
            "phoneticUk": sum(1 for w in words if w["phoneticUk"]),
            "partOfSpeech": sum(1 for w in words if w["partOfSpeech"]),
            "meanings": sum(1 for w in words if w["meanings"]),
            "exampleSentence": sum(1 for w in words if w["exampleSentence"]),
            "exampleTranslation": sum(1 for w in words if w["exampleTranslation"]),
            "collocations": sum(1 for w in words if w["collocations"]),
        }
        print(
            f"{pack['file']}: words={total} bytes={size} "
            f"phonetic={coverage[pack['level']]['phonetic']} pos={coverage[pack['level']]['partOfSpeech']} "
            f"dupes_in_list={stats['duplicates_in_list']} replaced={stats['duplicates_replaced']} "
            f"dropped_word={stats['dropped_empty_word']} dropped_meanings={stats['dropped_empty_meanings']} "
            f"slashes_added={stats['phonetic_slashes_added']}"
        )
        if stats["duplicate_words"]:
            print(f"   duplicate words: {sorted(set(stats['duplicate_words']))}")
        coverage[pack["level"]]["duplicatesInList"] = stats["duplicates_in_list"]
        coverage[pack["level"]]["droppedEmptyWord"] = stats["dropped_empty_word"]
        coverage[pack["level"]]["droppedEmptyMeanings"] = stats["dropped_empty_meanings"]

    info = {
        "version": 2,
        "sourceRepo": SOURCE_REPO,
        "sourceCommit": COMMIT,
        "sourceUrls": {p["level"]: p["source_url"] for p in PACKS},
        "sourceFiles": {p["level"]: "ecdict.csv" for p in PACKS},
        "licence": LICENCE_ID,
        "licenceUrl": LICENCE_URL,
        "licenceName": "MIT License",
        "licenceCopyright": "Copyright (c) 2025 Linwei",
        "downloadedAt": DOWNLOADED_AT,
        "generatedAt": DOWNLOADED_AT,
        "transformNotes": (
            "Rows of ECDICT ecdict.csv whose 'tag' column contains cet4 / cet6 respectively; one word "
            "pack per level. 'meanings' comes from splitting the source 'translation' cell on newlines "
            "(a literal backslash-n in a few rows is treated as a newline too). A leading "
            "part-of-speech marker (n. / v. / vt. / vi. / a. / adv. ...) is removed from the meaning "
            "text and recorded in 'partOfSpeech' only when the whole entry uses exactly one distinct "
            "marker; entries with mixed markers keep their text verbatim, marker included. 'phonetic' "
            "is the source 'phonetic' column with one leading and trailing slash added when absent; "
            "ECDICT carries a single transcription, so phoneticUs and phoneticUk are empty strings and "
            "'phonetic' holds that single value. ECDICT has no example sentences, no collocations and "
            "no separate US/UK phonetics, so those fields are empty rather than invented. Rows with an "
            "empty word or an empty translation were dropped; case-insensitive duplicates inside one "
            "list kept the richest row. 'id' is level + 1-based index in the emitted order."
        ),
        "wordCounts": word_counts,
        "fieldCoverage": coverage,
        "phoneticUsAvailable": phon_us,
        "phoneticUkAvailable": phon_uk,
    }
    info_path = os.path.join(OUT_DIR, "PACK_INFO.json")
    with open(info_path, "w", encoding="utf-8", newline="") as fh:
        json.dump(info, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("PACK_INFO.json written:", os.path.getsize(info_path), "bytes")


if __name__ == "__main__":
    sys.exit(main())
