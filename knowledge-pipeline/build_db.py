#!/usr/bin/env python
"""Compile the Janadhikar knowledge base: PDFs -> janadhikar_knowledge.db.

Stages (COGNEE.md pipeline contract):
  extract  - deterministic section extraction (pipeline/extract.py, no LLM)
  graph    - STATUTE/SECTION nodes, PART_OF + RELATED_RIGHT edges
  embed    - sentence-transformers distiluse-base-multilingual-cased-v2 (512-d)
  pack     - SQLite file matching the app's Room schema exactly (identity hash
             + user_version injected from app/schemas/.../1.json), plus the
             vec_chunks sqlite-vec virtual table

Usage:
  .venv/bin/python build_db.py            # writes build/ and app assets
"""

from __future__ import annotations

import hashlib
import json
import sqlite3
import struct
import sys
from datetime import date
from pathlib import Path

import sqlite_vec

from pipeline.extract import extract_flow, extract_sections

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
ASSETS_DB_DIR = REPO / "app/src/main/assets/db"
ROOM_SCHEMA = REPO / "app/schemas/com.janadhikar.memory.KnowledgeDatabase/1.json"

# MUST match QueryEmbedder on the device — verified at app startup (kb_meta).
# Chosen by experiment_models.py: P@1 7/14, Hit@3 12/14, refusal window
# (0.285, 0.386) — the only candidate with a usable junk/genuine gap.
EMBEDDER_MODEL = "paraphrase-multilingual-MiniLM-L12-v2"
EMBEDDER_MODEL_ID = "paraphrase-multilingual-minilm-l12-v2/384/v2"
EMBEDDING_DIM = 384

