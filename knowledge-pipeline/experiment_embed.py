#!/usr/bin/env python
"""Offline experiment: which embedding text maximises the refusal window?

Variants (same model, same chunks, in-memory cosine — no sqlite round-trip):
  full    : whole chunk text (current)
  title   : marginal note only (text before '.—')
  lead    : title + first 600 chars of body
  titlex2 : title twice + body lead (title-weighted)
"""

from __future__ import annotations

import numpy as np
from sentence_transformers import SentenceTransformer

from eval_queries import GOLDEN, JUNK
from pipeline.extract import extract_sections

ACTS = [
    ("corpus/bnss_2023.pdf", "Bharatiya Nagarik Suraksha Sanhita, 2023"),
    ("corpus/bns_2023.pdf", "Bharatiya Nyaya Sanhita, 2023"),
]


def split_title(text: str) -> tuple[str, str]:
    for marker in (".—", ".—"):
        if marker in text[:220]:
            head, _, tail = text.partition(marker)
            return head.strip() + ".", tail.strip()
    return "", text


def main() -> None:
    model = SentenceTransformer("distiluse-base-multilingual-cased-v2")

    chunks = []
    for pdf, statute in ACTS:
        for chunk in extract_sections(pdf).chunks:
            title, body = split_title(chunk.text)
            chunks.append((statute, chunk.section_number, title, body, chunk.text))

    variants = {
        "full": [c[4] for c in chunks],
        "title": [c[2] or c[4][:120] for c in chunks],
        "lead": [f"{c[2]} {c[3][:600]}" for c in chunks],
        "titlex2": [f"{c[2]} {c[2]} {c[3][:600]}" for c in chunks],
    }

    query_texts = [g[0] for g in GOLDEN] + JUNK
    query_embs = model.encode(query_texts, convert_to_numpy=True, normalize_embeddings=True)

    for name, texts in variants.items():
        corpus = model.encode(
            texts, batch_size=64, convert_to_numpy=True, normalize_embeddings=True
        )
        sims = query_embs @ corpus.T

        hits1 = hits3 = 0
        worst_good, best_junk = 1.0, 0.0
        for qi, (_, statute, sections) in enumerate(GOLDEN):
            order = np.argsort(-sims[qi])
            top3 = [(chunks[ci][0], chunks[ci][1], sims[qi][ci]) for ci in order[:3]]
            ok1 = top3[0][0].startswith(statute.split(",")[0]) and top3[0][1] in sections
            ok3 = any(s.startswith(statute.split(",")[0]) and n in sections for s, n, _ in top3)
            hits1 += ok1
            hits3 += ok3
            if ok3:
                worst_good = min(
                    worst_good, max(v for s, n, v in top3 if n in sections)
                )
        for qi in range(len(GOLDEN), len(query_texts)):
            best_junk = max(best_junk, float(sims[qi].max()))

        print(
            f"{name:<8} P@1 {hits1:>2}/{len(GOLDEN)}  Hit@3 {hits3:>2}/{len(GOLDEN)}  "
            f"window ({best_junk:.3f}, {worst_good:.3f})  "
            f"{'OK' if worst_good > best_junk + 0.05 else 'THIN'}"
        )


if __name__ == "__main__":
    main()
