package cz.playlingo.core

/**
 * Stránky s pomocí krok za krokem (složka `pomoc` v repozitáři na GitHubu). Vedou na ně čtverečky
 * a tlačítka v aplikaci. Na nich je hlavní odkaz na registraci / návod LG, Sony, Samsungu… a pod
 * ním záložní odkazy a co hledat. Když výrobce stránku přesune, opraví se odkaz ve složce `pomoc`
 * a aplikace se měnit nemusí. Fungují v jakémkoli telefonu, i v iPhonu (jen webová stránka).
 */
object HelpLinks {
    /**
     * GitHub Pages ze složky `pomoc` (od 0.97.30, `.github/workflows/pomoc.yml`) – obyčejný web bez přihlášení.
     * Dřív github.com/…/blob/…: telefon s aplikací GitHub ji otevřel v ní a ta chtěla přihlášení.
     */
    const val BASE = "https://adusa89.github.io/PlayLingo/"

    val LG_ACCOUNT = page("lg", "ucet")
    val PS5_PREP = page("ps5", "ucet")
    val ANDROID_TV = page("android-tv")
    val PHONE = page("telefon")
    val SAMSUNG = page("samsung")
    val XBOX = page("xbox")

    fun page(name: String, anchor: String? = null) = BASE + name + ".html" + (anchor?.let { "#$it" } ?: "")
}
