<div align="center">

# ⚖️ Janadhikar — जनाधिकार

### *Your rights. In your own words. On your phone.*

**An on-device AI legal assistant for Indian citizens — ask about your rights by
typing or speaking (English · Hindi · Hinglish) and get a clear, grounded answer
with the exact law behind it.**

<br/>

<img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android"/>
<img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>
<img src="https://img.shields.io/badge/Inference-100%25%20On--Device-FF6F00?style=for-the-badge&logo=google&logoColor=white" alt="On-Device AI"/>
<img src="https://img.shields.io/badge/Queries-Never%20Leave%20the%20Phone-2E7D32?style=for-the-badge&logo=lock&logoColor=white" alt="Private"/>

<br/><br/>

### 📲 [**Download Janadhikar v1.0.0 (APK)**](https://github.com/FiscalMindset/JanAdhikar/releases/latest)

<a href="https://github.com/FiscalMindset/JanAdhikar/releases/latest"><img src="https://img.shields.io/github/v/release/FiscalMindset/JanAdhikar?style=for-the-badge&label=Latest%20Release&color=3DDC84&logo=android&logoColor=white" alt="Latest release"/></a>
<a href="https://github.com/FiscalMindset/JanAdhikar/releases"><img src="https://img.shields.io/github/downloads/FiscalMindset/JanAdhikar/total?style=for-the-badge&label=Downloads&color=FF6F00&logo=cloudsmith&logoColor=white" alt="Total downloads"/></a>
<img src="https://img.shields.io/badge/APK-247%20MB-555?style=for-the-badge" alt="APK size"/>
<img src="https://img.shields.io/badge/Requires-Android%208.0%2B%20(arm64)-3DDC84?style=for-the-badge" alt="Requirements"/>

<sub>Install → open once on WiFi to fetch the AI model → use it fully offline.</sub>

<br/><br/>

<table>
<tr>
<td align="center" width="33%">
<h3>🔒 Private by design</h3>
Every question, transcript &<br/>answer stays on the device.<br/>Network is used <b>once</b> — to download the model.
</td>
<td align="center" width="33%">
<h3>⚡ Fast & offline</h3>
Sub-second retrieval from a<br/>local law database; the AI<br/>runs on-device after setup.
</td>
<td align="center" width="33%">
<h3>📜 Grounded in real law</h3>
Every answer links to the<br/><b>exact bare-act text</b> +<br/>the official PDF at the right page.
</td>
</tr>
</table>

**🏠 README** · **[📐 Architecture](ARCHITECTURE.md)** · **[🤝 Contributing](CONTRIBUTING.md)** · **[📚 Knowledge](KNOWLEDGE.md)** · **[🧠 Cognee](COGNEE.md)**

</div>

---

## ✨ What it does

```mermaid
flowchart LR
    A["🙋 'article 15 kya hain'<br/>'police slapped me'<br/>🎙 or typed"] --> B["Janadhikar"]
    B --> C["📄 Clear answer<br/>quote → meaning → why it matters"]
    B --> D["🔗 Exact law + official PDF"]
    B --> E["🌐 English · Hindi · Hinglish"]
```

- **Ask anything, any way** — type or speak; English, Hindi, or **Hinglish**
  (`article 15 kya hain` → answered in Hindi).
- **Rich, ChatGPT-style answers** — the exact wording quoted, what it means in
  simple words, how it's interpreted, and why it matters — rendered as decorative
  Markdown (bold, blockquotes, highlights).
- **Always shows the real law** — a collapsible **Sources** card with the verbatim
  section, page, and a **"📄 Read the official law"** button that opens the
  government PDF **in-app at the exact page**.
- **Smart routing** — `article 21`, `section 302`, `fundamental rights`,
  `how many articles`, or `explain this simpler` are each handled the right way.
- **📖 Constitution overview** — dates, drafters (Dr. B. R. Ambedkar), the
  original-vs-today structure table, the three pillars, borrowed sources.
- **🕘 History**, **✎ new chat**, **select-a-word → meaning**, **copy**, and a
  **model picker** (Fast / Balanced / Gemma).
- **Never fails open** — if the model errors or times out, you still get the
  **exact verified law**, never a hallucination.

---

## 🚀 Try it

### Option A — install the APK (for users / testers)

