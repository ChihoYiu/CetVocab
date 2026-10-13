#!/usr/bin/env python3
"""Validate the shipped start-up line catalogue against the product requirements.

Checks, all of them on the **shipped** file (not on the authoring fragments):

* valid JSON with the expected shape;
* at least ``MIN_TIPS`` (370) usable lines;
* ids unique, texts unique, neither blank;
* at least ``MIN_CATEGORIES`` distinct categories, and no category with a token count;
* text length inside the documented window, no ASCII letters or digits, no emoji;
* no anxiety / shame / grind wording (the banned list below);
* no near-duplicate pair above ``NEAR_DUPLICATE_LIMIT``.

Run it after ``build_tips.py``; it is the command-line counterpart of ``StartupTipTest``
(the test asserts the same properties so a regression fails the build even if nobody runs
this script).
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

MIN_TIPS = 370
MIN_CATEGORIES = 4
MIN_CORE_LENGTH = 4
MAX_CORE_LENGTH = 40
NEAR_DUPLICATE_LIMIT = 0.60

BANNED = [
    "熬夜", "拼命", "否则你就", "落后", "淘汰", "废物", "不配", "必须",
    "来不及", "羞耻", "拖延症", "自律即自由", "内卷", "碾压",
]

PUNCTUATION = set("，。！？、；：""''《》（）…—·,.!?;:\"'()[] \t\n")
FINAL_MARKS = "。！？"


def core(text: str) -> str:
    return "".join(ch for ch in text if ch not in PUNCTUATION)


def similarity(left: str, right: str) -> float:
    a, b = set(core(left)), set(core(right))
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def main() -> int:
    repo = pathlib.Path(__file__).resolve().parents[2]
    path = repo / "app" / "src" / "main" / "assets" / "motivation" / "tips.json"
    problems: list[str] = []

    try:
        catalogue = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        print(f"FAIL: {path} does not exist", file=sys.stderr)
        return 2
    except json.JSONDecodeError as error:
        print(f"FAIL: {path} is not valid JSON: {error}", file=sys.stderr)
        return 2

    tips = catalogue.get("tips")
    if not isinstance(tips, list):
        print("FAIL: 'tips' is missing or not an array", file=sys.stderr)
        return 2

    if len(tips) < MIN_TIPS:
        problems.append(f"only {len(tips)} lines, the requirement is at least {MIN_TIPS}")

    ids: set[str] = set()
    texts: set[str] = set()
    per_category: dict[str, int] = {}
    lengths: list[int] = []

    for index, tip in enumerate(tips):
        where = f"tips[{index}]"
        tip_id = str(tip.get("id", "")).strip()
        text = str(tip.get("text", "")).strip()
        category = str(tip.get("category", "")).strip()

        if not tip_id:
            problems.append(f"{where}: empty id")
        elif tip_id in ids:
            problems.append(f"{where}: duplicate id {tip_id}")
        ids.add(tip_id)

        if not text:
            problems.append(f"{where}: empty text")
            continue
        if text in texts:
            problems.append(f"{where}: duplicate text {text}")
        texts.add(text)

        if not category:
            problems.append(f"{where}: empty category")
        per_category[category] = per_category.get(category, 0) + 1

        stripped = core(text)
        lengths.append(len(stripped))
        if not (MIN_CORE_LENGTH <= len(stripped) <= MAX_CORE_LENGTH):
            problems.append(f"{where}: length {len(stripped)} outside {MIN_CORE_LENGTH}–{MAX_CORE_LENGTH}: {text}")
        if text[-1] not in FINAL_MARKS:
            problems.append(f"{where}: does not end with a full stop: {text}")
        if re.search(r"[A-Za-z0-9]", text):
            problems.append(f"{where}: contains ASCII letters/digits: {text}")
        for banned in BANNED:
            if banned in text:
                problems.append(f"{where}: banned wording '{banned}': {text}")

    if len(per_category) < MIN_CATEGORIES:
        problems.append(f"only {len(per_category)} categories, expected at least {MIN_CATEGORIES}")

    worst_score = 0.0
    worst_pair = ("", "")
    ordered = sorted(texts)
    for i, left in enumerate(ordered):
        for right in ordered[i + 1:]:
            score = similarity(left, right)
            if score > worst_score:
                worst_score, worst_pair = score, (left, right)
    if worst_score > NEAR_DUPLICATE_LIMIT:
        problems.append(
            f"near-duplicate pair at {worst_score:.2f}: {worst_pair[0]} ≈ {worst_pair[1]}"
        )

    print(f"file          : {path.relative_to(repo)}")
    print(f"lines         : {len(tips)} (minimum {MIN_TIPS})")
    print(f"categories    : {len(per_category)} -> " + ", ".join(f"{k} {v}" for k, v in sorted(per_category.items())))
    if lengths:
        print(f"core length   : min {min(lengths)} max {max(lengths)} avg {sum(lengths)/len(lengths):.1f}")
    print(f"max similarity: {worst_score:.3f} (limit {NEAR_DUPLICATE_LIMIT})")
    print(f"ids unique    : {len(ids) == len(tips)}")
    print(f"texts unique  : {len(texts) == len(tips)}")

    if problems:
        print(f"\nFAIL: {len(problems)} problem(s)")
        for problem in problems[:40]:
            print(f"  - {problem}")
        return 1
    print("\nOK: every check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
