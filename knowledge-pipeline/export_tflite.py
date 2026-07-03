#!/usr/bin/env python
"""Export the query embedder to TFLite for on-device (LiteRT) inference.

Model: paraphrase-multilingual-MiniLM-L12-v2 (chosen by experiment_models.py).

Produces, in app/src/main/assets/models/:
  embedder_minilm_384.tflite - XLM-R MiniLM encoder + mean-pool, int8 dynamic
  spm_vocab.tsv              - token \t id \t score for the Kotlin unigram
                               tokenizer (HF ids — no fairseq offset math on
                               the device)
and, in app/src/test/resources/:
  tokenizer_vectors.json     - golden (text -> input_ids) pairs; the Kotlin
                               SentencePieceTokenizer unit test must reproduce
                               them EXACTLY.

Run with the export venv: .venv-export/bin/python export_tflite.py
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import tensorflow as tf
from sentence_transformers import SentenceTransformer
from transformers import AutoTokenizer, TFAutoModel

MODEL = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
SEQ_LEN = 128
OUT_DIM = 384

REPO = Path(__file__).resolve().parent.parent
MODELS_DIR = REPO / "app/src/main/assets/models"
TEST_RES_DIR = REPO / "app/src/test/resources"

PARITY_TEXTS = [
    "police won't tell me why I am being arrested",
    "पुलिस ने मेरा फोन ज़ब्त कर लिया",
    "Police ne mera phone le liya bina warrant ke!",
    "can a woman be arrested at night?",
    "हत्या की सजा क्या है",
    "Section 47, BNSS — grounds of arrest.",
    "   whitespace   and    UPPERCASE MiXeD   ",
    "emoji 🙏 and numbers 12345",
]


def main() -> None:
    tokenizer = AutoTokenizer.from_pretrained(MODEL)

    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    out = MODELS_DIR / "embedder_minilm_384.tflite"
    if out.exists():
        print(f"• {out} already exists — skipping conversion (delete to force)")
        tflite_model = out.read_bytes()
    else:
        encoder = TFAutoModel.from_pretrained(MODEL, from_pt=True)

        class Embedder(tf.Module):
            @tf.function(
                input_signature=[
                    tf.TensorSpec([1, SEQ_LEN], tf.int32, name="input_ids"),
                    tf.TensorSpec([1, SEQ_LEN], tf.int32, name="attention_mask"),
                ]
            )
            def embed(self, input_ids, attention_mask):
                hidden = encoder(
                    input_ids=input_ids, attention_mask=attention_mask
                ).last_hidden_state
                mask = tf.cast(attention_mask, tf.float32)[..., tf.newaxis]
                return tf.reduce_sum(hidden * mask, axis=1) / tf.maximum(
                    tf.reduce_sum(mask, axis=1), 1e-9
                )

        module = Embedder()
        converter = tf.lite.TFLiteConverter.from_concrete_functions(
            [module.embed.get_concrete_function()], module
        )
        converter.optimizations = [tf.lite.Optimize.DEFAULT]  # dynamic-range int8
        tflite_model = converter.convert()
        out.write_bytes(tflite_model)
    print(f"✓ {out} ({out.stat().st_size / (1 << 20):.1f} MB)")

    # ── vocab: HF token -> (id, unigram score) ──────────────────────────────
    # Scores come from the sentencepiece model proto; ids from the HF mapping,
    # so the Kotlin side never re-implements fairseq offset logic.
    import sentencepiece.sentencepiece_model_pb2 as spm_pb
    from huggingface_hub import hf_hub_download

    spm_path = hf_hub_download(MODEL, "sentencepiece.bpe.model")
    proto = spm_pb.ModelProto()
    proto.ParseFromString(Path(spm_path).read_bytes())
    scores = {p.piece: p.score for p in proto.pieces}

    vocab = tokenizer.get_vocab()  # token -> HF id
    vocab_path = MODELS_DIR / "spm_vocab.tsv"
    with vocab_path.open("w") as fh:
        for token, hf_id in sorted(vocab.items(), key=lambda kv: kv[1]):
            fh.write(f"{token}\t{hf_id}\t{scores.get(token, -100.0)}\n")
    print(f"✓ {vocab_path} ({len(vocab)} tokens)")

    # ── golden tokenization vectors for the Kotlin parity test ──────────────
    TEST_RES_DIR.mkdir(parents=True, exist_ok=True)
    vectors = [
        {
            "text": text,
            "input_ids": tokenizer(text, truncation=True, max_length=SEQ_LEN)["input_ids"],
        }
        for text in PARITY_TEXTS
    ]
    vectors_path = TEST_RES_DIR / "tokenizer_vectors.json"
    vectors_path.write_text(json.dumps(vectors, ensure_ascii=False, indent=2))
    print(f"✓ {vectors_path} ({len(vectors)} vectors)")

    # ── parity: TFLite vs sentence-transformers reference ───────────────────
    st = SentenceTransformer(MODEL.split("/", 1)[1])
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    inputs = {d["name"].split(":")[0].removeprefix("serving_default_"): d["index"]
              for d in interpreter.get_input_details()}

    def tflite_embed(text: str) -> np.ndarray:
        enc = tokenizer(
            text, max_length=SEQ_LEN, padding="max_length", truncation=True, return_tensors="np"
        )
        interpreter.set_tensor(inputs["input_ids"], enc["input_ids"].astype(np.int32))
        interpreter.set_tensor(inputs["attention_mask"], enc["attention_mask"].astype(np.int32))
        interpreter.invoke()
        return interpreter.get_tensor(interpreter.get_output_details()[0]["index"])[0]

    for text in PARITY_TEXTS[:4]:
        reference = st.encode(text, convert_to_numpy=True)
        candidate = tflite_embed(text)
        cos = float(
            np.dot(reference, candidate)
            / (np.linalg.norm(reference) * np.linalg.norm(candidate))
        )
        print(f"  parity cos={cos:.4f}  {text[:44]!r}")
        assert cos > 0.98, "quantised model diverged from reference — do not ship"

    print("✓ parity check passed")


if __name__ == "__main__":
    main()
