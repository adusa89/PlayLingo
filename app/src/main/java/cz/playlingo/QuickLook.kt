package cz.playlingo

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import cz.playlingo.core.ScreenLayout
import cz.playlingo.core.TvSettings
import cz.playlingo.core.TvType

/**
 * Rychlá nabídka vzhledu titulků ovladačem na Android TV (od 0.97.15) – totéž, co má aplikace v TV LG/Samsung
 * po stisku OK při hře: pozadí pruhu, velikost písma, poloha, originál. Otevře ji tlačítko, které si hráč
 * zvolí v ⚙ Nastavení → Obecné („Tlačítko vzhledu titulků“, [Prefs.quickKey]); OK to být nesmí – televize
 * ho při HDMI často posílá do PlayStationu.
 *
 * Tlačítka chytá služba Usnadnění ([TitulkyAccessibility.onKeyEvent], `flagRequestFilterKeyEvents`) – jen
 * zvolené tlačítko, a dokud je nabídka otevřená, i šipky a Zpět. Ostatní tlačítka jdou dál beze změny.
 * Změny jdou stejnou cestou jako z TV LG ([TvSettings.Change] → [TranslatorService.quickChange]).
 */
object QuickLook {
    private const val ROWS = 4
    @Volatile var open = false
        private set
    private var row = 0
    private var view: LinearLayout? = null

    /** Tlačítka, která na nabídku dát nejde (ovládají hru, televizi nebo samotnou nabídku). */
    val FORBIDDEN = setOf(
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_ASSIST,
    )

    /** Název tlačítka pro Nastavení („PROG_RED“ → „Prog red“). */
    fun keyName(code: Int): String =
        KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercaseChar() }

    /** Volá služba Usnadnění pro každé tlačítko; true = tlačítko si nechala nabídka (dál nejde). */
    fun onKey(svc: TitulkyAccessibility, e: KeyEvent): Boolean {
        val key = Prefs.quickKey(svc)
        val active = key != 0 && TranslatorService.running && !UiState.visible && Prefs.tvType(svc) == TvType.ANDROID
        if (!active) { if (open) close(); return false }
        if (e.keyCode == key) {
            if (e.action == KeyEvent.ACTION_UP) { if (open) close() else show(svc) }
            return true
        }
        if (!open) return false
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (down) { row = (row + ROWS - 1) % ROWS; draw(svc) }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) { row = (row + 1) % ROWS; draw(svc) }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (down) change(svc, -1)
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (down) change(svc, 1)
            KeyEvent.KEYCODE_BACK -> if (!down) close()
            else -> return false
        }
        return true
    }

    private fun show(svc: TitulkyAccessibility) {
        row = 0
        open = true
        draw(svc)
        TranslatorService.quickPreview(true)
    }

    fun close() {
        if (!open) return
        open = false
        val v = view ?: run { TranslatorService.quickPreview(false); return }
        view = null
        try { TitulkyAccessibility.instance?.getSystemService(WindowManager::class.java)?.removeView(v) } catch (_: Throwable) {}
        TranslatorService.quickPreview(false)
    }

    /** Další hodnota zvolené volby (krok −1 / +1) – stejné seznamy jako v TV LG ([TvSettings]). */
    private fun change(svc: TitulkyAccessibility, step: Int) {
        val ch: TvSettings.Change = when (row) {
            0 -> TvSettings.Change.Opacity(next(TvSettings.OPACITIES, Prefs.opacity(svc), step))
            1 -> {
                val sizes = listOf(0) + TvSettings.TEXT_SIZES
                TvSettings.Change.TextSize(next(sizes, if (Prefs.matchSize(svc)) 0 else Prefs.textSize(svc), step))
            }
            2 -> {
                val all = TvSettings.POSITIONS.values.toList()
                TvSettings.Change.Position(next(all, Prefs.layout(svc).position, step))
            }
            else -> TvSettings.Change.Original(!Prefs.showOriginal(svc))
        }
        TranslatorService.quickChange(ch)
        draw(svc)
    }

    private fun <T> next(list: List<T>, cur: T, step: Int): T {
        val i = list.indexOf(cur)
        return if (i < 0) list[0] else list[(i + step + list.size) % list.size]
    }

    private fun value(svc: TitulkyAccessibility, i: Int): String = when (i) {
        0 -> "${Prefs.opacity(svc)} %"
        1 -> if (Prefs.matchSize(svc)) svc.tx("qk_size_game")
            else Prefs.textSize(svc).toString()
        2 -> svc.tx("pos_" + Prefs.layout(svc).position.name.lowercase())
        else -> svc.tx(if (Prefs.showOriginal(svc)) "on" else "off")
    }

    /** Nakreslí (nebo překreslí) nabídku vpravo nahoře přes hru. */
    private fun draw(svc: TitulkyAccessibility) {
        val d = svc.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val box = view ?: LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.argb(240, 14, 16, 20)); cornerRadius = dp(10).toFloat() }
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }.also { v ->
            val lp = WindowManager.LayoutParams(dp(380), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
                .apply { gravity = Gravity.TOP or Gravity.END; x = dp(24); y = dp(24) }
            try { svc.getSystemService(WindowManager::class.java).addView(v, lp); view = v } catch (_: Throwable) { open = false; return }
        }
        box.removeAllViews()
        fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(svc).apply {
            text = s; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        box.addView(text(svc.tx("qk_title"), 18f, Theme.ACCENT, bold = true))
        val labels = listOf(svc.tx("l_opacity"), svc.tx("l_size"), svc.tx("l_pos"), svc.tx("l_original"))
        labels.forEachIndexed { i, label ->
            val sel = i == row
            box.addView(LinearLayout(svc).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(10), dp(7), dp(10), dp(7))
                if (sel) background = GradientDrawable().apply { setColor(Theme.ACCENT); cornerRadius = dp(6).toFloat() }
                val c = if (sel) Theme.ACCENT_DARK else Theme.TEXT
                addView(text(label, 15f, c, sel), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(text("◀ " + value(svc, i) + " ▶", 15f, c, sel))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        box.addView(text(svc.tx("qk_hint"), 12f, Theme.MUTED), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        box.visibility = View.VISIBLE
    }
}
