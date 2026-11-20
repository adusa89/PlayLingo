package cz.playlingo

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import cz.playlingo.core.DiagLog
import cz.playlingo.core.LogServer
import cz.playlingo.core.LogShare
import java.io.File

/**
 * Záznam pro vývojáře v domácí síti (Nastavení → Experimentální → „Zpřístupnit záznam v domácí síti“, výchozí
 * vypnuto). Zapnuté = na portu [LogShare.PORT] běží [LogServer] s krátkým kódem; adresu i kód
 * ukáže řádek v Nastavení a obrazovka hlášení. Po [LogShare.DURATION_MS] se samo vypne (i když
 * mezitím aplikace spadla a znovu se spustila – konec platnosti je uložený).
 * Obsah = celé hlášení o stavu ([DiagReport], vše přes Redact).
 * Navíc: Kopírovat / Sdílet hlášení z obrazovky hlášení ([copy], [share]).
 */
object NetLogShare {
    private const val PREFS = "zaznam_sit"
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var server: LogServer? = null
    private val autoOff = Runnable { appCtx?.let { disable(it) } }
    @Volatile private var appCtx: Context? = null

    enum class Result { OK, NO_NETWORK, FAILED }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun until(c: Context) = prefs(c).getLong("until", 0L)
    private fun now() = System.currentTimeMillis()

    /** Je záznam v síti zapnutý (a ještě nevypršel)? */
    fun active(c: Context): Boolean = server?.running == true && LogShare.active(until(c), now())

    /** Adresa pro vývojáře (null = vypnuto nebo zařízení není v domácí síti). */
    fun url(c: Context): String? {
        val s = server?.takeIf { active(c) } ?: return null
        val ip = NetInfo.homeAddress(c) ?: return null
        return LogShare.url(ip, s.port, s.code)
    }

    /** Adresa stránky pro telefon (čtvereček na obrazovce hlášení); null = vypnuto nebo mimo domácí síť. */
    fun pageUrl(c: Context): String? {
        val s = server?.takeIf { active(c) } ?: return null
        val ip = NetInfo.homeAddress(c) ?: return null
        return LogShare.pageUrl(ip, s.port, s.code)
    }

