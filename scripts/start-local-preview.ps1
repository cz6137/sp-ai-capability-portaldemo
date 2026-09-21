param(
    [switch]$NoBrowser,
    [switch]$SkipConfiguredProviders
)
$ErrorActionPreference = 'Stop'
try {
    $projectRoot = Split-Path $PSScriptRoot -Parent
    $providerConfig = Join-Path $projectRoot 'outputs\local-secrets\meeting-minutes.local.json'
    if (-not $SkipConfiguredProviders -and (Test-Path -LiteralPath $providerConfig)) {
        $providerArgs = @('-ConfigPath', $providerConfig, '-SkipProviderTest')
        if ($NoBrowser) { $providerArgs += '-NoBrowser' }
        & (Join-Path $PSScriptRoot 'start-meeting-minutes-demo.ps1') @providerArgs
        exit $LASTEXITCODE
    }
    $frontend = Join-Path $projectRoot 'frontend'
    $node = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
    if (-not (Test-Path -LiteralPath $node)) { $node = (Get-Command node -ErrorAction Stop).Source }
    $vite = Join-Path $frontend 'node_modules\vite\bin\vite.js'
    $api = Join-Path $frontend 'scripts\feature-preview-server.mjs'
    if (-not (Test-Path -LiteralPath $vite)) { throw 'Missing frontend dependencies. Restore the existing dependencies first.' }
    $logs = Join-Path $projectRoot 'outputs\local-preview-logs'
    New-Item -ItemType Directory -Path $logs -Force | Out-Null
    function Test-Service($Port, $Path, $Marker) {
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port$Path" -UseBasicParsing -SkipHttpErrorCheck -TimeoutSec 3
        } catch { return $false }
        if ($response.Content -match $Marker) { return $true }
        throw "Port $Port is occupied by another service. Nothing was stopped."
    }
    $apiReady = Test-Service 8080 '/sp-ai-portal/api/v1/auth/me' 'local-preview-only'
    $webReady = Test-Service 5173 '/sp-ai-portal-web/' '/sp-ai-portal-web/@vite/client'
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    if (-not $apiReady) {
        Start-Process -FilePath $node -ArgumentList @(('"' + $api + '"')) -WorkingDirectory $frontend -WindowStyle Hidden -RedirectStandardOutput "$logs\api-$stamp.log" -RedirectStandardError "$logs\api-$stamp.error.log" | Out-Null
    }
    if (-not $webReady) {
        Start-Process -FilePath $node -ArgumentList @(('"' + $vite + '"'), '--host', '127.0.0.1', '--port', '5173', '--strictPort') -WorkingDirectory $frontend -WindowStyle Hidden -RedirectStandardOutput "$logs\web-$stamp.log" -RedirectStandardError "$logs\web-$stamp.error.log" | Out-Null
    }
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        try {
            $a = Invoke-WebRequest 'http://127.0.0.1:8080/sp-ai-portal/api/v1/auth/me' -UseBasicParsing -TimeoutSec 2
            $w = Invoke-WebRequest 'http://127.0.0.1:5173/sp-ai-portal-web/' -UseBasicParsing -TimeoutSec 2
            if ($a.Content -match 'local-preview-only' -and $w.Content -match '/sp-ai-portal-web/@vite/client') { $ready = $true; break }
        } catch {}
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw "Startup failed. Check logs: $logs" }
    Write-Host 'Local preview ready: http://127.0.0.1:5173/sp-ai-portal-web/'
    Write-Host 'Preview only. Database, deployment and external AI services are not started.'
    if (-not $NoBrowser) { Start-Process 'http://127.0.0.1:5173/sp-ai-portal-web/' }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
