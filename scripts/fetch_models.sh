#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# Janadhikar — model artifact fetcher (run once, on your dev machine)
#
# Downloads the three on-device models into app/src/main/assets/models/.
# These are BUILD inputs, not runtime downloads — the app itself never
# touches the network (CONTRIBUTING.md Rule 5).
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail
cd "$(dirname "$0")/.."
MODELS_DIR="app/src/main/assets/models"
mkdir -p "$MODELS_DIR"

# ── 1. whisper.cpp STT model (multilingual EN+HI, ~190 MB) — no login ──────
WHISPER_FILE="$MODELS_DIR/ggml-small-q5_1.bin"
if [[ ! -f "$WHISPER_FILE" ]]; then
    echo "→ Downloading whisper small (q5_1, multilingual)…"
    curl -fL --progress-bar -o "$WHISPER_FILE" \
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin"
else
    echo "✓ whisper model already present"
fi

# ── 2. Gemma 3 1B (4-bit, LiteRT .task) — requires HF login + license ──────
# Gemma is gated: accept the license at
#   https://huggingface.co/litert-community/Gemma3-1B-IT
# then create a token at https://huggingface.co/settings/tokens and run:
#   HF_TOKEN=hf_xxx ./scripts/fetch_models.sh
GEMMA_FILE="$MODELS_DIR/gemma3-1b-it-int4.task"
if [[ ! -f "$GEMMA_FILE" ]]; then
    if [[ -n "${HF_TOKEN:-}" ]]; then
        echo "→ Downloading Gemma 3 1B int4 (.task, ~550 MB)…"
        curl -fL --progress-bar -H "Authorization: Bearer $HF_TOKEN" -o "$GEMMA_FILE" \
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"
    else
        echo "✗ Gemma 3: set HF_TOKEN (see comments in this script). Skipping."
    fi
else
    echo "✓ Gemma model already present"
fi

# Optional: the bigger, more accurate Gemma 3 4B (int4, ~3 GB). If present the
# app prefers it automatically (slower — expect ~60-90s/answer on a phone).
# Enable with GEMMA_4B=1 ./scripts/fetch_models.sh
GEMMA4B_FILE="$MODELS_DIR/gemma3-4b-it-int4-web.task"
if [[ "${GEMMA_4B:-0}" == "1" && ! -f "$GEMMA4B_FILE" ]]; then
    if [[ -n "${HF_TOKEN:-}" ]]; then
        echo "→ Downloading Gemma 3 4B int4 (.task, ~3 GB)…"
        curl -fL --progress-bar -H "Authorization: Bearer $HF_TOKEN" -o "$GEMMA4B_FILE" \
            "https://huggingface.co/litert-community/Gemma3-4B-IT/resolve/main/gemma3-4b-it-int4-web.task"
    else
        echo "✗ Gemma 4B: set HF_TOKEN (accept license at litert-community/Gemma3-4B-IT). Skipping."
    fi
fi

# ── 3. Multilingual sentence embedder (384-dim .tflite) ────────────────────
# Must be the SAME model family the knowledge pipeline uses (COGNEE.md §5).
# Convert paraphrase-multilingual-MiniLM-L12-v2 to TFLite via
# knowledge-pipeline/embed/export_tflite.py (see that script for details),
# then place the result at:
EMBEDDER_FILE="$MODELS_DIR/embedder_multilingual_minilm_384.tflite"
if [[ ! -f "$EMBEDDER_FILE" ]]; then
    echo "✗ Embedder: produce via knowledge-pipeline/embed/export_tflite.py. Skipping."
else
    echo "✓ Embedder already present"
fi

echo
echo "Assets in $MODELS_DIR:"
ls -lh "$MODELS_DIR" | grep -v '^total\|.gitkeep' || true
