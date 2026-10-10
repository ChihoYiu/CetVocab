# Pronunciation audio verification (live internet)

> **注意（迭代 3 起）：本文档描述的网络音频模块已被移除。**
> 应用现已改为使用系统文字转语音（TTS），不再提供美式/英式选项，也不再申请 `INTERNET` 权限。
> 本文档保留作为历史证据，因为其中的实测结论（免费真人录音的英式覆盖只有 1/14、美式 11/14，
> 且 `upload.wikimedia.org` 会返回 HTTP 429 的 HTML 错误页）正是改用系统 TTS 的依据之一。
> 当前发音实现见 `docs/ITERATION_3_REPORT.md`。


Generated: **2026-10-10T04:04:33.405111+00:00**

Script: [`tools/pronunciation/verify_audio.py`](../tools/pronunciation/verify_audio.py)  
Raw response cache: `tools/pronunciation/cache/` (63 entries, raw bodies plus JSON metadata sidecars)

This report contains only what the script measured against the live network on the timestamp above. No behaviour is inferred beyond the observations listed.

## Method

Both sources are **keyless** and were called directly with `urllib.request`, an 8-second timeout, no third-party packages, and the User-Agent `CetVocab-verification/1.0 (offline-first vocabulary study app; contact: local)`. Requests are throttled to at most one per second per host, and every response body is cached under `tools/pronunciation/cache/` so re-runs perform no network I/O.

Because `api.dictionaryapi.dev` intermittently exceeded the 8-second read timeout during this run, a failed *transport* attempt (no HTTP status received) is retried up to 4 times with a 1.0s pause, always respecting the one-request-per-second-per-host rule. Only real HTTP answers are cached, so a timeout is never frozen into the cache as a fake “no data” result, and every retry is visible in Appendix C.

### Source A — Wikimedia Commons (human recordings)

1. Candidate file titles for a word are built exactly as the app does them: `File:En-us-<word>.ogg`, `File:En-us-<word>.oga` (American) and `File:En-uk-<word>.ogg`, `File:En-uk-<word>.oga` (British), where `<word>` is the lower-case spelling with spaces replaced by `_`. Where the word is normally capitalised, a capitalised variant (`<Word>`) is also probed. Words probed with a capitalised variant: `characteristically`, `internationalization`, `misunderstanding`, `naive`, `queue`, `rendezvous`, `rhythm`, `well-being`, `x-ray`, `xylophone`.
2. Titles are queried against `GET https://commons.wikimedia.org/w/api.php` with `action=query&format=json&prop=imageinfo&iiprop=url|size|extmetadata` and `titles=<titles joined with | , encoded as %7C>`. The script issues one request per word and accent, covering that accent's four candidate titles; the exact URLs are in Appendix A.
3. Per word and accent it records existence, the resolved `url`, the byte `size`, and from `extmetadata` the `LicenseShortName` plus the `Artist`/`Credit` values.

### Source B — dictionaryapi.dev (Wiktionary)

1. `GET https://api.dictionaryapi.dev/api/v2/entries/en/<word>` with the word percent-encoded.
2. Every `phonetics[].audio` URL across all returned entries is collected and classified by filename suffix: `-us.mp3/.ogg/.wav` as American, `-uk.mp3/.ogg/.wav` as British, everything else as other.
3. The report states whether a `-us.` and a `-uk.` file exist **separately** for the word.

### Verdict rules

- `US+UK distinct` — a US recording and a UK recording both resolve, and they are not the same URL.
- `US only` / `UK only` — exactly one accent resolves.
- `neither` — no recording from either source; the app falls back to synthetic speech.
- Source A takes precedence per accent; Source B fills the gap. A word counts as resolved for an accent if either source supplies it.

### Byte-level proofs

For the words in section 3 the script downloaded the audio and recorded the real byte count and `Content-Type`; for the words in section 4 it downloaded **both** accents and compared SHA-1 digests to prove the recordings are genuinely different bytes.

## 1. Per-word results

`A US` / `A UK` = Wikimedia Commons; `B US` / `B UK` = dictionaryapi.dev. Sizes are the Commons-reported byte sizes.

| Word | A US exists | A US size | A US licence | A UK exists | A UK size | A UK licence | B US URL | B UK URL | Verdict |
|---|---|---|---|---|---|---|---|---|---|
| `apple` | yes | 14,420 B | CC BY-SA 3.0 | yes | 16,265 B | CC BY 3.0 us | no | no | **US+UK distinct** |
| `abandon` | yes | 13,368 B | CC BY-SA 3.0 | no | — | — | no | no | **US only** |
| `ability` | yes | 13,472 B | CC BY-SA 3.0 | no | — | — | yes | no | **US only** |
| `internationalization` | no | — | — | no | — | — | no | no | **neither** |
| `characteristically` | no | — | — | no | — | — | no | no | **neither** |
| `misunderstanding` | yes | 20,273 B | CC BY-SA 3.0 | no | — | — | no | no | **US only** |
| `rhythm` | yes | 12,005 B | CC BY-SA 3.0 | no | — | — | no | no | **US only (UK unverified: B not consulted)** |
| `queue` | yes | 14,187 B | CC BY-SA 3.0 | no | — | — | no | no | **US only (UK unverified: B not consulted)** |
| `xylophone` | yes | 13,657 B | CC BY-SA 3.0 | no | — | — | no | no | **US only (UK unverified: B not consulted)** |
| `well-being` | no | — | — | no | — | — | no | no | **neither (B not consulted)** |
| `x-ray` | yes | 13,589 B | CC BY-SA 3.0 | no | — | — | no | no | **US only (UK unverified: B not consulted)** |
| `don't` | yes | 11,185 B | CC BY-SA 3.0 | no | — | — | no | no | **US only (UK unverified: B not consulted)** |
| `naive` | yes | 17,028 B | CC BY-SA 4.0 | no | — | — | no | no | **US only (UK unverified: B not consulted)** |
| `rendezvous` | yes | 12,254 B | CC BY-SA 3.0 | no | — | — | no | no | **US only (UK unverified: B not consulted)** |

