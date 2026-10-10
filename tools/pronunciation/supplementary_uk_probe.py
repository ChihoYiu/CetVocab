#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
supplementary_uk_probe.py

Answers one question that verify_audio.py cannot: for the 14 test words, does
Wikimedia Commons hold a *British* recording at all, or does the app's
`En-uk-<word>.ogg/.oga` naming scheme simply miss files that exist under other
titles?

It does NOT change the app's behaviour model. It only searches the Commons file
namespace for the `en-uk-<word>` naming family, including capitalised variants,
so the report can say whether the UK coverage gap measured by the app's exact
algorithm reflects reality or an over-narrow title guess.

Same politeness rules as verify_audio.py: descriptive User-Agent, 8-second
timeout, one request per second, shared on-disk cache.
"""

import io
import json
import os
import sys
import time
import urllib.parse
import urllib.request

UA = ("CetVocab-verification/1.0 "
      "(offline-first vocabulary study app; contact: local)")
COMMONS_API = "https://commons.wikimedia.org/w/api.php"
HERE = os.path.dirname(os.path.abspath(__file__))
CACHE_DIR = os.path.join(HERE, "cache")
WORDS = [
    "apple", "abandon", "ability",
    "internationalization", "characteristically", "misunderstanding",
    "rhythm", "queue", "xylophone", "well-being", "x-ray", "don't",
    "naive", "rendezvous",
]


def cache_paths(url):
    import hashlib
    digest = hashlib.sha1(url.encode("utf-8")).hexdigest()[:16]
    return os.path.join(CACHE_DIR, "ukprobe_%s.body" % digest)


def get(url, attempts=4, backoff=15.0):
    """Fetch JSON, retrying 429s. Only successful answers are cached, so a
    throttle is never recorded as an empty result."""
    path = cache_paths(url)
    if os.path.exists(path):
        with io.open(path, "rb") as fh:
            payload = json.loads(fh.read().decode("utf-8"))
        if payload is not None:
            return payload
    last = None
    for attempt in range(1, attempts + 1):
        request = urllib.request.Request(
            url, headers={"User-Agent": UA, "Accept": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=8) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
            os.makedirs(CACHE_DIR, exist_ok=True)
            with io.open(path, "wb") as fh:
                fh.write(json.dumps(payload, ensure_ascii=False).encode("utf-8"))
            return payload
        except urllib.error.HTTPError as exc:
            last = "HTTP %s" % exc.code
            if exc.code == 429:
                retry_after = exc.headers.get("Retry-After") if exc.headers else None
                try:
                    wait = float(retry_after) if retry_after else backoff
                except (TypeError, ValueError):
                    wait = backoff
                time.sleep(max(wait, 1.0))
                continue
            break
        except Exception as exc:
            last = "%s: %s" % (type(exc).__name__, exc)
            time.sleep(1.0)
    raise RuntimeError("giving up after %d attempts (last: %s)" % (attempts, last))


def search(term):
    query = urllib.parse.urlencode({
        "action": "query", "format": "json", "list": "search",
        "srsearch": term, "srnamespace": "6", "srlimit": "10",
    }, quote_via=urllib.parse.quote)
    return get(COMMONS_API + "?" + query)


def file_info(titles):
    query = urllib.parse.urlencode({
        "action": "query", "format": "json", "prop": "imageinfo",
        "iiprop": "url|size", "titles": "|".join(titles),
    }, quote_via=urllib.parse.quote)
    return get(COMMONS_API + "?" + query)


def main():
    os.makedirs(CACHE_DIR, exist_ok=True)
    results = {}
    print("Searching Commons file namespace for British recordings")
    print("(pattern family: en-uk-<word>, any capitalisation)\n")
    for word in WORDS:
        found = {}
        failed = []
        # Two search terms catch both "En-uk-<word>" and "En-uk-<Word>".
        for term in ('intitle:"en-uk-%s"' % word,
                     'intitle:"En-uk-%s"' % word):
            try:
                payload = search(term)
            except Exception as exc:
                print("  ! %s search failed: %s" % (word, exc), file=sys.stderr)
                failed.append(term)
                time.sleep(2.0)
                continue
            for hit in payload.get("query", {}).get("search", []):
                title = hit["title"]
                if title.lower().startswith("file:en-uk-"):
                    found[title] = None
            time.sleep(1.0)
        if found:
            # Resolve sizes/URLs for the titles actually found.
            try:
                info = file_info(sorted(found))
                for page in info.get("query", {}).get("pages", {}).values():
                    if "missing" in page:
                        continue
                    ii = (page.get("imageinfo") or [{}])[0]
                    found[page.get("title")] = {
                        "url": ii.get("url"), "size": ii.get("size"),
                    }
            except Exception as exc:
                print("  ! %s imageinfo failed: %s" % (word, exc), file=sys.stderr)
            time.sleep(1.0)
        if failed:
            found["__unreliable__"] = failed
        results[word] = found
        shown = [k for k in sorted(found) if k != "__unreliable__"]
        if failed:
            print("%-22s %s  [SEARCH FAILED: %s]" % (
                word, ", ".join(shown) or "NONE", "; ".join(failed)))
        else:
            print("%-22s %s" % (word, ", ".join(shown) or "NONE"))

    out_path = os.path.join(HERE, "uk_probe_findings.json")
    with io.open(out_path, "w", encoding="utf-8") as fh:
        fh.write(json.dumps(results, ensure_ascii=False, indent=2))
    print("\nWritten: %s" % out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
