package cz.playlingo

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import cz.playlingo.core.DiagLog
import cz.playlingo.core.ReportUpload
import cz.playlingo.core.SnapBatches
import cz.playlingo.core.TvSender
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * „Poslat vývojáři“: hlášení o stavu (a případně jedna dávka snímků ze hry) jako ZIP na úložiště vývojáře,
 * výsledkem je kód (`K7M2-QX9P`), který hráč pošle majiteli. Otevírá se z obrazovky hlášení ([DiagActivity])
 * a z ⚙ → Experimentální → Ladění a hlášení. Adresa serveru je na stránkách s pomocí (`hlaseni.json`,
 * [ReportUpload.CONFIG_URL]) a čte se až při otevření dialogu. Logika a smlouva se serverem: core/ReportUpload.kt.
 *
 * Jeden dialog, jehož obsah se mění po krocích: zjišťování → volba (jen hlášení / hlášení + dávka) → potvrzení
 * → průběh (příprava, odesílání podle bajtů) → kód s Kopírovat / Sdílet, nebo chyba se Zkusit znovu.
 * Všechno jde ovladačem (tlačítka a řádky jsou focusovatelné). Práce běží ve vlákně a jde zrušit
 * (Zrušit / Zpět zavře dialog). ZIP je jediný soubor v `cacheDir/odeslani`, po odeslání se smaže.
 */
class ReportSender private constructor(private val a: Activity, private val ui: Ui, private val reportText: String?) {

    companion object {
        /** Právě otevřený dialog – druhý se neotevře (jeden soubor ZIP, jedno odesílání). */
        @Volatile private var open: ReportSender? = null

        /** Otevře dialog. [reportText] = už sestavené hlášení (obrazovka hlášení); null = sestaví se při odeslání. */
        fun open(a: Activity, ui: Ui, reportText: String? = null) {
            if (open?.dialog?.isShowing == true) return
            ReportSender(a, ui, reportText).also { open = it }.start()
        }

        private fun zipFile(c: Context) = File(File(c.cacheDir, "odeslani"), "PlayLingo-balik.zip")
    }

    private val main = Handler(Looper.getMainLooper())
    private val cancel = AtomicBoolean(false)
    private val phone = a.resources.configuration.screenWidthDp in 1 until 600
    private val box = ui.vbox().apply {
        setBackgroundColor(Theme.BG)
        setPadding(ui.dp(22), ui.dp(18), ui.dp(22), ui.dp(14))
    }
    private var dialog: AlertDialog? = null
    @Volatile private var conn: HttpURLConnection? = null
    @Volatile private var cfg: ReportUpload.Config? = null
    private var batches: List<SnapBatches.Info> = emptyList()
    @Volatile private var lastBar = -1

    private fun t(k: String) = a.tx(k)
    private val alive: Boolean get() = dialog?.isShowing == true && !a.isFinishing && !cancel.get()
    private fun onMain(f: () -> Unit) = main.post { if (alive) f() }

    private fun start() {
        dialog = try {
            AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setView(android.widget.ScrollView(a).apply { addView(box) })
                .setCancelable(true)
                .setOnDismissListener { stop() }
                .show()
        } catch (_: Exception) { open = null; return }
        check()
    }

    /** Dialog zavřený (Zrušit, Zavřít, Zpět): práce skončí, rozepsaný ZIP se smaže. */
    private fun stop() {
        cancel.set(true)
        conn?.let { c -> Thread({ try { c.disconnect() } catch (_: Throwable) {} }, "tp-odeslani-stop").start() }
        Thread({ try { zipFile(a).delete() } catch (_: Throwable) {} }, "tp-odeslani-uklid").start()
        if (open === this) open = null
    }

    private fun close() { try { dialog?.dismiss() } catch (_: Throwable) {} }

    // ---------------- obsah dialogu ----------------

