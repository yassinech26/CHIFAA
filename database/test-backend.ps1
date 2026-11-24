$ErrorActionPreference = 'Stop'
$pg = $env:CHIFAA_POSTGRES_BIN
if (-not $pg) { $pg = Join-Path $env:ProgramFiles 'PostgreSQL/18/bin' }
$root = Join-Path ([System.IO.Path]::GetTempPath()) ('chifaa-test-' + [guid]::NewGuid())
$cluster = Join-Path $root 'cluster'
New-Item -Path $root -ItemType Directory | Out-Null
$pw = Join-Path $root 'password'
$env:CHIFAA_DB_PASSWORD = [guid]::NewGuid().ToString('N')
$env:CHIFAA_DEMO_PASSWORD = [guid]::NewGuid().ToString('N')
$env:CHIFAA_DB_USER = 'chifaa'
$env:CHIFAA_DB_URL = 'jdbc:postgresql://127.0.0.1:55432/chifaa_e3'
$env:CHIFAA_RUN_PG_TESTS = 'true'
$env:PGPASSWORD = $env:CHIFAA_DB_PASSWORD
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr' }
Set-Content -LiteralPath $pw -Value $env:CHIFAA_DB_PASSWORD -Encoding Ascii
& (Join-Path $pg 'initdb.exe') -D $cluster -U chifaa -A scram-sha-256 "--pwfile=$pw" --encoding=UTF8
$initialized = $LASTEXITCODE
Remove-Item -LiteralPath $pw
if ($initialized) { throw 'initdb failed' }
& (Join-Path $pg 'pg_ctl.exe') -D $cluster -l (Join-Path $root 'postgres.log') -o '-p 55432 -h 127.0.0.1' -w start
if ($LASTEXITCODE) { throw 'pg_ctl start failed' }
try {
    & (Join-Path $pg 'createdb.exe') -h 127.0.0.1 -p 55432 -U chifaa chifaa_e3
    if ($LASTEXITCODE) { throw 'createdb failed' }
    & "$PSScriptRoot/../backend/mvnw.cmd" -B -ntp -f "$PSScriptRoot/../backend/pom.xml" test
    if ($LASTEXITCODE) { throw 'Backend tests failed' }
    & (Join-Path $pg 'psql.exe') -h 127.0.0.1 -p 55432 -U chifaa -d chifaa_e3 -f "$PSScriptRoot/demo.sql"
    if ($LASTEXITCODE) { throw 'Demo seed failed' }
    $counts = 'SELECT (SELECT count(*) FROM organisations),(SELECT count(*) FROM organisation_languages),(SELECT count(*) FROM kb_items),(SELECT count(*) FROM chat_sessions),(SELECT count(*) FROM messages),(SELECT count(*) FROM safety_events)'
    $first = & (Join-Path $pg 'psql.exe') -h 127.0.0.1 -p 55432 -U chifaa -d chifaa_e3 -Atc $counts
    & (Join-Path $pg 'psql.exe') -h 127.0.0.1 -p 55432 -U chifaa -d chifaa_e3 -f "$PSScriptRoot/demo.sql"
    if ($LASTEXITCODE) { throw 'Demo replay failed' }
    $replay = & (Join-Path $pg 'psql.exe') -h 127.0.0.1 -p 55432 -U chifaa -d chifaa_e3 -Atc $counts
    if ($LASTEXITCODE -or -not $first -or $first -ne $replay) { throw 'Demo replay changed fixture counts' }
    Write-Output "Demo replay preserved counts: $replay"
} finally {
    & (Join-Path $pg 'pg_ctl.exe') -D $cluster -w stop
}