## 2. Coverage summary

Fourteen test words were probed end to end (`normal` 3, `long` 3, `special spelling` 8).

| Measure | Words | Count |
|---|---|---|
| Source A alone resolves **distinct** US and UK audio | `apple` | 1/14 |
| Source A + Source B resolves **distinct** US and UK audio | `apple` | 1/14 |
| Resolve **no audio at all** (synthetic-speech fallback) | `internationalization`, `characteristically` | 2/14 |
| Source A found nothing **and** Source B was never consulted (outage) — outcome unverified | `well-being` | 1/14 |
| Source A alone resolves a US recording | — | 11/14 |
| Source A alone resolves a UK recording | — | 1/14 |
| Source B consultations that produced an HTTP answer | — | 6/14 |

**Accents separately.**

| Accent | Words with any recording | Count | Words missing |
|---|---|---|---|
| US | `apple`, `abandon`, `ability`, `misunderstanding`, `rhythm`, `queue`, `xylophone`, `x-ray`, `don't`, `naive`, `rendezvous` | 11/14 | `internationalization`, `characteristically`, `well-being` |
| UK | `apple` | 1/14 | `abandon`, `ability`, `internationalization`, `characteristically`, `misunderstanding`, `rhythm`, `queue`, `xylophone`, `well-being`, `x-ray`, `don't`, `naive`, `rendezvous` |

## 3. Download probes (real size and Content-Type)

Files below the app's 512-byte minimum would be rejected by the app. `ext` is the extension the app derives from the URL.

| Word | Accent | HTTP | Content-Type | Real bytes | App extension | SHA-1 (short) | Usable by app |
|---|---|---|---|---|---|---|---|
| `apple` | US | 200 | application/ogg | 14,420 | `.ogg` | `3f8c6ca43d97` | yes |
| `apple` | UK | 200 | application/ogg | 16,265 | `.ogg` | `c147ea0c178c` | yes |
| `abandon` | US | 429 | text/html; charset=utf-8 | 1,965 | `.ogg` | `04c4af326970` | **no** |
| `ability` | US | 200 | application/ogg | 13,472 | `.ogg` | `6fde79df6045` | yes |
| `misunderstanding` | US | 429 | text/html; charset=utf-8 | 1,965 | `.ogg` | `80174663af6d` | **no** |

## 4. US vs UK must be different bytes

Both accents were downloaded for each word below and hashed. SHA-1 digests are of the downloaded bytes.

### `apple`

| Accent | URL | Bytes | Content-Type | SHA-1 |
|---|---|---|---|---|
| US | `https://upload.wikimedia.org/wikipedia/commons/9/9a/En-us-apple.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original` | 14,420 | application/ogg | `3f8c6ca43d97920a12e2abf17b7832525e10b21c` |
| UK | `https://upload.wikimedia.org/wikipedia/commons/c/c8/En-uk-apple.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original` | 16,265 | application/ogg | `c147ea0c178c3f6c285a6875782a4ba478080903` |

- Different URLs: **yes**
- Different bytes (SHA-1 differs): **yes**
- Byte-size difference: **yes**

## 5. Behaviour that would break the app's assumptions

Checked for and found:

- at least one response used an HTTP status other than 200 or 404
- at least one endpoint returned HTML (in one case with HTTP 200)
- at least one resolved file URL contains percent-encoded characters
- at least one existing Commons page had no imageinfo block
- at least one request failed at the transport level even after retries
- at least one endpoint needed retries within the 8-second timeout budget before answering (dictionaryapi.dev was the flaky one)
- Full per-entity detail is in Appendix B.

### Highest-impact finding: `upload.wikimedia.org` serves an HTML error page with HTTP 429

The Commons **API** host answered every request correctly, but the **file CDN** host rate-limited this client and returned an HTML error page. The app's stated assumptions only allow 200 and 404, so a 429 is unhandled. The measured evidence:

| Word | Accent | HTTP | Content-Type | Body bytes | `Retry-After` | Above app's 512-byte floor? |
|---|---|---|---|---|---|---|
| `abandon` | US | 429 | text/html; charset=utf-8 | 1,965 | 10 | **yes — 1965 bytes, so the size check passes** |
| `misunderstanding` | US | 429 | text/html; charset=utf-8 | 1,965 | 10 | **yes — 1965 bytes, so the size check passes** |

Why this matters for the app as specified:

