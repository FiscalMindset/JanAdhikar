package com.janadhikar.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GibberishSanitizerTest {
    @Test fun `repeated token gibberish is unusable`() {
        val v = OutputSanitizer.inspect("6-7 6-7 6-7 6-7 6-7 6-7 6-7 6-7")
        assertThat(v).isInstanceOf(OutputSanitizer.Verdict.Unusable::class.java)
    }
    @Test fun `leaked instruction is unusable`() {
        val v = OutputSanitizer.inspect("फिर 5 से 4 छोटे वाक्य लिखें और बताएँ")
        assertThat(v).isInstanceOf(OutputSanitizer.Verdict.Unusable::class.java)
    }
    @Test fun `a clean hindi explanation passes`() {
        val v = OutputSanitizer.inspect("यह कानून हर व्यक्ति को समानता का अधिकार देता है। राज्य किसी के साथ भेदभाव नहीं कर सकता।")
        assertThat(v).isInstanceOf(OutputSanitizer.Verdict.Clean::class.java)
    }
    @Test fun `a clean english explanation passes`() {
        val v = OutputSanitizer.inspect("You cannot be discriminated against based on your religion, caste or sex.")
        assertThat(v).isInstanceOf(OutputSanitizer.Verdict.Clean::class.java)
    }
}
