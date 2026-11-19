package cz.playlingo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XboxAuthTest {
    @Test fun deviceCodeParsed() {
        val c = XboxAuth.parseDeviceCode("""{"user_code":"ABCD1234","device_code":"DAQABAAEAAA","verification_uri":"https://www.microsoft.com/link","expires_in":900,"interval":5,"message":"…"}""", 1000)!!
        assertEquals("ABCD1234", c.userCode)
        assertEquals("DAQABAAEAAA", c.deviceCode)
        assertEquals(5, c.intervalS)
        assertEquals(1000 + 900_000L, c.expiresAt)
        assertEquals("microsoft.com/link", XboxAuth.shortLink(c.verificationUri))
    }

    @Test fun deviceCodeSafeDefaults() {
        // neobvyklé hodnoty se omezí, cizí adresa se nepoužije
        val c = XboxAuth.parseDeviceCode("""{"user_code":"X","device_code":"Y","verification_uri":"http://evil","interval":999,"expires_in":"10"}""", 0)!!
        assertEquals(XboxAuth.DEFAULT_LINK, c.verificationUri)
        assertEquals(15, c.intervalS)
        assertEquals(60_000L, c.expiresAt)
        assertNull(XboxAuth.parseDeviceCode("""{"device_code":"Y"}""", 0))
        assertNull(XboxAuth.parseDeviceCode("není json", 0))
    }

    @Test fun pollStates() {
        assertEquals(XboxAuth.Poll.PENDING, XboxAuth.pollState("""{"error":"authorization_pending"}"""))
        assertEquals(XboxAuth.Poll.PENDING, XboxAuth.pollState(""))
        assertEquals(XboxAuth.Poll.SLOW_DOWN, XboxAuth.pollState("""{"error":"slow_down"}"""))
        assertEquals(XboxAuth.Poll.EXPIRED, XboxAuth.pollState("""{"error":"expired_token"}"""))
        assertEquals(XboxAuth.Poll.DENIED, XboxAuth.pollState("""{"error":"authorization_declined"}"""))
        assertEquals(XboxAuth.Poll.FAILED, XboxAuth.pollState("""{"error":"bad_verification_code"}"""))
    }

    @Test fun tokensAndExpiry() {
        val t = XboxAuth.parseTokens("""{"access_token":"EwA","refresh_token":"M.C1_x","expires_in":3600}""", 0)!!
        assertEquals("EwA", t.access)
        assertEquals("M.C1_x", t.refresh)
        assertEquals(3_600_000L, t.expiresAt)
        assertTrue(XboxAuth.valid(t.expiresAt, 0))
        // rezerva: dvě minuty před koncem už se obnovuje
        assertTrue(XboxAuth.valid(t.expiresAt, t.expiresAt - XboxAuth.EXPIRY_MARGIN_MS - 1))
        assertFalse(XboxAuth.valid(t.expiresAt, t.expiresAt - XboxAuth.EXPIRY_MARGIN_MS))
        assertFalse(XboxAuth.valid(0, 0))
        assertNull(XboxAuth.parseTokens("""{"error":"x"}""", 0))
        assertNull(XboxAuth.parseTokens("""{"access_token":"a"}""", 0)!!.refresh)
    }

    @Test fun refreshNeverSignsOutOnNetworkTrouble() {
        assertEquals(XboxAuth.Refresh.OK, XboxAuth.refreshOutcome(200, """{"access_token":"a"}"""))
        assertEquals(XboxAuth.Refresh.SIGNED_OUT, XboxAuth.refreshOutcome(400, """{"error":"invalid_grant"}"""))
        assertEquals(XboxAuth.Refresh.RETRY, XboxAuth.refreshOutcome(400, """{"error":"temporarily_unavailable"}"""))
        assertEquals(XboxAuth.Refresh.RETRY, XboxAuth.refreshOutcome(0, ""))
        assertEquals(XboxAuth.Refresh.RETRY, XboxAuth.refreshOutcome(503, "<html>"))
        assertEquals(XboxAuth.Refresh.RETRY, XboxAuth.refreshOutcome(200, "{}"))
    }

    @Test fun xboxLiveTokens() {
        assertEquals("u1", XboxAuth.parseUserToken("""{"Token":"u1","DisplayClaims":{}}"""))
        val x = XboxAuth.parseXsts("""{"Token":"x1","DisplayClaims":{"xui":[{"uhs":"123","gtg":"Hrac 42"}]}}""")!!
        assertEquals("x1", x.token)
        assertEquals("Hrac 42", x.gamertag)
        assertNull(XboxAuth.parseXsts("""{"Token":"x1"}""")!!.gamertag)
        assertEquals(XboxFail.PROFILE, XboxAuth.xstsError(401, """{"XErr":2148916233}"""))
        assertEquals(XboxFail.PROFILE, XboxAuth.xstsError(401, """{"XErr":2148916238}"""))
        assertEquals(XboxFail.AUTH, XboxAuth.xstsError(401, ""))
        assertEquals(XboxFail.NETWORK, XboxAuth.xstsError(500, ""))
    }

    @Test fun streamTokenPicksDefaultRegion() {
        val s = XboxAuth.parseStream("""{"offeringSettings":{"regions":[
            {"name":"UkSouth","baseUri":"https://uks.core.gssv-play-prodxhome.xboxlive.com","isDefault":false},
            {"name":"WestEurope","baseUri":"https://weu.core.gssv-play-prodxhome.xboxlive.com/","isDefault":true}]},
            "gsToken":"gs1","durationInSeconds":14400}""", 0)!!
        assertEquals("gs1", s.gsToken)
        assertEquals("https://weu.core.gssv-play-prodxhome.xboxlive.com", s.baseUri)
        assertEquals(14_400_000L, s.expiresAt)
        // bez výchozího regionu první, bez regionu nic
        assertEquals("https://a", XboxAuth.parseStream("""{"gsToken":"g","offeringSettings":{"regions":[{"baseUri":"https://a"}]}}""", 0)!!.baseUri)
        assertNull(XboxAuth.parseStream("""{"gsToken":"g","offeringSettings":{"regions":[]}}""", 0))
        assertNull(XboxAuth.parseStream("""{"gsToken":"g","offeringSettings":{"regions":[{"baseUri":"http://a"}]}}""", 0))
    }

    @Test fun consoles() {
        val l = XboxAuth.parseConsoles("""{"totalItems":2,"results":[
            {"deviceName":"Obývák","serverId":"F4001234ABCD","powerState":"On","consoleType":"XboxSeriesX"},
            {"serverId":"F4005678","powerState":"ConnectedStandby"},{"deviceName":"bez id"}]}""")!!
        assertEquals(2, l.size)
        assertEquals("Obývák", l[0].name)
        assertTrue(l[0].isOn)
        assertEquals("Xbox", l[1].name)
        assertFalse(l[1].isOn)
        assertEquals(emptyList<XboxAuth.XboxConsole>(), XboxAuth.parseConsoles("{}"))
        assertNull(XboxAuth.parseConsoles("chyba"))
    }

    @Test fun consoleChoiceDefaultsToPlayStation() {
        assertEquals(Console.PLAYSTATION, Console.of(null))
        assertEquals(Console.PLAYSTATION, Console.of("cokoli"))
        assertEquals(Console.XBOX, Console.of("xbox"))
        assertEquals(Console.XBOX, Console.of(Console.XBOX.code))
    }
}