- The error body is **1,965 bytes**, which is **above the app's 512-byte minimum**, so a size-based guard would accept it.
- The URL still ends in `.ogg`, so the extension the app derives from the URL is plausible and the file is written to the cache as `.ogg`.
- The bytes are HTML, so decoding them as Ogg/MP3 audio fails. The practical outcome is an unplayable cached file, or the app's "no recording found" fallback firing after it has already spent a download.
- `Retry-After: 10` was returned, so the condition is recoverable and a bounded retry (or a status check) would fix it. The count was reduced between runs; see Appendix C for the per-request pattern.

## 6. Words hitting the app's "no recording found" path, and US vs UK coverage

- **No recording at all (synthetic speech fallback):** `internationalization`, `characteristically`.
- **Source A found nothing and Source B was never consulted (outage), so these are unverified rather than proven empty:** `well-being`. In the app they would take the "no recording found" path, but the measurement cannot confirm that no audio exists.
- **US but no UK:** `abandon`, `ability`, `misunderstanding`, `rhythm`, `queue`, `xylophone`, `x-ray`, `don't`, `naive`, `rendezvous`.
- **US coverage:** 11/14 words. **UK coverage:** 1/14 words. Difference: 10 word(s).
- Measured verdict on whether the British accent is materially worse covered: **yes** (UK 1/14 vs US 11/14; UK-only words: 0; US-only words: 10; Source A alone found a UK file for 1/14 words versus a US file for 11/14).
- **Candidate-title naming caveat:** a Commons namespace search for `intitle:"en-uk-<word>"` returned a file for exactly `apple` and nothing for the other 13 words, matching the title-existence results above. So the UK gap reflects the app's naming scheme as much as real file availability: British recordings may exist under other Commons titles that the app never probes (this run did not enumerate those, and does not claim otherwise).
- Source B `-uk.` files found for: none.
- Source B `-us.` files found for: `ability`.
- **Accent collapse risk in Source B:** for `ability` the service returned only one accent file, so a naive "use whatever audio exists" implementation would play the same recording for both accents.

## Appendix A — raw URLs and raw observations

### Source A candidate titles and API calls

#### `apple`

- **US** titles probed: `File:En-us-apple.ogg`, `File:En-us-apple.oga`
  - FOUND `File:En-us-apple.ogg`
  - URL: https://upload.wikimedia.org/wikipedia/commons/9/9a/En-us-apple.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
  - size: 14420 bytes; dimensions: 0x0
  - licence: CC BY-SA 3.0
  - artist: Dvortygirl
  - credit: Own work
  - file page: https://commons.wikimedia.org/wiki/File:En-us-apple.ogg
- **UK** titles probed: `File:En-uk-apple.ogg`, `File:En-uk-apple.oga`
  - FOUND `File:En-uk-apple.ogg`
  - URL: https://upload.wikimedia.org/wikipedia/commons/c/c8/En-uk-apple.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
  - size: 16265 bytes; dimensions: 0x0
  - licence: CC BY 3.0 us
  - artist: Association Shtooka, Judith Franck
  - credit: Shtooka Project
  - file page: https://commons.wikimedia.org/wiki/File:En-uk-apple.ogg
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-apple.ogg%7CFile%3AEn-us-apple.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-apple.ogg%7CFile%3AEn-uk-apple.oga

#### `abandon`

- **US** titles probed: `File:En-us-abandon.ogg`, `File:En-us-abandon.oga`
  - FOUND `File:En-us-abandon.ogg`
  - URL: https://upload.wikimedia.org/wikipedia/commons/d/dd/En-us-abandon.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
  - size: 13368 bytes; dimensions: 0x0
  - licence: CC BY-SA 3.0
  - artist: Dvortygirl
  - credit: Self, recorded using the program Shtooka .
  - file page: https://commons.wikimedia.org/wiki/File:En-us-abandon.ogg
- **UK** titles probed: `File:En-uk-abandon.ogg`, `File:En-uk-abandon.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-abandon.ogg%7CFile%3AEn-us-abandon.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-abandon.ogg%7CFile%3AEn-uk-abandon.oga

#### `ability`

- **US** titles probed: `File:En-us-ability.ogg`, `File:En-us-ability.oga`
  - FOUND `File:En-us-ability.ogg`
  - URL: https://upload.wikimedia.org/wikipedia/commons/6/66/En-us-ability.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
  - size: 13472 bytes; dimensions: 0x0
  - licence: CC BY-SA 3.0
  - artist: Dvortygirl
  - credit: Self, recorded using the program Shtooka .
  - file page: https://commons.wikimedia.org/wiki/File:En-us-ability.ogg
- **UK** titles probed: `File:En-uk-ability.ogg`, `File:En-uk-ability.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-ability.ogg%7CFile%3AEn-us-ability.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-ability.ogg%7CFile%3AEn-uk-ability.oga

#### `internationalization`

- **US** titles probed: `File:En-us-internationalization.ogg`, `File:En-us-internationalization.oga`, `File:En-us-Internationalization.ogg`, `File:En-us-Internationalization.oga`
  - not found
