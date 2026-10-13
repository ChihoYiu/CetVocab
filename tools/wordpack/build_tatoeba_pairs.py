#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build an offline English-Chinese example-sentence corpus from Tatoeba exports.

Inputs (raw downloads, unmodified) live in tools/wordpack/cache/tatoeba/:
    eng_sentences.tsv.bz2    id <TAB> eng <TAB> text
    cmn_sentences.tsv.bz2    id <TAB> cmn <TAB> text
    cmn-eng_links.tsv.bz2    cmn_id <TAB> eng_id

Output:
    tools/wordpack/cache/tatoeba/cmn-eng-pairs.tsv
    header: eng_id <TAB> cmn_id <TAB> english <TAB> chinese

Memory discipline: every input is streamed line by line with bz2.open(); nothing
larger than the retained id/text maps is ever held in memory.

Two passes over the compressed data:
  pass 1 - read the link file, keep only the (cmn_id, eng_id) pairs whose ids we
           will actually be able to look up later.
  pass 2 - stream the two sentence files, keeping only those ids, then join.

Selection rules (see build_pairs()):
  * English text: 15-200 chars after strip, no TAB / CR / LF, >= 3 ASCII letters,
    no CJK character (U+4E00-U+9FFF).
  * Chinese text: >= 1 CJK char (U+4E00-U+9FFF), no ASCII letter run of 3+.
  * One pair per English sentence: if an English sentence links to several usable
    Chinese sentences, keep the shortest Chinese one with length 4-80 chars.
  * Sentence text is never rewritten - only surrounding whitespace is stripped.
