@file:JvmName("AplikaceKt")
package cz.playlingo.pc

import cz.playlingo.core.Lang
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.concurrent.thread

/**
 * PlayLingo pro počítač – okno aplikace ve stejném duchu jako aplikace pro telefon a TV:
 * vlevo menu (Domů, Titulky, ⚙ Nastavení), Domů = velký stav, Spustit/Zastavit a stavové řádky
 * „Popis · hodnota“ s tečkou (zelená = jede, oranžová = je potřeba zásah, šedá = informace).
 *
 * Čtení a překlad dělá [Ctecka], pruh přes hru [Pruh]. Spouští se `PlayLingo.cmd`.
 */

/** Barvy aplikace – stejné jako `Theme` v aplikaci pro Android. */
private object B {
    val BG = Color(0x0E, 0x10, 0x14)
    val SIDEBAR = Color(0x14, 0x17, 0x1D)
    val CARD = Color(0x1B, 0x1F, 0x27)
    val CARD2 = Color(0x24, 0x29, 0x33)
    val FOCUS = Color(0x4A, 0x40, 0x22)
    val ACCENT_FOCUS = Color(0xFF, 0xE3, 0x8F)
    val TEXT = Color(0xF2, 0xF2, 0xF2)
    val MUTED = Color(0x9A, 0xA4, 0xAE)
    val ACCENT = Color(0xF2, 0xC1, 0x4E)
    val ACCENT_DARK = Color(0x1A, 0x14, 0x05)
    val OK = Color(0x4A, 0xDE, 0x80)
    val WARN = Color(0xF5, 0x9E, 0x0B)
}

private fun pismo(size: Int, tucne: Boolean = false) = Font("Segoe UI", if (tucne) Font.BOLD else Font.PLAIN, size)

private fun Graphics.hladce(): Graphics2D = (this as Graphics2D).apply {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
}

private fun popisek(text: String, size: Int = 16, barva: Color = B.TEXT, tucne: Boolean = false) =
    JLabel(text).apply { font = pismo(size, tucne); foreground = barva }

/** Svislý sloupec bez pozadí. */
private fun sloupec(vararg deti: Component) = JPanel().apply {
    isOpaque = false
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    deti.forEach { add(it) }
}

private fun JComponent.vlevo() = apply { alignmentX = Component.LEFT_ALIGNMENT }

/** Karta se zaoblenými rohy jako `ui.card()` na Androidu. */
private class Karta : JPanel() {
    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = EmptyBorder(10, 14, 10, 14)
        alignmentX = Component.LEFT_ALIGNMENT
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.hladce()
        g2.color = B.CARD
        g2.fillRoundRect(0, 0, width, height, 24, 24)
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/** Tlačítko – hlavní (žluté) nebo obyčejné; vybrané ovladačem/klávesnicí má zlatý rámeček. */
private class Tlacitko(text: String, private val hlavni: Boolean = false, akce: () -> Unit) : JButton(text) {
    init {
        isContentAreaFilled = false
        isFocusPainted = false
        isBorderPainted = false
        isRolloverEnabled = true
        font = pismo(if (hlavni) 18 else 15, true)
        foreground = if (hlavni) B.ACCENT_DARK else B.TEXT
        border = EmptyBorder(if (hlavni) 12 else 7, if (hlavni) 34 else 16, if (hlavni) 12 else 7, if (hlavni) 34 else 16)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addActionListener { akce() }
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = repaint()
            override fun focusLost(e: FocusEvent) = repaint()
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.hladce()
        val aktivni = model.isRollover || isFocusOwner
        g2.color = when {
            hlavni && aktivni -> B.ACCENT_FOCUS
            hlavni -> B.ACCENT
            aktivni -> B.FOCUS
            else -> B.CARD2
        }
        g2.fillRoundRect(0, 0, width, height, 20, 20)
        if (isFocusOwner) {
            g2.color = if (hlavni) Color.WHITE else B.ACCENT
            g2.stroke = BasicStroke(3f)
            g2.drawRoundRect(1, 1, width - 3, height - 3, 20, 20)
        }
        super.paintComponent(g)
    }
}

/** Řádek nastavení: název vlevo, hodnota a „›“ vpravo; klik / Enter = [akce]. */
private class Radek(nazev: String, private val hodnota: () -> String, private val akce: (Radek) -> Unit) : JPanel(BorderLayout()) {
    private val vpravo = popisek("", 16, B.ACCENT)
    private var nad = false