- **UK** titles probed: `File:En-uk-internationalization.ogg`, `File:En-uk-internationalization.oga`, `File:En-uk-Internationalization.ogg`, `File:En-uk-Internationalization.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-internationalization.ogg%7CFile%3AEn-us-internationalization.oga%7CFile%3AEn-us-Internationalization.ogg%7CFile%3AEn-us-Internationalization.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-internationalization.ogg%7CFile%3AEn-uk-internationalization.oga%7CFile%3AEn-uk-Internationalization.ogg%7CFile%3AEn-uk-Internationalization.oga

#### `characteristically`

- **US** titles probed: `File:En-us-characteristically.ogg`, `File:En-us-characteristically.oga`, `File:En-us-Characteristically.ogg`, `File:En-us-Characteristically.oga`
  - not found
- **UK** titles probed: `File:En-uk-characteristically.ogg`, `File:En-uk-characteristically.oga`, `File:En-uk-Characteristically.ogg`, `File:En-uk-Characteristically.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-characteristically.ogg%7CFile%3AEn-us-characteristically.oga%7CFile%3AEn-us-Characteristically.ogg%7CFile%3AEn-us-Characteristically.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-characteristically.ogg%7CFile%3AEn-uk-characteristically.oga%7CFile%3AEn-uk-Characteristically.ogg%7CFile%3AEn-uk-Characteristically.oga

#### `misunderstanding`

- **US** titles probed: `File:En-us-misunderstanding.ogg`, `File:En-us-misunderstanding.oga`, `File:En-us-Misunderstanding.ogg`, `File:En-us-Misunderstanding.oga`
  - FOUND `File:En-us-misunderstanding.ogg`
  - URL: https://upload.wikimedia.org/wikipedia/commons/6/66/En-us-misunderstanding.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
  - size: 20273 bytes; dimensions: 0x0
  - licence: CC BY-SA 3.0
  - artist: Neskaya
  - credit: Self, recorded using the program 'Shtooka'.
  - file page: https://commons.wikimedia.org/wiki/File:En-us-misunderstanding.ogg
- **UK** titles probed: `File:En-uk-misunderstanding.ogg`, `File:En-uk-misunderstanding.oga`, `File:En-uk-Misunderstanding.ogg`, `File:En-uk-Misunderstanding.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-misunderstanding.ogg%7CFile%3AEn-us-misunderstanding.oga%7CFile%3AEn-us-Misunderstanding.ogg%7CFile%3AEn-us-Misunderstanding.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-misunderstanding.ogg%7CFile%3AEn-uk-misunderstanding.oga%7CFile%3AEn-uk-Misunderstanding.ogg%7CFile%3AEn-uk-Misunderstanding.oga

#### `rhythm`

- **US** titles probed: `File:En-us-rhythm.ogg`, `File:En-us-rhythm.oga`, `File:En-us-Rhythm.ogg`, `File:En-us-Rhythm.oga`
  - FOUND `File:En-us-rhythm.ogg`
  - URL: https://upload.wikimedia.org/wikipedia/commons/a/a5/En-us-rhythm.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
  - size: 12005 bytes; dimensions: 0x0
  - licence: CC BY-SA 3.0
  - artist: Dvortygirl
  - credit: Own work
  - file page: https://commons.wikimedia.org/wiki/File:En-us-rhythm.ogg
- **UK** titles probed: `File:En-uk-rhythm.ogg`, `File:En-uk-rhythm.oga`, `File:En-uk-Rhythm.ogg`, `File:En-uk-Rhythm.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-rhythm.ogg%7CFile%3AEn-us-rhythm.oga%7CFile%3AEn-us-Rhythm.ogg%7CFile%3AEn-us-Rhythm.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-rhythm.ogg%7CFile%3AEn-uk-rhythm.oga%7CFile%3AEn-uk-Rhythm.ogg%7CFile%3AEn-uk-Rhythm.oga

#### `queue`

- **US** titles probed: `File:En-us-queue.ogg`, `File:En-us-queue.oga`, `File:En-us-Queue.ogg`, `File:En-us-Queue.oga`
  - FOUND `File:En-us-queue.ogg`
  - URL: https://upload.wikimedia.org/wikipedia/commons/e/e9/En-us-queue.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
  - size: 14187 bytes; dimensions: 0x0
  - licence: CC BY-SA 3.0
  - artist: EncycloPetey
  - credit: Own work
  - file page: https://commons.wikimedia.org/wiki/File:En-us-queue.ogg
- **UK** titles probed: `File:En-uk-queue.ogg`, `File:En-uk-queue.oga`, `File:En-uk-Queue.ogg`, `File:En-uk-Queue.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-queue.ogg%7CFile%3AEn-us-queue.oga%7CFile%3AEn-us-Queue.ogg%7CFile%3AEn-us-Queue.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-queue.ogg%7CFile%3AEn-uk-queue.oga%7CFile%3AEn-uk-Queue.ogg%7CFile%3AEn-uk-Queue.oga

#### `xylophone`

- **US** titles probed: `File:En-us-xylophone.ogg`, `File:En-us-xylophone.oga`, `File:En-us-Xylophone.ogg`, `File:En-us-Xylophone.oga`
  - FOUND `File:En-us-xylophone.ogg`
  - URL: https://upload.wikimedia.org/wikipedia/commons/3/37/En-us-xylophone.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
  - size: 13657 bytes; dimensions: 0x0
  - licence: CC BY-SA 3.0
  - artist: Dvortygirl
  - credit: Self, recorded using the program Shtooka .
  - file page: https://commons.wikimedia.org/wiki/File:En-us-xylophone.ogg
- **UK** titles probed: `File:En-uk-xylophone.ogg`, `File:En-uk-xylophone.oga`, `File:En-uk-Xylophone.ogg`, `File:En-uk-Xylophone.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-xylophone.ogg%7CFile%3AEn-us-xylophone.oga%7CFile%3AEn-us-Xylophone.ogg%7CFile%3AEn-us-Xylophone.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-xylophone.ogg%7CFile%3AEn-uk-xylophone.oga%7CFile%3AEn-uk-Xylophone.ogg%7CFile%3AEn-uk-Xylophone.oga

