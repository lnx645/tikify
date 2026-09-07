# TikTokSoundAlert - install Gradle 8.5 + Android SDK components (jalankan sekali di PowerShell)
# Butuh: JDK 21 (sudah ada di C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot)

$ErrorActionPreference = "Stop"

$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$jdk = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
$gradleDest = "D:\dev\gradle-8.5"

Write-Host "== 1/4 Java =="
$env:JAVA_HOME = $jdk
& "$jdk\bin\java.exe" -version

Write-Host "== 2/4 cmdline-tools =="
if (-not (Test-Path "$sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
    New-Item -ItemType Directory -Force -Path $sdk | Out-Null
    if (-not (Test-Path "$env:TEMP\cmdtools.zip")) {
        Invoke-WebRequest -UseBasicParsing -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile "$env:TEMP\cmdtools.zip"
    }
    Expand-Archive -Path "$env:TEMP\cmdtools.zip" -DestinationPath "$sdk\cmdline-tools" -Force
    Rename-Item "$sdk\cmdline-tools\cmdline-tools" "$sdk\cmdline-tools\latest" -Force
}
Write-Host "cmdline-tools OK"

Write-Host "== 3/4 platform android-34 (build-tools 36.0.0 sudah ada) =="
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-34"
Write-Host "SDK components OK"

Write-Host "== 4/4 Gradle 8.5 =="
if (-not (Test-Path "$gradleDest")) {
    if (-not (Test-Path "$env:TEMP\gradle.zip")) {
        Invoke-WebRequest -UseBasicParsing -Uri "https://services.gradle.org/distributions/gradle-8.5-bin.zip" -OutFile "$env:TEMP\gradle.zip"
    }
    Expand-Archive -Path "$env:TEMP\gradle.zip" -DestinationPath "D:\dev" -Force
}
& "$gradleDest\bin\gradle.bat" --version
Write-Host "`nSELESAI. Semua tools siap. Kabari saya kalau sudah done."