1. **[Download `janadhikar-v1.0.0.apk`](https://github.com/FiscalMindset/JanAdhikar/releases/latest)** and install it (allow *install from unknown sources*).
2. Open it **once on WiFi** — it downloads the ~350 MB AI model with a progress bar.
3. Done — it now works **fully offline**. Type or tap the mic and ask.

> First run needs internet + ~1 GB free storage. After that, no network is used.
> Requires Android 8.0+ (arm64). The APK is signed with the Janadhikar release key.
>
> **Verify the download (optional):** `shasum -a 256 janadhikar-v1.0.0.apk` →
> `526b801b994b65ee788428ab220eb165151e6219c0fcc6ef3e37f3b9f084a82b`

### Option B — build from source (for developers)

```bash
git clone --recurse-submodules git@github.com:FiscalMindset/JanAdhikar.git
cd JanAdhikar
./gradlew :app:assembleDebug            # builds native (llama.cpp + whisper.cpp + sqlite-vec)
adb install app/build/outputs/apk/debug/app-debug.apk

# Optional: push models over USB instead of downloading in-app
HF_TOKEN=hf_xxx ./scripts/fetch_models.sh
./scripts/push_models.sh
```

> A signed **release** build (`./gradlew :app:assembleRelease`) needs a
> `keystore.properties` at the repo root (gitignored) — see the release notes.

---

## 🤖 The models (pick one in Settings)

| Model | Runtime | Download | Speed | Best for |
|---|---|---|---|---|
| **Balanced — Qwen 2.5 1.5B** *(default)* | llama.cpp (CPU, Q4_0) | auto, ~1 GB | Moderate | **Best accuracy** & Hindi, richest answers |
| **⚡ Fast — Qwen 2.5 0.5B** | llama.cpp (CPU, Q4_0) | auto, ~350 MB | Quick | Speed on budget phones (lower accuracy) |
| **Gemma 3 1B / 4B** | MediaPipe (accelerated) | manual `.task` | Fast | Devices without CPU dot-product |

The **1.5B is the default** because it follows the retrieved bare-act text
faithfully — the 0.5B is faster per token but drifts and can mix up provisions,
which is unacceptable for legal answers. Speed comes from **Q4_0 repacking +
Flash Attention + ARM dot-product kernels** (≈ 4× faster than a naïve build)
**plus live token streaming** — the answer is written out word-by-word as it
generates, so it feels responsive instead of a long silent wait. On CPUs without
dot-product the app safely falls back to Gemma / verbatim.

---

## 🧱 Tech stack

| Area | Technology |
|---|---|
| **UI** | Kotlin · Jetpack Compose · Material 3 |
| **LLM** | llama.cpp (Qwen 2.5 GGUF) · MediaPipe GenAI (Gemma) — via JNI |
| **Voice (STT)** | whisper.cpp (small, multilingual) |
| **Retrieval** | SQLite + **sqlite-vec** (vector KNN) + **FTS5** (keyword), fused in one native lib |
| **Embedder** | paraphrase-multilingual-MiniLM-L12-v2 → TFLite (LiteRT), pure-Kotlin SentencePiece |
| **Knowledge** | Offline Python pipeline over official India Code / Constitution PDFs |

See **[ARCHITECTURE.md](ARCHITECTURE.md)** for the full diagrams (startup, routing,
retrieval, model selection, voice, native build, distribution).

---

## 📚 The law inside

5 statutes · **~1,742 provisions** — the **Constitution of India** (466 articles),
**Bharatiya Nyaya Sanhita**, **Bharatiya Nagarik Suraksha Sanhita**, **Bharatiya
Sakshya Adhiniyam**, and the **Motor Vehicles Act, 1988** — all with verbatim
English + Hindi text, page numbers, and official source links.
See [`knowledge_database.md`](knowledge_database.md).

---

## 🔐 Privacy & honesty

- **Your questions never leave the phone.** The only network call is the one-time
  model download from Hugging Face.
- **Grounded, not fabricated.** Answers are built from the retrieved law; the
  exact verified text is always one tap away; if the model produces garbage, the
  app shows the raw law instead.
- **Auditable.** The manifest, the prompt builder (`PromptContract`), and the
  output guard (`OutputSanitizer`) are small and reviewable.

---

<div align="center">
<sub>Built to put justice information in every hand. ⚖️</sub>
</div>