#### `well-being`

- **US** titles probed: `File:En-us-well-being.ogg`, `File:En-us-well-being.oga`, `File:En-us-Well-being.ogg`, `File:En-us-Well-being.oga`
  - not found
- **UK** titles probed: `File:En-uk-well-being.ogg`, `File:En-uk-well-being.oga`, `File:En-uk-Well-being.ogg`, `File:En-uk-Well-being.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-well-being.ogg%7CFile%3AEn-us-well-being.oga%7CFile%3AEn-us-Well-being.ogg%7CFile%3AEn-us-Well-being.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-well-being.ogg%7CFile%3AEn-uk-well-being.oga%7CFile%3AEn-uk-Well-being.ogg%7CFile%3AEn-uk-Well-being.oga

#### `x-ray`

- **US** titles probed: `File:En-us-x-ray.ogg`, `File:En-us-x-ray.oga`, `File:En-us-X-ray.ogg`, `File:En-us-X-ray.oga`
  - FOUND `File:En-us-X-ray.ogg`
  - URL: https://upload.wikimedia.org/wikipedia/commons/d/da/En-us-X-ray.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
  - size: 13589 bytes; dimensions: 0x0
  - licence: CC BY-SA 3.0
  - artist: Dvortygirl
  - credit: Self, recorded using the program Shtooka .
  - file page: https://commons.wikimedia.org/wiki/File:En-us-X-ray.ogg
- **UK** titles probed: `File:En-uk-x-ray.ogg`, `File:En-uk-x-ray.oga`, `File:En-uk-X-ray.ogg`, `File:En-uk-X-ray.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-x-ray.ogg%7CFile%3AEn-us-x-ray.oga%7CFile%3AEn-us-X-ray.ogg%7CFile%3AEn-us-X-ray.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-x-ray.ogg%7CFile%3AEn-uk-x-ray.oga%7CFile%3AEn-uk-X-ray.ogg%7CFile%3AEn-uk-X-ray.oga

#### `don't`

- **US** titles probed: `File:En-us-don't.ogg`, `File:En-us-don't.oga`
  - FOUND `File:En-us-don't.ogg`
  - URL: https://upload.wikimedia.org/wikipedia/commons/8/87/En-us-don%27t.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
  - size: 11185 bytes; dimensions: 0x0
  - licence: CC BY-SA 3.0
  - artist: Dvortygirl
  - credit: Own work
  - file page: https://commons.wikimedia.org/wiki/File:En-us-don%27t.ogg
- **UK** titles probed: `File:En-uk-don't.ogg`, `File:En-uk-don't.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-don%27t.ogg%7CFile%3AEn-us-don%27t.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-don%27t.ogg%7CFile%3AEn-uk-don%27t.oga

#### `naive`

- **US** titles probed: `File:En-us-naive.ogg`, `File:En-us-naive.oga`, `File:En-us-Naive.ogg`, `File:En-us-Naive.oga`
  - FOUND `File:En-us-naive.ogg`
  - URL: https://upload.wikimedia.org/wikipedia/commons/2/2d/En-us-naive.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
  - size: 17028 bytes; dimensions: 0x0
  - licence: CC BY-SA 4.0
  - artist: Sylvanmoon
  - credit: Own work
  - file page: https://commons.wikimedia.org/wiki/File:En-us-naive.ogg
- **UK** titles probed: `File:En-uk-naive.ogg`, `File:En-uk-naive.oga`, `File:En-uk-Naive.ogg`, `File:En-uk-Naive.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-naive.ogg%7CFile%3AEn-us-naive.oga%7CFile%3AEn-us-Naive.ogg%7CFile%3AEn-us-Naive.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-naive.ogg%7CFile%3AEn-uk-naive.oga%7CFile%3AEn-uk-Naive.ogg%7CFile%3AEn-uk-Naive.oga

#### `rendezvous`

- **US** titles probed: `File:En-us-rendezvous.ogg`, `File:En-us-rendezvous.oga`, `File:En-us-Rendezvous.ogg`, `File:En-us-Rendezvous.oga`
  - FOUND `File:En-us-rendezvous.ogg`
  - URL: https://upload.wikimedia.org/wikipedia/commons/e/e1/En-us-rendezvous.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
  - size: 12254 bytes; dimensions: 0x0
  - licence: CC BY-SA 3.0
  - artist: Dvortygirl
  - credit: Own work
  - file page: https://commons.wikimedia.org/wiki/File:En-us-rendezvous.ogg
