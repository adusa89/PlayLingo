package cz.playlingo

import android.app.Application

/**
 * Aplikace jako celek – vzniká jako první, ještě před obrazovkou, službou překladu i Usnadněním.
 * Proto se tu hned zapíná zápis pádů ([CrashLog]), ať se zachytí pád v kterékoli části.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
