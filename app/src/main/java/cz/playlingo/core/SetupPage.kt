package cz.playlingo.core

/**
 * Stránka „Nastavení TV z telefonu“ (od 0.97.63, přání majitele: nejhorší byly účty PlayStation a adresa „redirect“).
 * Vystavuje ji Android TV ([cz.playlingo.RemoteHost], stálý klíč, jeden čtvereček v průvodci i v ⚙ → Obecné) a otevře
 * se v prohlížeči telefonu – iPhonu i Androidu. Všechno, co se jinak píše ovladačem, jde na jedné stránce:
 *  1. 🔵 účet pro PlayLingo – Android: PlayLingo v telefonu (nic se nekopíruje, [PhoneLogin.appIntentLink]),
 *     jinak přihlášení Sony v prohlížeči s obrázkem, kde adresu „redirect“ zkopírovat ([OP_ACCOUNT]),
 *  2. PS5 pod 🔵 účtem – osmimístné číslo se opíše v telefonu a TV páruje sama ([OP_CODE]); dřív se kvůli
 *     němu na TV přepínalo mezi vstupem PS5 a aplikací,
 *  3. DeepL (nepovinné) – klíč ([RemoteSetup.OP_DEEPL]).
 * Stránka se každé 2 s ptá TV na stav ([OP_INFO]) a odškrtává kroky; totéž odškrtává TV.
 * Starý zápis JS (ES5) kvůli starším telefonům. Logika bez Androidu (testy `SetupPageTest`).
 */
object SetupPage {
    const val OP_INFO = "info"        // stav pro stránku (JSON [status])
    const val OP_ACCOUNT = "ucet"     // adresa „redirect“ z přihlášení Sony (pole `url`)
    const val OP_CODE = "kod"         // osmimístné číslo z PS5 (pole `pin`)

    /** Jak je na tom spárování, které TV spustila podle čísla z telefonu. */
    enum class Pair { IDLE, BUSY, OK, FAIL }

    data class Status(
        /** Jméno 🔵 účtu pro PlayLingo; prázdné = účet ověřený bez jména (starší), null = žádný. */
        val account: String?,
        val pair: Pair,
        /** Jméno spárované PS5 (PS5-151), když je spárovaná. */
        val console: String?,
        val deepl: Boolean,
    )

    /** Stav jako JSON pro stránku. */
    fun status(s: Status): String =
        "{\"acc\":" + (s.account?.let { RemoteSetup.json(it) } ?: "null") +
            ",\"pair\":\"" + s.pair.name.lowercase() + "\"" +
            ",\"ps5\":" + (s.console?.let { RemoteSetup.json(it) } ?: "null") +
            ",\"deepl\":" + s.deepl + "}"

    /** Je to osmimístné číslo z PS5 (mezery a pomlčky se ignorují)? */
    fun pin(input: String?): Int? = Ps5.parsePin((input ?: "").replace(" ", "").replace("-", ""))

