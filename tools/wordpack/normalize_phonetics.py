"""Normalise the notation of the phonetic strings in the shipped word packs.

Why this exists
---------------
ECDICT stores its phonetics in the "Kingsoft" ASCII style. Almost all of it is already genuine
IPA, but two *Cyrillic* letters that merely look like IPA letters are used in their place, and the
ASCII apostrophe/colon stand in for the IPA stress and length marks:

    U+04D9 CYRILLIC SMALL LETTER SCHWA          -> U+0259 LATIN SMALL LETTER SCHWA      (6171x)
    U+0454 CYRILLIC SMALL LETTER UKRAINIAN IE   -> U+025B LATIN SMALL LETTER OPEN E     (132x)
    U+0027 APOSTROPHE (primary stress)          -> U+02C8 MODIFIER LETTER VERTICAL LINE
    U+003A COLON (length mark)                  -> U+02D0 MODIFIER LETTER TRIANGULAR COLON

This is a pure transliteration of the source's notation: it invents no pronunciation, adds no
data and removes none. Every other IPA character in the data (æ, ʃ, ʌ, ʒ, ɑ, ŋ, θ, ð, ɔ, ɪ, ɡ, ʊ,
ˌ, …) is left exactly as the source has it.

The comma that separates alternative pronunciations (e.g. "/'kɒndʌkt, -dəkt/") is deliberately
NOT touched: unlike the apostrophe it is a list separator, not a stress mark.

Usage:  python tools/wordpack/normalize_phonetics.py
"""

import io
import json
import os
import sys

ASSET_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))),
    "app", "src", "main", "assets", "wordpacks",
)
PACKS = ("cet4.json", "cet6.json")

# Cyrillic look-alikes and ASCII stand-ins -> the IPA characters they were meant to be.
REPLACEMENTS = {
    "\u04d9": "\u0259",  # ә -> ə
    "\u0454": "\u025b",  # є -> ɛ
    "'": "\u02c8",       # ' -> ˈ
    ":": "\u02d0",       # : -> ː
}
PHONETIC_FIELDS = ("phonetic", "phoneticUs", "phoneticUk")


def normalise(text):
    if not text:
        return text, {}
    counts = {}
    out = []
    for ch in text:
        replacement = REPLACEMENTS.get(ch)
        if replacement is None:
            out.append(ch)
        else:
            counts[ch] = counts.get(ch, 0) + 1
            out.append(replacement)
    return "".join(out), counts


def main():
    total_counts = {}
    changed_rows = 0
    for name in PACKS:
        path = os.path.join(ASSET_DIR, name)
        with io.open(path, encoding="utf-8") as fh:
            pack = json.load(fh)
        for row in pack.get("words", []):
            row_changed = False
            for field in PHONETIC_FIELDS:
                value = row.get(field)
                if not value:
                    continue
                new_value, counts = normalise(value)
                if new_value != value:
                    row[field] = new_value
                    row_changed = True
                    for ch, n in counts.items():
                        total_counts[ch] = total_counts.get(ch, 0) + n
            if row_changed:
                changed_rows += 1
        with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(pack, fh, ensure_ascii=False, separators=(",", ":"))
        # size report
        print("%-10s rewritten, %d bytes" % (name, os.path.getsize(path)))

    print("rows changed:", changed_rows)
    for ch, n in sorted(total_counts.items(), key=lambda item: -item[1]):
        print("  U+%04X -> U+%04X  x%d" % (ord(ch), ord(REPLACEMENTS[ch]), n))

    info_path = os.path.join(ASSET_DIR, "PACK_INFO.json")
    if os.path.exists(info_path):
        with io.open(info_path, encoding="utf-8") as fh:
            info = json.load(fh)
        info["phoneticNormalization"] = {
            "applied": True,
            "replaced": {
                "U+04D9 CYRILLIC SMALL LETTER SCHWA": "U+0259 LATIN SMALL LETTER SCHWA",
                "U+0454 CYRILLIC SMALL LETTER UKRAINIAN IE": "U+025B LATIN SMALL LETTER OPEN E",
                "U+0027 APOSTROPHE (stress)": "U+02C8 MODIFIER LETTER VERTICAL LINE",
                "U+003A COLON (length)": "U+02D0 MODIFIER LETTER TRIANGULAR COLON",
            },
            "script": "tools/wordpack/normalize_phonetics.py",
            "note": "Pure notation transliteration of the source's Kingsoft-style phonetics; no "
                    "pronunciation was added, changed or invented.",
        }
        with io.open(info_path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(info, fh, ensure_ascii=False, indent=2)
        print("PACK_INFO.json updated")

    return 0


if __name__ == "__main__":
    sys.exit(main())
