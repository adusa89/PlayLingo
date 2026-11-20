package cz.playlingo.core

/**
 * Stránky s pomocí krok za krokem (složka `pomoc` v repozitáři na GitHubu). Vedou na ně čtverečky
 * a tlačítka v aplikaci. Na nich je hlavní odkaz na registraci / návod LG, Sony, Samsungu… a pod
 * ním záložní odkazy a co hledat. Když výrobce stránku přesune, opraví se odkaz ve složce `pomoc`
 * a aplikace se měnit nemusí. Fungují v jakémkoli telefonu, i v iPhonu (jen webová stránka).
 */
object HelpLinks {
    const val BASE = "https://github.com/adusa89/PlayLingo/blob/main/pomoc/"

    val LG_ACCOUNT = page("lg", "ucet")
    val PS5_PREP = page("ps5", "ucet")
    val ANDROID_TV = page("android-tv")
    val PHONE = page("telefon")
    val SAMSUNG = page("samsung")
    val XBOX = page("xbox")

    fun page(name: String, anchor: String? = null) = BASE + name + ".md" + (anchor?.let { "#$it" } ?: "")
}
