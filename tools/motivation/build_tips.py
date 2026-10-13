#!/usr/bin/env python3
"""Merge the authored start-up line fragments into the single shipped asset.

Input : ``tools/motivation/parts/*.json`` — each file a JSON array of
        ``{"category": "…", "text": "…"}`` objects (no ids).
Output: ``app/src/main/assets/motivation/tips.json`` — the catalogue the app reads,
        with a stable id per line and the category order fixed here.

Why a build step instead of hand-editing the asset:

* the shipped file must stay reproducible from the fragments, so a reviewer can diff the
  merge instead of trusting one large hand-edited JSON;
* de-duplication (exact *and* near-duplicate) and the id assignment happen in one place,
  which is what makes "no two cold starts show the same line" verifiable;
* every rejection is printed, so the build report says exactly what was dropped.

Near-duplicate rule: two lines whose punctuation-stripped character sets overlap by more
than ``NEAR_DUPLICATE_LIMIT`` are considered the same encouragement expressed twice; the
first one wins. This is the same measure ``app/src/test/.../StartupTipTest.kt`` asserts on
the shipped file.
"""

from __future__ import annotations

import json
import pathlib
import sys

NEAR_DUPLICATE_LIMIT = 0.60

CATEGORY_ORDER = [
    "坚持与习惯",
    "自律与行动",
    "时间与节奏",
    "积累与复利",
    "面对困难",
    "自信与心态",
    "成长与复盘",
    "生活与平衡",
]

PUNCTUATION = set("，。！？、；：""''《》（）…—·,.!?;:\"'()[] \t\n")


def core(text: str) -> str:
    """The text without punctuation, which is what similarity is measured on."""
    return "".join(ch for ch in text if ch not in PUNCTUATION)


def similarity(left: str, right: str) -> float:
    """Jaccard overlap of the character sets, so word order does not matter."""
    a, b = set(core(left)), set(core(right))
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def main() -> int:
    repo = pathlib.Path(__file__).resolve().parents[2]
    parts_dir = repo / "tools" / "motivation" / "parts"
    out_path = repo / "app" / "src" / "main" / "assets" / "motivation" / "tips.json"

    if not parts_dir.is_dir():
        print(f"error: {parts_dir} does not exist", file=sys.stderr)
        return 2

    entries: list[dict[str, str]] = []
    dropped_blank = 0
    dropped_exact = 0
    dropped_near: list[tuple[str, str, float]] = []

    for part in sorted(parts_dir.glob("*.json")):
        raw = json.loads(part.read_text(encoding="utf-8"))
        if not isinstance(raw, list):
            print(f"error: {part.name} is not a JSON array", file=sys.stderr)
            return 2
        for item in raw:
            text = str(item.get("text", "")).strip()
            category = str(item.get("category", "")).strip() or "未分类"
            if not text:
                dropped_blank += 1
                continue
            if any(core(existing["text"]) == core(text) for existing in entries):
                dropped_exact += 1
                continue
            worst = 0.0
            twin = ""
            for existing in entries:
                score = similarity(existing["text"], text)
                if score > worst:
                    worst, twin = score, existing["text"]
            if worst > NEAR_DUPLICATE_LIMIT:
                dropped_near.append((text, twin, worst))
                continue
            entries.append({"category": category, "text": text})

    # Stable, human-readable order: the fixed category list first, then anything unexpected.
    def category_rank(category: str) -> int:
        return CATEGORY_ORDER.index(category) if category in CATEGORY_ORDER else len(CATEGORY_ORDER)

    entries.sort(key=lambda e: category_rank(e["category"]))

    tips = [
        {
            "id": f"tip-{index:04d}",
            "category": entry["category"],
            "text": entry["text"],
        }
        for index, entry in enumerate(entries, start=1)
    ]

    catalogue = {
        "version": 1,
        "licence": "CC0-1.0（本项目自撰，未引用任何第三方文本或名人名言）",
        "note": (
            "冷启动开屏提示语。由 tools/motivation/build_tips.py 从 tools/motivation/parts/*.json "
            "合并生成：按条去重（完全重复与近似重复均剔除）、按分类稳定排序、逐条分配 id。"
            "校验脚本为 tools/motivation/validate_tips.py，回归测试见 StartupTipTest。"
        ),
        "categories": [c for c in CATEGORY_ORDER if any(t["category"] == c for t in tips)],
        "tips": tips,
    }

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(
        json.dumps(catalogue, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        # LF, not the platform default: the repository normalises text files to LF.
        newline="\n",
    )

    per_category: dict[str, int] = {}
    for tip in tips:
        per_category[tip["category"]] = per_category.get(tip["category"], 0) + 1

    print(f"wrote {out_path.relative_to(repo)} with {len(tips)} lines")
    for category in CATEGORY_ORDER:
        if category in per_category:
            print(f"  {category}: {per_category[category]}")
    extras = {c: n for c, n in per_category.items() if c not in CATEGORY_ORDER}
    if extras:
        print(f"  other categories: {extras}")
    print(
        f"dropped: blank={dropped_blank} exact_duplicate={dropped_exact} "
        f"near_duplicate={len(dropped_near)}"
    )
    for text, twin, score in dropped_near:
        print(f"  near-duplicate {score:.2f}: {text}  ≈  {twin}")

    if len(tips) < 370:
        print(f"error: only {len(tips)} lines, the requirement is at least 370", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
