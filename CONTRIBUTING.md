# Contributing to Janadhikar

Thank you for helping build a tool that people will rely on in some of the most stressful moments of their lives. That sentence is not rhetorical — it is the design constraint that governs every rule in this document.

**A wrong answer from Janadhikar is worse than no answer.** A hallucinated section number shown to a citizen mid-confrontation can destroy their credibility, escalate the situation, or expose them to legal harm. Therefore this project enforces rules that are stricter than typical open-source etiquette. Read them before writing a single line of code.

---

## ☢️ The Zero-Hallucination Code of Conduct

These rules are **non-negotiable**. PRs violating them will be closed without extended discussion, regardless of code quality.

### Rule 1 — The LLM never generates legal facts

The Gemma 3 model is a **translator and simplifier only**. It must never be prompted, fine-tuned, or post-processed in a way that allows it to produce:

- Statute names or act titles
- Section, clause, article, or sub-section numbers
- Page numbers or citation strings
- Legal claims not present verbatim in the retrieved database text

**Enforcement in code:** the LLM prompt template lives in a single sealed location (`llm/PromptContract.kt`). It accepts exactly one dynamic field: the verbatim statute text retrieved from SQLite. Any PR that adds a second free-text injection point into the prompt, or that surfaces raw LLM output containing citation-like patterns (`Section \d+`, `Sec\.`, `धारा \d+`, page references) without a matching database row, is an automatic rejection.

### Rule 2 — All metadata flows from database rows, verbatim

The citation card (Statute / Section / Page Number) must be populated **exclusively** from typed fields on the SQLite relational row — never from parsed LLM output, never from string manipulation of the statute text, never from a constant in Kotlin code.

```kotlin
// ✅ CORRECT — typed fields from the DB row
CitationCard(
    statute = row.statuteName,
    section = row.sectionNumber,
    page = row.pageNumber,
)

// ❌ FORBIDDEN — parsing metadata out of model output
val section = llmResponse.extractSectionNumber()   // instant PR rejection
```

### Rule 3 — Low confidence means silence, not creativity

If `sqlite-vec` returns no match above the confidence threshold, the app outputs exactly:

> **"No verified legal statute found. Do not speculate."**

Do not "improve" this path. Do not add fallbacks that ask the LLM to answer from general knowledge. Do not lower the confidence threshold to make demos look better. The threshold value is a governed constant and changing it requires a maintainer-approved evaluation run (see Rule 6).

### Rule 4 — The knowledge base is read-only at runtime and sacred at build time

- The app **never** writes to `janadhikar_knowledge.db`.
- Every row in the knowledge base must trace to an official legal publication (Gazette of India, official ministry PDF, or authenticated bare-act text). Each row carries `source_document`, `source_page`, and `compilation_date` columns — these are mandatory, non-null, and reviewed by hand.
- Changes to the knowledge pipeline (`knowledge-pipeline/`) require a **two-reviewer sign-off**, at least one of whom validates the extracted rows against the source PDF page-by-page for the affected sections.

### Rule 5 — No network. Not even a little.

- `android.permission.INTERNET` must never appear in any manifest, including transitively via dependencies. CI fails the build if the merged manifest contains it.
- No dependency may be added that phones home (analytics, crash reporting, remote config, ad SDKs). When adding any dependency, include the output of the merged-manifest check in your PR description.
- "It only syncs when on Wi-Fi" is not a feature request we will accept. Offline is the product.

### Rule 6 — Accuracy changes require evidence

Any PR touching retrieval (embedding model, chunking, confidence threshold, ranking) must include a run of the offline evaluation suite (`knowledge-pipeline/eval/`) showing:

