package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Průvodce TV Samsung přes Apps2Samsung: odkazy, hledání TV a pořadí kroků. */
class SamsungSetupTest {
    @Test fun linksCarryOnlyTheLatestRelease() {
        assertEquals("apps2samsung://source?url=https%3A%2F%2Fgithub.com%2Fadusa89%2FPlayLingo%2Freleases%2Ftag%2Flatest", SamsungSetup.sourceLink())
        assertEquals("https://apps2samsung.com/source?url=https%3A%2F%2Fgithub.com%2Fadusa89%2FPlayLingo%2Freleases%2Ftag%2Flatest", SamsungSetup.sourceWebLink())
        assertTrue(SamsungSetup.WGT_ASSET.endsWith(".wgt"))
    }

    @Test fun samsungAnswerGivesHomeAddress() {
        val m = SamsungSetup.searchMessage()
        assertTrue(m.startsWith("M-SEARCH * HTTP/1.1\r\n") && m.endsWith("\r\n\r\n"))
        val ok = "HTTP/1.1 200 OK\r\nST: urn:samsung.com:device:RemoteControlReceiver:1\r\nSERVER: SHP, UPnP/1.0, Samsung UPnP SDK/1.0\r\n\r\n"
        assertEquals("192.168.1.40", SamsungSetup.parseResponse(ok, "192.168.1.40"))
        assertNull(SamsungSetup.parseResponse(ok, "8.8.8.8"))
        assertNull(SamsungSetup.parseResponse("HTTP/1.1 200 OK\r\nSERVER: WebOS/4.1.0\r\n\r\n", "192.168.1.41"))
        assertNull(SamsungSetup.parseResponse("NOTIFY * HTTP/1.1\r\nSERVER: Samsung\r\n\r\n", "192.168.1.40"))
    }

    private fun p(dev: Boolean = false, inst: Boolean = false, src: Boolean = false, check: TvCheck = TvCheck.WAITING) =
        SamsungSetup.Probe(dev, inst, src, check)

    @Test fun stepsInOrder() {
        assertEquals(SamsungSetup.Step.DEV_MODE, SamsungSetup.step(p()))
        // Apps2Samsung už v telefonu je, ale TV ještě není ve vývojářském režimu → pořád krok 1
        assertEquals(SamsungSetup.Step.DEV_MODE, SamsungSetup.step(p(inst = true)))
        assertEquals(SamsungSetup.Step.INSTALLER, SamsungSetup.step(p(dev = true)))
        assertEquals(SamsungSetup.Step.SOURCE, SamsungSetup.step(p(dev = true, inst = true)))
        assertEquals(SamsungSetup.Step.INSTALL, SamsungSetup.step(p(dev = true, inst = true, src = true)))
        assertTrue(SamsungSetup.done(SamsungSetup.Step.SOURCE, p(dev = true, inst = true, src = true)))
        assertFalse(SamsungSetup.done(SamsungSetup.Step.INSTALL, p(dev = true, inst = true, src = true)))
    }

    @Test fun tvAnswerDecides() {
        // PlayLingo se z TV ohlásilo → hotovo, ať už se kroky odškrtly nebo ne
        assertEquals(SamsungSetup.Step.READY, SamsungSetup.step(p(check = TvCheck.SAME)))
        assertEquals(SamsungSetup.Step.READY, SamsungSetup.step(p(check = TvCheck.NEWER)))
        // starší PlayLingo v TV: jen aktualizovat v Apps2Samsungu
        assertEquals(SamsungSetup.Step.INSTALL, SamsungSetup.step(p(check = TvCheck.OLDER)))
        assertEquals(SamsungSetup.Step.INSTALL, SamsungSetup.step(p(check = TvCheck.NO_VERSION)))
        // ohlásila se LG: u Samsungu se nic neodškrtává
        assertEquals(SamsungSetup.Step.DEV_MODE, SamsungSetup.step(p(check = TvCheck.OTHER)))
    }
}
