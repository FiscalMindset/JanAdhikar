#!/usr/bin/env python
"""Offline experiment: which embedding MODEL maximises the refusal window?

multilingual-e5 models are asymmetric retrievers: queries get "query: " and
passages "passage: " prefixes, which is exactly our shape (short colloquial
query -> long statute passage).
"""

from __future__ import annotations

import numpy as np
from sentence_transformers import SentenceTransformer

from eval_queries import GOLDEN, JUNK
from experiment_embed import ACTS, split_title
from pipeline.extract import extract_sections

MODELS = [
    # (name, query_prefix, passage_prefix)
    ("distiluse-base-multilingual-cased-v2", "", ""),
    ("paraphrase-multilingual-MiniLM-L12-v2", "", ""),
    ("intfloat/multilingual-e5-small", "query: ", "passage: "),
]


def main() -> None:
    chunks = []
    for pdf, statute in ACTS:
        for chunk in extract_sections(pdf).chunks:
            title, body = split_title(chunk.text)
            chunks.append((statute, chunk.section_number, f"{title} {body}".strip()))

    for model_name, q_prefix, p_prefix in MODELS:
        model = SentenceTransformer(model_name)
        corpus = model.encode(
            [p_prefix + c[2] for c in chunks],
            batch_size=64,
            convert_to_numpy=True,
            normalize_embeddings=True,
        )
        query_embs = model.encode(
            [q_prefix + g[0] for g in GOLDEN] + [q_prefix + j for j in JUNK],
            convert_to_numpy=True,
            normalize_embeddings=True,
        )
        sims = query_embs @ corpus.T

        hits1 = hits3 = 0
        worst_good, best_junk = 1.0, 0.0
        misses = []
        for qi, (query, statute, sections) in enumerate(GOLDEN):
            order = np.argsort(-sims[qi])
            top3 = [(chunks[ci][0], chunks[ci][1], sims[qi][ci]) for ci in order[:3]]
            ok1 = top3[0][0].startswith(statute.split(",")[0]) and top3[0][1] in sections
            ok3 = any(s.startswith(statute.split(",")[0]) and n in sections for s, n, _ in top3)
            hits1 += ok1
            hits3 += ok3
            if ok3:
                worst_good = min(worst_good, max(v for s, n, v in top3 if n in sections))
            else:
                misses.append(f"{query[:38]} -> S.{top3[0][1]}")
        for qi in range(len(GOLDEN), len(query_embs)):
            best_junk = max(best_junk, float(sims[qi].max()))

        short = model_name.split("/")[-1]
        print(
            f"{short:<42} dim={corpus.shape[1]:<4} P@1 {hits1:>2}/{len(GOLDEN)} "
            f"Hit@3 {hits3:>2}/{len(GOLDEN)} window ({best_junk:.3f}, {worst_good:.3f}) "
            f"{'OK' if worst_good > best_junk + 0.05 else 'THIN'}"
        )
        for miss in misses:
            print(f"    miss: {miss}")


if __name__ == "__main__":
    main()
