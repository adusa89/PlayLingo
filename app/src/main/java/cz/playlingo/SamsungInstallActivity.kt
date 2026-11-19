package cz.playlingo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import cz.playlingo.core.SamsungBundle
import cz.playlingo.core.SamsungInstall
import cz.playlingo.core.TvAnnounce
import cz.playlingo.core.TvCheck
import cz.playlingo.core.TvSender

/**
 * Průvodce: PlayLingo do televize Samsung (Tizen). Otevírá se ze stránky Domů (typ televize
 * Samsung), z Nápovědy a z pomocníka v telefonu. Ovládá se ovladačem (šipky + OK) i dotykem.
 *
 * Přímo z Androidu se do Samsungu nahrávat nedá spolehlivě: televize přijme jen balíček podepsaný
 * certifikátem, který Samsung vystaví na účet uživatele pro identifikátor (DUID) jeho televize
 * (viz tizen/CTI-ME.md). Průvodce proto:
 *  1. vysvětlí vývojářský režim (Apps → 12345 → Developer mode, Host PC IP),
 *  2. uloží přibalený nepodepsaný balíček .wgt přes systémový dialog (bez oprávnění k úložišti),
 *  3. pojmenuje nástroje, které ho podepíšou a nahrají (Tizen Studio, komunitní instalátory),
 *  4. za úspěch považuje jen to, že se aplikace z televize opravdu připojí a ohlásí verzi
 *     ([TvSender.lastSeen], [TvCheck]). Pak pošle do televize zkušební větu.
 */
class SamsungInstallActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var content: LinearLayout
    private var bundle: SamsungBundle? = null
    private var saveMsg: String? = null
    private var saveOk = false
    /** Vlastní příjem televize, když neběží překlad (ten má svůj). */
    private var sender: TvSender? = null
    private var portBusy = false
    private var check = TvCheck.WAITING
    private var testSent = false
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            ensureListening()
            val now = currentCheck()
            if (now != check) {
                check = now
                if (check.ok) sendTestLine()
                render(true)
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun t(key: String) = tx(key)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        bundle = try {
            val manifest = assets.open("samsung/receiver-manifest.json").use { String(it.readBytes(), Charsets.UTF_8) }
            val data = assets.open("samsung/receiver.wgt").use { it.readBytes() }
            SamsungInstall.bundle(manifest, data)
        } catch (_: Exception) { null }
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Theme.BG) }
        content = ui.vbox().apply {
            val side = if (resources.configuration.screenWidthDp >= 900) ui.dp(80) else ui.dp(20)
            setPadding(side, ui.dp(28), side, ui.dp(36))
        }
        scroll.addView(content)
        setContentView(scroll)
        check = currentCheck()
        render()
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    override fun onDestroy() {
        sender?.stop(); sender = null
        super.onDestroy()
    }

    private fun currentCheck(): TvCheck {
        val b = bundle ?: return TvCheck.WAITING
        return TvCheck.of(TvSender.lastSeen?.announce, TvAnnounce.TIZEN, b.version)
    }

    /** Aby se televize měla kam připojit: když neběží překlad s posíláním, čeká na ni průvodce sám. */
    private fun ensureListening() {
        if (sender != null || TranslatorService.tvConnected >= 0) { portBusy = false; return }
        val s = TvSender { }
        if (s.start()) { sender = s; portBusy = false } else if (!portBusy) { portBusy = true; render(true) }
    }

    /** Zkušební věta do televize – jen přes vlastní příjem (překlad posílá své titulky sám). */
    private fun sendTestLine() {
        if (testSent) return
        sender?.let { it.show(t("ss_test_bar")); testSent = true }
    }

    private fun add(v: View, top: Int = 12) = content.addView(v, ui.lp(top = top))

    private fun <V : View> tagged(v: V, tag: String): V { v.tag = tag; return v }

    /** Návod Samsungu k instalaci Tizen Studia (TV SDK). */
    private val TIZEN_URL = "https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/installing-tv-sdk.html"

    private fun qr(text: String): android.graphics.Bitmap? = try {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
        val n = m.width
        val scale = maxOf(1, 480 / n)
        val px = IntArray(n * scale * n * scale)
        for (y in 0 until n * scale) for (x in 0 until n * scale)
            px[y * n * scale + x] = if (m.get(x / scale, y / scale)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        android.graphics.Bitmap.createBitmap(px, n * scale, n * scale, android.graphics.Bitmap.Config.ARGB_8888)
    } catch (e: Throwable) { null }

    private fun step(title: String, text: String) = ui.card().apply {
        addView(ui.text(title, 18f, Theme.ACCENT, bold = true))
        addView(ui.text(text, 16f, Theme.TEXT), ui.lp(top = 8))
    }

    private fun render(keepFocus: Boolean = false) {
        val focused = if (keepFocus) currentFocus?.tag else null
        content.removeAllViews()
        add(ui.text(t("ss_title"), 26f, Theme.TEXT, bold = true), 0)
        add(ui.text(t("ss_intro"), 16f, Theme.MUTED), 8)

        // výsledek nahoře: tohle uživatel hledá, když se vrací z instalace
        add(ui.card().apply {
            addView(ui.text(t("ss_s4_h"), 18f, Theme.ACCENT, bold = true))
            addView(ui.text(t("ss_s4"), 16f, Theme.TEXT), ui.lp(top = 8))
            val seen = TvSender.lastSeen?.announce
            val (line, color) = when (check) {
                TvCheck.WAITING -> t("ss_wait") to Theme.MUTED
                TvCheck.OTHER -> t("ss_other") to Theme.WARN
                TvCheck.NO_VERSION -> t("ss_noversion") to Theme.WARN
                TvCheck.OLDER -> t("ss_older").replace("%s", seen?.version ?: "?") to Theme.WARN
                TvCheck.SAME -> t("ss_ok").replace("%s", seen?.version ?: "?") to Theme.OK
                TvCheck.NEWER -> t("ss_ok_newer").replace("%s", seen?.version ?: "?") to Theme.OK
            }
            addView(ui.text(line, 17f, color, bold = check != TvCheck.WAITING).apply { tag = "sscheck" }, ui.lp(top = 10))
            seen?.model?.let { addView(ui.text("${it}" + (seen.system?.let { s -> " · $s" } ?: ""), 14f, Theme.MUTED), ui.lp(top = 4)) }
            if (portBusy) addView(ui.text(t("ss_port"), 15f, Theme.WARN), ui.lp(top = 8))
        }, 18)

        val ip = NetInfo.homeAddress(this)
        add(ui.card().apply {
            addView(ui.text(t("ss_s1_h"), 18f, Theme.ACCENT, bold = true))
            addView(ui.text(t("ss_s1"), 16f, Theme.TEXT), ui.lp(top = 8))
            addView(ui.text(if (ip != null) t("ss_ip").replace("%s", ip) else t("ss_ip_none"), 17f, if (ip != null) Theme.ACCENT else Theme.WARN, bold = true), ui.lp(top = 10))
        }, 14)

        val b = bundle
        add(ui.card().apply {
            addView(ui.text(t("ss_s2_h"), 18f, Theme.ACCENT, bold = true))
            if (b == null) {
                addView(ui.text(t("ss_pkg_bad"), 16f, Theme.BAD, bold = true), ui.lp(top = 8))
            } else {
                addView(ui.text(t("ss_s2").replace("%s", b.fileName).replace("%v", b.version), 16f, Theme.TEXT), ui.lp(top = 8))
                addView(tagged(ui.button(t("ss_save"), primary = !check.ok) { save() }, "sssave"), ui.lp(top = 12))
                saveMsg?.let { addView(ui.text(it, 15f, if (saveOk) Theme.OK else Theme.BAD, bold = true), ui.lp(top = 8)) }
            }
        }, 14)

        add(step(t("ss_s3_h"), t("ss_s3")).apply {
            // odkaz na návod a stažení Tizen Studia od Samsungu; na televizi čtvereček pro telefon
            addView(ui.text(t("ss_link"), 15f, Theme.MUTED), ui.lp(top = 12))
            addView(ui.text(TIZEN_URL, 14f, Theme.ACCENT), ui.lp(top = 4))
            if (Prefs.isTv(this@SamsungInstallActivity)) qr(TIZEN_URL)?.let { bmp ->
                addView(android.widget.ImageView(this@SamsungInstallActivity).apply {
                    setImageBitmap(bmp); setBackgroundColor(android.graphics.Color.WHITE); setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
                }, android.widget.LinearLayout.LayoutParams(ui.dp(180), ui.dp(180)).apply { topMargin = ui.dp(10) })
            }
            addView(tagged(ui.button(t("xb_login_open")) {
                try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(TIZEN_URL))) } catch (_: Exception) {}
            }, "sstizen"), ui.lp(w = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, top = 10))
        }, 14)

        add(tagged(ui.button(t("wz_back")) { finish() }, "ssback"), 22)

        val target = focused?.let { content.findViewWithTag<View>(it) }
            ?: content.findViewWithTag<View>(if (b != null && !check.ok) "sssave" else "ssback")
        target?.requestFocus()
    }

    // ---------------- uložení balíčku (Storage Access Framework) ----------------

    private fun save() {
        val b = bundle ?: return
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, b.fileName)
        }
        try {
            @Suppress("DEPRECATION") startActivityForResult(i, SAVE)
        } catch (_: Exception) {
            // Android TV bez správce souborů: dialog pro uložení neexistuje
            saveOk = false; saveMsg = t("ss_save_none"); render(true)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != SAVE) return
        val uri = data?.data
        val b = bundle
        if (resultCode != RESULT_OK || uri == null || b == null) return
        saveOk = try {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(b.data); true } ?: false
        } catch (_: Exception) { false }
        saveMsg = if (saveOk) t("ss_saved") else t("ss_save_fail")
        render(true)
    }

    companion object {
        private const val SAVE = 41
    }
}
