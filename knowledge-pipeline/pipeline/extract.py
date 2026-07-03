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

_CHAPTER_RE = re.compile(r"^\s*CHAPTER\s+([IVXLC]+)\s*$")
# "47. Text…" normally, but the official BNS PDF misprints one section as
# "255.—Text…" (em-dash, no space) — accept both.
_SECTION_START_RE = re.compile(r"^\s*(\d{1,3})\.(?:\s+|(?=[—–]))\s*(.*)$")
# The enacting formula opens every Indian act body, AFTER any arrangement-of-
# sections list — the reliable "act text starts here" anchor in both gazette
# prints and India Code consolidated layouts.
_BODY_START_RE = re.compile(r"BE\s+it\s+enacted\s+by\s+Parliament", re.IGNORECASE)

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


def extract_sections(pdf_path: str) -> ExtractionResult:
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

    # Walk lines; sections must be numbered monotonically (+1) — this is the
    # deterministic guard against numbered lists being mistaken for sections.
    sections: list[tuple[str, int, list[str], str | None]] = []
    dropped: list[str] = []
    current_chapter: str | None = None
    expected_next = 1
    current: tuple[str, int, list[str], str | None] | None = None

    for page_no, line in lines:
        if _BODY_END_RE.match(line):
            break
        chapter_match = _CHAPTER_RE.match(line)
        if chapter_match:
            current_chapter = chapter_match.group(1)
            continue
        section_match = _SECTION_START_RE.match(line)
        if section_match and int(section_match.group(1)) == expected_next:
            if current is not None:
                sections.append(current)
            current = (
                section_match.group(1),
                page_no,
                [section_match.group(2)],
                current_chapter,
            )
            expected_next += 1
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
        last_section=int(sections[-1][0]) if sections else 0,
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
