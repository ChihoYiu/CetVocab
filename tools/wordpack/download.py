#!/usr/bin/env python3
"""Download candidate source files into tools/wordpack/cache/ using urllib only."""
import os
import sys
import urllib.request

UA = "CetVocab-wordpack-fetch/1.0 (offline vocabulary asset build)"
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cache")

TARGETS = {
    # ECDICT - MIT
    "ecdict.csv": "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv",
    "ECDICT-LICENSE": "https://raw.githubusercontent.com/skywind3000/ECDICT/master/LICENSE",
    "ECDICT-README.md": "https://raw.githubusercontent.com/skywind3000/ECDICT/master/README.md",
    # KyleBing - BSD-3-Clause
    "KyleBing-3-CET4-sentence.jsonl": "https://raw.githubusercontent.com/KyleBing/english-vocabulary/master/full_line_jsonl/sentence/%E6%AD%A3%E5%BA%8F/%E5%9B%9B%E7%BA%A7.jsonl",
    "KyleBing-4-CET6-sentence.jsonl": "https://raw.githubusercontent.com/KyleBing/english-vocabulary/master/full_line_jsonl/sentence/%E6%AD%A3%E5%BA%8F/%E5%85%AD%E7%BA%A7.jsonl",
    "KyleBing-LICENSE": "https://raw.githubusercontent.com/KyleBing/english-vocabulary/master/LICENSE",
    "KyleBing-README.md": "https://raw.githubusercontent.com/KyleBing/english-vocabulary/master/README.md",
    # rejected candidates - licences only
    "qwerty-learner-LICENSE": "https://raw.githubusercontent.com/RealKai42/qwerty-learner/master/LICENSE",
    "mahavivo-README.md": "https://raw.githubusercontent.com/mahavivo/english-wordlists/master/README.md",
}


def main():
    os.makedirs(CACHE, exist_ok=True)
    only = set(sys.argv[1:])
    for name, url in TARGETS.items():
        if only and name not in only:
            continue
        dest = os.path.join(CACHE, name)
        if os.path.exists(dest) and os.path.getsize(dest) > 0:
            print(f"skip  {name} ({os.path.getsize(dest)} bytes, cached)")
            continue
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=600) as resp:
                data = resp.read()
        except Exception as exc:  # noqa: BLE001
            print(f"FAIL  {name}: {type(exc).__name__}: {exc}")
            continue
        with open(dest, "wb") as fh:
            fh.write(data)
        print(f"saved {name} {len(data)} bytes")


if __name__ == "__main__":
    main()
