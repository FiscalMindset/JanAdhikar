package com.janadhikar.memory

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DirectReferenceTest {

    @Test
    fun `article query resolves to Constitution`() {
        val ref = DirectReference.parse("what is article 15")!!
        assertThat(ref.unit).isEqualTo("ARTICLE")
        assertThat(ref.number).isEqualTo("15")
        assertThat(ref.statuteHint).isEqualTo("Constitution")
    }

    @Test
    fun `article with letter suffix`() {
        assertThat(DirectReference.parse("explain article 21A")!!.number).isEqualTo("21A")
    }

    @Test
    fun `section query parses number and act hint`() {
        val ref = DirectReference.parse("bns section 302")!!
        assertThat(ref.unit).isEqualTo("SECTION")
        assertThat(ref.number).isEqualTo("302")
        assertThat(ref.statuteHint).isEqualTo("Nyaya")
    }

    @Test
    fun `hinglish anuchhed resolves to article`() {
        val ref = DirectReference.parse("anuchhed 21 kya hai")!!
        assertThat(ref.unit).isEqualTo("ARTICLE")
        assertThat(ref.number).isEqualTo("21")
        assertThat(ref.statuteHint).isEqualTo("Constitution")
    }

    @Test
    fun `devanagari anuchhed resolves to article`() {
        assertThat(DirectReference.parse("अनुच्छेद 19 batao")!!.number).isEqualTo("19")
    }

    @Test
    fun `hinglish dhara resolves to section`() {
        val ref = DirectReference.parse("dhara 302 kya hai")!!
        assertThat(ref.unit).isEqualTo("SECTION")
        assertThat(ref.number).isEqualTo("302")
    }

    @Test
    fun `plain wording is not a direct reference`() {
        assertThat(DirectReference.parse("what is the punishment for theft")).isNull()
        assertThat(DirectReference.parse("police slapped me")).isNull()
        assertThat(DirectReference.parse("what is this about")).isNull()
    }
}
