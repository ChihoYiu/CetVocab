#!/usr/bin/env python3
"""Attach offline English example sentences to the built-in word packs.

Stage two of the vocabulary pipeline. Stage one (``build_packs.py``) turns ECDICT into
``cet4.json`` / ``cet6.json``; ECDICT has no example sentences, so this stage adds them from a
second, separately licensed source and bumps the pack version — which is what makes an
already-installed app receive the new field through its existing in-place upgrade path (word rows
are updated by id, so no progress, mistake or exam data is touched).

Data source: a dated Tatoeba snapshot (English sentences with Chinese translations, CC BY 2.0 FR),
downloaded and paired by ``build_tatoeba_pairs.py``. Everything here happens at build time; the app
never needs a network, and no sentence is invented.

Selection, per word:

1. the exact word form beats a regular inflection (``abandon`` over ``abandoned``);
2. the best quality tier beats a worse one, where a tier is
   (out-of-vocabulary tokens, has a proper name, translation converted from Traditional) —
   "in vocabulary" means the CET-4/CET-6 list itself, a frequency-ranked core of ECDICT, or a
   closed list of function words, which is what keeps a sentence readable for a CET learner;
3. the shorter sentence wins, then the lower Tatoeba id (deterministic).

Sentences are refused outright when they are too long, contain digits, quotes, semicolons or
acronyms, or use the target word more than once. A word with no acceptable sentence keeps an empty
example: the app hides an empty example rather than showing something invented or irrelevant.

Writes:

* ``app/src/main/assets/wordpacks/cet4.json`` / ``cet6.json`` (examples, pack version 3);
* ``app/src/main/assets/wordpacks/manifest.json`` (pack version 3);
* ``app/src/main/assets/wordpacks/PACK_INFO.json`` (example coverage per level);
* ``docs/EXAMPLE_SENTENCE_SOURCES.tsv`` (word -> Tatoeba sentence ids, for attribution);
* ``tools/wordpack/example_attachment_report.json`` (measured numbers and review samples).
"""

from __future__ import annotations

import csv
import json
import pathlib
import re
import sys
import urllib.request
from collections import defaultdict

REPO = pathlib.Path(__file__).resolve().parents[2]
WORD_PACKS = REPO / "app" / "src" / "main" / "assets" / "wordpacks"
PAIRS_PATH = REPO / "tools" / "wordpack" / "cache" / "tatoeba" / "cmn-eng-pairs.tsv"
PROVENANCE_PATH = REPO / "tools" / "wordpack" / "cache" / "tatoeba" / "PROVENANCE.json"
ECDICT_PATH = REPO / "tools" / "wordpack" / "cache" / "ecdict.csv"
OPENCC_CACHE = REPO / "tools" / "wordpack" / "cache" / "opencc"
SOURCES_TSV = REPO / "docs" / "EXAMPLE_SENTENCE_SOURCES.tsv"
REPORT_PATH = REPO / "tools" / "wordpack" / "example_attachment_report.json"

# Every file this stage writes is UTF-8 with LF endings: the repository normalises text files to LF
# (`.gitattributes`), and a CRLF asset would show up as a whole-file diff on every re-run.

OPENCC_FILES = {
    "TSCharacters.txt": "https://raw.githubusercontent.com/BYVoid/OpenCC/master/data/dictionary/TSCharacters.txt",
    "TSPhrases.txt": "https://raw.githubusercontent.com/BYVoid/OpenCC/master/data/dictionary/TSPhrases.txt",
}

PACK_VERSION = 3

# How common a word must be (best of its BNC / COCA rank in ECDICT) to count as known vocabulary.
COMMON_RANK = 8_000

# Sentence shape.
MIN_TOKENS = 4
MAX_TOKENS = 14
MIN_ENGLISH_CHARS = 15
MAX_ENGLISH_CHARS = 200
MAX_COMMAS = 2
MIN_CHINESE_CHARS = 4
MAX_CHINESE_CHARS = 60

# Quality tiers: (max out-of-vocabulary tokens, allow proper names, allow converted translation).
TIERS = [
    (0, False, False),
    (1, False, False),
    (1, False, True),
    (2, True, True),
    (3, True, True),
]