    /**
     * Stránka. [target] = adresa TV, jak ji telefon vidí (pro tlačítko do PlayLinga v telefonu; null = bez něj),
     * [apkUrl] PlayLingo do telefonu, [loginUrl] přihlášení Sony, [signupUrl]/[keysUrl] DeepL.
     */
    fun page(lang: Lang, tvName: String, target: PhoneLogin.Target?, apkUrl: String, loginUrl: String,
             signupUrl: String, keysUrl: String, shortcutUrl: String? = null): String {
        fun t(k: String) = I18n.t(lang, k)
        fun e(k: String) = PhoneLogin.esc(t(k))
        fun para(k: String) = PhoneLogin.esc(t(k)).replace("\n", "<br>")
        // texty pro skript (se zástupným %s pro jméno účtu / konzole)
        val texts = listOf("sp_acc_ok", "sp_acc_none", "sp_ps5_h", "sp_ps5_how", "sp_pair_btn", "sp_pair_busy", "sp_pair_ok",
            "sp_pair_fail", "sp_need_acc", "sp_bad_url", "sp_bad_pin", "sp_deepl_ok", "sp_deepl_bad", "sp_sent")
            .joinToString(",", "{", "}") { RemoteSetup.json(it) + ":" + RemoteSetup.json(t(it)) }
        val sb = StringBuilder()
        sb.append("<!doctype html><html lang=\"").append(lang.code).append("\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>PlayLingo</title><style>")
            .append("body{margin:0;background:#0e1014;color:#f2f2f2;font:17px/1.45 system-ui,sans-serif}")
            .append("main{max-width:34em;margin:0 auto;padding:18px 16px 44px}h1{font-size:23px;margin:6px 0 2px}")
            .append("h2{font-size:18px;margin:0 0 6px}p{margin:8px 0}.muted{color:#9aa4ae;font-size:15px}")
            .append(".card{background:#1b1f27;border-radius:14px;padding:14px 16px;margin:12px 0}")
            .append(".legend{border-left:4px solid #4a90e2;white-space:pre-line}")
            .append(".st{font-weight:600;margin:4px 0 8px}.ok{color:#4ade80}.wait{color:#9aa4ae}.bad{color:#ef4444}")
            .append("a.btn,button{display:block;width:100%;box-sizing:border-box;text-align:center;background:#f2c14e;color:#1a1405;")
            .append("font:600 18px system-ui,sans-serif;border:0;border-radius:12px;padding:14px;margin:10px 0 4px;text-decoration:none}")
            .append("a.btn.plain,button.plain{background:#2a303b;color:#f2f2f2}")
            .append("input{width:100%;box-sizing:border-box;font-size:17px;padding:13px;border-radius:12px;border:2px solid #3a4250;")
            .append("background:#0e1014;color:#f2f2f2;margin-top:6px}summary{cursor:pointer;color:#f2c14e;margin:10px 0}")
            .append("#done{display:none;border-left:4px solid #4ade80}#off{display:none}")
            .append("</style></head><body><main>")
            .append("<h1>").append(e("rs_page_title")).append("</h1>")
            .append("<p class=\"muted\">").append(PhoneLogin.esc(tvName)).append("</p>")
            .append("<p id=\"off\" class=\"card bad\">").append(e("sp_off")).append("</p>")
            .append("<div class=\"card legend\">").append(e("acc_legend")).append("</div>")

        // ---- 1. 🔵 účet pro PlayLingo ----
        sb.append("<div class=\"card\" id=\"c1\"><h2>").append(e("sp_1_h")).append("</h2>")
            .append("<p id=\"accst\" class=\"st wait\">").append(e("sp_acc_none")).append("</p><div id=\"acc\">")
        if (target != null) {
            // Android: PlayLingo v telefonu se přihlásí samo (skript tuhle část jinde schová)
            sb.append("<div id=\"app\"><a class=\"btn\" href=\"").append(PhoneLogin.esc(PhoneLogin.appIntentLink(target, lang, apkUrl)))
                .append("\">").append(e("wp_app_btn")).append("</a>")
                .append("<p class=\"muted\">").append(para("wp_app_inst")).append("</p></div>")
        }
        sb.append("<details id=\"web\" open><summary>").append(e("wp_web_h")).append("</summary>")
            .append("<p>").append(para("wp_1")).append("</p>")
            .append("<a id=\"l\" class=\"btn plain\" target=\"_blank\" rel=\"noopener\" href=\"").append(PhoneLogin.esc(loginUrl)).append("\">")
            .append(e("wp_login_btn")).append("</a>")
            .append("<p>").append(para("sp_copy_how")).append("</p>")
            .append(picture(t("sp_copy_word")))
            .append("<p>").append(para("wp_3")).append("</p>")
            .append("<input id=\"u\" autocomplete=\"off\" autocapitalize=\"off\" spellcheck=\"false\" placeholder=\"https://…/redirect?code=…\">")
            .append("<button id=\"ub\">").append(e("wp_send")).append("</button><p id=\"um\" class=\"bad\"></p>")
        // iPhone: zkratka „PlayLingo“ v Sdílet pošle adresu „redirect“ sem sama (stránka na GitHub Pages si zapamatuje TV)
        if (shortcutUrl != null) sb.append("<a id=\"sc\" class=\"btn plain\" data-base=\"").append(PhoneLogin.esc(shortcutUrl))
            .append("\" href=\"").append(PhoneLogin.esc(shortcutUrl)).append("\">").append(e("sp_shortcut")).append("</a>")
        sb.append("</details></div></div>")

        // ---- 2. PS5 pod 🔵 účtem: číslo opsat sem, TV páruje sama ----
        sb.append("<div class=\"card\" id=\"c2\"><h2 id=\"ps5h\">").append(PhoneLogin.esc(t("sp_ps5_h").replace("%s", "…"))).append("</h2>")
            .append("<p id=\"pairst\" class=\"st wait\"></p>")
            .append("<p id=\"ps5how\">").append(PhoneLogin.esc(t("sp_ps5_how").replace("%s", "…")).replace("\n", "<br>")).append("</p>")
            .append("<input id=\"pin\" inputmode=\"numeric\" autocomplete=\"off\" maxlength=\"9\" placeholder=\"12345678\">")
            .append("<button id=\"pb\">").append(e("sp_pair_btn")).append("</button><p id=\"pm\" class=\"bad\"></p></div>")

        // ---- 3. DeepL (nepovinné) ----
        sb.append("<div class=\"card\" id=\"c3\"><h2>").append(e("sp_3_h")).append("</h2>")
            .append("<p id=\"dlst\" class=\"st wait\"></p><div id=\"dl\">")
            .append("<a class=\"btn plain\" target=\"_blank\" rel=\"noopener\" href=\"").append(PhoneLogin.esc(signupUrl)).append("\">").append(e("dl_tv_1")).append("</a>")
            .append("<a class=\"btn plain\" target=\"_blank\" rel=\"noopener\" href=\"").append(PhoneLogin.esc(keysUrl)).append("\">").append(e("dl_tv_2")).append("</a>")
            .append("<input id=\"dk\" autocomplete=\"off\" autocapitalize=\"off\" spellcheck=\"false\" placeholder=\"xxxxxxxx-xxxx-…\">")
            .append("<button id=\"db\">").append(e("dl_tv_3")).append("</button><p id=\"dm\" class=\"bad\"></p></div></div>")

        sb.append("<div class=\"card\" id=\"done\"><p class=\"st ok\">").append(e("sp_done")).append("</p></div>")
        sb.append("<script type=\"application/json\" id=\"t\">").append(texts.replace("</", "<\\/")).append("</script>")
        sb.append(SCRIPT).append("</main></body></html>")
        return sb.toString()
    }

