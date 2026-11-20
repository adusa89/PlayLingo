# Spustí okno aplikace PlayLingo pro počítač (spouští ho PlayLingo.cmd dvojklikem).
# Když čtečka ještě není sestavená, sestaví ji (asi minuta, jen poprvé a po změně kódu).
# Knihovny pro čtení a překlad stáhne aplikace sama tlačítkem Připravit na stránce Domů.
$ErrorActionPreference = "Stop"
$slozka = "$env:LOCALAPPDATA\PlayLingoTools"
$java = Get-ChildItem "$slozka\jdk-17*", 'C:\Program Files\Eclipse Adoptium\jdk-17*' -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path "$($_.FullName)\bin\javaw.exe" } | Select-Object -First 1
if (-not $java) {
    Add-Type -AssemblyName PresentationFramework
    [System.Windows.MessageBox]::Show("Chybí Java 17 (Temurin). Nainstaluj ji z adoptium.net a spusť PlayLingo znovu.", "PlayLingo") | Out-Null
    exit 1
}
$env:JAVA_HOME = $java.FullName
$lib = "$PSScriptRoot\build\install\playlingo-pc\lib"
# sestavit, když chybí nebo je kód novější než sestavení
$jar = Get-Item "$lib\playlingo-pc.jar" -ErrorAction SilentlyContinue
$zdroje = Get-ChildItem "$PSScriptRoot\src", "$PSScriptRoot\..\app\src\main\java\cz\playlingo\core" -Recurse -Filter *.kt |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar -or $zdroje.LastWriteTime -gt $jar.LastWriteTime) {
    Write-Host "Sestavuji PlayLingo pro počítač…"
    & "$PSScriptRoot\..\gradlew.bat" -p $PSScriptRoot installDist --console=plain -q
    if ($LASTEXITCODE -ne 0) { Read-Host "Sestavení selhalo – Enter zavře okno"; exit 1 }
}
Start-Process "$($java.FullName)\bin\javaw.exe" -ArgumentList @(
    "-Dplaylingo.pc=`"$PSScriptRoot`"", "-Dfile.encoding=UTF-8",
    "-cp", "`"$lib\*`"", "cz.playlingo.pc.AplikaceKt")
