# Starts Mailpit (open-source SMTP catcher) for local OTP testing.
# SMTP: localhost:1025  |  UI: http://localhost:8025
# Prefer: docker compose up -d mailpit
# Fallback: downloads the Mailpit Windows binary if Docker is unavailable.

$ErrorActionPreference = "Stop"
$smtpPort = 1025
$uiPort = 8025

function Test-PortListening([int]$Port) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $async = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok = $async.AsyncWaitHandle.WaitOne(500)
        if ($ok -and $client.Connected) {
            $client.Close()
            return $true
        }
        $client.Close()
    } catch {
    }
    return $false
}

if (Test-PortListening $smtpPort) {
    Write-Host "SMTP already listening on :$smtpPort - open http://localhost:$uiPort"
    exit 0
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($null -ne $docker) {
    Write-Host "Starting Mailpit via Docker Compose..."
    $repoRoot = Split-Path $PSScriptRoot -Parent
    Push-Location $repoRoot
    docker compose up -d mailpit
    Pop-Location
    Start-Sleep -Seconds 2
    if (Test-PortListening $smtpPort) {
        Write-Host "Mailpit ready - SMTP :$smtpPort  UI http://localhost:$uiPort"
        exit 0
    }
    Write-Warning "Docker Compose started but SMTP port not ready yet."
}

$binDir = Join-Path $env:LOCALAPPDATA "parknova\mailpit"
$exe = Join-Path $binDir "mailpit.exe"
New-Item -ItemType Directory -Force -Path $binDir | Out-Null

if (-not (Test-Path $exe)) {
    Write-Host "Downloading Mailpit Windows binary..."
    $zip = Join-Path $env:TEMP "mailpit-windows-amd64.zip"
    $url = "https://github.com/axllent/mailpit/releases/download/v1.27.10/mailpit-windows-amd64.zip"
    Invoke-WebRequest -Uri $url -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $binDir -Force
}

Write-Host "Starting Mailpit ($exe)..."
Start-Process -FilePath $exe -WorkingDirectory $binDir -WindowStyle Minimized
Start-Sleep -Seconds 2

if (Test-PortListening $smtpPort) {
    Write-Host "Mailpit ready - SMTP :$smtpPort  UI http://localhost:$uiPort"
    exit 0
}

Write-Error "Failed to start Mailpit. Install Docker or run mailpit.exe manually."