- **Precision@1** on the golden query set (English and Hindi separately)
- **False-positive rate** at the proposed threshold (queries that *should* return "no verified statute" but don't)

A change that improves recall while increasing false positives will be rejected. In this project, **a false positive is a safety incident**.

### Rule 7 — Hindi is a first-class language, not a translation afterthought

- Every user-facing string ships in English and Hindi simultaneously. PRs adding English-only strings are incomplete.
- Hindi legal terminology must match the official Hindi versions of the statutes where they exist (e.g., use धारा for Section, not a colloquial paraphrase).
- STT changes must be tested against Hindi audio samples, including code-switched ("Hinglish") speech, which is the dominant real-world register.

---

## 🚨 Reporting a Factual Inaccuracy (Priority Zero)

If you find the app displaying an incorrect statute, section number, page number, or a directive that misstates the law:

1. Open an issue titled `[FACTUAL] <short description>` — this label pages maintainers.
2. Include: the spoken/typed query, the displayed citation card, and the correct citation with a link or scan of the official source.
3. Factual issues are triaged before all features and all bugs. A confirmed factual error triggers a knowledge-base hotfix release.

---

## 🔀 Development Workflow

### Branching & Commits

- Branch from `main`: `feat/<area>-<summary>`, `fix/<area>-<summary>`, `kb/<statute>-<summary>` (knowledge base changes).
- Conventional Commits (`feat:`, `fix:`, `kb:`, `eval:`, `docs:`). Knowledge-base commits (`kb:`) must reference the source document in the body.
- Keep PRs small. A PR that touches both the LLM layer and the memory layer will be asked to split.

### Code Standards

- Kotlin official style, enforced by `ktlint`; static analysis via `detekt` — both run in CI.
- Jetpack Compose only; no XML layouts, no fragments.
- All UI must remain usable at 200% font scale and pass a 7:1 contrast ratio check (the app targets outdoor, high-stress, possibly one-handed use).
- JNI boundaries (whisper.cpp) must be wrapped in Kotlin classes with explicit lifecycle management — no raw pointers escaping the `stt/` package.
- No blocking calls on the main thread. The Trigger → Active transition budget is **< 400 ms** on a Pixel 6a class device.

### Testing Requirements

| Layer | Required tests |
|---|---|
| `memory/` | Unit tests for metadata extraction — including malformed rows, NULL columns, and below-threshold matches returning the refusal string |
| `llm/` | Contract tests asserting the prompt template has exactly one injection point; output filter tests for citation-pattern leakage |
| `engine/` | State machine tests covering every transition, including mic-permission denial and mid-transcription cancellation |
| `ui/` | Compose screenshot tests for all three states in both languages |

PRs without tests for the affected layer will not be reviewed.

### PR Checklist

Copy this into your PR description and check every box:

```markdown
- [ ] No new injection points into the LLM prompt (Rule 1)
- [ ] All displayed metadata originates from typed DB row fields (Rule 2)
- [ ] The low-confidence refusal path is unchanged or improved-with-evidence (Rule 3)
- [ ] No writes to janadhikar_knowledge.db at runtime (Rule 4)
- [ ] Merged manifest contains no INTERNET permission (Rule 5)
- [ ] Retrieval-affecting changes include eval results (Rule 6)
- [ ] All new strings exist in English AND Hindi (Rule 7)
- [ ] Tests added for the affected layer
```

---

## 🤝 Community Conduct

We follow the [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/). Beyond that, one project-specific norm:

> **Argue about code freely; argue about the law with citations only.** Any claim about what a statute says must link to the official text. "I'm pretty sure the law says…" carries zero weight in this repository — by design, the same standard we hold the app to.

---

## ⚖️ Scope Boundaries

To keep the safety surface auditable, the following are **out of scope** and PRs proposing them will be declined:

- Cloud sync, accounts, or any server component
- Letting the LLM answer general legal questions ("chat with a lawyer" mode)
- Auto-updating the knowledge base over the network
- Recording or storing audio
- Jurisdictions beyond the current verified corpus, unless accompanied by a fully sourced knowledge-pipeline contribution meeting Rule 4

---

*Janadhikar exists because someone, somewhere, will trust it completely at the worst moment of their day. Build accordingly.*
