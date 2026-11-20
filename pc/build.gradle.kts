// Čtečka pro počítač (viz CTI-ME.md). Samostatný projekt: do aplikace pro Android TV se nedostane
// a sestavení na GitHubu o něm neví.
//
//   ..\gradlew.bat -p pc installDist     → pc/build/install/playlingo-pc
//
// Logika titulků se nepřepisuje: překládá se přímo složka `core` z aplikace (je bez Androidu).
plugins {
    kotlin("jvm") version "2.0.21"
    application
}
repositories { mavenCentral() }
// JNA: pruh na obrazovce potřebuje od Windows polohu okna hry a průchod myši skrz pruh
dependencies { implementation("net.java.dev.jna:jna-platform:5.14.0") }
kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.srcDirs(rootDir.resolve("../app/src/main/java/cz/playlingo/core"), "src")
}
application { mainClass.set("cz.playlingo.pc.MainKt") }
