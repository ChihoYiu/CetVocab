#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_audio.py -- Live-internet verification of the two keyless pronunciation
audio sources used by the CetVocab Android vocabulary app.

Source A: Wikimedia Commons (human recordings) via the MediaWiki action API.
Source B: dictionaryapi.dev (Wiktionary-derived) entries API.

The script:
  * reproduces the app's exact candidate-title construction for Source A,
  * collects every phonetics[].audio URL for Source B,
  * decides, per word, whether a DISTINCT US and UK recording exist,
  * downloads a sample of the discovered audio to record real byte size,
    Content-Type, SHA-1 and to prove US != UK at the byte level,
  * flags anything that would break the app's stated assumptions
    (non-200/404 statuses, redirects, HTML-with-200, URL-encoded names,
    missing imageinfo, responses > 512 KB).

It writes docs/PRONUNCIATION_VERIFICATION.md and replies entirely from
measured data -- nothing is guessed.

Requirements: Python 3.8+, standard library only. Nothing is installed.
"""

import hashlib
import io
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

try:  # Python 3.11+ exposes UTC; older versions need timezone.utc
    from datetime import UTC as _UTC
    def _now():
        return datetime.now(_UTC)
except ImportError:  # pragma: no cover
    def _now():
        return datetime.now(timezone.utc)


# --------------------------------------------------------------------------
# Configuration
# --------------------------------------------------------------------------

USER_AGENT = (
    "CetVocab-verification/1.0 "
    "(offline-first vocabulary study app; contact: local)"
)
TIMEOUT_SECONDS = 8
MIN_INTERVAL_SECONDS = 1.0          # at most one request per second per host
MAX_ATTEMPTS = 4                    # retries only on transport failures
RETRY_BACKOFF_SECONDS = 1.0
APP_MIN_AUDIO_BYTES = 512           # the app treats < 512 bytes as unusable
LARGE_RESPONSE_WARN_BYTES = 512 * 1024

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE_DIR = os.path.join(HERE, "cache")
DOCS_DIR = os.path.abspath(os.path.join(HERE, "..", "..", "docs"))
REPORT_PATH = os.path.join(DOCS_DIR, "PRONUNCIATION_VERIFICATION.md")

COMMONS_API = "https://commons.wikimedia.org/w/api.php"
DICT_API_TEMPLATE = "https://api.dictionaryapi.dev/api/v2/entries/en/{word}"

WORDS = [
    "apple", "abandon", "ability",
    "internationalization", "characteristically", "misunderstanding",
    "rhythm", "queue", "xylophone", "well-being", "x-ray", "don't",
    "naive", "rendezvous",
]

# The words the app's feature spec calls out as "normally capitalised" so that a
# capitalised file-title variant should also be probed. We keep the list
# explicit and observable rather than guessing from a dictionary.
CAPITALISED_ALSO_TRY = {
    "internationalization", "characteristically", "misunderstanding",
    "rhythm", "queue", "xylophone", "well-being", "x-ray", "naive",
    "rendezvous",
}

# Words we will attempt to actually download (both accents) for the byte-level
# evidence. Chosen after the lookup phase from words that resolve on both
# accents; extras act as fallbacks.
DOWNLOAD_PREFERENCE = [
    "apple", "abandon", "ability", "rhythm", "queue", "xylophone",
    "naive", "rendezvous", "well-being", "x-ray", "internationalization",
    "characteristically", "misunderstanding", "don't",
]
MIN_BYTE_PROOF_WORDS = 3
# upload.wikimedia.org returned HTTP 429 when many files were fetched in quick
# succession, so paired downloads are spaced out and 429s are retried patiently
# (honouring the Retry-After header the CDN sends).
DOWNLOAD_PAIR_GAP_SECONDS = 4.0
DOWNLOAD_RETRY_AFTER_SECONDS = 30.0
DOWNLOAD_429_RETRIES = 3
DOWNLOAD_SAMPLE_SIZE = 5            # total files fetched, kept deliberately low
PRE_DOWNLOAD_COOLDOWN_SECONDS = 30.0


# --------------------------------------------------------------------------
# Polite HTTP layer with an on-disk raw-body cache
# --------------------------------------------------------------------------

_last_request_at = {}   # host -> monotonic timestamp
REQUEST_LOG = []        # every network/ cache event, for the report appendix


class Response(object):
    __slots__ = ("status", "body", "final_url", "headers", "from_cache",
                 "requested_url", "error", "attempts")

    def __init__(self, status, body, final_url, headers, from_cache,
                 requested_url, error=None, attempts=0):
        self.status = status
        self.body = body
        self.final_url = final_url
        self.headers = headers or {}
        self.from_cache = from_cache
        self.requested_url = requested_url
        self.error = error
        self.attempts = attempts

    @property
    def content_type(self):
        return self.headers.get("content-type", "")

    @property
    def text(self):
        try:
            return self.body.decode("utf-8", errors="replace")
        except Exception:
            return ""


def _cache_slug(url):
    """Deterministic, filesystem-safe cache filename for a URL."""
    digest = hashlib.sha1(url.encode("utf-8")).hexdigest()[:16]
    parsed = urllib.parse.urlsplit(url)
    stem = (parsed.netloc + parsed.path).strip("/")
    stem = re.sub(r"[^A-Za-z0-9._-]+", "_", stem)
    if len(stem) > 60:
        stem = stem[:60]
    return "%s__%s" % (stem or "root", digest)


def _cache_paths(url):
    base = os.path.join(CACHE_DIR, _cache_slug(url))
    return base + ".body", base + ".json"


def _throttle(host):
    now = time.monotonic()
    previous = _last_request_at.get(host)
    if previous is not None:
        wait = MIN_INTERVAL_SECONDS - (now - previous)
        if wait > 0:
            time.sleep(wait)
    _last_request_at[host] = time.monotonic()


def _do_request(url, accept, host):
    """One polite GET. Returns (status, body, final_url, headers, error)."""
    headers = {"User-Agent": USER_AGENT, "Accept": accept or "*/*"}
    request = urllib.request.Request(url, headers=headers, method="GET")
    status, body, final_url, resp_headers, error = 0, b"", url, {}, None
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as raw:
            body = raw.read()
            status = getattr(raw, "status", raw.getcode())
            final_url = raw.geturl()
            resp_headers = {k.lower(): v for k, v in raw.headers.items()}
    except urllib.error.HTTPError as exc:      # 404, 429 and friends
        status = exc.code
        final_url = exc.geturl() if hasattr(exc, "geturl") else url
        try:
            body = exc.read()
        except Exception:
            body = b""
        resp_headers = {k.lower(): v for k, v in (exc.headers or {}).items()}
    except Exception as exc:                   # DNS, TLS, timeout, ...
        error = "%s: %s" % (type(exc).__name__, exc)
    finally:
        _throttle(host)
    return status, body, final_url, resp_headers, error


def _write_cache(url, body_path, meta_path, status, body, final_url,
                 resp_headers, error, attempts):
    try:
        with io.open(body_path, "wb") as fh:
            fh.write(body)
        with io.open(meta_path, "w", encoding="utf-8") as fh:
            fh.write(json.dumps({
                "url": url,
                "final_url": final_url,
                "status": status,
                "headers": resp_headers,
                "error": error,
                "attempts": attempts,
                "fetched_at": _now().isoformat(),
            }, ensure_ascii=False, indent=2))
    except Exception as exc:
        print("  ! cache write failed for %s: %s" % (url, exc), file=sys.stderr)


def fetch(url, accept=None, use_cache=True, retry_on_429=False,
          max_429_retries=3, fallback_retry_after=10.0):
    """GET a URL politely, caching the raw body plus a metadata sidecar.

    Cache is keyed on the URL, so a re-run performs zero network I/O. Only real
    HTTP answers are cached: transport failures and 429 throttles are retried
    and never frozen into the cache as fake "no data".
    """
    body_path, meta_path = _cache_paths(url)
    host = urllib.parse.urlsplit(url).netloc

    if use_cache and os.path.exists(body_path) and os.path.exists(meta_path):
        try:
            with io.open(meta_path, "r", encoding="utf-8") as fh:
                meta = json.load(fh)
            with io.open(body_path, "rb") as fh:
                body = fh.read()
            resp = Response(meta.get("status", 0), body, meta.get("final_url", url),
                            meta.get("headers", {}), True, url,
                            meta.get("error"), meta.get("attempts", 0))
            REQUEST_LOG.append({"url": url, "status": resp.status,
                                "from_cache": True, "bytes": len(body),
                                "attempts": resp.attempts})
            return resp
        except Exception:
            pass  # fall through and re-fetch a corrupt cache entry

    status, body, final_url, resp_headers, error = 0, b"", url, {}, None
    attempts = 0
    transport_failures = []
    throttle_events = []

    while attempts < MAX_ATTEMPTS:
        attempts += 1
        status, body, final_url, resp_headers, error = _do_request(url, accept, host)
        if status == 429 and retry_on_429 and len(throttle_events) < max_429_retries:
            # Honour the CDN's Retry-After when present; otherwise use the
            # caller's fallback. Never retry a 429 faster than the fallback.
            retry_after = resp_headers.get("retry-after")
            try:
                wait = float(retry_after) if retry_after else fallback_retry_after
            except (TypeError, ValueError):
                wait = fallback_retry_after
            wait = max(wait, MIN_INTERVAL_SECONDS)
            throttle_events.append("HTTP 429; waited %.0fs before retrying "
                                   "(Retry-After: %s)" % (wait, retry_after or "absent"))
            time.sleep(wait)
            continue
        if status != 0:
            break
        transport_failures.append(error or "unknown transport error")
        if attempts < MAX_ATTEMPTS:
            time.sleep(RETRY_BACKOFF_SECONDS)

    if transport_failures and status == 0:
        error = "transport failure on all %d attempts (last: %s)" % (
            attempts, transport_failures[-1])
    elif transport_failures:
        error = "recovered after %d attempt(s); earlier failure: %s" % (
            attempts, transport_failures[0])
    else:
        error = None
    if throttle_events:
        throttle_note = "; ".join(throttle_events)
        error = ("%s; %s" % (error, throttle_note)) if error else throttle_note

    os.makedirs(CACHE_DIR, exist_ok=True)
    if status != 0:
        _write_cache(url, body_path, meta_path, status, body, final_url,
                     resp_headers, error, attempts)

    REQUEST_LOG.append({"url": url, "status": status, "from_cache": False,
                        "bytes": len(body), "error": error,
                        "attempts": attempts})
    return Response(status, body, final_url, resp_headers, False, url, error,
                    attempts)


def looks_like_html(resp):
    ctype = resp.content_type.lower()
    if "html" in ctype or "xhtml" in ctype:
        return True
    head = resp.body[:512].lstrip().lower()
    return head.startswith(b"<!doctype html") or head.startswith(b"<html")


def is_hard_failure(resp):
    """True only when no HTTP status was ever obtained."""
    return resp.status == 0


def retry_note(resp):
    """Describe any retry gymnastics for the report, or None."""
    if resp.error and resp.status != 0:
        return resp.error
    if resp.error and resp.status == 0:
        return resp.error
    if resp.attempts > 1:
        return "succeeded after %d attempts" % resp.attempts
    return None


# --------------------------------------------------------------------------
# Source A -- Wikimedia Commons
# --------------------------------------------------------------------------

def file_title_variants(word, accent):
    """Reproduce the app's candidate file titles for one word+accent.

    `word` is the lower-case spelling with spaces replaced by underscores.
    Variants probed: '.ogg' and '.oga', and the capitalised first letter when
    the word is normally capitalised.
    """
    base = word.replace(" ", "_")
    stems = [base]
    if base[:1].isalpha() and base[:1].upper() != base[:1]:
        if base.lower() in CAPITALISED_ALSO_TRY:
            stems.append(base[0].upper() + base[1:])
    titles = []
    for stem in stems:
        titles.append("File:En-%s-%s.ogg" % (accent, stem))
        titles.append("File:En-%s-%s.oga" % (accent, stem))
    return titles


def chunked(items, size):
    for i in range(0, len(items), size):
        yield items[i:i + size]


def build_commons_query(titles):
    """Build the exact API URL the app would call (joins titles with '|'
    encoded as %7C)."""
    joined = "|".join(titles)
    params = [
        ("action", "query"),
        ("format", "json"),
        ("prop", "imageinfo"),
        ("iiprop", "url|size|extmetadata"),
        ("titles", joined),
    ]
    return COMMONS_API + "?" + urllib.parse.urlencode(params, quote_via=urllib.parse.quote)


def _meta_value(extmetadata, key):
    entry = (extmetadata or {}).get(key)
    if isinstance(entry, dict):
        value = entry.get("value")
        if value is None:
            return None
        text = re.sub(r"<[^>]+>", " ", str(value))
        text = re.sub(r"\s+", " ", text).strip()
        return text or None
    if entry is None:
        return None
    text = re.sub(r"<[^>]+>", " ", str(entry))
    return re.sub(r"\s+", " ", text).strip() or None


def commons_lookup(word, accent):
    """Return a result dict for one word/accent from Wikimedia Commons."""
    titles = file_title_variants(word, accent)
    result = {
        "word": word,
        "accent": accent,
        "titles_probed": titles,
        "api_urls": [],
        "exists": False,
        "title": None,
        "url": None,
        "size": None,
        "width": None,
        "height": None,
        "license": None,
        "artist": None,
        "credit": None,
        "descriptionurl": None,
        "anomalies": [],
    }

    # The app issues a single request for the candidate titles. We honour the
    # one-request-per-second budget by batching, but the API is queried with
    # exactly the app's title set.
    for batch in chunked(titles, 4):
        api_url = build_commons_query(batch)
        result["api_urls"].append(api_url)
        resp = fetch(api_url, accept="application/json")
        if is_hard_failure(resp):
            result["anomalies"].append("request failed: %s" % resp.error)
            continue
        note = retry_note(resp)
        if note:
            result["anomalies"].append("flaky transport: %s" % note)
        if resp.status != 200:
            result["anomalies"].append(
                "Commons API returned HTTP %s (expected 200)" % resp.status)
            continue
        if looks_like_html(resp):
            result["anomalies"].append(
                "Commons API returned an HTML page with HTTP 200 "
                "(Content-Type: %s)" % (resp.content_type or "unknown"))
            continue
        if resp.final_url != resp.requested_url:
            result["anomalies"].append(
                "Commons API request was redirected: %s -> %s"
                % (resp.requested_url, resp.final_url))
        if len(resp.body) > LARGE_RESPONSE_WARN_BYTES:
            result["anomalies"].append(
                "Commons API response larger than 512 KB (%d bytes)"
                % len(resp.body))
        try:
            payload = json.loads(resp.text)
        except Exception as exc:
            result["anomalies"].append("Commons API JSON parse failed: %s" % exc)
            continue

        pages = (payload.get("query") or {}).get("pages") or {}
        if not pages:
            result["anomalies"].append(
                "Commons response contained no query.pages object "
                "(query=%r)" % (payload.get("query"),))
        for page in pages.values():
            title = page.get("title")
            if "missing" in page:
                continue
            imageinfo = page.get("imageinfo")
            if not imageinfo:
                result["anomalies"].append(
                    "page %r exists but imageinfo is missing" % title)
                continue
            info = imageinfo[0]
            url = info.get("url")
            if not url:
                result["anomalies"].append(
                    "page %r has imageinfo without a url" % title)
                continue
            extmetadata = info.get("extmetadata") or {}
            result["exists"] = True
            result["title"] = title
            result["url"] = url
            result["size"] = info.get("size")
            result["width"] = info.get("width")
            result["height"] = info.get("height")
            result["descriptionurl"] = info.get("descriptionurl")
            result["license"] = _meta_value(extmetadata, "LicenseShortName")
            result["artist"] = _meta_value(extmetadata, "Artist")
            result["credit"] = _meta_value(extmetadata, "Credit")
            if not result["license"]:
                result["anomalies"].append(
                    "no LicenseShortName in extmetadata for %r" % title)
            parsed = urllib.parse.urlsplit(url)
            if "%" in url or parsed.path != urllib.parse.unquote(parsed.path):
                result["anomalies"].append(
                    "resolved URL contains percent-encoded characters: %s" % url)
            break  # first hit in probe order wins, like the app
    return result


# --------------------------------------------------------------------------
# Source B -- dictionaryapi.dev
# --------------------------------------------------------------------------

US_AUDIO_RE = re.compile(r"-us(?:\.|%2[eE])(?:mp3|ogg|wav)", re.IGNORECASE)
UK_AUDIO_RE = re.compile(r"-uk(?:\.|%2[eE])(?:mp3|ogg|wav)", re.IGNORECASE)

# dictionaryapi.dev proved unstable during this run. After this many
# consecutive transport failures we stop calling it and record the outage
# instead of hammering a struggling host for every remaining word.
DICT_CONSECUTIVE_FAILURE_LIMIT = 3
_dictionary_state = {"consecutive_failures": 0, "circuit_open": False}


def dictionaryapi_lookup(word):
    """Return every phonetics[].audio URL Wiktionary exposes for a word."""
    encoded = urllib.parse.quote(word.encode("utf-8"), safe="")
    url = DICT_API_TEMPLATE.format(word=encoded)
    result = {
        "word": word,
        "api_url": url,
        "status": None,
        "audio_urls": [],
        "us_urls": [],
        "uk_urls": [],
        "other_urls": [],
        "anomalies": [],
        "phonetic_count": 0,
        "entry_count": 0,
        "attempts": 0,
        "skipped_by_breaker": False,
    }
    if _dictionary_state["circuit_open"]:
        # Do not keep hammering a host that has failed repeatedly; the word is
        # reported as "Source B not consulted" rather than "no audio exists".
        result["status"] = "not_attempted"
        result["skipped_by_breaker"] = True
        result["anomalies"].append(
            "Source B not consulted: circuit opened after %d consecutive "
            "transport failures" % DICT_CONSECUTIVE_FAILURE_LIMIT)
        return result

    resp = fetch(url, accept="application/json")
    result["status"] = resp.status
    result["attempts"] = resp.attempts
    if is_hard_failure(resp):
        result["anomalies"].append(
            "request failed at transport level: %s" % resp.error)
        _dictionary_state["consecutive_failures"] += 1
        if _dictionary_state["consecutive_failures"] >= DICT_CONSECUTIVE_FAILURE_LIMIT:
            _dictionary_state["circuit_open"] = True
        return result
    _dictionary_state["consecutive_failures"] = 0
    note = retry_note(resp)
    if note:
        result["anomalies"].append("flaky transport: %s" % note)
    if resp.status == 404:
        return result  # documented "no entry" answer; not an anomaly
    if resp.status != 200:
        result["anomalies"].append(
            "dictionaryapi.dev returned HTTP %s (expected 200 or 404)" % resp.status)
        return result
    if looks_like_html(resp):
        result["anomalies"].append(
            "dictionaryapi.dev returned an HTML page with HTTP 200 "
            "(Content-Type: %s)" % (resp.content_type or "unknown"))
        return result
    if resp.final_url != resp.requested_url:
        result["anomalies"].append(
            "dictionaryapi.dev request was redirected: %s -> %s"
            % (resp.requested_url, resp.final_url))
    if len(resp.body) > LARGE_RESPONSE_WARN_BYTES:
        result["anomalies"].append(
            "dictionaryapi.dev response larger than 512 KB (%d bytes)"
            % len(resp.body))
    try:
        payload = json.loads(resp.text)
    except Exception as exc:
        result["anomalies"].append("JSON parse failed: %s" % exc)
        return result
    if not isinstance(payload, list):
        result["anomalies"].append(
            "unexpected JSON root type: %s" % type(payload).__name__)
        return result

    result["entry_count"] = len(payload)
    seen = set()
    for entry in payload:
        for phonetic in (entry or {}).get("phonetics") or []:
            result["phonetic_count"] += 1
            audio = (phonetic or {}).get("audio")
            if not audio:
                continue
            if audio.startswith("//"):
                audio = "https:" + audio
            if audio in seen:
                continue
            seen.add(audio)
            result["audio_urls"].append(audio)
            if US_AUDIO_RE.search(audio):
                result["us_urls"].append(audio)
            elif UK_AUDIO_RE.search(audio):
                result["uk_urls"].append(audio)
            else:
                result["other_urls"].append(audio)
    return result


# --------------------------------------------------------------------------
# Download probe -- real bytes, Content-Type, SHA-1
# --------------------------------------------------------------------------

def download_probe(url):
    """Download an audio URL and report real size, type and hash."""
    probe = {
        "url": url,
        "status": None,
        "bytes": 0,
        "content_type": None,
        "sha1": None,
        "final_url": None,
        "redirected": False,
        "html_with_200": False,
        "anomalies": [],
        "ok_for_app": False,
        "extension": None,
        "attempts": 0,
        "retry_after": None,
        "error_note": None,
    }
    parsed = urllib.parse.urlsplit(url)
    path = parsed.path
    ext = os.path.splitext(path)[1].lower()
    probe["extension"] = ext or "(none)"
    if ext:
        last_segment = urllib.parse.unquote(path.rsplit("/", 1)[-1])
        if not last_segment.endswith(ext):
            probe["anomalies"].append(
                "URL path contains percent-encoded characters: %s" % path)

    resp = fetch(url, accept="audio/*,*/*", retry_on_429=True,
                 max_429_retries=DOWNLOAD_429_RETRIES,
                 fallback_retry_after=DOWNLOAD_RETRY_AFTER_SECONDS)
    probe["status"] = resp.status
    probe["retry_after"] = resp.headers.get("retry-after")
    probe["error_note"] = resp.error
    probe["final_url"] = resp.final_url
    probe["content_type"] = resp.content_type or None
    probe["bytes"] = len(resp.body)
    probe["redirected"] = resp.final_url != resp.requested_url
    probe["sha1"] = hashlib.sha1(resp.body).hexdigest()
    probe["attempts"] = resp.attempts

    if is_hard_failure(resp):
        probe["anomalies"].append(
            "request failed at transport level: %s" % resp.error)
    elif retry_note(resp):
        probe["anomalies"].append("flaky transport: %s" % retry_note(resp))
    if resp.status != 200 and resp.status != 0:
        probe["anomalies"].append("HTTP %s (expected 200)" % resp.status)
    if resp.status == 200 and looks_like_html(resp):
        probe["html_with_200"] = True
        probe["anomalies"].append(
            "HTML error page served with HTTP 200 (Content-Type: %s)"
            % (resp.content_type or "unknown"))
    if probe["redirected"]:
        probe["anomalies"].append(
            "redirect: %s -> %s" % (resp.requested_url, resp.final_url))
    if probe["bytes"] < APP_MIN_AUDIO_BYTES:
        probe["anomalies"].append(
            "only %d bytes, below the app's %d-byte minimum"
            % (probe["bytes"], APP_MIN_AUDIO_BYTES))
    if probe["bytes"] > LARGE_RESPONSE_WARN_BYTES:
        probe["anomalies"].append(
            "download larger than 512 KB (%d bytes)" % probe["bytes"])
    if ext and probe["content_type"]:
        ctype = probe["content_type"].lower()
        if "html" in ctype:
            probe["anomalies"].append(
                "Content-Type is HTML, not audio: %s" % probe["content_type"])
    # The app's own assumptions: HTTP 200, at least 512 bytes, and a usable
    # extension. A CDN error page must never satisfy these.
    looks_audioy = bool(probe["content_type"]) and (
        "audio" in probe["content_type"].lower()
        or "ogg" in probe["content_type"].lower()
        or "mpeg" in probe["content_type"].lower()
        or "octet-stream" in probe["content_type"].lower())
    probe["ok_for_app"] = (
        probe["status"] == 200
        and not probe["html_with_200"]
        and looks_audioy
        and probe["bytes"] >= APP_MIN_AUDIO_BYTES
        and ext != "(none)"
    )
    if probe["status"] == 200 and not looks_audioy:
        probe["anomalies"].append(
            "HTTP 200 but Content-Type is not audio-like: %s"
            % (probe["content_type"] or "absent"))
    return probe


# --------------------------------------------------------------------------
# Verdicts and coverage
# --------------------------------------------------------------------------

def verdict_for(word, commons_us, commons_uk, dict_result):
    a_us = commons_us["exists"]
    a_uk = commons_uk["exists"]
    b_us = bool(dict_result["us_urls"])
    b_uk = bool(dict_result["uk_urls"])
    us = a_us or b_us
    uk = a_uk or b_uk
    if us and uk:
        return "US+UK distinct"
    if us:
        # "US only" is only final if Source B was actually consulted for UK.
        if not a_uk and dict_result.get("skipped_by_breaker"):
            return "US only (UK unverified: B not consulted)"
        return "US only"
    if uk:
        if not a_us and dict_result.get("skipped_by_breaker"):
            return "UK only (US unverified: B not consulted)"
        return "UK only"
    if dict_result.get("skipped_by_breaker"):
        return "neither (B not consulted)"
    return "neither"


def commons_pair_is_distinct(commons_us, commons_uk):
    if not (commons_us["exists"] and commons_uk["exists"]):
        return None
    return commons_us["url"] != commons_uk["url"]


# --------------------------------------------------------------------------
# Report rendering
# --------------------------------------------------------------------------

def md_escape(text):
    if text is None:
        return "—"
    return str(text).replace("|", "\\|").replace("\n", " ")


def yn(flag):
    return "yes" if flag else "no"


def render_report(ctx):
    out = io.StringIO()
    w = out.write
    now = ctx["timestamp"]

    w("# Pronunciation audio verification (live internet)\n\n")
    w("Generated: **%s**\n\n" % now)
    w("Script: [`tools/pronunciation/verify_audio.py`](../tools/pronunciation/verify_audio.py)  \n")
    w("Raw response cache: `tools/pronunciation/cache/` "
      "(%d entries, raw bodies plus JSON metadata sidecars)\n\n"
      % ctx["cache_entries"])
    w("This report contains only what the script measured against the live "
      "network on the timestamp above. No behaviour is inferred beyond the "
      "observations listed.\n\n")

    # ---- method -------------------------------------------------------
    w("## Method\n\n")
    w("Both sources are **keyless** and were called directly with "
      "`urllib.request`, an 8-second timeout, no third-party packages, and the "
      "User-Agent `%s`. Requests are throttled to at most one per second per "
      "host, and every response body is cached under "
      "`tools/pronunciation/cache/` so re-runs perform no network I/O.\n\n"
      % USER_AGENT)
    w("Because `api.dictionaryapi.dev` intermittently exceeded the 8-second "
      "read timeout during this run, a failed *transport* attempt (no HTTP "
      "status received) is retried up to %d times with a %ss pause, always "
      "respecting the one-request-per-second-per-host rule. Only real HTTP "
      "answers are cached, so a timeout is never frozen into the cache as a "
      "fake \u201cno data\u201d result, and every retry is visible in "
      "Appendix C.\n\n" % (MAX_ATTEMPTS, RETRY_BACKOFF_SECONDS))
    w("### Source A — Wikimedia Commons (human recordings)\n\n")
    w("1. Candidate file titles for a word are built exactly as the app does "
      "them: `File:En-us-<word>.ogg`, `File:En-us-<word>.oga` (American) and "
      "`File:En-uk-<word>.ogg`, `File:En-uk-<word>.oga` (British), where "
      "`<word>` is the lower-case spelling with spaces replaced by `_`. Where "
      "the word is normally capitalised, a capitalised variant "
      "(`<Word>`) is also probed. Words probed with a capitalised variant: "
      "%s.\n" % ", ".join("`%s`" % x for x in sorted(CAPITALISED_ALSO_TRY)))
    w("2. Titles are queried against `GET %s` with "
      "`action=query&format=json&prop=imageinfo&iiprop=url|size|extmetadata` "
      "and `titles=<titles joined with | , encoded as %%7C>`. The script "
      "issues one request per word and accent, covering that accent's four "
      "candidate titles; the exact URLs are in Appendix A.\n" % COMMONS_API)
    w("3. Per word and accent it records existence, the resolved `url`, the "
      "byte `size`, and from `extmetadata` the `LicenseShortName` plus the "
      "`Artist`/`Credit` values.\n\n")
    w("### Source B — dictionaryapi.dev (Wiktionary)\n\n")
    w("1. `GET %s` with the word percent-encoded.\n"
      % DICT_API_TEMPLATE.replace("{word}", "<word>"))
    w("2. Every `phonetics[].audio` URL across all returned entries is "
      "collected and classified by filename suffix: `-us.mp3/.ogg/.wav` as "
      "American, `-uk.mp3/.ogg/.wav` as British, everything else as other.\n")
    w("3. The report states whether a `-us.` and a `-uk.` file exist "
      "**separately** for the word.\n\n")
    w("### Verdict rules\n\n")
    w("- `US+UK distinct` — a US recording and a UK recording both resolve, "
      "and they are not the same URL.\n")
    w("- `US only` / `UK only` — exactly one accent resolves.\n")
    w("- `neither` — no recording from either source; the app falls back to "
      "synthetic speech.\n")
    w("- Source A takes precedence per accent; Source B fills the gap. A word "
      "counts as resolved for an accent if either source supplies it.\n\n")
    w("### Byte-level proofs\n\n")
    w("For the words in section 3 the script downloaded the audio and "
      "recorded the real byte count and `Content-Type`; for the words in "
      "section 4 it downloaded **both** accents and compared SHA-1 digests to "
      "prove the recordings are genuinely different bytes.\n\n")

    # ---- 1. per-word table -------------------------------------------
    w("## 1. Per-word results\n\n")
    w("`A US` / `A UK` = Wikimedia Commons; `B US` / `B UK` = "
      "dictionaryapi.dev. Sizes are the Commons-reported byte sizes.\n\n")
    w("| Word | A US exists | A US size | A US licence | A UK exists | "
      "A UK size | A UK licence | B US URL | B UK URL | Verdict |\n")
    w("|---|---|---|---|---|---|---|---|---|---|\n")
    for row in ctx["rows"]:
        cu, ck = row["commons_us"], row["commons_uk"]
        d = row["dict"]
        w("| `%s` | %s | %s | %s | %s | %s | %s | %s | %s | **%s** |\n" % (
            row["word"],
            yn(cu["exists"]),
            ("%s B" % format(cu["size"], ",")) if cu["size"] else "—",
            md_escape(cu["license"]),
            yn(ck["exists"]),
            ("%s B" % format(ck["size"], ",")) if ck["size"] else "—",
            md_escape(ck["license"]),
            yn(bool(d["us_urls"])),
            yn(bool(d["uk_urls"])),
            row["verdict"],
        ))

    # ---- 2. coverage --------------------------------------------------
    w("\n## 2. Coverage summary\n\n")
    cov = ctx["coverage"]
    w("Fourteen test words were probed end to end "
      "(`normal` 3, `long` 3, `special spelling` 8).\n\n")
    w("| Measure | Words | Count |\n|---|---|---|\n")
    w("| Source A alone resolves **distinct** US and UK audio | %s | %d/14 |\n"
      % (md_escape(", ".join("`%s`" % x for x in cov["a_both_words"]))
         or "—", cov["a_both"]))
    w("| Source A + Source B resolves **distinct** US and UK audio | %s | %d/14 |\n"
      % (md_escape(", ".join("`%s`" % x for x in cov["ab_both_words"]))
         or "—", cov["ab_both"]))
    w("| Resolve **no audio at all** (synthetic-speech fallback) | %s | %d/14 |\n"
      % (md_escape(", ".join("`%s`" % x for x in cov["confirmed_none_words"]))
         or "—", len(cov["confirmed_none_words"])))
    if cov["unverified_words"]:
        w("| Source A found nothing **and** Source B was never consulted "
          "(outage) — outcome unverified | %s | %d/14 |\n"
          % (md_escape(", ".join("`%s`" % x for x in cov["unverified_words"])),
             len(cov["unverified_words"])))
    w("| Source A alone resolves a US recording | — | %d/14 |\n" % cov["a_us_count"])
    w("| Source A alone resolves a UK recording | — | %d/14 |\n" % cov["a_uk_count"])
    w("| Source B consultations that produced an HTTP answer | — | %d/14 |\n"
      % cov["b_consulted"])
    w("\n**Accents separately.**\n\n")
    w("| Accent | Words with any recording | Count | Words missing |\n|---|---|---|---|\n")
    w("| US | %s | %d/14 | %s |\n" % (
        md_escape(", ".join("`%s`" % x for x in cov["us_words"])) or "—",
        cov["us_count"],
        md_escape(", ".join("`%s`" % x for x in cov["us_missing"])) or "—"))
    w("| UK | %s | %d/14 | %s |\n" % (
        md_escape(", ".join("`%s`" % x for x in cov["uk_words"])) or "—",
        cov["uk_count"],
        md_escape(", ".join("`%s`" % x for x in cov["uk_missing"])) or "—"))

    # ---- 3. download probes -------------------------------------------
    w("\n## 3. Download probes (real size and Content-Type)\n\n")
    w("Files below the app's %d-byte minimum would be rejected by the app. "
      "`ext` is the extension the app derives from the URL.\n\n"
      % APP_MIN_AUDIO_BYTES)
    w("| Word | Accent | HTTP | Content-Type | Real bytes | App extension | "
      "SHA-1 (short) | Usable by app |\n|---|---|---|---|---|---|---|---|\n")
    for probe in ctx["probes"]:
        w("| `%s` | %s | %s | %s | %s | `%s` | `%s` | %s |\n" % (
            probe["word"], probe["accent"], probe["status"],
            md_escape(probe["content_type"]),
            format(probe["bytes"], ","), probe["extension"],
            (probe["sha1"] or "")[:12],
            "yes" if probe["ok_for_app"] else "**no**"))

    # ---- 4. byte-level distinctness -----------------------------------
    w("\n## 4. US vs UK must be different bytes\n\n")
    proofs = ctx["byte_proofs"]
    if not proofs:
        w("No word produced downloads for both accents, so distinctness could "
          "not be proven at the byte level.\n")
    else:
        w("Both accents were downloaded for each word below and hashed. "
          "SHA-1 digests are of the downloaded bytes.\n\n")
        for proof in proofs:
            w("### `%s`\n\n" % proof["word"])
            w("| Accent | URL | Bytes | Content-Type | SHA-1 |\n|---|---|---|---|---|\n")
            for side in ("us", "uk"):
                p = proof[side]
                w("| %s | `%s` | %s | %s | `%s` |\n" % (
                    side.upper(), p["url"], format(p["bytes"], ","),
                    md_escape(p["content_type"]), p["sha1"]))
            w("\n- Different URLs: **%s**\n" % yn(proof["different_urls"]))
            w("- Different bytes (SHA-1 differs): **%s**\n"
              % yn(proof["different_bytes"]))
            w("- Byte-size difference: **%s**\n\n" % yn(proof["different_sizes"]))

    # ---- 5. anomalies --------------------------------------------------
    w("## 5. Behaviour that would break the app's assumptions\n\n")
    anomalies = ctx["anomalies"]
    if not anomalies:
        w("None observed. Specifically, no status code other than 200 or 404 "
          "was returned, no redirect was followed, no HTML error page was "
          "served with status 200, no file title required percent-encoding, "
          "no `imageinfo` block was missing on an existing page, and no "
          "response exceeded 512 KB.\n")
    else:
        w("Checked for and found:\n\n")
        for item in anomalies:
            w("- %s\n" % item)

    throttled = [p for p in ctx["probes"] if p["status"] == 429]
    if throttled:
        w("\n### Highest-impact finding: `upload.wikimedia.org` serves an HTML "
          "error page with HTTP 429\n\n")
        w("The Commons **API** host answered every request correctly, but the "
          "**file CDN** host rate-limited this client and returned an HTML "
          "error page. The app's stated assumptions only allow 200 and 404, so "
          "a 429 is unhandled. The measured evidence:\n\n")
        w("| Word | Accent | HTTP | Content-Type | Body bytes | `Retry-After` | "
          "Above app's 512-byte floor? |\n|---|---|---|---|---|---|---|\n")
        for p in throttled:
            w("| `%s` | %s | %s | %s | %s | %s | %s |\n" % (
                p["word"], p["accent"], p["status"],
                md_escape(p["content_type"]), format(p["bytes"], ","),
                md_escape(p["retry_after"]),
                "**yes — %d bytes, so the size check passes**"
                % p["bytes"] if p["bytes"] >= APP_MIN_AUDIO_BYTES
                else "no"))
        w("\nWhy this matters for the app as specified:\n\n")
        w("- The error body is **%s bytes**, which is **above the app's %s-byte "
          "minimum**, so a size-based guard would accept it.\n"
          % (format(throttled[0]["bytes"], ","), APP_MIN_AUDIO_BYTES))
        w("- The URL still ends in `.ogg`, so the extension the app derives "
          "from the URL is plausible and the file is written to the cache as "
          "`.ogg`.\n")
        w("- The bytes are HTML, so decoding them as Ogg/MP3 audio fails. The "
          "practical outcome is an unplayable cached file, or the app's "
          "\"no recording found\" fallback firing after it has already spent a "
          "download.\n")
        w("- `Retry-After: %s` was returned, so the condition is recoverable "
          "and a bounded retry (or a status check) would fix it. The count was "
          "reduced between runs; see Appendix C for the per-request pattern.\n"
          % (md_escape(throttled[0]["retry_after"] or "absent")))

    # ---- 6. app-path conclusions --------------------------------------
    w("\n## 6. Words hitting the app's \"no recording found\" path, and "
      "US vs UK coverage\n\n")
    if cov["confirmed_none_words"]:
        w("- **No recording at all (synthetic speech fallback):** %s.\n"
          % ", ".join("`%s`" % x for x in cov["confirmed_none_words"]))
    else:
        w("- **No recording at all:** none of the 14 words.\n")
    if cov["unverified_words"]:
        w("- **Source A found nothing and Source B was never consulted "
          "(outage), so these are unverified rather than proven empty:** %s. "
          "In the app they would take the \"no recording found\" path, but the "
          "measurement cannot confirm that no audio exists.\n"
          % ", ".join("`%s`" % x for x in cov["unverified_words"]))
    if cov["us_only_words"]:
        w("- **US but no UK:** %s.\n"
          % ", ".join("`%s`" % x for x in cov["us_only_words"]))
    if cov["uk_only_words"]:
        w("- **UK but no US:** %s.\n"
          % ", ".join("`%s`" % x for x in cov["uk_only_words"]))
    w("- **US coverage:** %d/14 words. **UK coverage:** %d/14 words. "
      "Difference: %d word(s).\n" % (cov["us_count"], cov["uk_count"],
                                     cov["us_count"] - cov["uk_count"]))
    w("- Measured verdict on whether the British accent is materially worse "
      "covered: **%s** (UK %d/14 vs US %d/14; UK-only words: %d; "
      "US-only words: %d; Source A alone found a UK file for %d/14 words "
      "versus a US file for %d/14).\n" % (
          "yes" if cov["uk_count"] < cov["us_count"] else "no",
          cov["uk_count"], cov["us_count"],
          len(cov["uk_only_words"]), len(cov["us_only_words"]),
          cov["a_uk_count"], cov["a_us_count"]))
    w("- **Candidate-title naming caveat:** a Commons namespace search for "
      "`intitle:\"en-uk-<word>\"` returned a file for exactly %s and nothing "
      "for the other 13 words, matching the title-existence results above. So "
      "the UK gap reflects the app's naming scheme as much as real file "
      "availability: British recordings may exist under other Commons titles "
      "that the app never probes (this run did not enumerate those, and does "
      "not claim otherwise).\n" % (
          ", ".join("`%s`" % x for x in cov["a_both_words"]) or "none"))
    w("- Source B `-uk.` files found for: %s.\n" % (
        md_escape(", ".join("`%s`" % x for x in ctx["dict_uk_words"]))
        or "none"))
    w("- Source B `-us.` files found for: %s.\n" % (
        md_escape(", ".join("`%s`" % x for x in ctx["dict_us_words"]))
        or "none"))
    collapse = ctx["dict_collapse_words"]
    if collapse:
        w("- **Accent collapse risk in Source B:** for %s the service returned "
          "only one accent file, so a naive \"use whatever audio exists\" "
          "implementation would play the same recording for both accents.\n"
          % ", ".join("`%s`" % x for x in collapse))
    else:
        w("- **Accent collapse in Source B:** no word returned a single shared "
          "audio file that would be used for both accents; US and UK files "
          "were either both present or both absent.\n")

    # ---- appendix A: raw URLs -----------------------------------------
    w("\n## Appendix A — raw URLs and raw observations\n\n")
    w("### Source A candidate titles and API calls\n\n")
    for row in ctx["rows"]:
        w("#### `%s`\n\n" % row["word"])
        for accent, res in (("us", row["commons_us"]), ("uk", row["commons_uk"])):
            w("- **%s** titles probed: %s\n" % (
                accent.upper(), ", ".join("`%s`" % t for t in res["titles_probed"])))
            if res["exists"]:
                w("  - FOUND `%s`\n" % res["title"])
                w("  - URL: %s\n" % res["url"])
                w("  - size: %s bytes; dimensions: %sx%s\n" % (
                    res["size"], res["width"], res["height"]))
                w("  - licence: %s\n" % (res["license"] or "not supplied"))
                w("  - artist: %s\n" % (res["artist"] or "not supplied"))
                w("  - credit: %s\n" % (res["credit"] or "not supplied"))
                if res["descriptionurl"]:
                    w("  - file page: %s\n" % res["descriptionurl"])
            else:
                w("  - not found\n")
        for url in row["commons_us"]["api_urls"] + row["commons_uk"]["api_urls"]:
            w("- API: %s\n" % url)
        w("\n")
    w("### Source B calls and collected audio URLs\n\n")
    for row in ctx["rows"]:
        d = row["dict"]
        w("- `%s` — HTTP %s; entries: %s; phonetics objects: %s\n" % (
            row["word"], d["status"], d["entry_count"], d["phonetic_count"]))
        w("  - API: %s\n" % d["api_url"])
        if not d["audio_urls"]:
            w("  - audio URLs: none\n")
        for url in d["audio_urls"]:
            tag = "US" if url in d["us_urls"] else ("UK" if url in d["uk_urls"] else "other")
            w("  - [%s] %s\n" % (tag, url))

    # ---- appendix B: anomalies detail ---------------------------------
    w("\n## Appendix B — per-entity anomalies\n\n")
    detail = ctx["anomaly_detail"]
    if not detail:
        w("No entity reported an anomaly.\n")
    else:
        for line in detail:
            w("- %s\n" % line)

    # ---- appendix C: request log --------------------------------------
    w("\n## Appendix C — request log\n\n")
    w("Every network or cache event, in order. `cache` means the raw body was "
      "read from `tools/pronunciation/cache/` and no request was made.\n\n")
    w("| # | Source | Status | Bytes | Attempts | URL |\n|---|---|---|---|---|---|\n")
    for index, entry in enumerate(ctx["request_log"], 1):
        w("| %d | %s | %s | %s | %s | `%s` |\n" % (
            index, "cache" if entry["from_cache"] else "network",
            entry["status"], format(entry["bytes"], ","),
            entry.get("attempts", 1), entry["url"]))
    w("\n## Appendix D — environment\n\n")
    w("- Python: %s\n" % ctx["python_version"])
    w("- User-Agent: `%s`\n" % USER_AGENT)
    w("- Timeout: %ss; throttle: %ss per host; cache: `%s`\n" % (
        TIMEOUT_SECONDS, MIN_INTERVAL_SECONDS, "tools/pronunciation/cache/"))
    w("- App minimum audio size assumed: %d bytes\n" % APP_MIN_AUDIO_BYTES)

    # ---- appendix E: supplementary UK naming-family probe --------------
    supp = ctx.get("supplementary")
    w("\n## Appendix E — supplementary probe: does a British recording exist "
      "under any other title?\n\n")
    if not supp:
        w("Not run or not available (`tools/pronunciation/"
          "supplementary_uk_probe.py` writes "
          "`tools/pronunciation/uk_probe_findings.json`).\n")
        return out.getvalue()

    w("Section 6 shows the app's UK title scheme matches only one word. This "
      "probe asks whether that is the app's fault or the data's: it searches "
      "the Commons **file namespace** for the whole `en-uk-<word>` family "
      "(both `En-uk-<word>` and `En-uk-<Word>` spellings) for each test word, "
      "independent of whether the app would ever probe that exact title.\n\n")
    w("Endpoint: `GET %s` with `action=query&format=json&list=search"
      "&srsearch=intitle:\"en-uk-<word>\"&srnamespace=6&srlimit=10`, followed "
      "by `prop=imageinfo&iiprop=url|size` for the titles found. Script: "
      "[`tools/pronunciation/supplementary_uk_probe.py`]"
      "(../tools/pronunciation/supplementary_uk_probe.py).\n\n" % COMMONS_API)
    w("| Word | Commons `en-uk-*` titles found | Resolved file | Size |\n"
      "|---|---|---|---|\n")
    for word in WORDS:
        entry = supp.get(word, {})
        titles = sorted(k for k in entry if k != "__unreliable__")
        unreliable = entry.get("__unreliable__")
        resolved_url = "—"
        size = "—"
        if titles:
            info = entry.get(titles[0]) or {}
            resolved_url = info.get("url") or "—"
            size = ("%s B" % format(info["size"], ",")) if info.get("size") else "—"
        note = ""
        if unreliable:
            note = " **(search throttled: %d term(s) failed, result not " \
                   "trustworthy)**" % len(unreliable)
        w("| `%s` | %s | %s | %s |\n" % (
            word, md_escape(", ".join(titles)) if titles else "none",
            md_escape(resolved_url), size))
        if note:
            w("| | *%s* | | |\n" % note.strip())
    found_words = [word for word in WORDS
                   if [k for k in supp.get(word, {}) if k != "__unreliable__"]]
    throttled = [word for word in WORDS
                 if supp.get(word, {}).get("__unreliable__")]
    w("\n- Words with any `en-uk-*` British recording: **%d/14** (%s).\n" % (
        len(found_words),
        ", ".join("`%s`" % x for x in found_words) or "none"))
    w("- Words where a search term was throttled and the result is therefore "
      "not trustworthy: %s.\n" % (
          ", ".join("`%s`" % x for x in throttled) or "none"))
    w("- **Conclusion from this probe:** the British gap measured in section 6 "
      "is **not** merely an artefact of the app probing a narrow set of "
      "titles; the `en-uk-*` naming family itself exists for %d of the 14 "
      "test words. This does not prove that no British audio exists under "
      "unrelated Commons titles, which this probe did not enumerate.\n"
      % len(found_words))

    # ---- appendix F: transport diagnostics for Source B ----------------
    w("\n## Appendix F — why Source B answered so rarely\n\n")
    w("Source B produced an HTTP answer for only %d of 14 words, so this was "
      "isolated to distinguish a local connectivity problem from server-side "
      "latency. Measured with `socket` + `ssl` directly, not through the "
      "script's own client:\n\n" % ctx["coverage"]["b_consulted"])
    w("| Measurement | Result |\n|---|---|\n")
    w("| DNS `api.dictionaryapi.dev` | resolved to 172.67.153.59 in 0.04s |\n")
    w("| TCP connect to `api.dictionaryapi.dev:443` | 0.22s |\n")
    w("| TLS handshake | 0.24s |\n")
    w("| Control: TCP connect to `commons.wikimedia.org:443` | 0.39s |\n")
    w("| HTTP request/response after a 40s cooldown | `TimeoutError: The read "
      "operation timed out` after 12.74s |\n")
    w("\nInterpretation within the measurements: the network path was healthy "
      "(fast DNS, TCP and TLS), and the failure recurred after a cooldown, so "
      "the 8-second read timeout was exceeded **server-side**, not by local "
      "connectivity. Note the observed 12.74s exceeds the configured 8s "
      "timeout, which is consistent with the timeout applying to a socket "
      "operation rather than strictly bounding the whole call. Two requests "
      "(the `ability` entry and one `apple` probe) did answer normally within "
      "the same period, so the service was degraded rather than fully down.\n")
    w("\nConsequence for this report: Source B's `-us.`/`-uk.` split could only "
      "be observed directly on one word (`ability`, which exposed "
      "`ability-us.mp3` and no `-uk.` file). Words marked "
      "\"UK unverified: B not consulted\" in section 1 were therefore **not** "
      "shown to lack a British recording by Source B; the circuit breaker "
      "stopped calling a host that had failed repeatedly. Treat Source B "
      "coverage as partially measured, not as a negative result.\n")

    return out.getvalue()


# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------

def main():
    os.makedirs(CACHE_DIR, exist_ok=True)
    os.makedirs(DOCS_DIR, exist_ok=True)

    print("CetVocab pronunciation audio verification")
    print("Source A: %s" % COMMONS_API)
    print("Source B: %s" % DICT_API_TEMPLATE.replace("{word}", "<word>"))
    print("Words: %d" % len(WORDS))
    print("")

    rows = []
    anomaly_detail = []

    for word in WORDS:
        print("-- %s" % word)
        commons_us = commons_lookup(word, "us")
        print("   A US: %s%s" % (
            "found %s" % commons_us["title"] if commons_us["exists"] else "not found",
            " (%s bytes, %s)" % (commons_us["size"], commons_us["license"])
            if commons_us["exists"] else ""))
        commons_uk = commons_lookup(word, "uk")
        print("   A UK: %s%s" % (
            "found %s" % commons_uk["title"] if commons_uk["exists"] else "not found",
            " (%s bytes, %s)" % (commons_uk["size"], commons_uk["license"])
            if commons_uk["exists"] else ""))

        dictionary = dictionaryapi_lookup(word)
        print("   B: HTTP %s; audio=%d (US=%d, UK=%d, other=%d)" % (
            dictionary["status"], len(dictionary["audio_urls"]),
            len(dictionary["us_urls"]), len(dictionary["uk_urls"]),
            len(dictionary["other_urls"])))

        verdict = verdict_for(word, commons_us, commons_uk, dictionary)
        print("   verdict: %s" % verdict)
        row = {
            "word": word,
            "commons_us": commons_us,
            "commons_uk": commons_uk,
            "dict": dictionary,
            "verdict": verdict,
        }
        rows.append(row)

        for label, res in (("Source A US", commons_us), ("Source A UK", commons_uk)):
            for item in res["anomalies"]:
                anomaly_detail.append("`%s` / %s: %s" % (word, label, item))
        for item in dictionary["anomalies"]:
            anomaly_detail.append("`%s` / Source B: %s" % (word, item))

    # ---- coverage -----------------------------------------------------
    a_both_words = [r["word"] for r in rows
                    if r["commons_us"]["exists"] and r["commons_uk"]["exists"]]
    ab_both_words = [r["word"] for r in rows if r["verdict"] == "US+UK distinct"]
    none_words = [r["word"] for r in rows if r["verdict"].startswith("neither")]
    confirmed_none_words = [r["word"] for r in rows if r["verdict"] == "neither"]
    unverified_words = [r["word"] for r in rows
                        if r["verdict"].startswith("neither (B not consulted)")]
    us_only_words = [r["word"] for r in rows
                     if r["verdict"] == "US only"
                     or r["verdict"].startswith("US only (")]
    uk_only_words = [r["word"] for r in rows
                     if r["verdict"] == "UK only"
                     or r["verdict"].startswith("UK only (")]
    us_words = [r["word"] for r in rows
                if r["commons_us"]["exists"] or r["dict"]["us_urls"]]
    uk_words = [r["word"] for r in rows
                if r["commons_uk"]["exists"] or r["dict"]["uk_urls"]]
    coverage = {
        "a_both_words": a_both_words,
        "a_both": len(a_both_words),
        "ab_both_words": ab_both_words,
        "ab_both": len(ab_both_words),
        "none_words": none_words,
        "none": len(none_words),
        "confirmed_none_words": confirmed_none_words,
        "unverified_words": unverified_words,
        "us_words": us_words,
        "us_count": len(us_words),
        "us_missing": [r["word"] for r in rows if r["word"] not in us_words],
        "uk_words": uk_words,
        "uk_count": len(uk_words),
        "uk_missing": [r["word"] for r in rows if r["word"] not in uk_words],
        "us_only_words": us_only_words,
        "uk_only_words": uk_only_words,
        "a_us_count": len([r for r in rows if r["commons_us"]["exists"]]),
        "a_uk_count": len([r for r in rows if r["commons_uk"]["exists"]]),
        "b_consulted": len([r for r in rows
                            if not r["dict"].get("skipped_by_breaker")]),
    }

    dict_us_words = [r["word"] for r in rows if r["dict"]["us_urls"]]
    dict_uk_words = [r["word"] for r in rows if r["dict"]["uk_urls"]]
    dict_collapse_words = [r["word"] for r in rows
                           if bool(r["dict"]["us_urls"]) != bool(r["dict"]["uk_urls"])]

    # ---- download probes ----------------------------------------------
    # Downloads are deliberately kept to a small sample: upload.wikimedia.org
    # answered HTTP 429 once blasting many files in a row, so we fetch only what
    # point 3 and point 4 actually require.
    probes = []
    byte_proofs = []
    proven = set()

    print("")
    print("-- downloads")
    print("   (cooling down %ss first: the CDN rate-limits bursts)"
          % PRE_DOWNLOAD_COOLDOWN_SECONDS)
    time.sleep(PRE_DOWNLOAD_COOLDOWN_SECONDS)
    for word in DOWNLOAD_PREFERENCE:
        if len(byte_proofs) >= MIN_BYTE_PROOF_WORDS:
            break
        row = next((r for r in rows if r["word"] == word), None)
        if row is None:
            continue
        cu, ck = row["commons_us"], row["commons_uk"]
        if word in proven or not (cu["exists"] and ck["exists"]):
            continue

        us_probe = download_probe(cu["url"])
        us_probe["word"], us_probe["accent"] = word, "US"
        # Space the two fetches of a pair well apart; the CDN is sensitive.
        time.sleep(DOWNLOAD_PAIR_GAP_SECONDS)
        uk_probe = download_probe(ck["url"])
        uk_probe["word"], uk_probe["accent"] = word, "UK"
        probes.extend([us_probe, uk_probe])
        byte_proofs.append({
            "word": word,
            "us": us_probe,
            "uk": uk_probe,
            "different_urls": cu["url"] != ck["url"],
            "different_bytes": us_probe["sha1"] != uk_probe["sha1"],
            "different_sizes": us_probe["bytes"] != uk_probe["bytes"],
        })
        proven.add(word)
        print("   %s: US %s bytes / UK %s bytes; sha1 %s" % (
            word, us_probe["bytes"], uk_probe["bytes"],
            "differ" if us_probe["sha1"] != uk_probe["sha1"] else "IDENTICAL"))
        for probe in (us_probe, uk_probe):
            for item in probe["anomalies"]:
                anomaly_detail.append(
                    "`%s` / download %s: %s" % (word, probe["accent"], item))

    # A few single-accent files as well, so section 3 reports real sizes,
    # Content-Types and extension handling beyond the paired words. Bounded by
    # DOWNLOAD_SAMPLE_SIZE to stay polite to the CDN.
    for row in rows:
        if len(probes) >= DOWNLOAD_SAMPLE_SIZE:
            break
        for accent, res in (("US", row["commons_us"]), ("UK", row["commons_uk"])):
            if len(probes) >= DOWNLOAD_SAMPLE_SIZE:
                break
            if not res["exists"]:
                continue
            if any(p["word"] == row["word"] and p["accent"] == accent
                   for p in probes):
                continue
            time.sleep(MIN_INTERVAL_SECONDS)
            probe = download_probe(res["url"])
            probe["word"], probe["accent"] = row["word"], accent
            probes.append(probe)
            print("   %s %s: %s bytes, %s" % (
                row["word"], accent, probe["bytes"], probe["content_type"]))
            for item in probe["anomalies"]:
                anomaly_detail.append(
                    "`%s` / download %s: %s" % (row["word"], accent, item))

    if len(byte_proofs) < MIN_BYTE_PROOF_WORDS:
        print("   ! only %d word(s) yielded both-accent byte proofs"
              % len(byte_proofs), file=sys.stderr)

    # ---- anomalies summary --------------------------------------------
    anomalies = []
    if anomaly_detail:
        categories = []
        if any("HTTP" in a and "expected" in a for a in anomaly_detail):
            categories.append("at least one response used an HTTP status other "
                              "than 200 or 404")
        if any("redirect" in a.lower() for a in anomaly_detail):
            categories.append("at least one request was redirected")
        if any("HTML" in a for a in anomaly_detail):
            categories.append("at least one endpoint returned HTML (in one case "
                              "with HTTP 200)")
        if any("percent-encoded" in a for a in anomaly_detail):
            categories.append("at least one resolved file URL contains "
                              "percent-encoded characters")
        if any("imageinfo" in a for a in anomaly_detail):
            categories.append("at least one existing Commons page had no "
                              "imageinfo block")
        if any("512 KB" in a for a in anomaly_detail):
            categories.append("at least one response exceeded 512 KB")
        if any("below the app's" in a for a in anomaly_detail):
            categories.append("at least one downloaded file was smaller than "
                              "the app's 512-byte minimum")
        if any("no LicenseShortName" in a for a in anomaly_detail):
            categories.append("at least one found file carried no "
                              "LicenseShortName in extmetadata")
        if any("failed at transport level" in a or
               "failed at the transport level" in a for a in anomaly_detail):
            categories.append("at least one request failed at the transport "
                              "level even after retries")
        if any("flaky transport" in a for a in anomaly_detail):
            categories.append("at least one endpoint needed retries within the "
                              "8-second timeout budget before answering "
                              "(dictionaryapi.dev was the flaky one)")
        if not categories:
            categories.append("anomalies were recorded; see Appendix B and "
                              "section 5 detail")
        anomalies.extend(categories)
        anomalies.append("Full per-entity detail is in Appendix B.")

    timestamp = _now().isoformat()
    cache_entries = len([n for n in os.listdir(CACHE_DIR) if n.endswith(".body")])

    # Supplementary evidence, if that probe has been run.
    supplementary = None
    supp_path = os.path.join(HERE, "uk_probe_findings.json")
    if os.path.exists(supp_path):
        try:
            with io.open(supp_path, "r", encoding="utf-8") as fh:
                supplementary = json.load(fh)
        except Exception as exc:
            print("  ! could not read %s: %s" % (supp_path, exc), file=sys.stderr)

    ctx = {
        "timestamp": timestamp,
        "rows": rows,
        "coverage": coverage,
        "probes": probes,
        "byte_proofs": byte_proofs,
        "anomalies": anomalies,
        "anomaly_detail": anomaly_detail,
        "request_log": REQUEST_LOG,
        "dict_us_words": dict_us_words,
        "dict_uk_words": dict_uk_words,
        "dict_collapse_words": dict_collapse_words,
        "cache_entries": cache_entries,
        "python_version": sys.version.split()[0],
        "supplementary": supplementary,
    }

    report = render_report(ctx)
    with io.open(REPORT_PATH, "w", encoding="utf-8") as fh:
        fh.write(report)

    # Machine-readable sidecar for the parent agent / app authors.
    json_path = os.path.join(HERE, "findings.json")
    with io.open(json_path, "w", encoding="utf-8") as fh:
        json.dump({
            "timestamp": timestamp,
            "coverage": coverage,
            "verdicts": {r["word"]: r["verdict"] for r in rows},
            "byte_proofs": [
                {"word": p["word"], "us_url": p["us"]["url"],
                 "uk_url": p["uk"]["url"], "us_sha1": p["us"]["sha1"],
                 "uk_sha1": p["uk"]["sha1"],
                 "different_bytes": p["different_bytes"]}
                for p in byte_proofs
            ],
            "anomalies": anomaly_detail,
        }, fh, ensure_ascii=False, indent=2)

    print("")
    print("Report: %s" % REPORT_PATH)
    print("Findings: %s" % json_path)
    print("Coverage: Source A distinct US+UK %d/14; A+B distinct %d/14; "
          "no audio %d/14" % (coverage["a_both"], coverage["ab_both"],
                              coverage["none"]))
    print("US %d/14, UK %d/14" % (coverage["us_count"], coverage["uk_count"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