    /** Obrázek: adresní řádek se stránkou „redirect“, kam klepnout, a bublina „Kopírovat“. */
    private fun picture(copyWord: String): String =
        "<svg viewBox=\"0 0 320 150\" width=\"100%\" role=\"img\" style=\"max-width:420px;display:block;margin:6px auto\">" +
            "<rect x=\"8\" y=\"8\" width=\"304\" height=\"134\" rx=\"16\" fill=\"#1b1f27\" stroke=\"#3a4250\" stroke-width=\"2\"/>" +
            "<rect x=\"22\" y=\"22\" width=\"276\" height=\"32\" rx=\"9\" fill=\"#2a303b\" stroke=\"#f2c14e\" stroke-width=\"3\"/>" +
            "<text x=\"34\" y=\"43\" fill=\"#f2f2f2\" font-size=\"13\" font-family=\"system-ui,sans-serif\">…playstation.net/…/redirect?code=…</text>" +
            "<circle cx=\"262\" cy=\"38\" r=\"13\" fill=\"#f2c14e\" opacity=\".55\"/>" +
            "<text x=\"262\" y=\"43\" fill=\"#1a1405\" font-size=\"14\" font-weight=\"700\" text-anchor=\"middle\" font-family=\"system-ui,sans-serif\">1</text>" +
            "<rect x=\"170\" y=\"64\" width=\"120\" height=\"34\" rx=\"8\" fill=\"#f2f2f2\"/>" +
            "<path d=\"M222 64 l8 -8 l8 8 z\" fill=\"#f2f2f2\"/>" +
            "<text x=\"230\" y=\"86\" fill=\"#1a1405\" font-size=\"15\" font-weight=\"600\" text-anchor=\"middle\" font-family=\"system-ui,sans-serif\">2 · " +
            PhoneLogin.esc(copyWord) + "</text>" +
            "<text x=\"34\" y=\"126\" fill=\"#9aa4ae\" font-size=\"16\" font-family=\"system-ui,sans-serif\">redirect</text></svg>"