class XboxSessionTest {
    @Test fun sessionStates() {
        assertEquals(XboxSession.Step.READY, XboxSession.state("""{"state":"Provisioned"}""").step)
        assertEquals(XboxSession.Step.CONNECT, XboxSession.state("""{"state":"ReadyToConnect"}""").step)
        assertEquals(XboxSession.Step.WAIT, XboxSession.state("""{"state":"Provisioning"}""").step)
        assertEquals(XboxSession.Step.WAIT, XboxSession.state("nesmysl").step)
        val f = XboxSession.state("""{"state":"Failed","errorDetails":{"code":"x","message":"WaitingForServerToRegister"}}""")
        assertEquals(XboxSession.Step.FAILED, f.step)
        assertEquals(XboxFail.NOT_READY, XboxSession.failOf(f.error))
        assertEquals(XboxFail.REFUSED, XboxSession.failOf("něco jiného"))
        assertEquals(XboxFail.REFUSED, XboxSession.failOf(null))
    }

    @Test fun exchange() {
        assertNull(XboxSession.exchangeResponse("""{"exchangeResponse":""}"""))
        assertNull(XboxSession.exchangeResponse(""))
        val sdp = """{"exchangeResponse":"{\"sdp\":\"v=0\\r\\n\",\"status\":\"success\"}"}"""
        val ex = XboxSession.exchangeResponse(sdp)!!
        val a = XboxSession.sdpAnswer(ex)
        assertTrue(a.ok)
        assertEquals("v=0\r\n", a.sdp)
        assertFalse(XboxSession.sdpAnswer("""{"status":"failed","sdp":"v=0"}""").ok)
        assertFalse(XboxSession.sdpAnswer("""{"status":"success"}""").ok)
        assertEquals("busy", XboxSession.exchangeError("""{"errorDetails":{"message":"busy"}}"""))
        assertEquals(30, XboxSession.keepAliveSeconds("""{"keepAlivePulseInSeconds":30}"""))
        assertEquals(10, XboxSession.keepAliveSeconds("""{"keepAlivePulseInSeconds":1}"""))
        assertEquals(20, XboxSession.keepAliveSeconds("{}"))
    }

