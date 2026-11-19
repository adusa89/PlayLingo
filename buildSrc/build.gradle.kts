// Pomocný kód pro sestavení. Jediný soubor: core/LgIpk.kt z aplikace – balíček pro televizi LG
// (assets/lg/receiver.ipk) tak skládá tentýž kód, který pokrývají unit testy (LgIpkTest).
// Jen standardní knihovna Javy, žádný Node ani Python – funguje na GitHubu i v sestavit-na-pc.ps1.
plugins { `embedded-kotlin` }
repositories { mavenCentral() }
sourceSets["main"].kotlin {
    srcDir("../app/src/main/java")
    include("cz/playlingo/core/LgIpk.kt")
}
