# Knowledge Pipeline (desktop, build-time only)

Compiles official legal PDFs into `janadhikar_knowledge.db` and the on-device
query embedder — the factual + retrieval artifacts shipped inside the APK.
**Nothing in this directory runs on the device.**

## Corpus (checked in, `corpus/`)

Official India Code bare-act PDFs (SHA-256 recorded per document into the DB):

| Act | Source |
|---|---|
| Bharatiya Nagarik Suraksha Sanhita, 2023 (531 §) | indiacode.nic.in, Act 46 of 2023 |
| Bharatiya Nyaya Sanhita, 2023 (358 §) | indiacode.nic.in, Act 45 of 2023 |

## Setup

```bash
uv venv --python 3.12 .venv
uv pip install --python .venv/bin/python \
    pypdf sentence-transformers sqlite-vec

# Separate env for the TFLite export (needs transformers 4.x + TensorFlow)
uv venv --python 3.12 .venv-export
uv pip install --python .venv-export/bin/python \
    "transformers<5" "sentence-transformers<4" tensorflow tf-keras sentencepiece
```

## Build

```bash
# 1. Compile the knowledge DB (extract → graph → embed → pack).
#    Writes app/src/main/assets/db/janadhikar_knowledge.db (+ .sha256).
.venv/bin/python build_db.py

# 2. Gate on retrieval quality (CONTRIBUTING.md Rule 6). Must show a safe
#    threshold window; the value feeds HybridRetriever.CONFIDENCE_THRESHOLD.
.venv/bin/python eval_queries.py

# 3. Export the on-device embedder + tokenizer vocab + parity vectors.
#    Writes app/src/main/assets/models/embedder_minilm_384.tflite,
#    spm_vocab.tsv, and app/src/test/resources/tokenizer_vectors.json.
.venv-export/bin/python export_tflite.py
```

## Stages (`pipeline/` + top-level scripts)

1. **extract** (`pipeline/extract.py`) — deterministic, regex/structure-based
   section extraction with page anchors. NO LLM: every byte is verbatim from
   the official PDF. Sections that can't be parsed deterministically are
   dropped and reported, never guessed.
2. **graph** (`build_db.py`) — STATUTE/SECTION nodes; PART_OF edges; and
   RELATED_RIGHT edges from in-text cross-references ("… under section 35 …").
3. **embed** (`build_db.py`) — `paraphrase-multilingual-MiniLM-L12-v2` (384-d),
   chosen over distiluse and multilingual-e5 by `experiment_models.py` on the
   EN+HI golden set (only model with a usable junk/genuine refusal gap).
4. **pack** (`build_db.py`) — SQLite matching the app's Room schema exactly
   (identity hash + user_version injected from the exported schema JSON), plus
   the `vec_chunks` sqlite-vec virtual table and `kb_meta` self-description.
5. **eval** (`eval_queries.py`) — golden EN/HI queries + junk queries that must
   be refused; prints Precision@1, Hit@3, and the safe threshold window.
6. **export** (`export_tflite.py`) — encoder+mean-pool baked into one TFLite
   graph (int8), the SentencePiece vocab as `spm_vocab.tsv`, and golden
   tokenization vectors that lock the Kotlin tokenizer via a parity unit test.

## The embedder/DB pairing invariant

The DB's `kb_meta.embedder_model_id` and the app's `QueryEmbedder.MODEL_ID`
MUST match — the app refuses to start otherwise (query and corpus vectors would
live in different spaces). Rebuild the DB and re-export the embedder together.