# Closed-class words a beginner is expected to know, so they never count as out of vocabulary.
FUNCTION_WORDS = set(
    """
    a an the this that these those there here
    i you he she it we they me him her us them my your his its our their mine yours
    am is are was were be been being do does did done have has had having
    will would shall should can could may might must ought need dare
    and or but if because so than then when where why how what which who whom whose while
    not no nor none never ever always often sometimes usually again already still just only
    very too also as at by for from in into of off on out over to up with without about after
    before between during under above across along around behind below beside near since through
    toward until upon within each every all both few many much more most other some any own same
    one two three four five six seven eight nine ten first second third last next another such
    yes please thanks thank hello hi okay ok well now today tomorrow yesterday soon later early
    good bad big small long short new old young right left high low hard easy nice fine great
    get got go going gone come came make made take took give gave see saw look looked say said
    tell told think thought know knew want wanted like liked love loved use used find found
    keep kept put let leave left feel felt seem seemed try tried ask asked work worked call called
    """.split()
)

# Tatoeba's recurring cast; a sentence built around a name teaches the name, not the word.
COMMON_NAMES = set(
    """
    tom mary john jane alice bob bill sam ken peter paul jack linda susan mike david jim joe
    kate lucy anna mark betty charles robert james george harry helen emma ben dan steve nancy
    boston tokyo japan china america england english french london paris germany canada australia
    monday tuesday wednesday thursday friday saturday sunday january february march april june
    july august september october november december
    """.split()
)

TOKEN_RE = re.compile(r"^[A-Za-z][A-Za-z'’.-]*$")
CJK_RE = re.compile(r"[\u4e00-\u9fff]")


# --- inflection ------------------------------------------------------------------------------


def surface_forms(word: str) -> set[str]:
    """Spellings of ``word`` that may appear in a sentence (regular inflections only).

    Deliberately conservative, because a form that is not really a form of the word produces a
    **wrong example** rather than a missing one: earlier drafts generated ``kin -> kind``,
    ``hop -> hoping`` (that is "hope") and ``mast -> master``. Comparatives
    (``arch -> archer``) are not generated at all, and a short consonant-vowel-consonant word
    takes the doubled form only (``hop -> hopping``, never ``hoping``).
    """
    w = word.lower()
    forms = {w}
    doubles = (
        len(w) > 2 and w[-1] not in "aeiou" and w[-2] in "aeiou" and w[-3] not in "aeiou"
    )
    # A short consonant-vowel-consonant word takes the doubled past tense ("hop" -> "hopped");
    # "hoped" belongs to "hope" and must never be accepted as an example of "hop".
    short_double = doubles and len(w) <= 4

    if w.endswith(("s", "x", "z", "ch", "sh")):
        forms |= {w + "es", w + "ed"}
    elif w.endswith("y") and len(w) > 2 and w[-2] not in "aeiou":
        forms |= {w[:-1] + "ies", w[:-1] + "ied"}
    elif w.endswith("e"):
        forms |= {w + "s", w + "d"}
    else:
        forms.add(w + "s")
        forms.add(w + w[-1] + "ed" if short_double else w + "ed")

    if w.endswith("e") and not w.endswith("ee"):
        forms.add(w[:-1] + "ing")
    elif short_double:
        forms |= {w + w[-1] + "ing", w + w[-1] + "ed"}
    else:
        forms.add(w + "ing")
        if doubles:
            # Longer words may or may not double ("visit/visited", "occur/occurred"); both
            # spellings are offered and the corpus decides which one exists.
            forms |= {w + w[-1] + "ing", w + w[-1] + "ed"}
    return forms


def lemmas(token: str) -> set[str]:
    """Possible base forms of ``token`` — the reverse of the rules above."""
    t = token.lower()
    out = {t}
    if t.endswith("ies") and len(t) > 4:
        out.add(t[:-3] + "y")
    if t.endswith("es") and len(t) > 3:
        out.add(t[:-2])
    if t.endswith("s") and len(t) > 3:
        out.add(t[:-1])
    if t.endswith("ied") and len(t) > 4:
        out.add(t[:-3] + "y")
    if t.endswith("ed") and len(t) > 3:
        out.add(t[:-2])
        out.add(t[:-1])
        if len(t) > 4 and t[-3] == t[-4]:
            out.add(t[:-3])
    if t.endswith("ing") and len(t) > 4:
        out.add(t[:-3])
        out.add(t[:-3] + "e")
        if len(t) > 5 and t[-4] == t[-5]:
            out.add(t[:-4])
    if t.endswith("er") and len(t) > 4:
        out.add(t[:-2])
        out.add(t[:-1])
    if t.endswith("est") and len(t) > 5:
        out.add(t[:-3])
        out.add(t[:-2])
    return {form for form in out if form}


