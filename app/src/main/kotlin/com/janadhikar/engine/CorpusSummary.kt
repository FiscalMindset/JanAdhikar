package com.janadhikar.engine

import com.janadhikar.memory.ProvisionCount

/**
 * Turns live database counts into a self-describing answer for meta-questions
 * ("how many articles are there", "what laws do you cover"). Nothing hardcoded —
 * it reflects exactly what is in the shipped knowledge base.
 */
object CorpusSummary {

    fun build(counts: List<ProvisionCount>): String {
        if (counts.isEmpty()) return "The knowledge base is empty."
        // Merge units per statute (e.g. Constitution has ARTICLEs, acts have SECTIONs).
        val byStatute = counts.groupBy { it.statuteName }
        val lines = byStatute.entries.joinToString("\n") { (statute, rows) ->
            val parts = rows.sortedByDescending { it.count }.joinToString(", ") { r ->
                val unit = when {
                    r.unit.equals("ARTICLE", true) -> if (r.count == 1) "article" else "articles"
                    else -> if (r.count == 1) "section" else "sections"
                }
                "${r.count} $unit"
            }
            "- **${statute.substringBefore(",")}** — $parts"
        }
        val totalProvisions = counts.sumOf { it.count }
        val totalArticles = counts.filter { it.unit.equals("ARTICLE", true) }.sumOf { it.count }
        val totalSections = counts.filter { !it.unit.equals("ARTICLE", true) }.sumOf { it.count }

        return buildString {
            append("I cover **${byStatute.size} Indian laws** with **$totalProvisions provisions** in total")
            if (totalArticles > 0 && totalSections > 0) {
                append(" (**$totalArticles** Constitution articles and **$totalSections** sections across the other Acts)")
            }
            append(":\n\n")
            append(lines)
            append("\n\nAsk me about any of them — e.g. \"what is article 21\", \"punishment for theft\", or \"my rights when arrested\".")
        }
    }
}