    init {
        isOpaque = false
        isFocusable = true
        border = EmptyBorder(11, 12, 11, 12)
        alignmentX = Component.LEFT_ALIGNMENT
        add(popisek(nazev, 16), BorderLayout.WEST)
        add(vpravo, BorderLayout.EAST)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        obnov()
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { requestFocusInWindow(); akce(this@Radek) }
            override fun mouseEntered(e: MouseEvent) { nad = true; repaint() }
            override fun mouseExited(e: MouseEvent) { nad = false; repaint() }
        })
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) akce(this@Radek)
            }
        })
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) { repaint(); scrollRectToVisible(bounds.let { java.awt.Rectangle(0, 0, width, height) }) }
            override fun focusLost(e: FocusEvent) = repaint()
        })
    }

    fun obnov() { vpravo.text = hodnota().let { if (it == "▼" || it == "▲") it else "$it  ›" } }

    override fun paintComponent(g: Graphics) {
        if (!nad && !isFocusOwner) return
        val g2 = g.hladce()
        g2.color = B.FOCUS
        g2.fillRoundRect(0, 0, width, height, 18, 18)
        if (isFocusOwner) {
            g2.color = B.ACCENT
            g2.stroke = BasicStroke(3f)
            g2.drawRoundRect(1, 1, width - 3, height - 3, 18, 18)
        }
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/** Stavový řádek: tečka + „Popis · hodnota“ + případně tlačítko pod tím. */
private class StavRadek : JPanel() {
    private val tecka = object : JComponent() {
        var barva = B.MUTED
        init { preferredSize = Dimension(14, 14); maximumSize = preferredSize }
        override fun paintComponent(g: Graphics) {
            val g2 = g.hladce(); g2.color = barva; g2.fillOval(1, 1, 12, 12)
        }
    }
    private val text = popisek("", 17)
    private val tlacitka = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
    private var akce: (() -> Unit)? = null

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        border = EmptyBorder(7, 4, 7, 4)
        val horni = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(tecka); add(Box.createHorizontalStrut(12)); add(text)
        }
        add(horni)
        tlacitka.alignmentX = Component.LEFT_ALIGNMENT
        tlacitka.border = EmptyBorder(8, 26, 0, 0)
        add(tlacitka)
    }

    /** [tlacitko] = popisek tlačítka pod řádkem (jen u oranžových, kde je potřeba zásah). */
    fun nastav(barva: Color, obsah: String, tlacitko: String? = null, akce: (() -> Unit)? = null) {
        tecka.barva = barva; tecka.repaint()
        text.text = obsah
        isVisible = obsah.isNotEmpty()
        if (tlacitko != (tlacitka.components.firstOrNull() as? JButton)?.text) {
            tlacitka.removeAll()
            if (tlacitko != null) tlacitka.add(Tlacitko(tlacitko) { this.akce?.invoke() })
            tlacitka.isVisible = tlacitko != null
            revalidate()
        }
        this.akce = akce
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
}

/** Nabídka možností pod řádkem (jako výběr na Androidu). */
private fun vyber(kde: Component, moznosti: List<String>, aktualni: Int, vybrano: (Int) -> Unit) {
    val menu = JPopupMenu().apply {
        background = B.CARD2
        border = BorderFactory.createLineBorder(B.ACCENT, 2)
    }
    moznosti.forEachIndexed { i, m ->
        menu.add(JMenuItem((if (i == aktualni) "✔  " else "     ") + m).apply {
            font = Font("Segoe UI Symbol", if (i == aktualni) Font.BOLD else Font.PLAIN, 16)
            background = B.CARD2
            foreground = if (i == aktualni) B.ACCENT else B.TEXT
            border = EmptyBorder(8, 12, 8, 24)
            addActionListener { vybrano(i) }
        })
    }
    menu.show(kde, kde.width - menu.preferredSize.width, kde.height)
}