def is_known(token: str, vocabulary: set[str]) -> bool:
    if token in vocabulary or token in FUNCTION_WORDS:
        return True
    for separator in ("'", "’"):
        if separator in token:
            base = token.split(separator)[0]
            if base and (base in vocabulary or base in FUNCTION_WORDS):
                return True
            return any(lemma in vocabulary for lemma in lemmas(base))
    return any(lemma in vocabulary for lemma in lemmas(token))


# --- traditional -> simplified ---------------------------------------------------------------


def download_opencc() -> tuple[dict[str, str], dict[str, str], int]:
    """OpenCC conversion tables, downloaded once into the (gitignored) build cache."""
    OPENCC_CACHE.mkdir(parents=True, exist_ok=True)
    chars: dict[str, str] = {}
    phrases: dict[str, str] = {}
    longest = 0
    for name, url in OPENCC_FILES.items():
        path = OPENCC_CACHE / name
        if not path.exists():
            print(f"  downloading {name}…")
            with urllib.request.urlopen(url, timeout=60) as response:
                path.write_bytes(response.read())
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) < 2:
                continue
            source, target = parts[0], parts[1]
            if len(source) == 1:
                chars[source] = target
            else:
                phrases[source] = target
                longest = max(longest, len(source))
    return chars, phrases, longest


def to_simplified(
    text: str,
    chars: dict[str, str],
    phrases: dict[str, str],
    longest: int,
) -> tuple[str, bool]:
    """Longest-match phrase conversion, then character conversion; reports whether it changed."""
    if not any(character in chars for character in text):
        return text, False
    out: list[str] = []
    index = 0
    length = len(text)
    while index < length:
        swallowed = 0
        for size in range(min(longest, length - index), 1, -1):
            candidate = text[index:index + size]
            if candidate in phrases:
                out.append(phrases[candidate])
                index += size
                swallowed = size
                break
        if swallowed:
            continue
        out.append(chars.get(text[index], text[index]))
        index += 1
    converted = "".join(out)
    return converted, converted != text


# --- corpus ----------------------------------------------------------------------------------


def load_pairs(vocabulary: set[str], chars, phrases, longest):
    """Usable Tatoeba rows with everything that does not depend on the target word precomputed."""
    pairs = []
    with PAIRS_PATH.open(encoding="utf-8", newline="") as handle:
        reader = csv.reader(handle, delimiter="\t")
        next(reader)  # header
        for row in reader:
            if len(row) != 4:
                continue
            eng_id, cmn_id, english, chinese = row
            english = english.strip()
            chinese = chinese.strip()
            if not (MIN_ENGLISH_CHARS <= len(english) <= MAX_ENGLISH_CHARS):
                continue
            if english[-1] not in ".!?" or not english[0].isupper():
                continue
            if any(character.isdigit() for character in english) or '"' in english:
                continue
            if english.count(",") > MAX_COMMAS or ";" in english:
                continue
            tokens = english.split()
            if not (MIN_TOKENS <= len(tokens) <= MAX_TOKENS):
                continue
            if not all(TOKEN_RE.match(token) for token in tokens):
                continue
            # Acronyms ("CPPCC") make poor teaching sentences.
            if any(token.isupper() and len(token) > 1 for token in tokens):
                continue
            if not (MIN_CHINESE_CHARS <= len(chinese) <= MAX_CHINESE_CHARS):
                continue
            if not CJK_RE.search(chinese):
                continue
            if re.search(r"[A-Za-z]{2,}", chinese):
                continue

            translation, converted = to_simplified(chinese, chars, phrases, longest)
            if not CJK_RE.search(translation):
                continue
            lower = [token.lower().strip(".,!?").strip("'’") for token in tokens]
            unknown = sum(1 for token in lower if not is_known(token, vocabulary))
            # Proper names: Tatoeba is full of "Tom" and "Mary", and a sentence built around a name
            # teaches the name rather than the word. A capitalised word in the middle of a sentence
            # is a name unless it is vocabulary ("Chinese", "Civil"); a capitalised *first* word is
            # only a name when it is one of the recurring Tatoeba names — otherwise every sentence
            # would count as name-bearing just for starting with a capital letter.
            capitalised = {
                token.split("'")[0].split("’")[0].lower()
                for token in tokens
                if token[0].isupper() and token.isalpha()
            }
            first_word = tokens[0].split("'")[0].split("’")[0].lower()
            has_name = bool(capitalised & COMMON_NAMES) or bool(
                {name for name in capitalised if name not in vocabulary} - {first_word}
            )
            tier = next(
                (
                    index
                    for index, (max_unknown, allow_names, allow_converted) in enumerate(TIERS)
                    if unknown <= max_unknown
                    and (allow_names or not has_name)
                    and (allow_converted or not converted)
                ),
                None,
            )
            if tier is None:
                continue
            pairs.append(
                {
                    "eng_id": eng_id,
                    "cmn_id": cmn_id,
                    "english": english,
                    "translation": translation,
                    "tokens": tokens,
                    "lower": lower,
                    "converted": converted,
                    "tier": tier,
                }
            )
    return pairs


