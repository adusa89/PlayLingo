package cz.playlingo

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import cz.playlingo.core.Deepl
import cz.playlingo.core.DeeplPage
import cz.playlingo.core.LoginServer
import cz.playlingo.core.PhoneLogin

/**
 * Nastavení → Experimentální: pokus „Lepší překlad přes internet (DeepL)“ (výchozí vypnuto).
 * Zapnutí, klíč hráče (políčko, nebo vložení z telefonu přes stránku z televize – stejný server
 * jako přihlášení, viz [LoginServer.forPage]), ověření klíče a měření tohoto běhu.
 * Stránka pro telefon běží jen, dokud je na obrazovce její čtvereček.
 */
class DeeplSection(
    private val a: Activity,
    private val ui: Ui,
    private val applyToService: () -> Unit,
    private val rerender: () -> Unit,
    private val qrRow: (String, String) -> View,
) {
    private var draft = ""
    /** Rozbalený postup „Jak získat klíč“; null = podle zařízení. */
    private var howto: Boolean? = null
    private var busy = false
    private var msg: String? = null
    private var msgColor = Theme.MUTED
    private var page: LoginServer? = null
    private val main = Handler(Looper.getMainLooper())

    private fun t(k: String) = a.tx(k)


    fun build(content: LinearLayout) {
        fun add(v: View, top: Int) = content.addView(v, ui.lp(top = top))
        val has = DeeplKey.has(a)
        val on = has && DeeplKey.enabled(a)
        content.addView(ui.section(t("dl_section")))
        add(ui.settingRow(t("dl_toggle"), t(if (on) "on" else "off")) {
            if (!has) note(t("dl_need_key"), Theme.WARN)
            else { DeeplKey.setEnabled(a, !on); applyToService() }
            rerender()
        }.apply { tag = "deepl" }, 0)
        add(ui.text(t("dl_desc"), 14f, Theme.MUTED), 6)
        add(ui.text(t("dl_privacy"), 14f, Theme.WARN), 6)

        // klíč: uložený (jen začátek a konec) a výsledek posledního ověření
        val key = if (has) DeeplKey.get(a) else null
        add(ui.text(if (key != null) t("dl_key_saved").replace("%s", Deepl.masked(key)) else t("dl_key_none"), 15f, Theme.TEXT), 10)
        if (has) {
            val p = DeeplKey.lastProblem(a)
            val u = DeeplKey.lastUsage(a)
            if (p != null) add(ui.text(problem(p), 14f, if (p == Deepl.Problem.BAD_KEY) Theme.BAD else Theme.WARN), 4)
            else if (u != null) add(ui.text(usage(u), 14f, Theme.OK), 4)
        }
        // DeepL za běhu: proč se teď nepoužívá a měření
        val b = DeeplTranslator.backoff
        val left = b.minutesLeft(SystemClock.uptimeMillis())
        b.lastProblem?.takeIf { on }?.let { p ->
            add(ui.text(problem(p) + " " + when {
                left < 0 -> t("dl_paused_key")
                left > 0 -> t("dl_paused").replace("%s", left.toString())
                else -> ""
            }, 14f, Theme.WARN), 4)
        }
        val s = DeeplTranslator.stats
        if (s.requests + s.cached + s.failed > 0) add(ui.text(t("dl_stats")
            .replace("%1\$s", s.requests.toString()).replace("%2\$s", s.avgMs.toString())
            .replace("%3\$s", s.improved.toString()).replace("%4\$s", s.late.toString())
            .replace("%5\$s", s.same.toString()).replace("%6\$s", s.chars.toString()), 14f, Theme.MUTED), 6)
        msg?.let { add(ui.text(it, 15f, msgColor), 8) }

        val url = pageUrl()
        if (url != null) {
            // vložení klíče z telefonu: čtvereček se stránkou z televize
            add(qrRow(url, t("dl_qr_text")), 10)
            add(ui.text(url, 14f, Theme.MUTED), 6)
            add(ui.button(t("dl_qr_close")) { stopPage(); rerender() }.apply { tag = "dlqrclose" }, 10)
            return
        }
        // postup, jak klíč získat, se čtverečkem na stránku DeepL (telefonem se otevře snáz)
        // (v telefonu je postup rozbalený hned, dokud klíč není)
        val open = howto ?: (!Prefs.isTv(a) && !has)
        add(ui.button(t("dl_howto_btn") + if (open) "  ▲" else "  ▼") { howto = !open; rerender() }.apply { tag = "dlhowto" }, 10)
        if (open) {
            add(qrRow(Deepl.SIGNUP_URL, t("dl_howto")), 10)
            add(ui.text(Deepl.SIGNUP_URL, 14f, Theme.MUTED), 6)
            add(ui.button(t("dl_open")) {
                try { a.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Deepl.SIGNUP_URL))) }
                catch (_: Exception) { note(t("dl_qr_nolan"), Theme.WARN); rerender() }
            }.apply { tag = "dlopen" }, 8)
        }
        add(EditText(a).apply {
            setText(draft)
            hint = t("dl_key_hint")
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(Theme.TEXT)
            setHintTextColor(Theme.MUTED)
            textSize = 16f
            background = ui.focusBg(Theme.CARD)
            setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12))
            tag = "dlkey"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(e: Editable?) { draft = e?.toString() ?: "" }
            })
        }, 10)
        val row = row()
        row.addView(ui.button(t(if (busy) "dl_checking" else "dl_key_save"), primary = true) { if (!busy) saveTyped() }.apply { tag = "dlsave" })
        row.addView(ui.button(t("dl_key_phone")) { openPage() }.apply { tag = "dlphone" }, gap())
        add(row, 10)
        if (key != null) {
            val row2 = row()
            row2.addView(ui.button(t("dl_key_check")) { if (!busy) verify(key, fromField = false) }.apply { tag = "dlcheck" })
            row2.addView(ui.button(t("dl_key_remove")) {
                DeeplKey.remove(a); msg = null; applyToService(); rerender()
            }.apply { tag = "dlremove" }, gap())
            add(row2, 8)
        }
    }

    /** Na úzké obrazovce (telefon na výšku) tlačítka pod sebou. */
    private fun row(): LinearLayout = if (a.resources.configuration.screenWidthDp in 1 until 600) ui.vbox() else ui.hbox()

    private fun gap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (a.resources.configuration.screenWidthDp in 1 until 600) topMargin = ui.dp(8) else leftMargin = ui.dp(12) }

    private fun note(s: String?, color: Int) { msg = s; msgColor = color }

    private fun problem(p: Deepl.Problem) = t("dl_p_" + p.name.lowercase())

    private fun usage(u: Deepl.Usage) = t("dl_usage").replace("%1\$s", group(u.left)).replace("%2\$s", group(u.limit))

    /** 1000000 → „1 000 000“ */
    private fun group(n: Long): String = n.toString().reversed().chunked(3).joinToString(" ").reversed()

    private fun saveTyped() {
        val key = Deepl.cleanKey(draft)
        if (Deepl.keyKind(key) == null) { note(t("dl_e_shape"), Theme.BAD); rerender(); return }
        verify(key, fromField = true)
    }

    /** Ověří klíč u DeepL; platný (nebo neověřitelný kvůli síti) se uloží. */
    private fun verify(key: String, fromField: Boolean) {
        busy = true; note(t("dl_checking"), Theme.MUTED); rerender()
        Thread {
            val r = DeeplTranslator.check(key)
            val text = store(key, r)
            a.runOnUiThread {
                busy = false
                if (r.problem != Deepl.Problem.BAD_KEY && fromField) draft = ""
                note(text, if (r.problem == null) Theme.OK else if (r.problem == Deepl.Problem.BAD_KEY) Theme.BAD else Theme.WARN)
                applyToService()
                if (!a.isFinishing) rerender()
            }
        }.apply { isDaemon = true }.start()
    }

    /** Uloží klíč podle výsledku ověření (odmítnutý se neukládá) a vrátí text pro hráče. */
    private fun store(key: String, r: DeeplTranslator.Check): String {
        if (r.problem == Deepl.Problem.BAD_KEY) { if (DeeplKey.get(a) == key) DeeplKey.saveCheck(a, r); return problem(r.problem) }
        if (DeeplKey.get(a) != key) DeeplKey.save(a, key)
        DeeplKey.saveCheck(a, r)
        return r.usage?.let { usage(it) } ?: problem(r.problem ?: Deepl.Problem.BAD_REPLY)
    }

    // ---------------- vložení klíče z telefonu ----------------

    private fun pageUrl(): String? {
        val s = page?.takeIf { it.running } ?: return null
        val ip = NetInfo.homeAddress(a) ?: return null
        return PhoneLogin.pageUrl(PhoneLogin.Target(ip, s.port, s.key))
    }

    private fun openPage() {
        if (page != null) return
        val server = LoginServer.forPage({ Prefs.uiLang(a) }, object : LoginServer.Custom {
            override fun get(path: String) = DeeplPage.page(Prefs.uiLang(a), DeeplPage.State.ASK, path)
            override fun gone() = DeeplPage.page(Prefs.uiLang(a), DeeplPage.State.GONE, "")
            override fun post(path: String, form: Map<String, String>): String {
                val lang = Prefs.uiLang(a)
                val key = Deepl.cleanKey(form["key"])
                if (Deepl.keyKind(key) == null) return DeeplPage.page(lang, DeeplPage.State.BAD_SHAPE, path)
                val r = DeeplTranslator.check(key)
                val text = store(key, r)
                if (r.problem == Deepl.Problem.BAD_KEY) return DeeplPage.page(lang, DeeplPage.State.REJECTED, path, text)
                a.runOnUiThread {
                    note(text, if (r.problem == null) Theme.OK else Theme.WARN)
                    applyToService()
                    // stránka ještě odešle odpověď telefonu, pak se zavře
                    main.postDelayed({ stopPage(); if (!a.isFinishing) rerender() }, 1500)
                }
                return DeeplPage.page(lang, DeeplPage.State.DONE, path, text)
            }
        }, log = { android.util.Log.i("TP_TEST", "DEEPL|page " + it) })
        if (!server.start() || NetInfo.homeAddress(a) == null) {
            server.stop()
            note(t("dl_qr_nolan"), Theme.WARN)
        } else page = server
        rerender()
    }

    /** Stránka pro telefon jen, dokud je vidět její čtvereček (volá se i při odchodu z Nastavení). */
    fun stopPage() {
        page?.stop(); page = null
    }
}
