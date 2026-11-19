package cz.playlingo

import android.app.Application
import cz.playlingo.core.TvLog
import java.io.File

/**
 * Aplikace jako celek – vzniká jako první, ještě před obrazovkou, službou překladu i Usnadněním.
 * Proto se tu hned zapíná zápis pádů ([CrashLog]), ať se zachytí pád v kterékoli části.
 * Taky sem patří soubor se záznamem z televize ([TvLog]) a obnovení záznamu v síti
 * ([NetLogShare]), když ho uživatel zapnul a ještě nevypršel.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        TvLog.file = File(filesDir, TvLog.FILE)
        try { NetLogShare.resume(this) } catch (_: Throwable) {}
    }
}
