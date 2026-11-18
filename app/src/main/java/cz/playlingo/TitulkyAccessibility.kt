package cz.playlingo

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Služba Usnadnění. Umožní zobrazit titulky přes jiné aplikace i na televizích, které skrývají
 * povolení „Zobrazení přes jiné aplikace“ (typicky Google TV / Chromecast) – bez počítače a ADB.
 *
 * Nečte obsah obrazovky ani nic neodesílá. Jen si pamatuje, která aplikace je v popředí
 * (údaj pro ladicí výpis).
 */
class TitulkyAccessibility : AccessibilityService() {

    companion object {
        @Volatile var instance: TitulkyAccessibility? = null
            private set

        /** Balíček aplikace, která je právě v popředí (null = nevíme). */
        @Volatile var foregroundPackage: String? = null
            private set

        /** Okna, která nejsou "přepnutí aplikace" (klávesnice, systémová lišta…). */
        private val IGNORED = listOf("com.android.systemui", "inputmethod", "android.ime", "com.google.android.katniss")
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (IGNORED.any { pkg.contains(it) }) return
        foregroundPackage = pkg
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