    @Test fun teredoUnpacked() {
        // RFC 4380, příklad: server 65.54.227.120, klient 192.0.2.45 port 40000
        val c = "a=candidate:1 1 UDP 100 2001:0000:4136:e378:8000:63bf:3fff:fdd2 9002 typ host "
        val t = XboxSession.teredo(c)
        assertEquals(3, t.size)
        assertEquals("a=candidate:10 1 UDP 100 192.0.2.45 40000 typ host ", t[0])
        assertEquals("a=candidate:11 1 UDP 100 192.0.2.45 9002 typ host ", t[1])
        assertEquals("a=candidate:12 1 UDP 100 65.54.227.120 9002 typ host ", t[2])
        // zkrácený zápis
        assertEquals(3, XboxSession.teredo("a=candidate:1 1 UDP 100 2001:0:4136:e378:8000:63bf:3fff:fdd2 9002 typ host").size)
        // jiné adresy se nemění
        assertTrue(XboxSession.teredo("a=candidate:1 1 UDP 100 192.168.1.5 9002 typ host").isEmpty())
        assertTrue(XboxSession.teredo("a=candidate:1 1 UDP 100 fe80::1 9002 typ host").isEmpty())
        assertTrue(XboxSession.teredo("a=candidate:1 1 UDP").isEmpty())
    }

    @Test fun ipv6Parsing() {
        assertEquals(listOf(0x20, 0x01) + List(13) { 0 } + listOf(1), XboxSession.ipv6("2001::1")!!.toList())
        assertEquals(List(16) { 0 }, XboxSession.ipv6("::")!!.toList())
        assertNull(XboxSession.ipv6("192.168.1.1"))
        assertNull(XboxSession.ipv6("1::2::3"))
        assertNull(XboxSession.ipv6("1:2:3:4:5:6:7"))
        assertNull(XboxSession.ipv6("12345::1"))
        assertNull(XboxSession.ipv6("fe80::1%wlan0"))
    }

