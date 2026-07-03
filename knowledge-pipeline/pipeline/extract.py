"""Deterministic statute extractor: India Code bare-act PDF -> section chunks.

NO LLM IS INVOLVED HERE. Extraction is structural (regex over the PDF text
layer) so that every byte in the knowledge base is verbatim from the official
document. Where structure cannot be determined deterministically, the section
is DROPPED and reported — never guessed (the pipeline-side mirror of the app's
Rule 3).
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

from pypdf import PdfReader

# Lines that are page furniture, not statute text.
_NOISE_PATTERNS = [
    re.compile(r"^\s*\d+\s*$"),  # bare page numbers
    re.compile(r"THE GAZETTE OF INDIA", re.IGNORECASE),
    re.compile(r"EXTRAORDINARY", re.IGNORECASE),
    re.compile(r"^\s*SEC\.\s*\d+\]", re.IGNORECASE),  # running header "SEC. 47]"
    re.compile(r"^\s*\[PART\s+", re.IGNORECASE),
]

_CHAPTER_RE = re.compile(r"^\s*(?:CHAPTER|PART)\s+([IVXLC]+)", re.IGNORECASE)
# "47. Text…" or "21A. Text…" (Constitution articles carry letter suffixes);
# the official BNS PDF misprints one section as "255.—Text…" (em-dash, no
# space), so accept an em-dash immediately after the number too.
_SECTION_START_RE = re.compile(r"^\s*(\d{1,3}[A-Z]{0,2})\.(?:\s+|(?=[—–]))\s*(.*)$")


# ALL-CAPS part title glued after a "PART <roman>" header, up to the first
# article number: "THE UNION AND ITS TERRITORY" in "PART ITHE UNION…1. Name".
_PART_TITLE_RE = re.compile(r"^[A-Z][A-Z ]+?(?=\d)")


def _num_key(section: str) -> tuple[int, str]:
    """Sort/compare key: ('21A' -> (21, 'A')), ('47' -> (47, ''))."""
    match = re.match(r"(\d+)([A-Z]*)", section)
    return (int(match.group(1)), match.group(2)) if match else (0, "")
# The enacting formula opens the body AFTER any arrangement-of-sections list —
# the reliable "text starts here" anchor. Bare acts use "BE it enacted by
# Parliament"; the Constitution opens with its Preamble, "WE, THE PEOPLE OF
# INDIA … DO HEREBY ADOPT".
_BODY_START_RE = re.compile(
    r"BE\s+it\s+enacted\s+by\s+Parliament"
    r"|WE,?\s*THE\s+PEOPLE\s+OF\s+INDIA,?\s+having\s+solemnly",
    re.IGNORECASE,
)

# Schedules follow the last section as huge classification tables; glued onto
# the final section they poison retrieval. Structured schedule ingestion is a
# separate stage — for now the act body ends here.
_BODY_END_RE = re.compile(r"^\s*THE\s+FIRST\s+SCHEDULE\s*$", re.IGNORECASE)
_SUBSECTION_RE = re.compile(r"^\s*\(\d+\)\s")
# Cross-references inside section text: "section 47", "sections 35 and 36"
_XREF_RE = re.compile(r"\bsections?\s+(\d{1,3})", re.IGNORECASE)

# Embedding-chunk sizing: one chunk per section unless it is very long.
_MAX_CHUNK_CHARS = 1800


@dataclass
class SectionChunk:
    section_number: str
    chunk_index: int          # 0-based within the section
    page_number: int          # 1-based page in the official PDF
    text: str
    chapter: str | None
    cross_references: list[str] = field(default_factory=list)


@dataclass
class ExtractionResult:
    chunks: list[SectionChunk]
    sections_found: int
    last_section: int
    dropped: list[str]        # human-readable reasons, for the report


# Flow-mode header: "<num>. <Titlecase marginal note>.—" embedded anywhere in
# the text. The ".—" em-dash reliably closes a marginal note in gazette prints.
# The title may not itself contain a "<num>. " header start, so a stray
# bracketed cross-reference ("…article 368.]Right to Equality14. Equality
# before law.—") cannot swallow the real header that follows it.
_FLOW_HEADER_RE = re.compile(
    r"(\d{1,3}[A-Z]{0,2})\.\s*"
    r"((?:(?!\d{1,3}[A-Z]{0,2}\.\s)[^—\n]){2,140}?)\s*\.—"
)


def extract_flow(pdf_path: str, max_gap: int = 8) -> ExtractionResult:
    """Extraction for PDFs whose text layer glues everything onto one line per
    page (e.g. the Constitution). Finds provisions by the `<num>. Title.—`
    marginal-note pattern across the continuous text, not by line position.
    """
    reader = PdfReader(pdf_path)

    # Concatenate body text; keep a char-offset → page map for page anchoring.
    buffer: list[str] = []
    offsets: list[tuple[int, int]] = []  # (char_offset_start, page_number)
    total = 0
    body_started = False
    for page_no, page in enumerate(reader.pages, start=1):
        text = page.extract_text() or ""
        if not body_started:
            if not _BODY_START_RE.search(text):
                continue
            body_started = True
        if _BODY_END_RE.search(text):
            break
        offsets.append((total, page_no))
        buffer.append(text)
        total += len(text)
    blob = "".join(buffer)

    def page_of(offset: int) -> int:
        page = offsets[0][1] if offsets else 1
        for start, pno in offsets:
            if start > offset:
                break
            page = pno
        return page

    # Sequence-valid headers only (gap rule rejects list items / stray numbers).
    headers: list[tuple[int, str]] = []
    current_key = (0, "")
    for match in _FLOW_HEADER_RE.finditer(blob):
        number = match.group(1)
        key = _num_key(number)
        cur_n, cur_s = current_key
        cand_n, cand_s = key
        ok = (cand_s > cur_s and len(cand_s) <= 1) if cand_n == cur_n else (cur_n < cand_n <= cur_n + max_gap)
        if ok:
            headers.append((match.start(), number))
            current_key = key

    chunks: list[SectionChunk] = []
    dropped: list[str] = []
    for index, (start, number) in enumerate(headers):
        end = headers[index + 1][0] if index + 1 < len(headers) else len(blob)
        text = _reflow(blob[start:end].splitlines() or [blob[start:end]])
        if len(text) < 40:
            dropped.append(f"article {number}: too short ({len(text)} chars)")
            continue
        xrefs = sorted(
            {m.group(1) for m in _XREF_RE.finditer(text) if m.group(1) != number},
            key=lambda s: _num_key(s),
        )
        for piece_index, piece in enumerate(_split(text)):
            chunks.append(
                SectionChunk(
                    section_number=number,
                    chunk_index=piece_index,
                    page_number=page_of(start),
                    text=piece,
                    chapter=None,
                    cross_references=xrefs if piece_index == 0 else [],
                ),
            )

    return ExtractionResult(
        chunks=chunks,
        sections_found=len(headers),
        last_section=_num_key(headers[-1][1])[0] if headers else 0,
        dropped=dropped,
    )


def extract_sections(pdf_path: str, max_gap: int = 1) -> ExtractionResult:
    """Extract numbered provisions (sections OR articles) with page anchors.

    A candidate `N. Title.—…` line is accepted as a new provision only if N
    advances from the current number by a bounded, non-decreasing step — the
    deterministic guard against numbered lists inside a provision being
    mistaken for provisions. Two shapes:

      max_gap=1  strict +1 (bare acts: BNSS/BNS/BSA number contiguously)
      max_gap=N  allow gaps up to N and letter-suffixed insertions
                 (the Constitution: 21 → 21A → 22, with repealed-article gaps)
    """
    reader = PdfReader(pdf_path)

    # Annotated line stream: (page_number, line)
    lines: list[tuple[int, str]] = []
    body_started = False
    for page_no, page in enumerate(reader.pages, start=1):
        text = page.extract_text() or ""
        if not body_started and _BODY_START_RE.search(text):
            body_started = True
        if not body_started:
            continue  # skip title page + ARRANGEMENT OF SECTIONS
        for raw in text.splitlines():
            line = raw.rstrip()
            if not line.strip():
                continue
            if any(p.search(line) for p in _NOISE_PATTERNS):
                continue
            lines.append((page_no, line))

    # Walk lines; a provision header is accepted only if its number advances
    # from the current one by a bounded, non-decreasing step (see docstring).
    sections: list[tuple[str, int, list[str], str | None]] = []
    dropped: list[str] = []
    current_chapter: str | None = None
    current_key: tuple[int, str] = (0, "")
    current: tuple[str, int, list[str], str | None] | None = None

    def advances(candidate: str) -> bool:
        cand = _num_key(candidate)
        cur_n, cur_s = current_key
        cand_n, cand_s = cand
        if cand_n == cur_n:
            # Letter-suffixed insertion: 21 → 21A, 21A → 21B.
            return cand_s > cur_s and len(cand_s) <= 1
        return cur_n < cand_n <= cur_n + max_gap

    for page_no, line in lines:
        if _BODY_END_RE.match(line):
            break
        chapter_match = _CHAPTER_RE.match(line)
        if chapter_match:
            current_chapter = chapter_match.group(1)
            # In the Constitution PDF the first article is glued onto its PART
            # header: "PART ITHE UNION AND ITS TERRITORY1. Name…". Strip the
            # PART number + its ALL-CAPS title run and re-examine the remainder.
            remainder = _PART_TITLE_RE.sub("", line[chapter_match.end():], count=1)
            if not _SECTION_START_RE.match(remainder):
                continue
            line = remainder
        section_match = _SECTION_START_RE.match(line)
        if section_match and advances(section_match.group(1)):
            if current is not None:
                sections.append(current)
            current = (
                section_match.group(1),
                page_no,
                [section_match.group(2)],
                current_chapter,
            )
            current_key = _num_key(section_match.group(1))
            continue
        if current is not None:
            current[2].append(line)

    if current is not None:
        sections.append(current)

    # Sections -> embedding chunks
    chunks: list[SectionChunk] = []
    for number, page_no, body_lines, chapter in sections:
        text = _reflow(body_lines)
        if len(text) < 40:
            dropped.append(f"section {number}: too short after cleaning ({len(text)} chars)")
            continue
        xrefs = sorted(
            {m.group(1) for m in _XREF_RE.finditer(text) if m.group(1) != number},
            key=int,
        )
        for index, piece in enumerate(_split(text)):
            chunks.append(
                SectionChunk(
                    section_number=number,
                    chunk_index=index,
                    page_number=page_no,
                    text=piece,
                    chapter=chapter,
                    cross_references=xrefs if index == 0 else [],
                ),
            )

    return ExtractionResult(
        chunks=chunks,
        sections_found=len(sections),
        last_section=_num_key(sections[-1][0])[0] if sections else 0,
        dropped=dropped,
    )


def _reflow(body_lines: list[str]) -> str:
    """Join hard-wrapped PDF lines into flowing text, keeping (n) markers."""
    parts: list[str] = []
    for line in body_lines:
        line = line.strip()
        if _SUBSECTION_RE.match(line) and parts:
            parts.append("\n" + line)
        else:
            parts.append((" " if parts and not parts[-1].endswith("\n") else "") + line)
    text = "".join(parts)
    text = re.sub(r"-\s+", "", text)      # de-hyphenate wraps
    text = re.sub(r"[ \t]{2,}", " ", text)
    return text.strip()


def _split(text: str) -> list[str]:
    """Whole section as one chunk unless very long; split on (n) boundaries."""
    if len(text) <= _MAX_CHUNK_CHARS:
        return [text]
    pieces: list[str] = []
    buffer = ""
    for paragraph in text.split("\n"):
        if buffer and len(buffer) + len(paragraph) > _MAX_CHUNK_CHARS:
            pieces.append(buffer.strip())
            buffer = paragraph
        else:
            buffer = f"{buffer}\n{paragraph}" if buffer else paragraph
    if buffer.strip():
        pieces.append(buffer.strip())
    return pieces
