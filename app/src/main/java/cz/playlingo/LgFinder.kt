package cz.playlingo

import android.content.Context
import android.net.wifi.WifiManager
import cz.playlingo.core.LgSetup
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Hledání televize LG v domácí síti a zkouška, jestli běží vývojářský režim a Key Server
 * (pro pomocníka v [LgInstallActivity], rozhodování v core/LgSetup.kt). Jen čte – nic neposílá
 * kromě dotazu „je tu televize LG?“ a pokusu o spojení na pár portů. Volat mimo hlavní vlákno.
 */
object LgFinder {
    /** Televize LG, které se ozvaly na dotaz SSDP (adresy domácí sítě). */
    fun ssdp(ctx: Context, timeoutMs: Int = 2500): List<String> {
        val found = LinkedHashSet<String>()
        val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = try { wifi?.createMulticastLock("playlingo-lg")?.apply { setReferenceCounted(false); acquire() } } catch (_: Throwable) { null }
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 400
                val msg = LgSetup.searchMessage().toByteArray(Charsets.US_ASCII)
                val group = InetAddress.getByName("239.255.255.250")
                repeat(2) { s.send(DatagramPacket(msg, msg.size, group, 1900)) }
                val end = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < end) {
                    val p = DatagramPacket(buf, buf.size)
                    try { s.receive(p) } catch (_: java.net.SocketTimeoutException) { continue }
                    val text = String(p.data, 0, p.length, Charsets.UTF_8)
                    LgSetup.parseResponse(text, p.address.hostAddress ?: "")?.let { found.add(it) }
                }
            }
        } catch (_: Throwable) {
        } finally { try { lock?.release() } catch (_: Throwable) {} }
        return found.toList()
    }

    /** Záloha, když SSDP nic nenajde: projít adresy domácí sítě (/24) a zkusit port webOS nebo SSH. */
    fun scan(ctx: Context): List<String> {
        val me = NetInfo.homeAddress(ctx) ?: return emptyList()
        val prefix = me.substringBeforeLast('.')
        val pool = Executors.newFixedThreadPool(32)
        return try {
            val jobs = (1..254).map { "$prefix.$it" }.filter { it != me }.map { ip ->
                pool.submit(Callable { if (open(ip, LgSetup.WEBOS_PORT, 350) || open(ip, LgSetup.SSH_PORT, 350)) ip else null })
            }
            jobs.mapNotNull { runCatching { it.get(3, TimeUnit.SECONDS) }.getOrNull() }
        } finally { pool.shutdownNow() }
    }

    /** Dá se na [port] navázat spojení? (Jen otevře a hned zavře.) */
    fun open(ip: String, port: Int, timeoutMs: Int = 800): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs); true }
    } catch (_: Throwable) { false }
}
