#!/usr/bin/env python
"""Golden-query evaluation gate (CONTRIBUTING.md Rule 6).

Measures, against the packed DB:
  - Precision@1 / Hit@3 on queries with a known correct section (EN and HI)
  - the similarity of junk queries that MUST be refused
  - the resulting safe threshold window

Any change to chunking, embedding, or the confidence threshold must re-run
this and keep: every junk query BELOW threshold, hit-rate not degraded.
"""

from __future__ import annotations

import sqlite3
import struct
import sys
from pathlib import Path

import sqlite_vec
from sentence_transformers import SentenceTransformer

ROOT = Path(__file__).resolve().parent

# (query, statute prefix, acceptable sections)
GOLDEN = [
    # English
    ("police won't tell me why I am being arrested", "Bharatiya Nagarik", {"47", "35"}),
    ("do I have the right to know the grounds of my arrest", "Bharatiya Nagarik", {"47"}),
    ("can a woman be arrested at night", "Bharatiya Nagarik", {"43", "35"}),
    ("police want to search my house without a warrant", "Bharatiya Nagarik", {"185", "103"}),
    ("how long can police keep me in custody before court", "Bharatiya Nagarik", {"58", "57", "187"}),
    ("I was arrested and want to call my lawyer", "Bharatiya Nagarik", {"38", "47"}),
    ("what is the punishment for murder", "Bharatiya Nyaya", {"103", "101"}),
    ("someone threatened to kill me", "Bharatiya Nyaya", {"351"}),
    ("my husband beats me for dowry", "Bharatiya Nyaya", {"85", "80"}),
    ("someone stole my mobile phone", "Bharatiya Nyaya", {"303", "304"}),
    # Hindi
    ("पुलिस गिरफ्तारी का कारण नहीं बता रही", "Bharatiya Nagarik", {"47", "35"}),
    ("क्या पुलिस बिना वारंट के घर की तलाशी ले सकती है", "Bharatiya Nagarik", {"185", "103"}),
    ("हत्या की सजा क्या है", "Bharatiya Nyaya", {"103", "101"}),
    ("मेरा फोन चोरी हो गया", "Bharatiya Nyaya", {"303", "304"}),
]

# Must ALL fall below the confidence threshold (Rule 3: refusal, not guessing).
JUNK = [
    "my pizza is cold",
    "how do I bake a chocolate cake",
    "what is the capital of France",
    "मुझे गाना गाना पसंद है",
    "best cricket player in the world",
]


def main() -> None:
    db = ROOT / "build/janadhikar_knowledge.db"
    model = SentenceTransformer("paraphrase-multilingual-MiniLM-L12-v2")
    conn = sqlite3.connect(db)
    conn.enable_load_extension(True)
    sqlite_vec.load(conn)

    def top(query: str, k: int = 3):
        emb = model.encode(query)
        return conn.execute(
            "SELECT c.statute_name, c.section_number, 1.0 - v.distance "
            "FROM vec_chunks v JOIN statute_chunks c ON c.id = v.chunk_id "
            "WHERE v.embedding MATCH ? AND k = ? ORDER BY v.distance",
            (struct.pack("384f", *emb), k),
        ).fetchall()

    hits1 = hits3 = 0
    worst_good = 1.0
    print(f"{'query':<52} top1  hit@1 hit@3")
    for query, statute, sections in GOLDEN:
        rows = top(query)
        ok1 = rows and rows[0][0].startswith(statute) and rows[0][1] in sections
        ok3 = any(r[0].startswith(statute) and r[1] in sections for r in rows)
        hits1 += ok1
        hits3 += ok3
        if ok3:
            worst_good = min(worst_good, max(r[2] for r in rows if r[1] in sections))
        top_label = f"S.{rows[0][1]} {rows[0][2]:.3f}" if rows else "none"
        print(f"{query[:50]:<52} {top_label:<12} {'✓' if ok1 else '✗':<5} {'✓' if ok3 else '✗'}")

    best_junk = 0.0
    print("\njunk (must refuse):")
    for query in JUNK:
        rows = top(query, 1)
        sim = rows[0][2] if rows else 0.0
        best_junk = max(best_junk, sim)
        print(f"  {query[:48]:<50} best sim {sim:.3f}")

    print(f"\nPrecision@1 {hits1}/{len(GOLDEN)}   Hit@3 {hits3}/{len(GOLDEN)}")
    print(f"worst genuine-hit sim  : {worst_good:.3f}")
    print(f"best junk sim          : {best_junk:.3f}")
    print(f"→ safe threshold window: ({best_junk:.3f}, {worst_good:.3f})")
    if best_junk >= worst_good:
        print("!! NO SAFE THRESHOLD — do not ship this artifact")
        sys.exit(1)


if __name__ == "__main__":
    main()
