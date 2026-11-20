package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvStyleTest {
    @Test fun vzhledProTelevizi() {
        val m = TvSender.styleMessage(70, "above", 0.74f, 0.98f, -1f, 0.84f, 1.5f, "#FFE066", true)
        assertEquals("{\"typ\":\"vzhled\",\"pruhlednost\":70,\"poloha\":\"above\",\"oblast\":[0.740,0.980],\"stred\":0.000," +
            "\"sirka\":0.840,\"meritko\":1.500,\"barva\":\"#FFE066\",\"tucne\":true}", m)
    }

    @Test fun nesmyslneHodnotySeOrezou() {
        val m = TvSender.styleMessage(250, "<script>", 2f, -1f, 5f, 0.1f, 9f, "red;x", false)
        assertTrue(m, m.contains("\"pruhlednost\":100"))
        assertTrue(m, m.contains("\"poloha\":\"over\""))
        assertTrue(m, m.contains("\"barva\":\"#FFFFFF\""))
        assertTrue(m, m.contains("\"sirka\":0.300"))
        assertTrue(m, m.contains("\"meritko\":3.000"))
    }
}
