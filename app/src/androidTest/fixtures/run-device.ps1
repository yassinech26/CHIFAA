param([Parameter(Mandatory)][string]$Serial, [ValidateSet('B2','E1')][string]$Scenario = 'B2')
$ErrorActionPreference = 'Stop'
Push-Location (Resolve-Path "$PSScriptRoot/../../../..")
try {
    $suite = if ($Scenario -eq 'E1') { 'chat.ChatApiTest' } else { 'clinicalreview.ClinicalReviewApiTest' }
    $report = [xml](Get-Content -Raw "backend/target/surefire-reports/TEST-tn.chifaa.$suite.xml")
    $classpath = ($report.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
    $classes = (New-Item -Force -ItemType Directory 'backend/target/device-check').FullName
    $adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' } else { (Get-Command adb).Source }
    javac -encoding UTF-8 -cp $classpath -d $classes 'app/src/androidTest/fixtures/DeviceCheck.java' "app/src/androidTest/fixtures/${Scenario}DeviceCheck.java"
    if ($LASTEXITCODE -ne 0) { throw 'Compilation du lanceur de test impossible' }
    java -cp "$classes;$classpath" "tn.chifaa.${Scenario}DeviceCheck" $adb $Serial
    if ($LASTEXITCODE -ne 0) { throw "Échec du parcours Android $Scenario sur HTTPS" }
} finally {
    Pop-Location
}