- **UK** titles probed: `File:En-uk-rendezvous.ogg`, `File:En-uk-rendezvous.oga`, `File:En-uk-Rendezvous.ogg`, `File:En-uk-Rendezvous.oga`
  - not found
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-rendezvous.ogg%7CFile%3AEn-us-rendezvous.oga%7CFile%3AEn-us-Rendezvous.ogg%7CFile%3AEn-us-Rendezvous.oga
- API: https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-rendezvous.ogg%7CFile%3AEn-uk-rendezvous.oga%7CFile%3AEn-uk-Rendezvous.ogg%7CFile%3AEn-uk-Rendezvous.oga

### Source B calls and collected audio URLs

- `apple` — HTTP 0; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/apple
  - audio URLs: none
- `abandon` — HTTP 0; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/abandon
  - audio URLs: none
- `ability` — HTTP 200; entries: 1; phonetics objects: 1
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/ability
  - [US] https://api.dictionaryapi.dev/media/pronunciations/en/ability-us.mp3
- `internationalization` — HTTP 0; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/internationalization
  - audio URLs: none
- `characteristically` — HTTP 0; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/characteristically
  - audio URLs: none
- `misunderstanding` — HTTP 0; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/misunderstanding
  - audio URLs: none
- `rhythm` — HTTP not_attempted; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/rhythm
  - audio URLs: none
- `queue` — HTTP not_attempted; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/queue
  - audio URLs: none
- `xylophone` — HTTP not_attempted; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/xylophone
  - audio URLs: none
- `well-being` — HTTP not_attempted; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/well-being
  - audio URLs: none
- `x-ray` — HTTP not_attempted; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/x-ray
  - audio URLs: none
- `don't` — HTTP not_attempted; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/don%27t
  - audio URLs: none
- `naive` — HTTP not_attempted; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/naive
  - audio URLs: none
- `rendezvous` — HTTP not_attempted; entries: 0; phonetics objects: 0
  - API: https://api.dictionaryapi.dev/api/v2/entries/en/rendezvous
  - audio URLs: none

## Appendix B — per-entity anomalies

- `apple` / Source B: request failed at transport level: transport failure on all 4 attempts (last: TimeoutError: The read operation timed out)
- `abandon` / Source B: request failed at transport level: transport failure on all 4 attempts (last: TimeoutError: The read operation timed out)
- `internationalization` / Source B: request failed at transport level: transport failure on all 4 attempts (last: TimeoutError: The read operation timed out)
- `characteristically` / Source B: request failed at transport level: transport failure on all 4 attempts (last: TimeoutError: The read operation timed out)
- `misunderstanding` / Source B: request failed at transport level: transport failure on all 4 attempts (last: TimeoutError: The read operation timed out)
- `rhythm` / Source B: Source B not consulted: circuit opened after 3 consecutive transport failures
- `queue` / Source B: Source B not consulted: circuit opened after 3 consecutive transport failures
- `xylophone` / Source B: Source B not consulted: circuit opened after 3 consecutive transport failures
- `well-being` / Source B: Source B not consulted: circuit opened after 3 consecutive transport failures
- `x-ray` / Source B: Source B not consulted: circuit opened after 3 consecutive transport failures
- `don't` / Source A US: resolved URL contains percent-encoded characters: https://upload.wikimedia.org/wikipedia/commons/8/87/En-us-don%27t.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original
- `don't` / Source B: Source B not consulted: circuit opened after 3 consecutive transport failures
- `naive` / Source B: Source B not consulted: circuit opened after 3 consecutive transport failures
- `rendezvous` / Source B: Source B not consulted: circuit opened after 3 consecutive transport failures
- `abandon` / download US: flaky transport: HTTP 429; waited 10s before retrying (Retry-After: 10); HTTP 429; waited 10s before retrying (Retry-After: 10); HTTP 429; waited 10s before retrying (Retry-After: 10)
- `abandon` / download US: HTTP 429 (expected 200)
- `abandon` / download US: Content-Type is HTML, not audio: text/html; charset=utf-8
- `ability` / download US: flaky transport: HTTP 429; waited 10s before retrying (Retry-After: 10); HTTP 429; waited 10s before retrying (Retry-After: 10); HTTP 429; waited 10s before retrying (Retry-After: 10)
- `misunderstanding` / download US: flaky transport: HTTP 429; waited 10s before retrying (Retry-After: 10); HTTP 429; waited 10s before retrying (Retry-After: 10); HTTP 429; waited 10s before retrying (Retry-After: 10)
- `misunderstanding` / download US: HTTP 429 (expected 200)
- `misunderstanding` / download US: Content-Type is HTML, not audio: text/html; charset=utf-8

## Appendix C — request log

Every network or cache event, in order. `cache` means the raw body was read from `tools/pronunciation/cache/` and no request was made.