    @Test fun remoteCandidatesIncludeTeredo() {
        val ex = """[{"candidate":"a=candidate:1 1 UDP 100 2001:0:4136:e378:8000:63bf:3fff:fdd2 9002 typ host ","sdpMid":"0","sdpMLineIndex":"0"},
            {"candidate":"a=candidate:2 1 UDP 1 192.168.1.9 9002 typ host","sdpMid":"0","sdpMLineIndex":0},
            {"candidate":"a=end-of-candidates","sdpMid":"0","sdpMLineIndex":"0"}]"""
        val c = XboxSession.remoteCandidates(ex)
        assertEquals(5, c.size)
        assertTrue(c.all { it.mid == "0" && it.index == 0 })
        assertEquals("a=candidate:2 1 UDP 1 192.168.1.9 9002 typ host", c.last().text)
        assertTrue(XboxSession.remoteCandidates("nesmysl").isEmpty())
    }

    private val offer = listOf(
        "v=0", "o=- 1 2 IN IP4 127.0.0.1", "s=-", "t=0 0",
        "m=audio 9 UDP/TLS/RTP/SAVPF 111", "c=IN IP4 0.0.0.0", "a=rtpmap:111 opus/48000/2",
        "m=video 9 UDP/TLS/RTP/SAVPF 96 97 102 103 104 105", "c=IN IP4 0.0.0.0",
        "a=rtpmap:96 VP8/90000", "a=rtpmap:97 rtx/90000", "a=fmtp:97 apt=96",
        "a=rtpmap:102 H264/90000", "a=fmtp:102 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42001f",
        "a=rtpmap:103 H264/90000", "a=fmtp:103 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f",
        "a=rtpmap:104 H264/90000", "a=fmtp:104 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=4d001f",
        "a=rtpmap:105 VP9/90000", "",
    ).joinToString("\r\n")

    @Test fun h264FirstAndLowBitrate() {
        val out = XboxSession.preferH264(offer, 3000)
        val lines = out.split("\r\n")
        assertEquals("m=video 9 UDP/TLS/RTP/SAVPF 104 103 102 96 97 105", lines.first { it.startsWith("m=video") })
        // b=AS hned za c= v části s obrazem, zvuk beze změny
        val m = lines.indexOfFirst { it.startsWith("m=video") }
        assertEquals("b=AS:3000", lines[m + 2])
        assertEquals(1, lines.count { it.startsWith("b=AS") })
        assertTrue(out.endsWith("\r\n"))
        assertTrue(lines.contains("m=audio 9 UDP/TLS/RTP/SAVPF 111"))
        // podruhé se řádek b= přepíše, nepřidá
        assertEquals(1, XboxSession.preferH264(out, 2000).split("\r\n").count { it.startsWith("b=AS") })
        assertTrue(XboxSession.preferH264(out, 2000).contains("b=AS:2000"))
        // bez obrazu beze změny
        assertEquals("v=0\r\nm=audio 9 X 111\r\n", XboxSession.preferH264("v=0\r\nm=audio 9 X 111\r\n"))
    }
}

class XboxRedactTest {
    @Test fun xboxTokensNeverInReport() {
        val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJ4dWkiOlt7InVocyI6IjEyMyJ9XX0.c2lnbmF0dXJl"
        val access = "EwBwA+l3BAAUWm1xSeJRIJK6txKjBez4GzapzqMAAXjQ8vq2ZQ5e5W3P+Qz/abc="
        val refresh = "M.C534_BAY.0.U.-CgABCDEF*12!34\$xyz0987654321"
        val text = """gsToken: gsXYZ1234 · "userToken":"tr4nsf3r" · RpsTicket=d=$access · $jwt · $refresh · device_code=DAQABAAEAAAD · Xbox připraven"""
        val out = Redact.clean(text)
        for (s in listOf("gsXYZ1234", "tr4nsf3r", access, jwt, refresh, "DAQABAAEAAAD", "eyJ", "EwBwA", "M.C534")) assertFalse("$s v: $out", out.contains(s))
        assertTrue(out.contains("Xbox připraven"))
    }

    @Test fun storedRefreshTokenMaskedAsSecret() {
        val rt = "M.C1-short*tok"
        assertFalse(Redact.clean("x $rt y", listOf(rt)).contains(rt))
    }

