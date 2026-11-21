package cz.playlingo.core

/**
 * Texty nastavení Android TV z telefonu (návrh `navrhy/nastaveni-z-telefonu.md`, [RemoteSetup]):
 * řádek v ⚙ a čtvereček v TV, stránka z TV pro telefon. REMOTE_S = čeština a slovenština, REMOTE_X = ostatní.
 */
internal val REMOTE_S: Map<String, Pair<String, String>> = mapOf(
    "rs_row" to ("Nastavit z telefonu" to "Nastaviť z telefónu"),
    "rs_qr_text" to ("Naskenuj čtvereček telefonem ve stejné Wi-Fi. V PlayLingu v telefonu (Android) nastavíš celou aplikaci, každá změna se hned projeví v TV." to
        "Naskenuj štvorček telefónom v rovnakej Wi-Fi. V PlayLingu v telefóne (Android) nastavíš celú aplikáciu, každá zmena sa hneď prejaví v TV."),
    "rs_nolan" to ("Stránku pro telefon se nepodařilo vystavit. Je TV v domácí síti?" to "Stránku pre telefón sa nepodarilo vystaviť. Je TV v domácej sieti?"),
    "rs_page_title" to ("PlayLingo · nastavení TV" to "PlayLingo · nastavenie TV"),
    "rs_page_app" to ("Otevřít v aplikaci PlayLingo" to "Otvoriť v aplikácii PlayLingo"),
    "rs_page_noapp" to ("Bez aplikace (iPhone): webové nastavení připravujeme. Zatím nastav TV ovladačem." to
        "Bez aplikácie (iPhone): webové nastavenie pripravujeme. Zatiaľ nastav TV ovládačom."),
    "rs_done" to ("Nastaveno z telefonu" to "Nastavené z telefónu"),
)