| # | Source | Status | Bytes | Attempts | URL |
|---|---|---|---|---|---|
| 1 | cache | 200 | 2,125 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-apple.ogg%7CFile%3AEn-us-apple.oga` |
| 2 | cache | 200 | 2,310 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-apple.ogg%7CFile%3AEn-uk-apple.oga` |
| 3 | network | 0 | 0 | 4 | `https://api.dictionaryapi.dev/api/v2/entries/en/apple` |
| 4 | cache | 200 | 2,380 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-abandon.ogg%7CFile%3AEn-us-abandon.oga` |
| 5 | cache | 200 | 202 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-abandon.ogg%7CFile%3AEn-uk-abandon.oga` |
| 6 | network | 0 | 0 | 4 | `https://api.dictionaryapi.dev/api/v2/entries/en/abandon` |
| 7 | cache | 200 | 2,379 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-ability.ogg%7CFile%3AEn-us-ability.oga` |
| 8 | cache | 200 | 202 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-ability.ogg%7CFile%3AEn-uk-ability.oga` |
| 9 | cache | 200 | 1,463 | 1 | `https://api.dictionaryapi.dev/api/v2/entries/en/ability` |
| 10 | cache | 200 | 416 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-internationalization.ogg%7CFile%3AEn-us-internationalization.oga%7CFile%3AEn-us-Internationalization.ogg%7CFile%3AEn-us-Internationalization.oga` |
| 11 | cache | 200 | 416 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-internationalization.ogg%7CFile%3AEn-uk-internationalization.oga%7CFile%3AEn-uk-Internationalization.ogg%7CFile%3AEn-uk-Internationalization.oga` |
| 12 | network | 0 | 0 | 4 | `https://api.dictionaryapi.dev/api/v2/entries/en/internationalization` |
| 13 | cache | 200 | 408 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-characteristically.ogg%7CFile%3AEn-us-characteristically.oga%7CFile%3AEn-us-Characteristically.ogg%7CFile%3AEn-us-Characteristically.oga` |
| 14 | cache | 200 | 408 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-characteristically.ogg%7CFile%3AEn-uk-characteristically.oga%7CFile%3AEn-uk-Characteristically.ogg%7CFile%3AEn-uk-Characteristically.oga` |
| 15 | network | 0 | 0 | 4 | `https://api.dictionaryapi.dev/api/v2/entries/en/characteristically` |
| 16 | cache | 200 | 2,519 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-misunderstanding.ogg%7CFile%3AEn-us-misunderstanding.oga%7CFile%3AEn-us-Misunderstanding.ogg%7CFile%3AEn-us-Misunderstanding.oga` |
| 17 | cache | 200 | 400 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-misunderstanding.ogg%7CFile%3AEn-uk-misunderstanding.oga%7CFile%3AEn-uk-Misunderstanding.ogg%7CFile%3AEn-uk-Misunderstanding.oga` |
| 18 | network | 0 | 0 | 4 | `https://api.dictionaryapi.dev/api/v2/entries/en/misunderstanding` |
| 19 | cache | 200 | 2,249 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-rhythm.ogg%7CFile%3AEn-us-rhythm.oga%7CFile%3AEn-us-Rhythm.ogg%7CFile%3AEn-us-Rhythm.oga` |
| 20 | cache | 200 | 360 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-rhythm.ogg%7CFile%3AEn-uk-rhythm.oga%7CFile%3AEn-uk-Rhythm.ogg%7CFile%3AEn-uk-Rhythm.oga` |
| 21 | cache | 200 | 2,411 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-queue.ogg%7CFile%3AEn-us-queue.oga%7CFile%3AEn-us-Queue.ogg%7CFile%3AEn-us-Queue.oga` |
| 22 | cache | 200 | 356 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-queue.ogg%7CFile%3AEn-uk-queue.oga%7CFile%3AEn-uk-Queue.ogg%7CFile%3AEn-uk-Queue.oga` |
| 23 | cache | 200 | 2,558 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-xylophone.ogg%7CFile%3AEn-us-xylophone.oga%7CFile%3AEn-us-Xylophone.ogg%7CFile%3AEn-us-Xylophone.oga` |
| 24 | cache | 200 | 372 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-xylophone.ogg%7CFile%3AEn-uk-xylophone.oga%7CFile%3AEn-uk-Xylophone.ogg%7CFile%3AEn-uk-Xylophone.oga` |
| 25 | cache | 200 | 376 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-well-being.ogg%7CFile%3AEn-us-well-being.oga%7CFile%3AEn-us-Well-being.ogg%7CFile%3AEn-us-Well-being.oga` |
| 26 | cache | 200 | 376 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-well-being.ogg%7CFile%3AEn-uk-well-being.oga%7CFile%3AEn-uk-Well-being.ogg%7CFile%3AEn-uk-Well-being.oga` |
| 27 | cache | 200 | 2,525 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-x-ray.ogg%7CFile%3AEn-us-x-ray.oga%7CFile%3AEn-us-X-ray.ogg%7CFile%3AEn-us-X-ray.oga` |
| 28 | cache | 200 | 356 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-x-ray.ogg%7CFile%3AEn-uk-x-ray.oga%7CFile%3AEn-uk-X-ray.ogg%7CFile%3AEn-uk-X-ray.oga` |
| 29 | cache | 200 | 2,091 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-don%27t.ogg%7CFile%3AEn-us-don%27t.oga` |
| 30 | cache | 200 | 198 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-don%27t.ogg%7CFile%3AEn-uk-don%27t.oga` |
| 31 | cache | 200 | 2,305 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-naive.ogg%7CFile%3AEn-us-naive.oga%7CFile%3AEn-us-Naive.ogg%7CFile%3AEn-us-Naive.oga` |
| 32 | cache | 200 | 356 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-naive.ogg%7CFile%3AEn-uk-naive.oga%7CFile%3AEn-uk-Naive.ogg%7CFile%3AEn-uk-Naive.oga` |
| 33 | cache | 200 | 2,277 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-us-rendezvous.ogg%7CFile%3AEn-us-rendezvous.oga%7CFile%3AEn-us-Rendezvous.ogg%7CFile%3AEn-us-Rendezvous.oga` |
| 34 | cache | 200 | 376 | 1 | `https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Csize%7Cextmetadata&titles=File%3AEn-uk-rendezvous.ogg%7CFile%3AEn-uk-rendezvous.oga%7CFile%3AEn-uk-Rendezvous.ogg%7CFile%3AEn-uk-Rendezvous.oga` |
| 35 | cache | 200 | 14,420 | 1 | `https://upload.wikimedia.org/wikipedia/commons/9/9a/En-us-apple.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original` |
| 36 | cache | 200 | 16,265 | 1 | `https://upload.wikimedia.org/wikipedia/commons/c/c8/En-uk-apple.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original` |
| 37 | cache | 429 | 1,965 | 4 | `https://upload.wikimedia.org/wikipedia/commons/d/dd/En-us-abandon.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original` |
| 38 | cache | 200 | 13,472 | 4 | `https://upload.wikimedia.org/wikipedia/commons/6/66/En-us-ability.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original` |
| 39 | cache | 429 | 1,965 | 4 | `https://upload.wikimedia.org/wikipedia/commons/6/66/En-us-misunderstanding.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original` |

