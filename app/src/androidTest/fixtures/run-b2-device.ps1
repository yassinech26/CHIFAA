param([Parameter(Mandatory)][string]$Serial)
& "$PSScriptRoot/run-device.ps1" -Serial $Serial -Scenario B2