internal val REMOTE_X: Map<Lang, Map<String, String>> = mapOf(
    Lang.EN to mapOf(
        "rs_row" to "Set up from phone",
        "rs_qr_text" to "Scan the QR code with a phone on the same Wi-Fi. In PlayLingo on the phone (Android) you set up the whole app; every change shows on the TV right away.",
        "rs_nolan" to "The page for the phone could not be opened. Is the TV on the home network?",
        "rs_page_title" to "PlayLingo · TV settings",
        "rs_page_app" to "Open in the PlayLingo app",
        "rs_page_noapp" to "Without the app (iPhone): web settings are coming. For now, set up the TV with the remote.",
        "rs_done" to "Set from phone",
    ),
    Lang.PL to mapOf(
        "rs_row" to "Ustaw z telefonu",
        "rs_qr_text" to "Zeskanuj kod telefonem w tej samej sieci Wi-Fi. W PlayLingo w telefonie (Android) ustawisz całą aplikację, każda zmiana od razu pojawi się na TV.",
        "rs_nolan" to "Nie udało się udostępnić strony dla telefonu. Czy TV jest w sieci domowej?",
        "rs_page_title" to "PlayLingo · ustawienia TV",
        "rs_page_app" to "Otwórz w aplikacji PlayLingo",
        "rs_page_noapp" to "Bez aplikacji (iPhone): ustawienia przez stronę przygotowujemy. Na razie ustaw TV pilotem.",
        "rs_done" to "Ustawione z telefonu",
    ),
    Lang.DE to mapOf(
        "rs_row" to "Vom Handy einrichten",
        "rs_qr_text" to "Scanne den Code mit einem Handy im selben WLAN. In PlayLingo auf dem Handy (Android) richtest du die ganze App ein; jede Änderung erscheint sofort auf dem TV.",
        "rs_nolan" to "Die Seite für das Handy konnte nicht geöffnet werden. Ist der TV im Heimnetz?",
        "rs_page_title" to "PlayLingo · TV-Einstellungen",
        "rs_page_app" to "In der PlayLingo-App öffnen",
        "rs_page_noapp" to "Ohne App (iPhone): Web-Einstellungen sind in Arbeit. Richte den TV vorerst mit der Fernbedienung ein.",
        "rs_done" to "Vom Handy eingestellt",
    ),
    Lang.HU to mapOf(
        "rs_row" to "Beállítás telefonról",
        "rs_qr_text" to "Olvasd be a kódot egy ugyanazon a Wi-Fi-n lévő telefonnal. A telefonos PlayLingóban (Android) az egész alkalmazást beállíthatod, minden változás azonnal megjelenik a TV-n.",
        "rs_nolan" to "Nem sikerült megnyitni az oldalt a telefonnak. A TV az otthoni hálózaton van?",
        "rs_page_title" to "PlayLingo · TV-beállítások",
        "rs_page_app" to "Megnyitás a PlayLingo alkalmazásban",
        "rs_page_noapp" to "Alkalmazás nélkül (iPhone): a webes beállítás készül. Addig állítsd be a TV-t a távirányítóval.",
        "rs_done" to "Beállítva telefonról",
    ),
    Lang.UK to mapOf(
        "rs_row" to "Налаштувати з телефона",
        "rs_qr_text" to "Відскануй код телефоном у тій самій мережі Wi-Fi. У PlayLingo на телефоні (Android) налаштуєш увесь застосунок, кожна зміна одразу з’явиться на TV.",
        "rs_nolan" to "Не вдалося відкрити сторінку для телефона. TV у домашній мережі?",
        "rs_page_title" to "PlayLingo · налаштування TV",
        "rs_page_app" to "Відкрити в застосунку PlayLingo",
        "rs_page_noapp" to "Без застосунку (iPhone): веб-налаштування готуємо. Поки що налаштуй TV пультом.",
        "rs_done" to "Налаштовано з телефона",
    ),
    Lang.ES to mapOf(
        "rs_row" to "Configurar desde el móvil",
        "rs_qr_text" to "Escanea el código con un móvil en la misma red Wi-Fi. En PlayLingo en el móvil (Android) configuras toda la app; cada cambio aparece al momento en la TV.",
        "rs_nolan" to "No se pudo abrir la página para el móvil. ¿Está la TV en la red de casa?",
        "rs_page_title" to "PlayLingo · ajustes de la TV",
        "rs_page_app" to "Abrir en la app PlayLingo",
        "rs_page_noapp" to "Sin la app (iPhone): estamos preparando los ajustes web. Por ahora, configura la TV con el mando.",
        "rs_done" to "Configurado desde el móvil",
    ),
    Lang.FR to mapOf(
        "rs_row" to "Configurer depuis le téléphone",
        "rs_qr_text" to "Scanne le code avec un téléphone sur le même Wi-Fi. Dans PlayLingo sur le téléphone (Android), tu configures toute l'appli ; chaque changement apparaît aussitôt sur la TV.",
        "rs_nolan" to "Impossible d'ouvrir la page pour le téléphone. La TV est-elle sur le réseau de la maison ?",
        "rs_page_title" to "PlayLingo · réglages de la TV",
        "rs_page_app" to "Ouvrir dans l'appli PlayLingo",
        "rs_page_noapp" to "Sans l'appli (iPhone) : les réglages web arrivent bientôt. En attendant, configure la TV avec la télécommande.",
        "rs_done" to "Configuré depuis le téléphone",
    ),
    Lang.IT to mapOf(
        "rs_row" to "Configura dal telefono",
        "rs_qr_text" to "Scansiona il codice con un telefono sulla stessa rete Wi-Fi. In PlayLingo sul telefono (Android) configuri tutta l'app; ogni modifica compare subito sulla TV.",
        "rs_nolan" to "Impossibile aprire la pagina per il telefono. La TV è nella rete di casa?",
        "rs_page_title" to "PlayLingo · impostazioni TV",
        "rs_page_app" to "Apri nell'app PlayLingo",
        "rs_page_noapp" to "Senza l'app (iPhone): le impostazioni web sono in preparazione. Per ora configura la TV con il telecomando.",
        "rs_done" to "Configurato dal telefono",
    ),
)