## Appendix D — environment

- Python: 3.11.5
- User-Agent: `CetVocab-verification/1.0 (offline-first vocabulary study app; contact: local)`
- Timeout: 8s; throttle: 1.0s per host; cache: `tools/pronunciation/cache/`
- App minimum audio size assumed: 512 bytes

## Appendix E — supplementary probe: does a British recording exist under any other title?

Section 6 shows the app's UK title scheme matches only one word. This probe asks whether that is the app's fault or the data's: it searches the Commons **file namespace** for the whole `en-uk-<word>` family (both `En-uk-<word>` and `En-uk-<Word>` spellings) for each test word, independent of whether the app would ever probe that exact title.

Endpoint: `GET https://commons.wikimedia.org/w/api.php` with `action=query&format=json&list=search&srsearch=intitle:"en-uk-<word>"&srnamespace=6&srlimit=10`, followed by `prop=imageinfo&iiprop=url|size` for the titles found. Script: [`tools/pronunciation/supplementary_uk_probe.py`](../tools/pronunciation/supplementary_uk_probe.py).

| Word | Commons `en-uk-*` titles found | Resolved file | Size |
|---|---|---|---|
| `apple` | File:En-uk-apple.ogg | https://upload.wikimedia.org/wikipedia/commons/c/c8/En-uk-apple.ogg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original | 16,265 B |
| `abandon` | none | — | — |
| `ability` | none | — | — |
| `internationalization` | none | — | — |
| `characteristically` | none | — | — |
| `misunderstanding` | none | — | — |
| `rhythm` | none | — | — |
| `queue` | none | — | — |
| `xylophone` | none | — | — |
| `well-being` | none | — | — |
| `x-ray` | none | — | — |
| `don't` | none | — | — |
| `naive` | none | — | — |
| `rendezvous` | none | — | — |

- Words with any `en-uk-*` British recording: **1/14** (`apple`).
- Words where a search term was throttled and the result is therefore not trustworthy: none.
- **Conclusion from this probe:** the British gap measured in section 6 is **not** merely an artefact of the app probing a narrow set of titles; the `en-uk-*` naming family itself exists for 1 of the 14 test words. This does not prove that no British audio exists under unrelated Commons titles, which this probe did not enumerate.

## Appendix F — why Source B answered so rarely

Source B produced an HTTP answer for only 6 of 14 words, so this was isolated to distinguish a local connectivity problem from server-side latency. Measured with `socket` + `ssl` directly, not through the script's own client:

| Measurement | Result |
|---|---|
| DNS `api.dictionaryapi.dev` | resolved to 172.67.153.59 in 0.04s |
| TCP connect to `api.dictionaryapi.dev:443` | 0.22s |
| TLS handshake | 0.24s |
| Control: TCP connect to `commons.wikimedia.org:443` | 0.39s |
| HTTP request/response after a 40s cooldown | `TimeoutError: The read operation timed out` after 12.74s |

Interpretation within the measurements: the network path was healthy (fast DNS, TCP and TLS), and the failure recurred after a cooldown, so the 8-second read timeout was exceeded **server-side**, not by local connectivity. Note the observed 12.74s exceeds the configured 8s timeout, which is consistent with the timeout applying to a socket operation rather than strictly bounding the whole call. Two requests (the `ability` entry and one `apple` probe) did answer normally within the same period, so the service was degraded rather than fully down.

Consequence for this report: Source B's `-us.`/`-uk.` split could only be observed directly on one word (`ability`, which exposed `ability-us.mp3` and no `-uk.` file). Words marked "UK unverified: B not consulted" in section 1 were therefore **not** shown to lack a British recording by Source B; the circuit breaker stopped calling a host that had failed repeatedly. Treat Source B coverage as partially measured, not as a negative result.