    /**
     * Chování stránky: dotaz na stav každé 2 s (odškrtá kroky, sbalí hotové), odeslání adresy hned po vložení,
     * čísla z PS5 po osmi číslicích, klíče DeepL tlačítkem. Přihlášení Sony na nové kartě (po návratu je políčko
     * připravené). Na iPhonu se tlačítko do PlayLinga v telefonu schová, na Androidu se sbalí přihlášení v prohlížeči.
     */
    private const val SCRIPT = "<script>(function(){" +
        "var P=location.pathname,T={};try{T=JSON.parse(document.getElementById('t').textContent)}catch(e){}" +
        "function $(i){return document.getElementById(i)}" +
        "function post(d,cb){var x=new XMLHttpRequest();x.open('POST',P);x.setRequestHeader('Content-Type','application/x-www-form-urlencoded');" +
        "x.onload=function(){cb(x.status==200?x.responseText:null)};x.onerror=function(){cb(null)};x.send(d)}" +
        "var android=/Android/i.test(navigator.userAgent);" +
        "if(!android){if($('app'))$('app').style.display='none'}else if($('app')&&$('web')){$('web').open=false}" +
        // zkratka pro iPhone: odkaz nese adresu téhle stránky (TV si ji zapamatuje stránka zkratky); na Androidu není potřeba
        "var sc=$('sc');if(sc){if(android)sc.style.display='none';else sc.href=sc.getAttribute('data-base')+'?tv='+encodeURIComponent(location.protocol+'//'+location.host+P)}" +
        "var acc=null,busy=false;" +
        "function st(id,txt,cls){var e=$(id);e.textContent=txt;e.className='st '+cls}" +
        "function info(){post('op=info',function(r){var s=null;try{s=r&&JSON.parse(r)}catch(e){}" +
        "$('off').style.display=s?'none':'block';if(!s)return;acc=s.acc;var name=acc||'?';" +
        "if(acc!==null){st('accst',T.sp_acc_ok.replace('%s',name),'ok');$('acc').style.display='none'}" +
        "else{st('accst',T.sp_acc_none,'wait');$('acc').style.display='block'}" +
        "$('ps5h').textContent=T.sp_ps5_h.replace('%s',name);$('ps5how').innerHTML='';" +
        "var lines=T.sp_ps5_how.replace('%s',name).split('\\n');for(var i=0;i<lines.length;i++){if(i)$('ps5how').appendChild(document.createElement('br'));$('ps5how').appendChild(document.createTextNode(lines[i]))}" +
        "busy=s.pair=='busy';$('pb').textContent=busy?T.sp_pair_busy:T.sp_pair_btn;" +
        "if(s.pair=='ok'){st('pairst',T.sp_pair_ok.replace('%s',s.ps5||'PS5'),'ok');$('pin').style.display='none';$('pb').style.display='none'}" +
        "else{$('pin').style.display='block';$('pb').style.display='block';" +
        "if(s.pair=='fail')st('pairst',T.sp_pair_fail.replace('%s',name),'bad');else if(acc===null)st('pairst',T.sp_need_acc,'wait');else st('pairst','','wait')}" +
        "if(s.deepl){st('dlst',T.sp_deepl_ok,'ok');$('dl').style.display='none'}else{st('dlst','','wait');$('dl').style.display='block'}" +
        "$('done').style.display=(acc!==null&&s.pair=='ok')?'block':'none'})}" +
        "function sendUrl(){var v=$('u').value;if(v.indexOf('code=')<0){$('um').textContent=T.sp_bad_url;return}" +
        "$('um').textContent=T.sp_sent;post('op=ucet&url='+encodeURIComponent(v),function(r){" +
        "if(r=='OK'){$('um').textContent='';$('u').value='';try{if(window.sonyTab)window.sonyTab.close()}catch(e){}}else $('um').textContent=T.sp_bad_url;info()})}" +
        "$('ub').addEventListener('click',sendUrl);" +
        "$('u').addEventListener('paste',function(){setTimeout(function(){if($('u').value.indexOf('code=')>=0)sendUrl()},80)});" +
        "var l=$('l');l.addEventListener('click',function(e){var w=null;try{w=window.open(l.href,'_blank')}catch(x){}if(w){window.sonyTab=w;e.preventDefault()}});" +
        "document.addEventListener('visibilitychange',function(){if(!document.hidden){info();if(window.sonyTab&&acc===null){try{$('u').focus()}catch(e){}}}});" +
        "function sendPin(){if(busy)return;var v=$('pin').value.replace(/[^0-9]/g,'');if(v.length!=8){$('pm').textContent=T.sp_bad_pin;return}" +
        "$('pm').textContent='';busy=true;$('pb').textContent=T.sp_pair_busy;post('op=kod&pin='+v,function(r){" +
        "if(r=='NOACC')$('pm').textContent=T.sp_need_acc;else if(r!='OK')$('pm').textContent=T.sp_bad_pin;else $('pin').value='';info()})}" +
        "$('pb').addEventListener('click',sendPin);" +
        "$('pin').addEventListener('input',function(){if($('pin').value.replace(/[^0-9]/g,'').length==8)sendPin()});" +
        "$('db').addEventListener('click',function(){var v=$('dk').value.trim();if(!v)return;$('dm').textContent=T.sp_sent;" +
        "post('op=deepl&key='+encodeURIComponent(v),function(r){$('dm').textContent=r=='OK'?'':T.sp_deepl_bad;if(r=='OK')$('dk').value='';info()})});" +
        // adresa „redirect“ poslaná zkratkou z iPhonu (?u=…) → rovnou odeslat a adresu stránky uklidit
        "var m=/[?&]u=([^&]+)/.exec(location.search);if(m){try{$('u').value=decodeURIComponent(m[1]);if(history.replaceState)history.replaceState(null,'',P)}catch(e){}" +
        "if($('u').value.indexOf('code=')>=0)sendUrl()}" +
        "info();setInterval(info,2000);" +
        "})();</script>"
}
