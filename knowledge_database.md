# Janadhikar Knowledge Database

> **Mission: justice information in every hand.** This file is the single source
> of truth about *what law the app knows*, *where it came from*, and *how it is
> built*. If the app can cite it, it is described here. If it is not here, the
> app will refuse to answer rather than guess.

---

## 1. What is shipped today

A single SQLite file — `app/src/main/assets/db/janadhikar_knowledge.db`
(**9.4 MB**) — compiled offline from official Government of India legal PDFs and
bundled inside the APK. It is opened **read-only** on the device; nothing is
ever fetched over a network.

| # | Statute | Provisions | Chunks | Official source (SHA-256 recorded per document) |
|---|---|---|---|---|
| 1 | **Constitution of India** | 466 articles | 466 | Govt. of India, consolidated 2024 |
| 2 | **Bharatiya Nyaya Sanhita, 2023** (BNS — offences) | 358 sections | 398 | indiacode.nic.in, Act 45 of 2023 |
| 3 | **Bharatiya Nagarik Suraksha Sanhita, 2023** (BNSS — procedure) | 531 sections | 628 | indiacode.nic.in, Act 46 of 2023 |
| 4 | **Bharatiya Sakshya Adhiniyam, 2023** (BSA — evidence) | 170 sections | 182 | indiacode.nic.in, Act 47 of 2023 |
| 5 | **Motor Vehicles Act, 1988** | 217 sections | 320 | indiacode.nic.in, Act 59 of 1988 |
| | **Total** | **1,742 provisions** | **1,994 chunks** | |

This covers the **Constitution** plus the **entire new criminal-law code**
(the trio that replaced IPC/CrPC/Evidence Act from 1 July 2024) and the law
most citizens meet on the road. Between them these are the statutes behind the
overwhelming majority of everyday police, arrest, rights, and offence questions.

**Graph:** 1,747 nodes (one STATUTE node + one node per provision) and 2,882
edges (`PART_OF` linking provisions to their act, `RELATED_RIGHT` from in-text
cross-references like "… as provided in section 35 …").

**Vectors:** every chunk carries a 384-dimension embedding
(`paraphrase-multilingual-MiniLM-L12-v2`) in a `sqlite-vec` virtual table for
cosine nearest-neighbour search. The same model runs on-device for the query,
so English and Hindi share one semantic space.

---

## 2. Why these sources, and why they are trustworthy

- **Only official Government of India PDFs.** The Constitution from the Govt.
  consolidated edition; every act from India Code (`indiacode.nic.in`), the
  National Repository of all Central and State Acts. No third-party summaries,
  no commentary, no blog text.
- **Every row is traceable.** Each chunk stores `source_document`,
  `source_sha256` (hash of the exact PDF ingested), and `compilation_date`. The
  citation card shows the compilation date so a user always knows how current
  the text is.
- **Verbatim, not paraphrased.** Extraction copies the statute text exactly;
  the LLM is never allowed to author legal text (see the Zero-Hallucination rule
  in `CONTRIBUTING.md`).

---

## 3. How it is built (the pipeline)

Everything runs **offline, on a desktop, at build time** — never on the phone.
Full details in `knowledge-pipeline/README.md`; the stages:

```
official PDFs
   │  extract   deterministic, regex/structure based — NO LLM.
   │            Two modes: "lines" for bare acts (BNS/BNSS/BSA/MV, section-
   │            numbered) and "flow" for the Constitution (articles embedded in
   │            a single text run, detected by the ".—" marginal-note marker).
   │            Article numbers with letter suffixes (21A, 51A, 371J) supported.
   ▼
page-anchored verbatim chunks  (statute, unit, number, page, text)
   │  graph     STATUTE/provision nodes; PART_OF + RELATED_RIGHT edges
   │  embed     MiniLM-384 over every chunk (EN+HI shared space)
   │  pack      SQLite matching the app's Room schema exactly (identity hash +
   │            user_version injected) + sqlite-vec vec_chunks + kb_meta
   ▼
janadhikar_knowledge.db  (+ .sha256)  →  bundled in the APK
```

**Quality gate (`eval_queries.py`, CONTRIBUTING.md Rule 6).** A golden set of
English + Hindi queries with known-correct provisions, plus junk queries that
*must* be refused. Current artifact:

- **Precision@1 = 11/19, Hit@3 = 16/19**
- Safe confidence window **(0.367, 0.388)** → the app's governed
  `CONFIDENCE_THRESHOLD = 0.38`. A false match is treated as a safety incident,
  so the threshold is biased toward refusal: below it the app says
  *"No verified legal statute found. Do not speculate."*

---

## 4. Schema (what the device reads)

```
statute_chunks(id, node_id, statute_name, statute_name_hi, unit,
               section_number, clause, page_number,
               chunk_text_en, chunk_text_hi,
               source_document, source_sha256, compilation_date)
graph_nodes(id, kind, label, statute_name, section_number)
graph_edges(id, src_node_id, dst_node_id, relation, weight)
vec_chunks(chunk_id, embedding FLOAT[384] distance_metric=cosine)   -- sqlite-vec
kb_meta(key, value)   -- schema_version, embedder_model_id, embedding_dim, …
```

`unit` is `SECTION` or `ARTICLE`, so the citation card reads **"Article 21"** for
the Constitution and **"Section 47"** for an act — never mislabelled.

The app refuses to start if `kb_meta.embedder_model_id` / `embedding_dim` do not
match the on-device embedder: mismatched vectors would silently corrupt every
similarity score.

---

## 5. Honest coverage limits

- **Not "every Indian law".** India has thousands of central and state acts;
  no phone ships them all. Janadhikar ships the Constitution + the statutes
  behind the most common citizen–state encounters, and **refuses** on anything
  outside that corpus rather than fabricate. Coverage grows by adding sources to
  the pipeline, not by loosening the refusal.
- **Hindi text.** UI and directives are fully bilingual, and the multilingual
  embedder retrieves correctly for Hindi queries. The *verbatim statute text*
  currently stored is the official English; official Hindi gazette text
  ingestion is the top data roadmap item (the schema already has `chunk_text_hi`
  waiting for it).
- **Schedules & tables** (e.g. the BNSS First Schedule) are excluded for now —
  they are large classification tables that need structured, not prose,
  handling.

## 6. Roadmap (add sources, keep the guarantee)

Priority acts to ingest next, all citizen-facing: Protection of Women from
Domestic Violence Act 2005 · Right to Information Act 2005 · Consumer Protection
Act 2019 · SC/ST (Prevention of Atrocities) Act 1989 · Juvenile Justice Act
2015 · Dowry Prohibition Act 1961. Then official Hindi verbatim text for the
existing corpus. Every addition must pass the `eval_queries.py` gate before it
ships.

---

*Every row here is something a citizen can hold up and act on. That is the
whole point.*
