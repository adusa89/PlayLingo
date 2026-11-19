plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Sestavení na PC s Windows (sestavit-na-pc.ps1): nativní část se tam přeložit nedá (OpenSSL chce
// linuxové nástroje), proto se vezme hotová z vydaného APK. -PnativniZ=<složka s <abi>/*.so>.
// Na GitHubu se nepoužívá, tam se nativní část překládá vždy.
val nativniZ: String? = (project.findProperty("nativniZ") as String?)?.takeIf { it.isNotBlank() }

android {
    namespace = "cz.playlingo"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.playlingo"
        minSdk = 26          // Android 8 a novější (prakticky všechny Android TV a boxy)
        targetSdk = 34
        versionCode = 73
        versionName = "0.73"

        // televize a boxy mají procesory ARM; emulátor při zkoušce x86 (-Pabis=x86)
        val abis = ((project.findProperty("abis") as String?) ?: "armeabi-v7a,arm64-v8a").split(",")
        ndk { abiFilters += abis }

        // spojení s PlayStationem: otevřená knihovna chiaki-ng (third_party/chiaki-ng)
        if (nativniZ == null) externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCHIAKI_ENABLE_TESTS=OFF", "-DCHIAKI_ENABLE_CLI=OFF", "-DCHIAKI_ENABLE_GUI=OFF",
                    "-DCHIAKI_ENABLE_SETSU=OFF", "-DCHIAKI_ENABLE_ANDROID=ON",
                    "-DCHIAKI_ENABLE_FFMPEG_DECODER=OFF", "-DCHIAKI_ENABLE_PI_DECODER=OFF",
                    "-DCHIAKI_LIB_ENABLE_OPUS=OFF", "-DCHIAKI_LIB_OPENSSL_EXTERNAL_PROJECT=ON",
                    "-DCHIAKI_LIB_JSONC_EXTERNAL_PROJECT=ON", "-DCHIAKI_LIB_MINIUPNPC_EXTERNAL_PROJECT=ON",
                    "-DCHIAKI_ENABLE_STEAM_SHORTCUT=OFF", "-DCHIAKI_ENABLE_STEAMDECK_NATIVE=OFF",
                    "-DANDROID_STL=c++_shared", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384",
                )
            }
        }
    }

    ndkVersion = "28.2.13676358"
    if (nativniZ == null) externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("../third_party/chiaki-ng/CMakeLists.txt")
        }
    } else {
        sourceSets["main"].jniLibs.srcDir(nativniZ)
    }

    // Stálý podpis: všechny verze musí být podepsané stejným klíčem, jinak je televize nenainstaluje
    // přes starší verzi a uživatel přijde o spárování i nastavení. Klíč v repozitáři není a nesmí
    // být: sestavení na GitHubu ho rozbalí z tajného údaje PLAYLINGO_KEY_BASE64 do dočasného
    // souboru a jeho cestu předá v PLAYLINGO_KEY_FILE; heslo je v PLAYLINGO_KEY_PASSWORD.
    // Bez nich (např. sestavení mimo GitHub) se podepíše dočasným klíčem – takové APK nejde
    // nainstalovat přes vydanou verzi a sestavení ho nevydá.
    val signingPassword: String? = System.getenv("PLAYLINGO_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
    val signingFile: File? = System.getenv("PLAYLINGO_KEY_FILE")?.takeIf { it.isNotBlank() }
        ?.let { File(it) }?.takeIf { it.isFile }
    val stableSigning = signingPassword != null && signingFile != null
    signingConfigs {
        if (stableSigning) create("stable") {
            storeFile = signingFile
            storeType = "pkcs12"
            storePassword = signingPassword
            keyAlias = "playlingo"
            keyPassword = signingPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (stableSigning) "stable" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // nativní knihovny jsou v APK komprimované (menší soubor ke stažení)
    packaging { jniLibs { useLegacyPackaging = true } }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Naše úprava nativní části (bez zvuku a bez nativního dekodéru videa) se před sestavením
// zkopíruje na místo původní android/app v knihovně chiaki-ng.
val patchChiaki by tasks.registering(Copy::class) {
    from("src/main/cpp")
    into("../third_party/chiaki-ng/android/app")
}
tasks.named("preBuild") { dependsOn(patchChiaki) }

// Aplikace pro televizi LG (webos/obchod/app) jako balíček .ipk přímo v APK: z telefonu nebo boxu
// jde nainstalovat do televize ve vývojářském režimu (LgInstaller.kt). Balíček skládá
// core/LgIpk.kt (přes buildSrc) stejně jako ares-package, jen s pevným datem 1. 1. 2000.
// Po sestavení se přečte zpět a porovná se zdroji. Výsledek: assets lg/receiver.ipk
// a lg/receiver-manifest.json (id, verze, velikost, sha256). Nic se necommituje.
val lgAssets = layout.buildDirectory.dir("generated/lgAssets")
val lgBalicek by tasks.registering {
    val src = rootProject.file("webos/obchod/app")
    inputs.dir(src)
    outputs.dir(lgAssets)
    doLast {
        val files = src.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }
            .associate { it.relativeTo(src).invariantSeparatorsPath to it.readBytes() }
        val ipk = cz.playlingo.core.LgIpk.build(files)
        val (id, version) = cz.playlingo.core.LgIpk.verify(ipk, files)
        require(id == cz.playlingo.core.LgIpk.APP_ID) { "webos/obchod/app má jiné id: $id" }
        val out = lgAssets.get().dir("lg").asFile
        out.deleteRecursively(); out.mkdirs()
        out.resolve("receiver.ipk").writeBytes(ipk)
        out.resolve("receiver-manifest.json").writeText(cz.playlingo.core.LgIpk.manifest(id, version, ipk))
        logger.lifecycle("LG: ${cz.playlingo.core.LgIpk.fileName(id, version)} (${ipk.size} B)")
    }
}
android.sourceSets.getByName("main").assets.srcDir(lgAssets)
tasks.named("preBuild") { dependsOn(lgBalicek) }

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // QR kód s přihlašovací adresou Sony (přihlášení účtem PlayStation přes telefon)
    implementation("com.google.zxing:core:3.5.3")

    // Instalace aplikace do televize LG ve vývojářském režimu (SSH/SFTP, udržovaná větev JSch)
    implementation("com.github.mwiede:jsch:2.28.8")

    testImplementation("junit:junit:4.13.2")
}
