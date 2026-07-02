# COGNEE.md — How Janadhikar Uses Cognee

> **TL;DR:** Cognee runs **only at build time, on a desktop**. It compiles official
> legal PDFs into a knowledge graph, which we flatten into plain SQLite tables and
> ship inside the APK. **Zero Cognee code, zero Python, zero network exists on the
> device.** The app stays 100% offline; Cognee's intelligence survives as data.

---

## 1. Why Cognee at all?

A naive RAG pipeline chunks PDFs and embeds them. That fails legal text in
specific, dangerous ways:

| Failure of naive RAG | What Cognee's graph extraction fixes |
|---|---|
| A chunk of a **repealed** section matches the query best | Graph edge `SUPERSEDED_BY` lets the retriever hop to the current law (e.g., IPC 1860 → BNS 2023) |
| Section text split across a page boundary loses its section number | Cognee's entity extraction anchors every chunk to a typed `Section` node with its page span |
| "Right to know grounds of arrest" appears in 4 statutes | `RELATED_RIGHT` / `IMPLEMENTS` edges let us surface the primary section plus verified related provisions |
| Definitions live far from the section using them | `DEFINED_IN` edges connect a term's use-site to its definition clause |

Cognee gives us **entities** (Statute, Section, Clause, Right, Authority) and
**relations** (SUPERSEDED_BY, AMENDED_BY, RELATED_RIGHT, DEFINED_IN, PART_OF)
extracted from the corpus — the "relational extraction" half of our memory layer.

## 2. The hard rule: Cognee never runs on the device

Cognee is a Python framework with LLM-assisted extraction and its own storage
backends. Running it on-device would violate every product constraint (offline,
zero-latency, auditable). So the boundary is absolute:

```
┌──────────────────────  DESKTOP (build time)  ──────────────────────┐
│  Official PDFs → Cognee (ingest → cognify → graph + chunks)        │
│               → flatten_to_sqlite.py                               │
│               → janadhikar_knowledge.db  (+ eval report)           │
└────────────────────────────────┬───────────────────────────────────┘
                                 │  one file, checked into the APK assets
┌────────────────────────────────▼───────────────────────────────────┐
│  DEVICE (runtime) — Kotlin + statically-linked sqlite-vec ONLY     │
│  vector KNN  +  graph-table hops  +  relational metadata rows      │
└─────────────────────────────────────────────────────────────────────┘
```

Every LLM-assisted extraction Cognee performs at build time is **hand-verified
against the source PDF before it enters the shipped DB** (CONTRIBUTING.md Rule 4).
The graph is treated as *reviewed data*, not as trusted AI output.

## 3. Pipeline stages (`knowledge-pipeline/`)

1. **ingest/** — authenticated bare-act PDFs; SHA-256 recorded per document.
2. **cognify/** — `cognee.add()` + `cognee.cognify()` over the corpus produces
   the knowledge graph (entities, relations, page-anchored chunks).
3. **review/** — human sign-off UI: every node/edge/chunk diffed against the
   source page. Unreviewed rows are dropped, never shipped.
4. **flatten/** — the Cognee graph is exported into four plain SQLite tables
   (schema below) plus a `vec0` virtual table of chunk embeddings. All Cognee
   runtime concepts disappear here.
5. **eval/** — golden EN/HI query set gates the artifact (Precision@1,
   false-positive rate — CONTRIBUTING.md Rule 6).

## 4. What ships on the device (the flattened schema)

```sql
-- Verbatim facts. The ONLY source for anything shown on the citation card.
CREATE TABLE statute_chunks (
  id               INTEGER PRIMARY KEY,
  node_id          INTEGER NOT NULL REFERENCES graph_nodes(id),
  statute_name     TEXT    NOT NULL,   -- e.g. 'Bharatiya Nagarik Suraksha Sanhita, 2023'
  statute_name_hi  TEXT    NOT NULL,
  section_number   TEXT    NOT NULL,   -- e.g. '47'
  clause           TEXT,               -- e.g. '(1)'
  page_number      INTEGER NOT NULL,   -- page in the official PDF
  chunk_text_en    TEXT    NOT NULL,   -- verbatim statute text
  chunk_text_hi    TEXT    NOT NULL,   -- official Hindi text where published
  source_document  TEXT    NOT NULL,
  source_sha256    TEXT    NOT NULL,
  compilation_date TEXT    NOT NULL    -- ISO-8601, shown on the citation card
);

-- Cognee's entity graph, flattened.
CREATE TABLE graph_nodes (
  id             INTEGER PRIMARY KEY,
  kind           TEXT NOT NULL,        -- STATUTE | SECTION | CLAUSE | RIGHT | AUTHORITY
  label          TEXT NOT NULL,
  statute_name   TEXT,
  section_number TEXT
);

-- Cognee's relation graph, flattened.
CREATE TABLE graph_edges (
  id           INTEGER PRIMARY KEY,
  src_node_id  INTEGER NOT NULL REFERENCES graph_nodes(id),
  dst_node_id  INTEGER NOT NULL REFERENCES graph_nodes(id),
  relation     TEXT    NOT NULL,       -- SUPERSEDED_BY | AMENDED_BY | RELATED_RIGHT | DEFINED_IN | PART_OF
  weight       REAL    NOT NULL DEFAULT 1.0
);

-- sqlite-vec virtual table: cosine KNN over chunk embeddings (EN + HI).
CREATE VIRTUAL TABLE vec_chunks USING vec0(
  chunk_id  INTEGER PRIMARY KEY,
  embedding FLOAT[384] distance_metric=cosine
);

-- Artifact self-description; the app refuses a schema/embedder it doesn't know.
CREATE TABLE kb_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
-- keys: schema_version, embedder_model_id, embedding_dim, compiled_at, corpus_manifest_sha256
```

## 5. How the app queries it — the hybrid walk

For every user query (typed or transcribed), `HybridRetriever` executes:

1. **VECTOR** — embed the query on-device (same multilingual embedder family the
   pipeline used; identity asserted against `kb_meta.embedder_model_id`), then
   KNN over `vec_chunks`.
2. **CONFIDENCE GATE** — best cosine similarity below the governed threshold →
   the retriever returns `NoVerifiedStatute`. Hard stop. No fallback.
3. **GRAPH** — for each candidate chunk's node: follow `SUPERSEDED_BY` /
   `AMENDED_BY` edges. A superseded section is *replaced* by its successor's
   chunk (or dropped if the successor isn't in-corpus). `RELATED_RIGHT` edges
   (capped, weight-ranked) attach verified related provisions.
4. **RELATIONAL** — load the surviving chunks' typed rows; `MetadataExtractor`
   strictly validates every field (non-null statute, section, page, source,
   date). A malformed row is discarded, never repaired.
5. The result carries the **verbatim text + typed metadata**. The LLM downstream
   sees only the text; the citation card renders only the typed fields.

## 6. Keeping it 100% offline — the checklist

- ✅ Cognee: build-time dependency of `knowledge-pipeline/` only; it does not
  appear in any Gradle file.
- ✅ Graph queries on device are plain SQL over `graph_nodes`/`graph_edges` —
  no graph database, no server.
- ✅ Vector search is statically-linked sqlite-vec — no extension download,
  no runtime loading.
- ✅ Query embedding runs on LiteRT from an APK-bundled `.tflite` — no
  embedding API.
- ✅ Knowledge updates ship as a new APK with a new `compilation_date` — never
  over the air.
