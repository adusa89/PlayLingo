package cz.playlingo.pc

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import cz.playlingo.core.Segment
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pruh s překladem přímo na obrazovce počítače, přes okno s hrou (chiaki-ng, Remote Play…).
 *
 * Okno pruhu je vždy navrchu, průhledné, nebere si zaměření a myš i dotyky jím propadají do hry.
 * Hra se tak dál ovládá úplně stejně. Pruh jede s oknem hry, když se posune nebo změní velikost.
 *
 * Windows hlásí polohu okna hry ve skutečných bodech, Swing kreslí v bodech po zvětšení obrazovky
 * (na handheldu 150–200 %) – [obnov] to přepočítá podle [meritko]. Funguje pro hlavní obrazovku.
 */

/** Obdélník na obrazovce ve skutečných bodech. */
data class Obdelnik(val x: Int, val y: Int, val w: Int, val h: Int)

/** Funkce Windows, které v knihovně JNA nejsou ve všech verzích. */
private interface U32 : StdCallLibrary {
    fun IsIconic(hwnd: WinDef.HWND): Boolean
    fun IsWindow(hwnd: WinDef.HWND): Boolean
    fun ClientToScreen(hwnd: WinDef.HWND, p: WinDef.POINT): Boolean

    companion object {
        val I: U32 = Native.load("user32", U32::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

/** Okno s hrou podle části titulku – stejné hledání jako v `cteni.py` (první viditelné shora). */
class OknoHry(nazev: String) {
    private val hledat = nazev.lowercase()
    private val mojePid = ProcessHandle.current().pid().toInt()
    private var hwnd: WinDef.HWND? = null

    private fun najdi(): WinDef.HWND? {
        var nalezeno: WinDef.HWND? = null
        User32.INSTANCE.EnumWindows({ h, _ ->
            if (User32.INSTANCE.IsWindowVisible(h)) {
                val n = User32.INSTANCE.GetWindowTextLength(h)
                if (n > 0) {
                    val buf = CharArray(n + 1)
                    User32.INSTANCE.GetWindowText(h, buf, n + 1)
                    val pid = IntByReference()
                    User32.INSTANCE.GetWindowThreadProcessId(h, pid)
                    if (pid.value != mojePid && Native.toString(buf).lowercase().contains(hledat)) {
                        nalezeno = h
                        return@EnumWindows false
                    }
                }
            }
            true
        }, null)
        return nalezeno
    }

    /** Vnitřek okna hry (bez rámečku a titulku) na obrazovce; null = okno není nebo je minimalizované. */
    fun oblast(): Obdelnik? {
        val h = hwnd?.takeIf { U32.I.IsWindow(it) } ?: najdi().also { hwnd = it } ?: return null
        if (U32.I.IsIconic(h)) return null
        val r = WinDef.RECT()
        if (!User32.INSTANCE.GetClientRect(h, r)) return null
        val p = WinDef.POINT(0, 0)
        if (!U32.I.ClientToScreen(h, p)) return null
        val w = r.right - r.left
        val v = r.bottom - r.top
        return if (w < 320 || v < 180) null else Obdelnik(p.x, p.y, w, v)
    }
}

/** Titulky viditelných oken (kromě vlastních) – nabídka „Vybrat okno“. */
fun otevrenaOkna(): List<String> {
    val moje = ProcessHandle.current().pid().toInt()
    val vysledek = LinkedHashSet<String>()
    User32.INSTANCE.EnumWindows({ h, _ ->
        if (User32.INSTANCE.IsWindowVisible(h)) {
            val n = User32.INSTANCE.GetWindowTextLength(h)
            if (n > 0) {
                val buf = CharArray(n + 1)
                User32.INSTANCE.GetWindowText(h, buf, n + 1)
                val pid = IntByReference()
                User32.INSTANCE.GetWindowThreadProcessId(h, pid)
                val t = Native.toString(buf).trim()
                if (pid.value != moje && t.isNotEmpty() && t != "Program Manager") vysledek += t
            }
        }
        true
    }, null)
    return vysledek.toList()
}

/** Zvětšení hlavní obrazovky ve Windows (1.0 = 100 %, 2.0 = 200 %), jak ho používá Swing. */
fun meritko(): Double =
    GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX

/** Celá hlavní obrazovka ve skutečných bodech – pro ukázku bez PlayStationu, kde žádné okno s hrou není. */
fun celaObrazovka(): Obdelnik {
    val b = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
    val m = meritko()
    return Obdelnik((b.x * m).toInt(), (b.y * m).toInt(), (b.width * m).toInt(), (b.height * m).toInt())
}

/**
 * Kde na obrazovce hry byl anglický titulek – poměry šířky/výšky okna hry (0..1).
 * [nahore]/[dole] < 0 = nevíme; [radek] = výška anglického řádku (0 = nevíme).
 */
data class Misto(val stredX: Float, val nahore: Float, val dole: Float, val radek: Float)

/** Kam pruh položit – jako na Android TV (Titulky → Pruh s překladem → Poloha). */
enum class Poloha(val nazev: String) { PRES("Přes titulky"), NAD("Nad titulky"), DOLE("Dole") }

/** Vzhled pruhu ze stránky Titulky. */
data class Vzhled(
    /** Velikost písma v % automatické velikosti (podle anglického titulku). */
    val velikost: Int = 100,
    /** Neprůhlednost pozadí v %. */
    val pozadi: Int = 85,
    val barva: Color = Color.WHITE,
    val tucne: Boolean = true,
    val poloha: Poloha = Poloha.PRES,
)

/**
 * Pruh. [oblast] říká, kde je teď okno hry; volá se pořád dokola, aby pruh jel s oknem.
 *
 * Kam pruh patří, se zadává poměrem v okně hry (0..1) – stejně jako na Android TV leží přes
 * anglický titulek, ze kterého překlad vznikl. Velikost písma se řídí výškou anglického řádku.
 */
class Pruh(private val oblast: () -> Obdelnik?) {
    private class Obsah(val casti: List<Segment>, val barvy: Map<String, Int>, val misto: Misto)

    @Volatile var vzhled = Vzhled()
        set(v) { field = v; SwingUtilities.invokeLater { naposledy = null; obnov() } }

    /** Kousek řádku jednou barvou. */
    private class Kus(val text: String, val barva: Color)

    private val okno = JWindow()
    private val plocha = Plocha()
    @Volatile private var obsah: Obsah? = null
    private var naposledy: Pair<Obsah?, Obdelnik?>? = null
    private var stylNastaven = false

    init {
        val nastav = {
            okno.background = Color(0, 0, 0, 0)
            okno.focusableWindowState = false
            okno.isAlwaysOnTop = true
            okno.contentPane = plocha
            okno.setBounds(0, 0, 1, 1)
            Timer(150) { obnov() }.start()
        }
        if (SwingUtilities.isEventDispatchThread()) nastav() else SwingUtilities.invokeAndWait(nastav)
    }

    /** Ukáže překlad tam, kde byl anglický titulek ([misto]). */
    fun ukaz(casti: List<Segment>, barvy: Map<String, Int>, misto: Misto) {
        obsah = Obsah(casti, barvy, misto)
        SwingUtilities.invokeLater { obnov() }
    }

    /** Zkušební věta na pár vteřin – náhled vzhledu ze stránky Titulky. */
    fun zkouska() {
        ukaz(
            listOf(Segment("Marek", "Potřebujeme peníze, Jonasi. Ráno vyrážíme do Greyfordu.")),
            mapOf("Marek" to 0x4FC3F7), Misto(0.5f, 0.84f, 0.9f, 0.05f),
        )
        val ted = obsah
        Timer(4000) { if (obsah === ted) schovej() }.apply { isRepeats = false; start() }
    }

    fun schovej() {
        obsah = null
        SwingUtilities.invokeLater { obnov() }
    }

    // ---- jen ve vlákně Swingu ----

    private fun obnov() {
        val o = obsah
        // Windows hlásí okno hry ve skutečných bodech, Swing kreslí v bodech po zvětšení
        val r = (if (o == null) null else oblast())?.let { s ->
            val m = meritko()
            Obdelnik((s.x / m).roundToInt(), (s.y / m).roundToInt(), (s.w / m).roundToInt(), (s.h / m).roundToInt())
        }
        if (naposledy?.first === o && naposledy?.second == r) return
        naposledy = o to r
        if (o == null || r == null) {
            okno.isVisible = false
            return
        }
        val v = vzhled
        val m = o.misto
        // písmo: zhruba jako anglický titulek (výška řádku ze čtení zahrnuje i okraj kolem písmen),
        // ale aby se dalo číst i na malém displeji handheldu
        val auto = (if (m.radek > 0f) m.radek * r.h * 0.7f else r.h * 0.035f)
            .coerceIn(max(16f, r.h * 0.025f), r.h * 0.05f)
        val px = (auto * v.velikost / 100f).coerceIn(12f, r.h * 0.09f)
        plocha.pismo = Font("Segoe UI", if (v.tucne) Font.BOLD else Font.PLAIN, px.roundToInt())
        plocha.pozadi = Color(0, 0, 0, (255 * v.pozadi / 100).coerceIn(0, 255))
        val fm = plocha.getFontMetrics(plocha.pismo)
        val okraj = (px * 0.45f).roundToInt()
        val radky = zalom(o, fm, (r.w * 0.9f).roundToInt() - 2 * okraj, v.barva)
        plocha.radky = radky
        plocha.okraj = okraj
        val sirka = (radky.maxOfOrNull { r2 -> r2.sumOf { fm.stringWidth(it.text) } } ?: 0) + 2 * okraj
        val vyska = radky.size * fm.height + 2 * okraj
        val znamo = m.nahore >= 0f && m.dole >= 0f
        val poloha = if (znamo) v.poloha else Poloha.DOLE
        val stredX = if (poloha == Poloha.DOLE) 0.5f else m.stredX
        val yf = when (poloha) {
            Poloha.PRES -> r.y + (m.nahore + m.dole) / 2f * r.h - vyska / 2f
            Poloha.NAD -> r.y + m.nahore * r.h - vyska - px * 0.3f
            Poloha.DOLE -> r.y + r.h * 0.97f - vyska
        }
        val x = (r.x + stredX * r.w - sirka / 2f).roundToInt().coerceIn(r.x, max(r.x, r.x + r.w - sirka))
        val y = yf.roundToInt().coerceIn(r.y, max(r.y, r.y + r.h - vyska))
        okno.setBounds(x, y, sirka, vyska)
        if (!okno.isVisible) okno.isVisible = true
        nastavStyl()
        okno.toFront()
        plocha.repaint()
    }

    /** Myš a dotyky skrz pruh do hry, bez zaměření a bez tlačítka na hlavním panelu. */
    private fun nastavStyl() {
        if (stylNastaven) return
        val hwnd = WinDef.HWND(Native.getComponentPointer(okno) ?: return)
        val styl = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
        User32.INSTANCE.SetWindowLong(
            hwnd, WinUser.GWL_EXSTYLE,
            styl or WS_EX_LAYERED or WS_EX_TRANSPARENT or WS_EX_TOOLWINDOW or WS_EX_NOACTIVATE or WS_EX_TOPMOST,
        )
        stylNastaven = true
    }

    /** Rozdělí části titulku do řádků, které se vejdou do [max] bodů. Každá část začíná novým řádkem. */
    private fun zalom(o: Obsah, fm: FontMetrics, max: Int, barva: Color): List<List<Kus>> {
        val radky = ArrayList<List<Kus>>()
        for (c in o.casti) {
            val slova = ArrayList<Kus>()
            when {
                c.choice -> slova += Kus("› ", ZLUTA)
                c.speaker != null -> slova += Kus("${c.speaker}: ", Color(o.barvy[c.speaker] ?: 0xFFD54F))
            }
            c.body.split(' ').filter { it.isNotEmpty() }.forEach { slova += Kus("$it ", barva) }
            var radek = ArrayList<Kus>()
            var sirka = 0
            for (s in slova) {
                val w = fm.stringWidth(s.text)
                if (radek.isNotEmpty() && sirka + w - fm.charWidth(' ') > max) {
                    radky += radek
                    radek = ArrayList()
                    sirka = 0
                }
                radek += s
                sirka += w
            }
            if (radek.isNotEmpty()) radky += radek
        }
        // mezera na konci řádku se do šířky nepočítá
        return radky.map { r -> r.dropLast(1) + Kus(r.last().text.trimEnd(), r.last().barva) }
    }

    private inner class Plocha : JComponent() {
        var pismo = Font("Segoe UI", Font.BOLD, 28)
        var radky: List<List<Kus>> = emptyList()
        var okraj = 12
        var pozadi = Color(0, 0, 0, 215)

        init { isOpaque = false }

        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.composite = java.awt.AlphaComposite.Src
            g2.color = pozadi
            val oblouk = min(okraj * 2, height)
            g2.fillRoundRect(0, 0, width, height, oblouk, oblouk)
            g2.composite = java.awt.AlphaComposite.SrcOver
            g2.font = pismo
            val fm = g2.fontMetrics
            var y = okraj + fm.ascent
            for (r in radky) {
                var x = (width - r.sumOf { fm.stringWidth(it.text) }) / 2
                for (k in r) {
                    g2.color = k.barva
                    g2.drawString(k.text, x, y)
                    x += fm.stringWidth(k.text)
                }
                y += fm.height
            }
        }
    }

    private companion object {
        val ZLUTA = Color(0xFFD54F)
        const val WS_EX_TOPMOST = 0x00000008
        const val WS_EX_TRANSPARENT = 0x00000020
        const val WS_EX_TOOLWINDOW = 0x00000080
        const val WS_EX_LAYERED = 0x00080000
        const val WS_EX_NOACTIVATE = 0x08000000
    }
}
