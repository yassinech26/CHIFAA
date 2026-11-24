$ErrorActionPreference = 'Stop'
$pg = $env:CHIFAA_POSTGRES_BIN
if (-not $pg) { $pg = Join-Path $env:ProgramFiles 'PostgreSQL/18/bin' }
$root = Join-Path ([System.IO.Path]::GetTempPath()) ('chifaa-android-' + [guid]::NewGuid())
$cluster = Join-Path $root 'cluster'
New-Item -Path $root -ItemType Directory | Out-Null
$pw = Join-Path $root 'password'
$env:CHIFAA_DB_PASSWORD = [guid]::NewGuid().ToString('N')
$env:CHIFAA_DEMO_PASSWORD = [guid]::NewGuid().ToString('N')
$env:CHIFAA_DB_USER = 'chifaa'
$env:CHIFAA_DB_URL = 'jdbc:postgresql://127.0.0.1:55433/chifaa_e3'
$env:PGPASSWORD = $env:CHIFAA_DB_PASSWORD
$env:SPRING_PROFILES_ACTIVE = 'dev'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr' }
Set-Content -LiteralPath $pw -Value $env:CHIFAA_DB_PASSWORD -Encoding Ascii
& (Join-Path $pg 'initdb.exe') -D $cluster -U chifaa -A scram-sha-256 "--pwfile=$pw" --encoding=UTF8
$initialized = $LASTEXITCODE
Remove-Item -LiteralPath $pw
if ($initialized) { throw 'initdb failed' }
& (Join-Path $pg 'pg_ctl.exe') -D $cluster -l (Join-Path $root 'postgres.log') -o '-p 55433 -h 127.0.0.1' -w start
if ($LASTEXITCODE) { throw 'pg_ctl start failed' }
$server = $null
$reverse = $false
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
try {
    & (Join-Path $pg 'createdb.exe') -h 127.0.0.1 -p 55433 -U chifaa chifaa_e3
    if ($LASTEXITCODE) { throw 'createdb failed' }
    & "$PSScriptRoot/../backend/mvnw.cmd" -B -ntp -f "$PSScriptRoot/../backend/pom.xml" -DskipTests package
    if ($LASTEXITCODE) { throw 'Packaging failed' }
    $jar = Resolve-Path "$PSScriptRoot/../backend/target/backend-0.1.0-SNAPSHOT.jar"
    $server = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin/java.exe') -ArgumentList @(
        '-jar', ('"' + $jar.Path + '"'), '--server.port=18080', '--server.tomcat.max-keep-alive-requests=1'
    ) -WindowStyle Hidden -RedirectStandardOutput (Join-Path $root 'server.out') -RedirectStandardError (Join-Path $root 'server.err') -PassThru
    $healthy = $false
    for ($attempt = 0; $attempt -lt 50; $attempt++) {
        if ($server.HasExited) { break }
        try {
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/health' -TimeoutSec 2
            if ($health.status -eq 'up') { $healthy = $true; break }
        } catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $healthy) {
        Get-Content (Join-Path $root 'server.out') -Tail 35
        throw 'Demo server did not start'
    }
    if ($env:CHIFAA_EXPORT_OPENAPI -eq 'true') {
        Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:18080/v3/api-docs.yaml' -OutFile "$PSScriptRoot/../docs/api/openapi.yaml"
    }
    & (Join-Path $pg 'psql.exe') -h 127.0.0.1 -p 55433 -U chifaa -d chifaa_e3 -f "$PSScriptRoot/demo.sql"
    if ($LASTEXITCODE) { throw 'Demo seed failed' }
    & $adb reverse tcp:18080 tcp:18080
    if ($LASTEXITCODE) { throw 'ADB reverse failed' }
    $reverse = $true
    Push-Location "$PSScriptRoot/.."
    $offlineJob = $null
    try {
        $capture = $env:CHIFAA_CAPTURE -eq 'true'
        if ($capture) {
            & $adb shell 'mkdir -p /data/local/tmp/chifaa-e3-final; rm -f /data/local/tmp/chifaa-e3-final/offline-ready /data/local/tmp/chifaa-e3-final/offline-ack /data/local/tmp/chifaa-e3-final/offline-done /data/local/tmp/chifaa-e3-final/offline-restored'
            $offlineJob = Start-Job -ArgumentList $adb -ScriptBlock {
                param($adb)
                $deadline = (Get-Date).AddMinutes(4)
                while ((Get-Date) -lt $deadline) {
                    & $adb shell test -f /data/local/tmp/chifaa-e3-final/offline-ready
                    if (-not $LASTEXITCODE) {
                        try {
                            & $adb reverse --remove tcp:18080
                            & $adb reverse tcp:18080 tcp:18081
                            if ($LASTEXITCODE) { throw 'Offline route failed' }
                            & $adb shell touch /data/local/tmp/chifaa-e3-final/offline-ack
                            for ($attempt = 0; $attempt -lt 150; $attempt++) {
                                & $adb shell test -f /data/local/tmp/chifaa-e3-final/offline-done
                                if (-not $LASTEXITCODE) { break }
                                Start-Sleep -Milliseconds 200
                            }
                        } finally {
                            & $adb reverse tcp:18080 tcp:18080
                            & $adb shell touch /data/local/tmp/chifaa-e3-final/offline-restored
                        }
                        break
                    }
                    Start-Sleep -Milliseconds 300
                }
            }
        }
        $captureFlag = $capture.ToString().ToLowerInvariant()
        & .\gradlew.bat :app:connectedDebugAndroidTest '-PchifaaDebugApiUrl=http://127.0.0.1:18080/' `
        "-Pandroid.testInstrumentationRunnerArguments.chifaaDemoPassword=$env:CHIFAA_DEMO_PASSWORD" `
        "-Pandroid.testInstrumentationRunnerArguments.chifaaCapture=$captureFlag"
        $testExitCode = $LASTEXITCODE
        if ($capture) {
            $destination = "$PSScriptRoot/../docs/screenshots/student3"
            New-Item -ItemType Directory -Force $destination | Out-Null
            $screenshots = & $adb shell 'ls /data/local/tmp/chifaa-e3-final/*.png'
            if ($LASTEXITCODE -or -not $screenshots) { throw 'No emulator screenshots found' }
            foreach ($screenshot in $screenshots) {
                & $adb pull $screenshot.Trim() $destination
                if ($LASTEXITCODE) { throw 'Screenshot export failed' }
            }
        }
        if ($testExitCode) { throw 'Android instrumentation failed' }
    } finally {
        if ($null -ne $offlineJob) { Stop-Job $offlineJob; Remove-Job $offlineJob }
        Pop-Location
    }
} finally {
    if ($reverse) { & $adb reverse --remove tcp:18080 }
    if ($null -ne $server -and -not $server.HasExited) { Stop-Process -Id $server.Id }
    & (Join-Path $pg 'pg_ctl.exe') -D $cluster -w stop
}