private class Aplikace {
    private val nast = Nastaveni()
    private val okno = JFrame("PlayLingo")
    private val obsah = JPanel(CardLayout()).apply { background = B.BG }
    private val polozkyMenu = ArrayList<Pair<String, JComponent>>()
    private var stranka = "Domů"

    // čtení
    private var hra = OknoHry(nast.okno)
    private val pruh = Pruh { if (nast.ukazka) celaObrazovka() else hra.oblast() }
    private var ctecka: Ctecka? = null
    private var vlakno: Thread? = null
    @Volatile private var stav = Stav(Stav.Faze.STOJI)
    @Volatile private var priprava: Process? = null
    private var pripravaChyba = false
    private var oknoNalezeno = false
    private val zaznam = JTextArea().apply {
        isEditable = false; background = B.CARD; foreground = B.MUTED
        font = Font("Consolas", Font.PLAIN, 13); border = EmptyBorder(10, 12, 10, 12); lineWrap = true
    }

    // Domů
    private val velkyStav = popisek("", 34, B.TEXT, true)
    private val hlavniTlacitko = Tlacitko("Spustit", hlavni = true) { spustitNeboZastavit() }
    private val postup = JProgressBar().apply {
        isIndeterminate = true; isVisible = false; foreground = B.ACCENT; background = B.CARD2
        border = null; maximumSize = Dimension(360, 6); preferredSize = Dimension(360, 6); alignmentX = Component.LEFT_ALIGNMENT
    }
    private val rOkno = StavRadek()
    private val rPreklad = StavRadek()
    private val rVystup = StavRadek()
    private val rCteni = StavRadek()
    private val rTitulek = StavRadek()
    private val radky = ArrayList<Radek>()

