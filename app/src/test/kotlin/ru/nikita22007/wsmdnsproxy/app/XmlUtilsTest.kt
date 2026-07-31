package ru.nikita22007.wsmdnsproxy.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class XmlUtilsTest {
    @Test
    fun `escapes XML markup in untrusted text`() {
        assertEquals("NAS &amp; &lt;script&gt; &quot;quoted&quot;", "NAS & <script> \"quoted\"".xmlText())
    }
}
