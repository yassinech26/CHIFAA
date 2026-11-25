param([Parameter(Mandatory)][string]$Serial)
$ErrorActionPreference = 'Stop'
Push-Location (Resolve-Path "$PSScriptRoot/../../../..")
try {
    $report = [xml](Get-Content -Raw 'backend/target/surefire-reports/TEST-tn.chifaa.clinicalreview.ClinicalReviewApiTest.xml')
    $classpath = ($report.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
    $classes = (New-Item -Force -ItemType Directory 'backend/target/b2-device-check').FullName
    $adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' } else { (Get-Command adb).Source }
    javac -encoding UTF-8 -cp $classpath -d $classes 'app/src/androidTest/fixtures/B2DeviceCheck.java'
    if ($LASTEXITCODE -ne 0) { throw 'Compilation du lanceur de test impossible' }
    java -cp "$classes;$classpath" tn.chifaa.B2DeviceCheck $adb $Serial
    if ($LASTEXITCODE -ne 0) { throw 'Échec du parcours Android B2 sur HTTPS' }
} finally {
    Pop-Location
}