    @Test fun consoleTextKeys() {
        assertEquals("st_failed", Console.PLAYSTATION.key("st_failed"))
        assertEquals("xb_st_failed", Console.XBOX.key("st_failed"))
        assertEquals("xb_m_ps5", Console.XBOX.key("m_ps5"))
        // text bez varianty pro Xbox zůstává společný
        assertEquals("b_start", Console.XBOX.key("b_start"))
        assertTrue(I18n.t(Lang.CS, Console.XBOX.key("chk_ps5_missing")).contains("Xbox"))
    }
}

class XboxLinkStatusTest {
    @Test fun statusNamesTheChosenConsole() {
        val s = LinkStatus.evaluate(1000, true, false, false, Ps5.Link.CONNECTING, 0, 0)
        assertTrue(LinkStatus.line(Lang.CS, s).contains("PlayStation"))
        assertTrue(LinkStatus.line(Lang.CS, s, Console.XBOX).contains("Xbox"))
        val img = LinkStatus.evaluate(20_000, true, false, false, Ps5.Link.CONNECTED, 1_000, 19_000)
        assertTrue(LinkStatus.line(Lang.EN, img, Console.XBOX).contains("Xbox"))
        assertTrue(LinkStatus.details(Lang.DE, img, Console.XBOX)[1].contains("Xbox"))
        // stavy bez jména konzole zůstávají stejné
        val off = LinkStatus.evaluate(0, false, false, false, Ps5.Link.OFF, 0, 0)
        assertEquals(LinkStatus.line(Lang.CS, off), LinkStatus.line(Lang.CS, off, Console.XBOX))
    }
}

class XboxRetryTest {
    @Test fun signInProblemsStopImmediately() {
        for (f in listOf(XboxFail.AUTH, XboxFail.PROFILE, XboxFail.NOT_FOUND)) {
            val d = XboxRetry().failed(f, 0)
            assertEquals(Ps5.Link.FAILED, d.state)
            assertNull(d.retryInMs)
        }
    }

    @Test fun sleepingXboxIsOnlyCheckedNeverCounted() {
        val r = XboxRetry()
        repeat(20) {
            val d = r.failed(XboxFail.OFFLINE, 0)
            assertEquals(Ps5.Link.WAITING, d.state)
            assertEquals(XboxRetry.OFFLINE_CHECK_MS, d.retryInMs)
        }
        assertEquals(0, r.failures)
    }

    @Test fun repeatedFailuresBackOffThenGiveUp() {
        val r = XboxRetry()
        val delays = (1 until XboxRetry.GIVE_UP_AFTER).map { r.failed(XboxFail.ICE, 0).retryInMs!! }
        assertEquals(delays.sorted(), delays)
        assertTrue(delays.first() >= 5_000)
        val last = r.failed(XboxFail.ICE, 0)
        assertEquals(Ps5.Link.FAILED, last.state)
        assertNull(last.retryInMs)
    }

    @Test fun stableSessionResetsCounterAndDropWaits() {
        val r = XboxRetry()
        repeat(3) { r.failed(XboxFail.TIMEOUT, 0) }
        r.connected(1_000)
        val d = r.failed(XboxFail.KICKED, 1_000 + XboxRetry.STABLE_MS)
        assertEquals(1, r.failures)
        assertEquals(Ps5.Link.WAITING, d.state)
        // po rozpadlém spojení se nezkouší hned
        assertTrue(d.retryInMs!! >= 15_000)
    }

    @Test fun shortSessionsStillCount() {
        val r = XboxRetry()
        repeat(XboxRetry.GIVE_UP_AFTER - 1) { r.connected(0); r.failed(XboxFail.NO_VIDEO, 1_000) }
        r.connected(0)
        assertEquals(Ps5.Link.FAILED, r.failed(XboxFail.KICKED, 1_000).state)
    }

    @Test fun everyFailureHasText() {
        for (f in XboxFail.values()) for (l in Lang.values()) {
            val t = I18n.t(l, f.textKey)
            assertNotNull(t)
            assertFalse("$l ${f.textKey}", t == f.textKey)
        }
    }
}
