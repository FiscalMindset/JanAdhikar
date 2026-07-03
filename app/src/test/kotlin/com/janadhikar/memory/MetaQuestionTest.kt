package com.janadhikar.memory

import com.google.common.truth.Truth.assertThat
import com.janadhikar.engine.CorpusSummary
import org.junit.Test

class MetaQuestionTest {

    @Test
    fun `detects meta-questions about the corpus`() {
        assertThat(MetaQuestion.isMeta("how many articles are there")).isTrue()
        assertThat(MetaQuestion.isMeta("how many laws do you cover")).isTrue()
        assertThat(MetaQuestion.isMeta("what laws do you know")).isTrue()
        assertThat(MetaQuestion.isMeta("kitne article hai")).isTrue()
    }

    @Test
    fun `normal legal questions are not meta`() {
        assertThat(MetaQuestion.isMeta("what is article 15")).isFalse()
        assertThat(MetaQuestion.isMeta("punishment for theft")).isFalse()
    }

    @Test
    fun `summary reports live counts, nothing hardcoded`() {
        val text = CorpusSummary.build(
            listOf(
                ProvisionCount("Constitution of India", "ARTICLE", 395),
                ProvisionCount("Bharatiya Nyaya Sanhita, 2023", "SECTION", 358),
            ),
        )
        assertThat(text).contains("2 Indian laws")
        assertThat(text).contains("395 articles")
        assertThat(text).contains("358 sections")
        assertThat(text).contains("Constitution of India")
    }
}
