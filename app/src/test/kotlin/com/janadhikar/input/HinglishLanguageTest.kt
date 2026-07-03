package com.janadhikar.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HinglishLanguageTest {
    @Test fun `hinglish resolves to hindi`() {
        assertThat(QueryNormalizer.detectLanguage("article 15 kya hain")).isEqualTo(AppLanguage.HINDI)
        assertThat(QueryNormalizer.detectLanguage("mera adhikar kya hai")).isEqualTo(AppLanguage.HINDI)
        assertThat(QueryNormalizer.detectLanguage("police ne mujhe girftar kiya")).isEqualTo(AppLanguage.HINDI)
    }
    @Test fun `plain english stays english`() {
        assertThat(QueryNormalizer.detectLanguage("what is article 15")).isEqualTo(AppLanguage.ENGLISH)
        assertThat(QueryNormalizer.detectLanguage("punishment for theft")).isEqualTo(AppLanguage.ENGLISH)
    }
    @Test fun `devanagari stays hindi`() {
        assertThat(QueryNormalizer.detectLanguage("मेरे अधिकार क्या हैं")).isEqualTo(AppLanguage.HINDI)
    }
}
