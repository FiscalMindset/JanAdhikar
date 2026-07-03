package com.janadhikar.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FollowUpTest {

    @Test
    fun `rephrase requests are follow-ups`() {
        assertThat(FollowUp.isFollowUp("explain this in simple words")).isTrue()
        assertThat(FollowUp.isFollowUp("what does that mean")).isTrue()
        assertThat(FollowUp.isFollowUp("make it simpler")).isTrue()
        assertThat(FollowUp.isFollowUp("give an example")).isTrue()
        assertThat(FollowUp.isFollowUp("saral shabdon mein samjhao")).isTrue()
    }

    @Test
    fun `new questions are not follow-ups`() {
        assertThat(FollowUp.isFollowUp("what is the punishment for theft")).isFalse()
        assertThat(FollowUp.isFollowUp("police slapped me")).isFalse()
        // A concrete reference is a NEW question, even with the word 'explain'.
        assertThat(FollowUp.isFollowUp("explain article 15")).isFalse()
        assertThat(FollowUp.isFollowUp("section 302")).isFalse()
    }
}
