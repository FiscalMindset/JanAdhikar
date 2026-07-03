package com.janadhikar.memory

/**
 * Questions ABOUT the knowledge base itself — "how many articles are there",
 * "what laws do you cover", "which acts do you know". These aren't legal
 * provisions to retrieve; they're answered from live database counts (a smart,
 * self-describing answer — nothing hardcoded).
 */
object MetaQuestion {

    private val META = Regex(
        """\b(how many|how much|number of|count of|kitne|kitni|kitna)\b.*\b(article|section|law|act|dhara|anuchhed|kanoon)""" +
            """|\b(what|which|kaun|kaun se|konse)\b.*\b(law|act|laws|acts|statute|kanoon)\b.*\b(cover|know|have|support|hai|ho)""" +
            """|\bwhat can you (answer|do|help)\b|\bwhat do you (know|cover|contain)\b""" +
            """|\bhow many (laws|acts|articles|sections)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isMeta(query: String): Boolean = META.containsMatchIn(query)
}
