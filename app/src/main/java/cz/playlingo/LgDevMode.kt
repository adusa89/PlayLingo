package cz.playlingo

import android.content.Context
import cz.playlingo.core.LgDevTime
import cz.playlingo.core.LgInstall
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/**
 * Kolik dní zbývá vývojářskému režimu televize LG. Klíč relace se přečte z televize při spojení
 * instalace ([LgInstaller]); tady se jen zeptá služby LG (HTTPS, bez přesměrování), nejvýš jednou
 * za hodinu (ručně kdykoli). Nic se neprodlužuje – to dělá uživatel v televizi v aplikaci
 * Developer Mode. Klíč relace ani adresa s ním se nikam nepíšou.
 *
 * Podle LgDevMode.kt z projektu PlayCaption (AGPL-3.0).
 */
object LgDevMode {
    private val busy = AtomicBoolean(false)
    @Volatile var failed = false
        private set

    /** Poslední známý stav pro televizi, kam se naposledy instalovalo (null = zatím nevíme). */
    fun known(context: Context): LgDevTime? {
        val v = LgVault(context)
        val ip = v.lastIp.ifEmpty { return null }
        return v.devTime(ip)?.takeIf { it.fresh(System.currentTimeMillis()) }
    }

    fun hasSession(context: Context): Boolean = LgVault(context).let { v -> v.lastIp.isNotEmpty() && v.token(v.lastIp) != null }

    /** Na pozadí se zeptá služby LG; [done] se zavolá (z vlákna na pozadí), jen když se opravdu ptala. */
    fun refresh(context: Context, manual: Boolean, done: () -> Unit) {
        val app = context.applicationContext
        val vault = LgVault(app)
        val ip = vault.lastIp.ifEmpty { return }
        val now = System.currentTimeMillis()
        if (!LgDevTime.shouldRefresh(vault.lastTry(ip), now, manual)) return
        val token = vault.token(ip) ?: return
        if (!busy.compareAndSet(false, true)) return
        vault.setLastTry(ip, now)
        Thread({
            try {
                val t = LgDevTime.parse(fetch(token), System.currentTimeMillis())
                if (vault.token(ip) == token) vault.saveDevTime(ip, t)
                failed = false
            } catch (_: Exception) {
                failed = true
            } finally {
                busy.set(false)
                done()
            }
        }, "tp-lg-devmode").apply { isDaemon = true }.start()
    }

    private fun fetch(token: String): String {
        require(token.matches(Regex("[A-Za-z0-9]{8,256}")))
        val conn = URL(LgInstall.CHECK_URL + token).openConnection() as HttpsURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            require(conn.responseCode == 200)
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(2048)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    require(out.size() + n <= 16384)
                    out.write(buf, 0, n)
                }
            }
            return out.toString("UTF-8")
        } finally { conn.disconnect() }
    }
}