DB_NAME = "janadhikar_knowledge.db"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for block in iter(lambda: fh.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    manifest = json.loads((ROOT / "corpus/manifest.json").read_text())
    room = json.loads(ROOM_SCHEMA.read_text())["database"]

    # ── extract ─────────────────────────────────────────────────────────────
    chunks_rows: list[dict] = []
    nodes: list[tuple[int, str, str, str | None, str | None]] = []
    edges: list[tuple[int, int, int, str, float]] = []
    node_id = 0
    chunk_id = 0
    edge_id = 0

    for act in manifest["acts"]:
        pdf = ROOT / act["pdf"]
        source_sha = sha256_file(pdf)
        gap = act.get("max_gap", 1)
        if act.get("mode") == "flow":
            result = extract_flow(str(pdf), max_gap=gap)
        else:
            result = extract_sections(str(pdf), max_gap=gap)
        unit = act.get("unit", "Section").upper()
        expected = act["expected_last_section"]
        assert result.last_section == expected, (
            f"{act['statute_name']}: extracted up to {result.last_section}, "
            f"expected {expected}. Refusing to pack a partial act."
        )
        if result.dropped:
            print(f"  ! {act['statute_name']}: {len(result.dropped)} dropped:", file=sys.stderr)
            for reason in result.dropped:
                print(f"    - {reason}", file=sys.stderr)

        node_id += 1
        statute_node = node_id
        nodes.append((statute_node, "STATUTE", act["statute_name"], act["statute_name"], None))

        section_nodes: dict[str, int] = {}
        section_xrefs: dict[str, list[str]] = {}
        for chunk in result.chunks:
            if chunk.section_number not in section_nodes:
                node_id += 1
                section_nodes[chunk.section_number] = node_id
                label = f"{act['statute_name']} — {unit.title()} {chunk.section_number}"
                nodes.append((node_id, "SECTION", label, act["statute_name"], chunk.section_number))
                edge_id += 1
                edges.append((edge_id, node_id, statute_node, "PART_OF", 1.0))
            if chunk.cross_references:
                section_xrefs[chunk.section_number] = chunk.cross_references

            chunk_id += 1
            chunks_rows.append(
                {
                    "id": chunk_id,
                    "node_id": section_nodes[chunk.section_number],
                    "statute_name": act["statute_name"],
                    "statute_name_hi": act["statute_name_hi"],
                    "unit": unit,
                    "section_number": chunk.section_number,
                    "clause": None,
                    "page_number": chunk.page_number,
                    "chunk_text_en": chunk.text,
                    # Official Hindi text ingestion is a tracked follow-up; until
                    # then the verbatim English text is served for both languages
                    # (the LLM's translation duty covers the Hindi directive).
                    "chunk_text_hi": chunk.text,
                    "source_document": act["source_document"],
                    "source_sha256": source_sha,
                    "compilation_date": date.today().isoformat(),
                    "source_url": act.get("source_url", ""),
                }
            )

        # RELATED_RIGHT edges from in-text cross-references (same act only).
        for section, refs in section_xrefs.items():
            for ref in refs:
                if ref in section_nodes:
                    edge_id += 1
                    edges.append(
                        (edge_id, section_nodes[section], section_nodes[ref], "RELATED_RIGHT", 1.0)
                    )

        print(
            f"  ✓ {act['statute_name']}: {result.sections_found} sections, "
            f"{len(result.chunks)} chunks, sha256={source_sha[:12]}…"
        )

    # ── embed ───────────────────────────────────────────────────────────────
    print(f"  … embedding {len(chunks_rows)} chunks with {EMBEDDER_MODEL}")
    from sentence_transformers import SentenceTransformer

    model = SentenceTransformer(EMBEDDER_MODEL)
    # Plain verbatim text: the section's marginal note (its natural title) is
    # already the first sentence of every chunk; prefixing the act name was
    # measured to dilute short-query similarity (see eval_queries.py).
    texts = [row["chunk_text_en"] for row in chunks_rows]
    embeddings = model.encode(texts, batch_size=64, show_progress_bar=True, convert_to_numpy=True)
    assert embeddings.shape == (len(chunks_rows), EMBEDDING_DIM), embeddings.shape
    assert model.max_seq_length >= 128, model.max_seq_length

    # ── pack ────────────────────────────────────────────────────────────────
    build_dir = ROOT / "build"
    build_dir.mkdir(exist_ok=True)
    db_path = build_dir / DB_NAME
    db_path.unlink(missing_ok=True)

    conn = sqlite3.connect(db_path)
    conn.enable_load_extension(True)
    sqlite_vec.load(conn)
    conn.enable_load_extension(False)

    # Tables exactly as Room generated them (schema JSON is the contract).
    for entity in room["entities"]:
        conn.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))

    conn.executemany(
        "INSERT INTO statute_chunks VALUES (:id,:node_id,:statute_name,:statute_name_hi,"
        ":unit,:section_number,:clause,:page_number,:chunk_text_en,:chunk_text_hi,"
        ":source_document,:source_sha256,:compilation_date,:source_url)",
        chunks_rows,
    )
    conn.executemany("INSERT INTO graph_nodes VALUES (?,?,?,?,?)", nodes)
    conn.executemany("INSERT INTO graph_edges VALUES (?,?,?,?,?)", edges)

    conn.execute(
        f"CREATE VIRTUAL TABLE vec_chunks USING vec0("
        f"chunk_id INTEGER PRIMARY KEY, embedding FLOAT[{EMBEDDING_DIM}] distance_metric=cosine)"
    )
    conn.executemany(
        "INSERT INTO vec_chunks(chunk_id, embedding) VALUES (?, ?)",
        (
            (row["id"], struct.pack(f"{EMBEDDING_DIM}f", *embedding))
            for row, embedding in zip(chunks_rows, embeddings)
        ),
    )

    # ── FTS5 keyword index (title + body), for the hybrid retriever ──────────
    # Pure semantic search misses colloquial crisis language ("killed",
    # "slapped") that does not resemble formal statute text. The on-device
    # CrisisLexicon maps such words to legal terms ("murder", "assault") and
    # this index finds the matching sections. Title is weighted heavily at
    # query time so the marginal note (e.g. "Murder", "Theft") dominates.
    conn.execute("CREATE VIRTUAL TABLE chunk_fts USING fts5(title, body)")

    def _split_title(text: str) -> tuple[str, str]:
        head, marker, body = text.partition(".—")
        return (head.strip(), body.strip()) if marker and len(head) < 200 else ("", text)

    conn.executemany(
        "INSERT INTO chunk_fts(rowid, title, body) VALUES (?, ?, ?)",
        (
            (row["id"], *_split_title(row["chunk_text_en"]))
            for row in chunks_rows
        ),
    )

    meta = {
        "schema_version": str(room["version"]),
        "embedder_model_id": EMBEDDER_MODEL_ID,
        "embedding_dim": str(EMBEDDING_DIM),
        "compiled_at": date.today().isoformat(),
        "corpus_manifest_sha256": hashlib.sha256(
            (ROOT / "corpus/manifest.json").read_bytes()
        ).hexdigest(),
    }
    conn.executemany("INSERT INTO kb_meta VALUES (?,?)", meta.items())

    # Room handshake: identity hash + user_version make Room accept this file
    # as its own and skip destructive schema creation.
    conn.execute("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
    conn.execute("INSERT INTO room_master_table VALUES (42, ?)", (room["identityHash"],))
    conn.execute(f"PRAGMA user_version = {room['version']}")

    conn.commit()
    conn.execute("VACUUM")
    conn.close()

    # ── ship ────────────────────────────────────────────────────────────────
    ASSETS_DB_DIR.mkdir(parents=True, exist_ok=True)
    target = ASSETS_DB_DIR / DB_NAME
    target.write_bytes(db_path.read_bytes())
    (ASSETS_DB_DIR / f"{DB_NAME}.sha256").write_text(sha256_file(target))

    size_mb = target.stat().st_size / (1 << 20)
    print(f"  ✓ packed {target} ({size_mb:.1f} MB)")
    print(f"    chunks={len(chunks_rows)} nodes={len(nodes)} edges={len(edges)}")
    print(f"    identity_hash={room['identityHash']} user_version={room['version']}")


if __name__ == "__main__":
    main()
