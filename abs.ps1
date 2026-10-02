cd D:\Proyectos\DrywallMaterialsCalculator
.\gradlew.bat :app:assembleRelease :keygen:assembleRelease

$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adbPath)) {
    $adbPath = "C:\Android\Sdk\platform-tools\adb.exe"
}
if (-not (Test-Path $adbPath)) {
    Write-Error "adb no encontrado. Instala Android SDK Platform-Tools o agrega al PATH."
    pause
    exit 1
}

& $adbPath install -r D:\Proyectos\DrywallMaterialsCalculator\app\release\app-release.apk
& $adbPath install -r D:\Proyectos\DrywallMaterialsCalculator\keygen\release\keygen-release.apk

pause