def build_postings(pairs) -> dict[str, list[int]]:
    postings: dict[str, list[int]] = defaultdict(list)
    for index, pair in enumerate(pairs):
        for token in set(pair["lower"]):
            postings[token].append(index)
    return postings


def main() -> int:
    packs: dict[str, dict] = {}
    for level in ("cet4", "cet6"):
        packs[level] = json.loads((WORD_PACKS / f"{level}.json").read_text(encoding="utf-8"))

    vocabulary: set[str] = set()
    for pack in packs.values():
        for word in pack["words"]:
            vocabulary.add(word["word"].lower())

    # A frequency-ranked core of ECDICT widens "known vocabulary" beyond the CET lists, which is
    # what keeps an ordinary sentence ("I have to go to sleep.") from looking difficult.
    common: set[str] = set()
    with ECDICT_PATH.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            ranks = []
            for column in ("bnc", "frq"):
                try:
                    value = int(row.get(column) or 0)
                except ValueError:
                    value = 0
                if value > 0:
                    ranks.append(value)
            if ranks and min(ranks) <= COMMON_RANK:
                word = (row.get("word") or "").strip().lower()
                if word.isalpha():
                    common.add(word)
    vocabulary |= common
    print(f"known vocabulary: {len(vocabulary)} words ({len(common)} from ECDICT frequency)")

    print("OpenCC tables (build-time only, not shipped):")
    chars, phrases, longest = download_opencc()
    print(f"  {len(chars)} character mappings, {len(phrases)} phrase mappings")

    print("loading Tatoeba pairs…")
    pairs = load_pairs(vocabulary, chars, phrases, longest)
    postings = build_postings(pairs)
    print(f"  {len(pairs)} sentences passed the shape and quality filters")

    selection_cache: dict[str, tuple | None] = {}

    def select(word: str):
        """Best candidate sentence for one spelling, or None."""
        base = word.lower()
        if base in selection_cache:
            return selection_cache[base]
        forms = surface_forms(base)
        indices: set[int] = set()
        for form in forms:
            indices.update(postings.get(form, ()))
        best = None
        best_key = None
        for index in indices:
            pair = pairs[index]
            present = [form for form in forms if form in pair["lower"]]
            if len(present) != 1:
                continue  # the word must appear exactly once, in exactly one of its forms
            key = (
                0 if present[0] == base else 1,
                pair["tier"],
                len(pair["tokens"]),
                int(pair["eng_id"]),
            )
            if best_key is None or key < best_key:
                best_key = key
                best = (pair, present[0])
        selection_cache[base] = best
        return best

    report_levels = {}
    sources_rows = []
    samples = []

    for level, pack in packs.items():
        covered = 0
        converted_count = 0
        inflected_count = 0
        tier_histogram: dict[int, int] = defaultdict(int)
        for word in pack["words"]:
            chosen = select(word["word"])
            if chosen is None:
                word["exampleSentence"] = ""
                word["exampleTranslation"] = ""
                continue
            pair, matched = chosen
            word["exampleSentence"] = pair["english"]
            word["exampleTranslation"] = pair["translation"]
            covered += 1
            converted_count += 1 if pair["converted"] else 0
            inflected_count += 0 if matched == word["word"].lower() else 1
            tier_histogram[pair["tier"]] += 1
            sources_rows.append(
                (
                    level.upper(),
                    word["word"],
                    pair["eng_id"],
                    pair["cmn_id"],
                    matched,
                    "converted-from-traditional" if pair["converted"] else "simplified",
                )
            )
            if covered % 41 == 0 and len(samples) < 90:
                samples.append(
                    {
                        "level": level.upper(),
                        "word": word["word"],
                        "english": pair["english"],
                        "chinese": pair["translation"],
                        "tier": pair["tier"],
                    }
                )
        pack["version"] = PACK_VERSION
        pack["exampleSource"] = (
            "Tatoeba (CC BY 2.0 FR); paired by tools/wordpack/build_tatoeba_pairs.py and selected by "
            "tools/wordpack/attach_examples.py. ECDICT itself has no example sentences."
        )
        report_levels[level.upper()] = {
            "words": len(pack["words"]),
            "withExample": covered,
            "withoutExample": len(pack["words"]) - covered,
            "coveragePercent": round(100.0 * covered / len(pack["words"]), 2),
            "translationConvertedFromTraditional": converted_count,
            "matchedThroughInflection": inflected_count,
            "qualityTierHistogram": {str(k): v for k, v in sorted(tier_histogram.items())},
        }
        (WORD_PACKS / f"{level}.json").write_text(
            json.dumps(pack, ensure_ascii=False, separators=(",", ":")) + "\n",
            encoding="utf-8",
            newline="\n",
        )
        stats = report_levels[level.upper()]
        print(
            f"{level.upper()}: {covered}/{stats['words']} words have an example "
            f"({stats['coveragePercent']}%), {converted_count} translations converted, "
            f"{inflected_count} matched through an inflection"
        )

    manifest_path = WORD_PACKS / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for entry in manifest.get("packs", []):
        entry["version"] = PACK_VERSION
    manifest["note"] = (
        "Built-in vocabulary shipped with the app. The JSON files are produced by "
        "tools/wordpack/build_packs.py from skywind3000/ECDICT (MIT) and then enriched with example "
        "sentences by tools/wordpack/attach_examples.py from a dated Tatoeba snapshot (CC BY 2.0 FR); "
        "validated by tools/wordpack/validate_packs.py. See docs/WORD_PACK_VALIDATION.md for the "
        "measured counts and residual risks."
    )
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )

    # PACK_INFO.json keeps the licence and field-coverage record next to the data it describes.
    info_path = WORD_PACKS / "PACK_INFO.json"
    info = json.loads(info_path.read_text(encoding="utf-8"))
    info["version"] = PACK_VERSION
    for level, entry in info.get("fieldCoverage", {}).items():
        words = packs[level.lower()]["words"]
        entry["exampleSentence"] = sum(1 for word in words if word["exampleSentence"])
        entry["exampleTranslation"] = sum(1 for word in words if word["exampleTranslation"])
    provenance = json.loads(PROVENANCE_PATH.read_text(encoding="utf-8")) if PROVENANCE_PATH.exists() else {}
    info["examples"] = {
        "source": "Tatoeba (https://tatoeba.org) — English sentences with Chinese translations",
        "licence": provenance.get("licence", {}).get("statement", "CC BY 2.0 FR"),
        "attribution": (
            "The Tatoeba Project and its contributors; per-word sentence ids in "
            "docs/EXAMPLE_SENTENCE_SOURCES.tsv"
        ),
        "snapshotDateUtc": provenance.get("download_date_utc", "unknown"),
        "sourceFiles": provenance.get("sources", []),
        "pairCount": provenance.get("build", {}).get("pair_count"),
        "traditionalToSimplifiedTool": (
            "OpenCC TSCharacters/TSPhrases (Apache-2.0), used at build time only and not redistributed"
        ),
        "selection": (
            "One sentence per word: exact form preferred over a regular inflection; Simplified "
            "Chinese preferred (Traditional converted with the OpenCC tables); fewest "
            "out-of-vocabulary tokens against CET-4/CET-6 + an ECDICT frequency core + function "
            "words; no proper names; shortest sentence wins; longest Tatoeba id as tie-break."
        ),
        "perLevel": report_levels,
    }
    info["transformNotes"] = info.get("transformNotes", "") + (
        " Example sentences are a second stage (tools/wordpack/attach_examples.py): one Tatoeba "
        "sentence per word, selected for readability and never invented. Words with no acceptable "
        "sentence keep an empty example field; the app hides an empty example instead of faking one."
    )
    info_path.write_text(
        json.dumps(info, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )

    SOURCES_TSV.parent.mkdir(parents=True, exist_ok=True)
    with SOURCES_TSV.open("w", encoding="utf-8", newline="") as handle:
        handle.write("level\tword\teng_sentence_id\tcmn_sentence_id\tmatched_form\ttranslation\n")
        for row in sources_rows:
            handle.write("\t".join(row) + "\n")

    REPORT_PATH.write_text(
        json.dumps(
            {
                "packVersion": PACK_VERSION,
                "usableSentences": len(pairs),
                "vocabularySize": len(vocabulary),
                "levels": report_levels,
                "samples": samples,
            },
            ensure_ascii=False,
            indent=2,
        ) + "\n",
        encoding="utf-8",
        newline="\n",
    )

    print(f"wrote {SOURCES_TSV.relative_to(REPO)} ({len(sources_rows)} rows)")
    print(f"wrote {REPORT_PATH.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