    init {
        pruh.vzhled = nast.vzhled()
        okno.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        okno.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) = konec()
        })
        okno.contentPane.background = B.BG
        okno.layout = BorderLayout()
        obsah.add(svislePosuvne(domu()), "Domů")
        obsah.add(svislePosuvne(titulky()), "Titulky")
        obsah.add(svislePosuvne(nastaveni()), "Nastavení")
        okno.add(menu(), BorderLayout.WEST)
        okno.add(obsah, BorderLayout.CENTER)
        okno.size = Dimension(1000, 680)
        okno.minimumSize = Dimension(760, 520)
        okno.setLocationRelativeTo(null)
        obnovDomu()
        Timer(1000) { hlidejOkno() }.start()
        // jen pro snímky při ladění: -Dplaylingo.stranka=Titulky, -Dplaylingo.autostart=1 (jako Spustit)
        System.getProperty("playlingo.stranka")?.let { prepni(it) }
        if (System.getProperty("playlingo.autostart") == "1" && Nastroje.pripraveno(nast.jazyk)) spustit()
        okno.isVisible = true
        hlavniTlacitko.requestFocusInWindow()
    }

    private fun svislePosuvne(c: JComponent) = JScrollPane(c).apply {
        border = null
        viewport.background = B.BG
        verticalScrollBar.unitIncrement = 24
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }

    // zbylé místo dole, ať se řádky nerozjedou po výšce okna
    private fun stranka(vararg deti: Component) = sloupec(*deti, Box.createVerticalGlue()).apply {
        isOpaque = true; background = B.BG; border = EmptyBorder(28, 32, 28, 32)
    }

    // ---- menu vlevo ----

    private fun menu(): JComponent {
        val panel = object : JPanel() {
            override fun paintComponent(g: Graphics) { g.color = B.SIDEBAR; g.fillRect(0, 0, width, height) }
        }
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = EmptyBorder(24, 14, 20, 14)
        panel.preferredSize = Dimension(220, 0)
        panel.add(popisek("PlayLingo", 26, B.ACCENT, true).vlevo().apply { border = EmptyBorder(0, 12, 26, 0) })
        listOf("Domů", "Titulky").forEach { panel.add(polozkaMenu(it, it)) }
        panel.add(Box.createVerticalGlue())
        panel.add(polozkaMenu("⚙  Nastavení", "Nastavení"))
        return panel
    }

    private fun polozkaMenu(text: String, cil: String): JComponent {
        val p = object : JPanel(BorderLayout()) {
            var nad = false
            override fun paintComponent(g: Graphics) {
                val vybrana = stranka == cil
                if (!vybrana && !nad && !isFocusOwner) return
                val g2 = g.hladce()
                g2.color = if (vybrana || isFocusOwner) B.FOCUS else B.CARD
                g2.fillRoundRect(0, 0, width, height, 18, 18)
                if (vybrana) { g2.color = B.ACCENT; g2.fillRoundRect(0, 8, 5, height - 16, 4, 4) }
                if (isFocusOwner) {
                    g2.color = B.ACCENT; g2.stroke = BasicStroke(3f); g2.drawRoundRect(1, 1, width - 3, height - 3, 18, 18)
                }
            }
            override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
        }
        p.isOpaque = false
        p.isFocusable = true
        p.alignmentX = Component.LEFT_ALIGNMENT
        p.border = EmptyBorder(12, 18, 12, 12)
        p.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        // ozubené kolo v písmu Segoe UI není
        p.add(popisek(text, 18, B.TEXT, true).apply { if ('⚙' in text) font = Font("Segoe UI Symbol", Font.BOLD, 18) })
        val otevri = { prepni(cil) }
        p.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = otevri()
            override fun mouseEntered(e: MouseEvent) { p.nad = true; p.repaint() }
            override fun mouseExited(e: MouseEvent) { p.nad = false; p.repaint() }
        })
        p.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) { if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) otevri() }
        })
        p.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = p.repaint()
            override fun focusLost(e: FocusEvent) = p.repaint()
        })
        polozkyMenu += cil to p
        return sloupec(p, Box.createVerticalStrut(6)).vlevo()
    }

    private fun prepni(cil: String) {
        stranka = cil
        (obsah.layout as CardLayout).show(obsah, cil)
        polozkyMenu.forEach { it.second.repaint() }
        radky.forEach { it.obnov() }
    }

    // ---- Domů ----

    private fun domu(): JComponent {
        val karta = Karta().apply { listOf(rOkno, rPreklad, rVystup, rCteni, rTitulek).forEach { add(it) } }
        return stranka(
            velkyStav.vlevo(),
            Box.createVerticalStrut(10),
            postup,
            Box.createVerticalStrut(18),
            object : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)) {
                override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
            }.apply { isOpaque = false; add(hlavniTlacitko) }.vlevo(),
            Box.createVerticalStrut(26),
            karta,
        )
    }

    private fun jazyk() = Lang.of(nast.jazyk)

    private fun obnovDomu() {
        val s = stav
        val bezi = vlakno?.isAlive == true
        val pripravuje = priprava?.isAlive == true
        val pripraveno = Nastroje.pripraveno(nast.jazyk)
        velkyStav.text = when {
            pripravuje -> "Příprava…"
            !pripraveno -> "Chybí příprava"
            !bezi -> if (s.faze == Stav.Faze.CHYBA) "Chyba" else "Připraveno"
            s.faze == Stav.Faze.NACITANI -> "Načítání…"
            s.faze == Stav.Faze.BEZ_OKNA -> "Čeká na okno hry"
            else -> "Běží"
        }
        velkyStav.foreground = when (velkyStav.text) {
            "Běží" -> B.OK
            "Chybí příprava", "Čeká na okno hry", "Chyba" -> B.WARN
            else -> B.TEXT
        }
        postup.isVisible = pripravuje || (bezi && s.faze == Stav.Faze.NACITANI)
        hlavniTlacitko.text = if (bezi) "Zastavit" else "Spustit"
        hlavniTlacitko.isEnabled = !pripravuje && (bezi || pripraveno)

        // okno hry
        if (nast.ukazka) rOkno.nastav(B.MUTED, "Zdroj · ukázková hra")
        else if (bezi && s.faze == Stav.Faze.BEZI || oknoNalezeno) rOkno.nastav(B.OK, "Okno hry · ${nast.okno} · nalezeno")
        else rOkno.nastav(B.WARN, "Okno hry · ${nast.okno} · nenalezeno", "Vybrat okno") { vyberOkno(rOkno) }

        // překlad
        when {
            pripravuje -> rPreklad.nastav(B.MUTED, "Překlad · ${jazyk().label} · stahování")
            pripraveno -> rPreklad.nastav(B.OK, "Překlad · ${jazyk().label} · offline")
            pripravaChyba -> rPreklad.nastav(B.WARN, "Překlad · ${jazyk().label} · chyba přípravy", "Zkusit znovu") { pripravit() }
            else -> rPreklad.nastav(B.WARN, "Překlad · ${jazyk().label} · chybí", "Připravit") { pripravit() }
        }

        // výstup
        val nazevVystupu = Nastaveni.VYSTUPY.firstOrNull { it.first == nast.vystup }?.second ?: "Pruh na PC"
        when {
            nast.vystup == "pc" -> rVystup.nastav(B.OK, "Výstup · $nazevVystupu")
            !bezi -> rVystup.nastav(B.MUTED, "Výstup · $nazevVystupu")
            s.televize > 0 -> rVystup.nastav(B.OK, "TV · připojeno")
            else -> rVystup.nastav(B.WARN, "TV · nepřipojeno")
        }

        // čtení a poslední titulek
        if (bezi && s.ms > 0) rCteni.nastav(B.MUTED, "Čtení · ${s.ms} ms") else rCteni.nastav(B.MUTED, "")
        when {
            s.faze == Stav.Faze.CHYBA && s.chyba.isNotEmpty() -> rTitulek.nastav(B.WARN, "Chyba · ${s.chyba}")
            s.preklad.isNotEmpty() -> rTitulek.nastav(B.MUTED, "Titulek · ${s.preklad}")
            else -> rTitulek.nastav(B.MUTED, "")
        }
        okno.contentPane.revalidate()
    }

    /** Je okno hry otevřené? Hlídá se i před Spustit, ať Domů hned ukáže, jestli ho aplikace vidí. */
    private fun hlidejOkno() {
        oknoNalezeno = !nast.ukazka && hra.oblast() != null
        obnovDomu()
    }

    private fun spustitNeboZastavit() {
        if (vlakno?.isAlive == true) zastavit() else spustit()
    }

    private fun spustit() {
        if (vlakno?.isAlive == true) return
        stav = Stav(Stav.Faze.NACITANI)
        val c = Ctecka(
            jazyk = nast.jazyk,
            okno = if (nast.ukazka) "" else nast.okno,
            vystup = nast.vystup,
            pruh = if (nast.vystup == "tv") null else pruh,
            log = ::zapis,
            zmena = { s -> stav = s; SwingUtilities.invokeLater { obnovDomu() } },
            chyby = Nastroje.chyby,
        )
        ctecka = c
        vlakno = thread(name = "ctecka", isDaemon = true) {
            c.bez()
            SwingUtilities.invokeLater { obnovDomu() }
        }
        obnovDomu()
    }

    private fun zastavit() {
        ctecka?.zastav()
        thread(isDaemon = true) {
            vlakno?.join(3000)
            SwingUtilities.invokeLater { obnovDomu() }
        }
    }

    /** Po změně okna, jazyka nebo výstupu se běžící čtení spustí znovu s novým nastavením. */
    private fun restart() {
        if (vlakno?.isAlive != true) { obnovDomu(); return }
        ctecka?.zastav()
        thread(isDaemon = true) {
            vlakno?.join(5000)
            SwingUtilities.invokeLater { spustit() }
        }
    }

    private fun zapis(m: String) {
        val radek = "${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))} $m\n"
        SwingUtilities.invokeLater {
            zaznam.append(radek)
            val navic = zaznam.lineCount - 400
            if (navic > 0) zaznam.replaceRange("", 0, zaznam.getLineStartOffset(navic))
        }
    }

    // ---- příprava (stažení knihoven a překladového modelu) ----

    private fun pripravit() {
        if (priprava?.isAlive == true) return
        pripravaChyba = false
        zapis("Příprava pro jazyk ${nast.jazyk}…")
        val p = runCatching {
            ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", Nastroje.priprava.path,
                "-Jazyk", nast.jazyk, "-BezSestaveni",
            ).redirectErrorStream(true).start()
        }.getOrElse { zapis("Příprava se nespustila: ${it.message}"); pripravaChyba = true; obnovDomu(); return }
        priprava = p
        obnovDomu()
        thread(isDaemon = true) {
            p.inputStream.bufferedReader().forEachLine { if (it.isNotBlank()) zapis(it) }
            val kod = p.waitFor()
            pripravaChyba = kod != 0 || !Nastroje.pripraveno(nast.jazyk)
            zapis(if (pripravaChyba) "Příprava skončila chybou ($kod)." else "Příprava hotová.")
            SwingUtilities.invokeLater { obnovDomu() }
        }
    }

    // ---- Titulky ----

    private fun titulky(): JComponent {
        fun zmenaVzhledu() { pruh.vzhled = nast.vzhled(); radky.forEach { it.obnov() } }
        val karta = Karta()
        karta.add(volba("Velikost písma", Nastaveni.VELIKOSTI.map { "$it %" },
            { Nastaveni.VELIKOSTI.indexOf(nast.velikost).coerceAtLeast(0) }) { nast.velikost = Nastaveni.VELIKOSTI[it]; zmenaVzhledu() })
        karta.add(volba("Barva textu", Nastaveni.BARVY.map { it.first }, { nast.barva }) { nast.barva = it; zmenaVzhledu() })
        karta.add(volba("Tučné písmo", listOf("Ano", "Ne"), { if (nast.tucne) 0 else 1 }) { nast.tucne = it == 0; zmenaVzhledu() })
        karta.add(volba("Pozadí pruhu", Nastaveni.POZADI.map { "$it %" },
            { Nastaveni.POZADI.indexOf(nast.pozadi).coerceAtLeast(0) }) { nast.pozadi = Nastaveni.POZADI[it]; zmenaVzhledu() })
        karta.add(volba("Poloha", Poloha.values().map { it.nazev }, { nast.poloha.ordinal }) { nast.poloha = Poloha.values()[it]; zmenaVzhledu() })
        karta.add(Radek("Zkušební pruh", { "" }) { pruh.zkouska() }.also { radky += it })
        return stranka(popisek("Pruh s překladem", 15, B.MUTED, true).vlevo(), Box.createVerticalStrut(8), karta)
    }

    private fun volba(nazev: String, moznosti: List<String>, aktualni: () -> Int, nastav: (Int) -> Unit): Radek =
        Radek(nazev, { moznosti.getOrElse(aktualni()) { "" } }) { r ->
            vyber(r, moznosti, aktualni()) { nastav(it); r.obnov(); obnovDomu() }
        }.also { radky += it }

    // ---- Nastavení ----

    private fun nastaveni(): JComponent {
        val pole = JTextField(nast.okno).apply {
            font = pismo(16); background = B.CARD2; foreground = B.TEXT; caretColor = B.TEXT
            border = EmptyBorder(9, 12, 9, 12); columns = 18
            maximumSize = Dimension(320, preferredSize.height)
        }
        // název okna se uloží chvilku po psaní (ne po každém písmenu restart čtení)
        val ulozit = Timer(900) {
            val t = pole.text.trim()
            if (t.isNotEmpty() && t != nast.okno) { nast.okno = t; hra = OknoHry(t); restart() }
        }.apply { isRepeats = false }
        pole.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = ulozit.restart()
            override fun removeUpdate(e: DocumentEvent) = ulozit.restart()
            override fun changedUpdate(e: DocumentEvent) = ulozit.restart()
        })
        poleOkna = pole
        val oknoRadek = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            border = EmptyBorder(8, 12, 8, 12)
            add(popisek("Okno hry", 16)); add(Box.createHorizontalGlue()); add(pole); add(Box.createHorizontalStrut(10))
            add(Tlacitko("Vybrat") { vyberOkno(this) })
        }
        val zdroj = Karta().apply {
            add(oknoRadek)
            add(volba("Ukázková hra", listOf("Ne", "Ano"), { if (nast.ukazka) 1 else 0 }) { nast.ukazka = it == 1; restart() })
        }
        val preklad = Karta().apply {
            add(volba("Jazyk překladu", Lang.targets.map { it.label }, { Lang.targets.indexOf(jazyk()) }) {
                nast.jazyk = Lang.targets[it].code; pripravaChyba = false; restart()
            })
            add(volba("Výstup", Nastaveni.VYSTUPY.map { it.second },
                { Nastaveni.VYSTUPY.indexOfFirst { v -> v.first == nast.vystup }.coerceAtLeast(0) }) {
                nast.vystup = Nastaveni.VYSTUPY[it].first; restart()
            })
            add(Radek("Příprava", { if (Nastroje.pripraveno(nast.jazyk)) "hotová" else "chybí" }) { pripravit() }.also { radky += it })
        }
        val zaznamOtevreny = booleanArrayOf(false)
        val zaznamPosuv = JScrollPane(zaznam).apply {
            border = null; preferredSize = Dimension(600, 260); maximumSize = Dimension(Int.MAX_VALUE, 260)
            alignmentX = Component.LEFT_ALIGNMENT; isVisible = false
        }
        val zaznamKarta = Karta()
        zaznamKarta.add(Radek("Záznam", { if (zaznamOtevreny[0]) "▲" else "▼" }) { r ->
            zaznamOtevreny[0] = !zaznamOtevreny[0]; zaznamPosuv.isVisible = zaznamOtevreny[0]; r.obnov(); zaznamKarta.revalidate()
        })
        zaznamKarta.add(zaznamPosuv)
        return stranka(
            popisek("Hra", 15, B.MUTED, true).vlevo(), Box.createVerticalStrut(8), zdroj,
            Box.createVerticalStrut(20),
            popisek("Překlad", 15, B.MUTED, true).vlevo(), Box.createVerticalStrut(8), preklad,
            Box.createVerticalStrut(20),
            popisek("Ladění", 15, B.MUTED, true).vlevo(), Box.createVerticalStrut(8), zaznamKarta,
        )
    }

    private var poleOkna: JTextField? = null

    /** Nabídka otevřených oken; z titulku se vezme krátké slovo, které vydrží i změnu titulku. */
    private fun vyberOkno(kde: Component) {
        val okna = otevrenaOkna().filter { it != okno.title }.take(20)
        if (okna.isEmpty()) return
        vyber(kde, okna, -1) { i ->
            val t = okna[i]
            val kratce = listOf("chiaki", "PS Remote Play", "Xbox").firstOrNull { t.contains(it, ignoreCase = true) } ?: t
            nast.okno = kratce
            nast.ukazka = false
            hra = OknoHry(kratce)
            poleOkna?.text = kratce
            radky.forEach { it.obnov() }
            restart()
        }
    }

    private fun konec() {
        ctecka?.zastav()
        priprava?.destroy()
        okno.isVisible = false
        thread {
            vlakno?.join(3000)
            System.exit(0)
        }
    }
}

fun main() {
    // Okno aplikace se zvětšuje podle Windows (na handheldu bývá 150–200 %); pruh si polohu okna
    // hry ze skutečných bodů přepočítá sám (Pruh.obnov).
    System.setProperty("awt.useSystemAAFontSettings", "on")
    SwingUtilities.invokeLater {
        UIManager.put("PopupMenu.background", B.CARD2)
        UIManager.put("MenuItem.selectionBackground", B.FOCUS)
        UIManager.put("MenuItem.selectionForeground", B.ACCENT_FOCUS)
        UIManager.put("ScrollBar.thumb", B.CARD2)
        UIManager.put("ScrollBar.track", B.BG)
        UIManager.put("ScrollBar.width", 12)
        UIManager.put("ProgressBar.foreground", B.ACCENT)
        UIManager.put("ProgressBar.background", B.CARD2)
        UIManager.put("Button.disabledText", B.MUTED)
        Aplikace()
    }
}
