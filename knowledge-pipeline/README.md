# Knowledge Pipeline (desktop, build-time only)

Compiles official legal PDFs into `janadhikar_knowledge.db` — the single
source of factual truth shipped inside the APK. **Nothing in this directory
runs on the device.**

## Pipeline stages (Phase 3+ implements these)

1. `ingest/` — pull authenticated bare-act PDFs (Gazette of India / ministry
   sources), record SHA-256 of each source document.
2. `extract/` — Cognee entity/relation extraction → statute, section, clause,
   page-anchored chunks. Every row carries `source_document`, `source_page`,
   `compilation_date` (non-null, hand-reviewed — CONTRIBUTING.md Rule 4).
3. `embed/` — sentence embeddings for EN + HI chunk text (same model family
   the app uses for query embedding).
4. `pack/` — emit SQLite file with `statute_chunks` relational table +
   `vec_chunks` sqlite-vec virtual table, then `VACUUM` and mark read-only.
5. `eval/` — golden query set (EN/HI). Gates any retrieval change
   (CONTRIBUTING.md Rule 6): reports Precision@1 and false-positive rate.

## Output contract

```
app/src/main/assets/db/janadhikar_knowledge.db
```

The app opens this file READ-ONLY via a statically-linked sqlite-vec build.
Schema is versioned; the app refuses to start the pipeline against a schema
version it does not recognise.
