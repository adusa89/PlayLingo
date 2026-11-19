package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ps5CodeScanTest {
    @Test fun cisloSMezerou() {
        assertEquals("12345678", Ps5CodeScan.parse(listOf("Link Device", "Enter this number on the device", "1234 5678", "Time remaining: 4:59")))
    }

    @Test fun cisloVcelkuANaDvouRadcich() {
        assertEquals("87654321", Ps5CodeScan.parse(listOf("Propojit zařízení", "87654321")))
        assertEquals("11223344", Ps5CodeScan.parse(listOf("1122", "3344", "Zbývající čas 4:12")))
    }

    @Test fun nejisteNeboNic() {
        assertNull(Ps5CodeScan.parse(listOf("Remote Play", "192.168.3.72")))
        assertNull(Ps5CodeScan.parse(listOf("1234 5678", "8765 4321")))
        assertNull(Ps5CodeScan.parse(listOf("123456789")))
    }

    @Test fun hlasovani() {
        val v = Ps5CodeVote()
        assertNull(v.add("12345678"))
        assertNull(v.add(null))
        assertEquals("12345678", v.add("12345678"))
        v.clear()
        assertNull(v.current())
    }
}