"""

from __future__ import annotations

import bz2
import os
import sys
from collections import defaultdict

CACHE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cache", "tatoeba")
ENG_BZ2 = os.path.join(CACHE_DIR, "eng_sentences.tsv.bz2")
CMN_BZ2 = os.path.join(CACHE_DIR, "cmn_sentences.tsv.bz2")
LINKS_BZ2 = os.path.join(CACHE_DIR, "cmn-eng_links.tsv.bz2")
OUT_TSV = os.path.join(CACHE_DIR, "cmn-eng-pairs.tsv")

CJK_LO, CJK_HI = 0x4E00, 0x9FFF
LETTERS = frozenset("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")


def has_cjk(s: str) -> bool:
    for ch in s:
        if CJK_LO <= ord(ch) <= CJK_HI:
            return True
    return False


def ascii_letter_runs(s: str, n: int) -> bool:
    """True if s contains a run of >= n consecutive ASCII letters."""
    run = 0
    for ch in s:
        if ch in LETTERS:
            run += 1
            if run >= n:
                return True
        else:
            run = 0
    return False


def count_ascii_letters(s: str) -> int:
    return sum(1 for ch in s if ch in LETTERS)


def eng_ok(text: str):
    """Return cleaned English text if usable, else None."""
    t = text.strip()
    if not (15 <= len(t) <= 200):
        return None
    if "\t" in t or "\n" in t or "\r" in t:
        return None
    if count_ascii_letters(t) < 3:
        return None
    if has_cjk(t):
        return None
    return t


def cmn_ok(text: str):
    """Return cleaned Chinese text if usable, else None."""
    t = text.strip()
    if not has_cjk(t):
        return None
    if ascii_letter_runs(t, 3):
        return None
    return t


def split_sentence_line(line: str):
    """'id<TAB>lang<TAB>text' -> (id, text) or None. Text may contain TABs."""
    parts = line.rstrip("\n").rstrip("\r").split("\t", 2)
    if len(parts) != 3:
        return None
    return parts[0], parts[2]


def read_links(path: str):
    """Stream the link file -> list of (cmn_id, eng_id).

    Handles both column orders by sniffing the first line against a heuristic:
    the exports are 'cmn_id<TAB>eng_id', but we degrade gracefully.
    """
    pairs = []
    with bz2.open(path, "rt", encoding="utf-8", newline="") as f:
        for line in f:
            parts = line.rstrip("\n").rstrip("\r").split("\t")
            if len(parts) < 2:
                continue
            a, b = parts[0].strip(), parts[1].strip()
            if not a.isdigit() or not b.isdigit():
                continue
            pairs.append((a, b))
    return pairs


def collect_sentences(path: str, wanted: set):
    """Stream a sentence file; keep id -> text for ids in `wanted`."""
    kept = {}
    with bz2.open(path, "rt", encoding="utf-8", newline="") as f:
        for line in f:
            row = split_sentence_line(line)
            if row is None:
                continue
            sid, text = row
            if sid in wanted:
                kept[sid] = text
    return kept


def build_pairs():
    print("pass 1: reading links ...", flush=True)
    links = read_links(LINKS_BZ2)
    print("  raw links: %d" % len(links), flush=True)

    # cmn_id -> [eng_id, ...] ; one English sentence may link to many Chinese ones.
    by_cmn = defaultdict(list)
    eng_ids = set()
    for cmn_id, eng_id in links:
        by_cmn[cmn_id].append(eng_id)
        eng_ids.add(eng_id)
    cmn_ids = set(by_cmn)
    print("  distinct cmn ids: %d, distinct eng ids: %d" % (len(cmn_ids), len(eng_ids)), flush=True)

    print("pass 2: streaming sentence files ...", flush=True)
    eng_raw = collect_sentences(ENG_BZ2, eng_ids)
    print("  eng sentences present: %d" % len(eng_raw), flush=True)
    cmn_raw = collect_sentences(CMN_BZ2, cmn_ids)
    print("  cmn sentences present: %d" % len(cmn_raw), flush=True)

    eng_txt = {}
    for sid, raw in eng_raw.items():
        t = eng_ok(raw)
        if t is not None:
            eng_txt[sid] = t
    cmn_txt = {}
    for sid, raw in cmn_raw.items():
        t = cmn_ok(raw)
        if t is not None:
            cmn_txt[sid] = t
    print("  eng after rules: %d, cmn after rules: %d" % (len(eng_txt), len(cmn_txt)), flush=True)

    # Join: one pair per English sentence, prefer the shortest Chinese in 4..80.
    best = {}  # eng_id -> (cmn_id, eng_text, cmn_text, length)
    for cmn_id, cmn_text in cmn_txt.items():
        n = len(cmn_text)
        for eng_id in by_cmn.get(cmn_id, ()):  # links may repeat; harmless
            eng_text = eng_txt.get(eng_id)
            if eng_text is None:
                continue
            if not (4 <= n <= 80):
                # outside the "shortest reasonable" window: only usable as a
                # last resort, tracked separately so a strict pair still wins.
                cand = (cmn_id, eng_text, cmn_text, 10 ** 9 + n)
            else:
                cand = (cmn_id, eng_text, cmn_text, n)
            cur = best.get(eng_id)
            if cur is None or cand[3] < cur[3] or (cand[3] == cur[3] and cand[0] < cur[0]):
                best[eng_id] = cand

    rows = []
    for eng_id in sorted(best, key=lambda x: int(x)):
        cmn_id, eng_text, cmn_text, _ = best[eng_id]
        rows.append((eng_id, cmn_id, eng_text, cmn_text))

    tmp = OUT_TSV + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as out:
        out.write("eng_id\tcmn_id\tenglish\tchinese\n")
        for eng_id, cmn_id, eng_text, cmn_text in rows:
            out.write("%s\t%s\t%s\t%s\n" % (eng_id, cmn_id, eng_text, cmn_text))
    os.replace(tmp, OUT_TSV)
    return len(rows)


def main():
    for p in (ENG_BZ2, CMN_BZ2, LINKS_BZ2):
        if not os.path.exists(p):
            print("missing input: %s" % p, file=sys.stderr)
            return 2
    n = build_pairs()
    print("pair count: %d" % n)
    print("written: %s" % OUT_TSV)
    return 0


if __name__ == "__main__":
    sys.exit(main())