    /**
     * Hlášení do telefonu: zapne záznam v síti (když neběží) a ukáže čtvereček na stránku pro telefon.
     * Telefon na stejné Wi-Fi si hlášení stáhne jako soubor a hráč ho pošle autorovi chatem nebo e-mailem.
     */
    fun showQr(a: Activity, ui: Ui) {
        if (!active(a)) {
            val r = enable(a)
            if (r != Result.OK) {
                Toast.makeText(a, a.tx(if (r == Result.NO_NETWORK) "zz_nonet" else "zz_fail"), Toast.LENGTH_LONG).show()
                return
            }
        }
        val url = pageUrl(a) ?: return
        val box = ui.vbox().apply { setBackgroundColor(Theme.BG); setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(8)) }
        QrImage.bitmap(url)?.let { qr ->
            box.addView(android.widget.ImageView(a).apply { setImageBitmap(qr); setBackgroundColor(android.graphics.Color.WHITE); setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)) },
                android.widget.LinearLayout.LayoutParams(ui.dp(240), ui.dp(240)).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL })
        }
        box.addView(ui.text(a.tx("zz_qr_text"), 16f, Theme.TEXT), ui.lp(top = 12))
        box.addView(ui.text(url, 13f, Theme.MUTED), ui.lp(top = 6))
        try {
            android.app.AlertDialog.Builder(a).setTitle(a.tx("zz_qr_title")).setView(box)
                .setPositiveButton(a.tx("dg_close")) { d, _ -> d.dismiss() }.show()
        } catch (_: Exception) {}
    }

    fun minutesLeft(c: Context): Long = LogShare.minutesLeft(until(c), now())

    /** Zapne na [LogShare.DURATION_MS]. */
    @Synchronized fun enable(c: Context): Result {
        if (NetInfo.homeAddress(c) == null) return Result.NO_NETWORK
        val code = LogShare.newCode()
        val until = now() + LogShare.DURATION_MS
        prefs(c).edit().putLong("until", until).putString("code", code).apply()
        return if (start(c, code, until)) Result.OK else { disable(c); Result.FAILED }
    }

    @Synchronized fun disable(c: Context) {
        prefs(c).edit().remove("until").remove("code").apply()
        main.removeCallbacks(autoOff)
        server?.stop()
        server = null
        DiagLog.add("ZAZNAM|off")
    }

    /** Po spuštění aplikace: bylo zapnuté a ještě nevypršelo → pustit znovu se stejným kódem. */
    @Synchronized fun resume(c: Context) {
        val u = until(c)
        val code = prefs(c).getString("code", null)
        if (u == 0L) return
        if (!LogShare.active(u, now()) || !LogShare.validCode(code)) { disable(c); return }
        start(c, code!!, u)
    }

    private fun start(c: Context, code: String, end: Long): Boolean {
        val ctx = c.applicationContext
        appCtx = ctx
        server?.stop()
        val s = LogServer(
            code,
            parts = { DiagReport.build(ctx).parts },
            secrets = { DiagReport.secrets(ctx) },
            open = { LogShare.active(until(ctx), now()) },
            onStop = { main.post { disable(ctx) } },
            log = { DiagLog.add("ZAZNAM|$it") },
            lang = { Prefs.uiLang(ctx) },
        )
        // poslouchat jen na adrese domácí sítě (ne na mobilních datech); neznámá = všude, ptát se
        // stejně smí jen domácí síť (LogShare.allowedPeer)
        val bind = NetInfo.homeAddress(ctx)?.let { runCatching { java.net.InetAddress.getByName(it) }.getOrNull() }
        if (!s.start(LogShare.PORT, bind)) return false
        server = s
        main.removeCallbacks(autoOff)
        main.postDelayed(autoOff, LogShare.remainingMs(end, now()))
        return true
    }

    /**
     * Řádek do Nastavení (jeden přepínač; když je zapnuto, pod ním adresa a zbývající čas).
     * Celé je to jeden pohled, aby šel vložit jedním voláním.
     */
    fun row(a: Activity, ui: Ui, rerender: () -> Unit): View {
        val box = ui.vbox()
        val on = active(a)
        box.addView(ui.settingRow(a.tx("zz_toggle"), a.tx(if (on) "on" else "off")) {
            if (active(a)) disable(a)
            else when (enable(a)) {
                Result.OK -> {}
                Result.NO_NETWORK -> Toast.makeText(a, a.tx("zz_nonet"), Toast.LENGTH_LONG).show()
                Result.FAILED -> Toast.makeText(a, a.tx("zz_fail"), Toast.LENGTH_LONG).show()
            }
            rerender()
        }.apply { tag = "zaznamsit" })
        val u = url(a)
        val desc = if (on && u != null)
            a.tx("zz_url").replace("%s", u) + "\n" + a.tx("zz_left").replace("%s", minutesLeft(a).toString())
        else a.tx("zz_desc")
        box.addView(ui.text(desc, 14f, if (on) Theme.OK else Theme.MUTED), ui.lp(top = 6))
        return box
    }

    // ---------------- záloha: schránka a Sdílet ----------------

    fun copy(a: Activity, text: String) {
        try {
            val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("PlayLingo", text))
            Toast.makeText(a, a.tx("zz_copied"), Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(a, a.tx("zz_noshare"), Toast.LENGTH_LONG).show()
        }
    }

    /** Pošle hlášení jako textový soubor přes systémové Sdílet ([ReportProvider]). */
    fun share(a: Activity, text: String) {
        try {
            val f = ReportProvider.file(a)
            f.parentFile?.mkdirs()
            f.writeText(text, Charsets.UTF_8)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, ReportProvider.uri(a))
                putExtra(Intent.EXTRA_SUBJECT, "PlayLingo " + BuildInfo.VERSION + " – hlášení")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            a.startActivity(Intent.createChooser(send, a.tx("zz_share")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Throwable) {
            Toast.makeText(a, a.tx("zz_noshare"), Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Vydá jiným aplikacím (jen s povolením ze Sdílet) jediný soubor: poslední sdílené hlášení.
 * Nic jiného nečte a nic nepřijímá.
 */
class ReportProvider : ContentProvider() {
    companion object {
        private const val NAME = "PlayLingo-hlaseni.txt"
        fun authority(c: Context) = c.packageName + ".hlaseni"
        fun file(c: Context) = File(File(c.cacheDir, "sdileni"), NAME)
        fun uri(c: Context): Uri = Uri.parse("content://" + authority(c) + "/" + NAME)
    }

    override fun onCreate() = true

    private fun ours(uri: Uri) = uri.lastPathSegment == NAME && uri.pathSegments.size == 1

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val c = context ?: return null
        if (!ours(uri) || mode != "r") throw java.io.FileNotFoundException()
        return ParcelFileDescriptor.open(file(c), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = if (ours(uri)) "text/plain" else null

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val c = context ?: return null
        if (!ours(uri)) return null
        val cols = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols, 1).apply { addRow(arrayOf<Any>(NAME, file(c).length())) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