    /** Nový obsah: nadpis, řádky a dole tlačítka; [focus] dostane výběr ovladače. */
    private fun page(vararg rows: View, buttons: List<View>, focus: View? = null) {
        box.removeAllViews()
        box.addView(ui.text(t("rs_btn"), if (phone) 20f else 24f, Theme.TEXT, bold = true))
        rows.forEach { v -> box.addView(v, (v.layoutParams as? LinearLayout.LayoutParams) ?: ui.lp(top = 12)) }
        val bar = ui.hbox()
        buttons.forEachIndexed { i, b ->
            bar.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { if (i > 0) leftMargin = ui.dp(12) })
        }
        box.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(bar) }, ui.lp(top = 18))
        (focus ?: buttons.firstOrNull())?.let { f -> f.post { f.requestFocus() } }
    }

    private fun line(text: String, size: Float = 16f, color: Int = Theme.TEXT, top: Int = 12) =
        ui.text(text, size, color).apply { layoutParams = ui.lp(top = top) }

    private fun bar(f: Float?) = ui.progress(f).apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = ui.dp(14) }

    private fun cancelButton() = ui.button(t("lg_cancel")) { close() }
    private fun closeButton() = ui.button(t("dg_close")) { close() }

    // ---------------- 1. zjištění serveru a dávek ----------------

    private fun check() {
        page(line(t("rs_checking")), bar(null), buttons = listOf(cancelButton()))
        val ctx = a.applicationContext
        Thread({
            var problem: String? = null
            try {
                val text = get(ReportUpload.CONFIG_URL)
                cfg = ReportUpload.config(text)
                if (cfg == null) problem = ReportUpload.E_OFF
            } catch (_: IOException) { problem = ReportUpload.E_NET }
            catch (_: Throwable) { problem = ReportUpload.E_OFF }
            val list = try { ReportUpload.offered(SnapBatches.scan(SnapRecorder.root(ctx)), SnapRecorder.recording) } catch (_: Throwable) { emptyList() }
            val p = problem
            onMain {
                batches = list
                if (p != null) failed(p, retry = p == ReportUpload.E_NET) { check() } else choose()
            }
        }, "tp-odeslani-server").apply { isDaemon = true; start() }
    }

    /** Text z adresy (nejvýš [ReportUpload.MAX_ANSWER] znaků); null = soubor není (jiný stav než 200). */
    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        c.useCaches = false
        c.setRequestProperty("Cache-Control", "no-cache")
        return try {
            if (c.responseCode != 200) null else readSome(c.inputStream)
        } finally { c.disconnect() }
    }

    private fun readSome(s: java.io.InputStream?): String? = s?.use { inp ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (out.size() <= ReportUpload.MAX_ANSWER) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n) }
        if (out.size() > ReportUpload.MAX_ANSWER) null else out.toString("UTF-8")
    }

    // ---------------- 2. volba a potvrzení ----------------

    private fun batchLabel(b: SnapBatches.Info): String =
        t("rs_snaps").replace("%n", b.n.toString()).replace("%c", b.snaps.toString())
            .replace("%t", SimpleDateFormat("d. M. HH:mm", Locale.ROOT).format(Date(b.start)))

    private fun choose() {
        if (batches.isEmpty()) { confirm(null); return }
        val rows = mutableListOf<View>()
        rows += ui.settingRow(t("rs_only"), "›") { confirm(null) }.apply { layoutParams = ui.lp(top = 14) }
        batches.forEach { b -> rows += ui.settingRow(batchLabel(b), "›") { confirm(b) }.apply { layoutParams = ui.lp(top = 8) } }
        page(*rows.toTypedArray(), buttons = listOf(cancelButton()), focus = rows.first())
    }

    private fun confirm(b: SnapBatches.Info?) {
        val what = if (b == null) t("rs_confirm")
            else t("rs_confirm_snaps").replace("%n", b.n.toString()).replace("%c", b.snaps.toString())
        val send = ui.button(t("rs_send"), primary = true) { send(b) }
        page(line(what), line(t("rs_private"), 14f, Theme.MUTED, top = 8), buttons = listOf(send, cancelButton()), focus = send)
    }

    // ---------------- 3. příprava a odeslání ----------------

    /** Hotový balík: soubor, druh a kolik snímků z kolika (zmenšené?). */
    private class Packed(val file: File, val kind: String, val sent: Int, val total: Int, val shrunk: Boolean)

    private fun progress(key: String, f: Float?) {
        val pct = ((f ?: 0f) * 100).toInt()
        page(line(t(key).replace("%p", pct.toString())), bar(f), buttons = listOf(cancelButton()))
    }

    private fun send(b: SnapBatches.Info?) {
        val c = cfg ?: return
        lastBar = -1
        progress("rs_packing", if (b == null) null else 0f)
        val ctx = a.applicationContext
        Thread({
            val packed = try { pack(ctx, c, b) } catch (_: Throwable) { null }
            if (cancel.get()) { packed?.file?.delete(); return@Thread }
            if (packed == null) { log("chyba balíku"); onMain { failed(ReportUpload.E_PACK, retry = true) { confirm(b) } }; return@Thread }
            val ans = try { upload(c, packed.file, packed.kind) } catch (_: Throwable) { ReportUpload.error(ReportUpload.E_NET) }
            try { packed.file.delete() } catch (_: Throwable) {}
            if (cancel.get()) return@Thread
            log(if (ans.ok) "ok ${packed.kind} ${packed.file.length()} B · snímky ${packed.sent}/${packed.total}" else "chyba ${ans.error}")
            onMain {
                if (ans.ok) done(ans, packed)
                // moc velké → zpět na volbu (jen hlášení); ostatní → stejná volba znovu
                else failed(ans.error ?: ReportUpload.E_FAIL, retry = ans.error != ReportUpload.E_LIMIT) {
                    if (ans.error == ReportUpload.E_BIG) choose() else confirm(b)
                }
            }
        }, "tp-odeslani").apply { isDaemon = true; start() }
    }

    private fun log(msg: String) = DiagLog.add("SEND|$msg")

    /** Průběh z vlákna – na hlavní vlákno jen po celých procentech. */
    private fun progressLater(key: String, f: Float) {
        val pct = (f * 100).toInt()
        if (pct == lastBar) return
        lastBar = pct
        onMain { progress(key, f) }
    }

    /** Počítá zapsané bajty (pojistka limitu). */
    private class Counting(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
        override fun write(b: Int) { out.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
    }

    private fun pack(ctx: Context, c: ReportUpload.Config, b: SnapBatches.Info?): Packed? {
        val text = (reportText ?: DiagReport.build(ctx).full).toByteArray(Charsets.UTF_8)
        val file = zipFile(ctx)
        file.parentFile?.mkdirs()
        file.parentFile?.listFiles()?.forEach { it.delete() }

        val dir = b?.let { SnapBatches.find(SnapRecorder.root(ctx), it.n) }
        val index = dir?.let { File(it, SnapBatches.INDEX) }?.takeIf { it.isFile }
        val snaps = (dir?.listFiles() ?: emptyArray()).filter { it.isFile && SnapBatches.isSnap(it.name) }.sortedBy { it.name }
        val sizes = snaps.map { it.length() }
        val fixed = text.size + (index?.length() ?: 0L) + 2048 + 3 * ReportUpload.ENTRY_OVERHEAD

        // plán: celé dávky beze změny → zmenšené snímky → každý n-tý (poměr zmenšení změřený na pár snímcích)
        var plan = ReportUpload.plan(sizes, fixed, c.maxBytes, null)
        if (!plan.all) {
            val sample = listOf(0, snaps.size / 2, snaps.size - 1).distinct().filter { it in snaps.indices }
            val shrunk = sample.mapNotNull { i -> shrink(snaps[i])?.let { sizes[i] to it.size.toLong() } }
            ReportUpload.ratio(shrunk.map { it.first }, shrunk.map { it.second })?.let { r -> plan = ReportUpload.plan(sizes, fixed, c.maxBytes, r) }
        }
        if (cancel.get()) return null

        var sent = 0
        var shrunkAny = false
        FileOutputStream(file).use { fos ->
            val counting = Counting(fos.buffered(64 * 1024))
            val z = ZipOutputStream(counting)
            fun put(name: String, data: ByteArray, compress: Boolean) {
                z.setLevel(if (compress) Deflater.DEFAULT_COMPRESSION else Deflater.NO_COMPRESSION)
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
            put(ReportUpload.ZIP_REPORT, text, true)
            if (index != null) put(ReportUpload.ZIP_SNAPS + SnapBatches.INDEX, index.readBytes(), true)
            plan.picked.forEachIndexed { k, i ->
                if (cancel.get()) return null
                val f = snaps[i]
                var data = if (plan.shrink) shrink(f) else null
                if (data == null || data.size >= sizes[i]) data = try { f.readBytes() } catch (_: IOException) { null } else shrunkAny = true
                z.flush()
                if (data != null && ReportUpload.fits(counting.count, data.size.toLong(), c.maxBytes)) {
                    put(ReportUpload.ZIP_SNAPS + f.name, data, false)
                    sent++
                }
                progressLater("rs_packing", (k + 1f) / plan.picked.size)
            }
            put(ReportUpload.ZIP_INFO, info(ctx, b, sent, snaps.size, shrunkAny, plan.step).toByteArray(Charsets.UTF_8), true)
            z.finish()
            z.flush()
        }
        if (file.length() > c.maxBytes) { file.delete(); return null }
        return Packed(file, if (b != null && sent > 0) ReportUpload.KIND_SNAPS else ReportUpload.KIND_REPORT, sent, snaps.size, shrunkAny)
    }

    /** Snímek zmenšený na delší stranu [ReportUpload.SHRINK_W] a JPEG [ReportUpload.SHRINK_QUALITY]; null = nejde. */
    private fun shrink(f: File): ByteArray? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        val long = maxOf(o.outWidth, o.outHeight)
        if (long <= 0) null else {
            var sample = 1
            while (long / (sample * 2) >= ReportUpload.SHRINK_W) sample *= 2
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bmp == null) null else {
                val l = maxOf(bmp.width, bmp.height)
                val out = if (l > ReportUpload.SHRINK_W) {
                    val s = ReportUpload.SHRINK_W.toFloat() / l
                    Bitmap.createScaledBitmap(bmp, maxOf(1, (bmp.width * s).toInt()), maxOf(1, (bmp.height * s).toInt()), true)
                } else bmp
                val bytes = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, ReportUpload.SHRINK_QUALITY, it) }.toByteArray()
                if (out !== bmp) out.recycle()
                bmp.recycle()
                bytes
            }
        }
    } catch (_: Throwable) { null }

    /** `info.json`: verze, zařízení, nastavení (nic osobního). */
    private fun info(c: Context, b: SnapBatches.Info?, sent: Int, total: Int, shrunk: Boolean, step: Int): String {
        val tv = TvSender.lastSeen?.announce
        return ReportUpload.infoJson(listOf(
            "verze" to BuildInfo.VERSION.removePrefix("verze "),
            "verzeKod" to BuildInfo.VERSION_CODE,
            "cas" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date()),
            "zarizeni" to (Build.MANUFACTURER + " " + Build.MODEL),
            "androidSdk" to Build.VERSION.SDK_INT,
            "jeTv" to Prefs.isTv(c),
            "typTv" to Prefs.tvType(c).key,
            "konzole" to Prefs.console(c).code,
            "hra" to Prefs.gameId(c),
            "jazykPrekladu" to Prefs.lang(c).code,
            "jazykAplikace" to Prefs.uiLang(c).code,
            "tvPlatforma" to tv?.platform,
            "tvVerze" to tv?.version,
            "davka" to b?.name,
            "snimkuPoslano" to sent,
            "snimkuCelkem" to total,
            "snimkyZmensene" to shrunk,
            "snimkyKazdyNty" to step.coerceAtMost(maxOf(1, total)),
        ))
    }

    private fun upload(c: ReportUpload.Config, file: File, kind: String): ReportUpload.Answer {
        val len = file.length()
        val h = URL(c.uploadUrl).openConnection() as HttpURLConnection
        conn = h
        try {
            h.requestMethod = "POST"
            h.doOutput = true
            h.useCaches = false
            h.instanceFollowRedirects = false
            h.connectTimeout = 15000
            h.readTimeout = 90000
            h.setFixedLengthStreamingMode(len)
            h.setRequestProperty("Content-Type", "application/zip")
            h.setRequestProperty(ReportUpload.HEADER_VERSION, BuildInfo.VERSION_CODE.toString())
            h.setRequestProperty(ReportUpload.HEADER_KIND, kind)
            progressLater("rs_sending", 0f)
            h.outputStream.use { out ->
                file.inputStream().use { inp ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        if (cancel.get()) throw IOException("zrušeno")
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        progressLater("rs_sending", if (len > 0) done.toFloat() / len else 1f)
                    }
                }
            }
            val status = h.responseCode
            val body = try { readSome(if (status < 400) h.inputStream else h.errorStream) } catch (_: IOException) { null }
            return ReportUpload.answer(status, body)
        } finally {
            conn = null
            h.disconnect()
        }
    }

    // ---------------- 4. výsledek ----------------

    private fun done(ans: ReportUpload.Answer, p: Packed) {
        val code = ReportUpload.display(ans.code ?: return)
        val rows = mutableListOf<View>(
            line(t("rs_done"), 16f, Theme.MUTED),
            ui.text(code, if (phone) 34f else 44f, Theme.OK, bold = true).apply {
                letterSpacing = 0.08f
                setTextIsSelectable(false)
                layoutParams = ui.lp(top = 6)
            },
            line(t("rs_valid").replace("%d", ans.days.toString()), 14f, Theme.MUTED, top = 4),
        )
        if (p.kind == ReportUpload.KIND_SNAPS && (p.sent < p.total || p.shrunk)) {
            val part = t("rs_part").replace("%a", p.sent.toString()).replace("%b", p.total.toString()) +
                (if (p.shrunk) " · " + t("rs_small") else "")
            rows += line(part, 14f, Theme.MUTED, top = 4)
        }
        val copy = ui.button(t("zz_copy"), primary = true) { copy(code) }
        val share = ui.button(t("zz_share")) { share(code) }
        page(*rows.toTypedArray(), buttons = listOf(copy, share, closeButton()), focus = copy)
    }

    private fun failed(key: String, retry: Boolean, again: () -> Unit) {
        val buttons = mutableListOf<View>()
        if (retry) buttons += ui.button(t("rs_retry"), primary = true) { again() }
        buttons += closeButton()
        page(line(t(key), 17f, if (key == ReportUpload.E_OFF) Theme.MUTED else Theme.WARN), buttons = buttons)
    }

    private fun copy(code: String) {
        try {
            val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("PlayLingo", code))
            Toast.makeText(a, t("rs_copied"), Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(a, t("zz_noshare"), Toast.LENGTH_LONG).show()
        }
    }

    private fun share(code: String) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, t("rs_share_text").replace("%s", code))
            }
            a.startActivity(Intent.createChooser(send, t("zz_share")))
        } catch (_: Throwable) {
            Toast.makeText(a, t("zz_noshare"), Toast.LENGTH_LONG).show()
        }
    }
}
