plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cz.testpreklad"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.testpreklad"
        minSdk = 26          // Android 8 a novější (prakticky všechny Android TV a boxy)
        targetSdk = 34
        versionCode = 34
        versionName = "0.34"

        // televize a boxy mají procesory ARM; emulátor při zkoušce x86 (-Pabis=x86)
        val abis = ((project.findProperty("abis") as String?) ?: "armeabi-v7a,arm64-v8a").split(",")
        ndk { abiFilters += abis }

        // spojení s PlayStationem: otevřená knihovna chiaki-ng (third_party/chiaki-ng)
        externalNativeBuild {
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
    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("../third_party/chiaki-ng/CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // pro jednoduchost podepsáno debug klíčem, aby šlo APK rovnou nainstalovat
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // nativní knihovny v APK zabalené (jinak by přibalený prohlížeč zvětšil APK na dvojnásobek)
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

// Úprava knihovny: režim „jen se dívat“. Aplikace obraz jen čte, proto se konzoli nemá hlásit jako
// další ovladač ani mikrofon – jinak konzole přehazuje ovladače hráčům u televize.
// Zapíná se za běhu proměnnou chiaki_view_only (viz chiaki-jni.c); vypnuto = původní chování.
val patchChiakiLib by tasks.registering {
    val f = file("../third_party/chiaki-ng/lib/src/streamconnection.c")
    inputs.property("v", 1)
    doLast {
        var t = f.readText()
        if (!t.contains("chiaki_view_only")) {
            fun rep(old: String, new: String) {
                check(t.contains(old)) { "úprava knihovny: nenalezeno: $old" }
                t = t.replaceFirst(old, new)
            }
            rep("static ChiakiErrorCode stream_connection_send_controller_connection(ChiakiStreamConnection *stream_connection);",
                "int chiaki_view_only = 0;\nstatic ChiakiErrorCode stream_connection_send_controller_connection(ChiakiStreamConnection *stream_connection);")
            rep("ChiakiErrorCode err = stream_connection_send_controller_connection(stream_connection);",
                "ChiakiErrorCode err = chiaki_view_only ? CHIAKI_ERR_SUCCESS : stream_connection_send_controller_connection(stream_connection);")
            rep("err = stream_connection_enable_microphone(stream_connection);",
                "err = chiaki_view_only ? CHIAKI_ERR_SUCCESS : stream_connection_enable_microphone(stream_connection);")
            f.writeText(t)
        }
    }
}
tasks.named("preBuild") { dependsOn(patchChiaki, patchChiakiLib) }

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Prohlížeč pro přihlášení k účtu PlayStation (jádro Firefoxu, licence MPL 2.0). Televize mívají
    // vlastní prohlížeč zastaralý a neaktualizovatelný; stránka Sony v něm nefunguje.
    implementation("org.mozilla.geckoview:geckoview:136.0.20250317200840")

    // QR kód s přihlašovací adresou (přihlášení přes telefon, když ho Sony v televizi odmítne)
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}
