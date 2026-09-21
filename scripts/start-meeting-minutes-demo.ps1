param(
    [string]$ConfigPath = '',
    [string]$XfyunAppId = $env:PORTAL_XFYUN_APP_ID,
    [string]$DeepSeekBaseUrl = 'https://api.deepseek.com',
    [string]$DeepSeekModel = 'deepseek-flash',
    [switch]$SkipProviderTest,
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'

function Read-PlainSecret([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Stop-ExistingPreviewApi {
    $listenerLines = @(netstat -ano | Select-String '^\s*TCP\s+127\.0\.0\.1:8080\s+\S+\s+LISTENING\s+(\d+)\s*$')
    if (-not $listenerLines.Count) { return }
    try {
        $identity = Invoke-WebRequest 'http://127.0.0.1:8080/sp-ai-portal/api/v1/auth/me' -UseBasicParsing -TimeoutSec 2
    } catch {
        throw "Port 8080 is occupied but does not answer as the local preview API. Nothing was stopped."
    }
    if ($identity.Content -notmatch 'local-preview-only') {
        throw "Port 8080 is occupied by a service that is not the local preview API. Nothing was stopped."
    }
    foreach ($line in $listenerLines) {
        $ownerId = [int]$line.Matches[0].Groups[1].Value
        $process = Get-Process -Id $ownerId -ErrorAction Stop
        if ($process.ProcessName -ne 'node') { throw "The verified preview port is not owned by node. Nothing was stopped." }
        Stop-Process -Id $ownerId -Force
    }
    Start-Sleep -Milliseconds 600
}

$projectRoot = Split-Path $PSScriptRoot -Parent
$frontend = Join-Path $projectRoot 'frontend'
$apiScript = Join-Path $frontend 'scripts\feature-preview-server.mjs'
$node = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
if (-not (Test-Path -LiteralPath $node)) { $node = (Get-Command node -ErrorAction Stop).Source }
if ([string]::IsNullOrWhiteSpace($ConfigPath)) { $ConfigPath = Join-Path $projectRoot 'outputs\local-secrets\meeting-minutes.local.json' }
if (Test-Path -LiteralPath $ConfigPath) {
    $config = Get-Content -Raw -LiteralPath $ConfigPath | ConvertFrom-Json
    $XfyunAppId = [string]$config.xfyunAppId
    $xfyunSecret = [string]$config.xfyunSecretKey
    $deepSeekKey = [string]$config.aiKey
    $DeepSeekBaseUrl = [string]$config.aiBaseUrl
    $DeepSeekModel = [string]$config.aiModel
    $maxFileSizeMb = [int]$config.maxFileSizeMb
} else {
    if ([string]::IsNullOrWhiteSpace($XfyunAppId)) { $XfyunAppId = Read-Host 'iFlytek AppID' }
    $xfyunSecret = Read-PlainSecret 'iFlytek transcription SecretKey'
    $deepSeekKey = Read-PlainSecret 'DeepSeek API Key'
    $maxFileSizeMb = 25
}
if ([string]::IsNullOrWhiteSpace($XfyunAppId) -or [string]::IsNullOrWhiteSpace($xfyunSecret) -or [string]::IsNullOrWhiteSpace($deepSeekKey)) {
    throw "Meeting-minutes private config is incomplete: $ConfigPath"
}

$names = @(
    'PORTAL_MEETING_MINUTES_ENABLED', 'PORTAL_XFYUN_APP_ID', 'PORTAL_XFYUN_SECRET_KEY',
    'PORTAL_AI_BASE_URL', 'PORTAL_AI_KEY', 'PORTAL_AI_MODEL', 'PORTAL_MEETING_MINUTES_MAX_FILE_SIZE_MB'
)
$old = @{}
foreach ($name in $names) { $old[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

try {
    $env:PORTAL_MEETING_MINUTES_ENABLED = 'true'
    $env:PORTAL_XFYUN_APP_ID = $XfyunAppId
    $env:PORTAL_XFYUN_SECRET_KEY = $xfyunSecret
    $env:PORTAL_AI_BASE_URL = $DeepSeekBaseUrl.TrimEnd('/')
    $env:PORTAL_AI_KEY = $deepSeekKey
    $env:PORTAL_AI_MODEL = $DeepSeekModel
    $env:PORTAL_MEETING_MINUTES_MAX_FILE_SIZE_MB = [string]$maxFileSizeMb

    if (-not $SkipProviderTest) {
        & $node (Join-Path $PSScriptRoot 'test-external-providers.mjs')
        if ($LASTEXITCODE -ne 0) { throw "External provider test failed (exit code $LASTEXITCODE)" }
    }

    Stop-ExistingPreviewApi
    $logs = Join-Path $projectRoot 'outputs\local-preview-logs'
    New-Item -ItemType Directory -Path $logs -Force | Out-Null
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    Start-Process -FilePath $node -ArgumentList @(('"' + $apiScript + '"')) -WorkingDirectory $frontend -WindowStyle Hidden -RedirectStandardOutput "$logs\meeting-demo-api-$stamp.log" -RedirectStandardError "$logs\meeting-demo-api-$stamp.error.log" | Out-Null

    $ready = $false
    for ($index = 0; $index -lt 30; $index++) {
        try {
            $response = Invoke-WebRequest 'http://127.0.0.1:8080/sp-ai-portal/api/v1/tools/meeting-minutes/capabilities' -UseBasicParsing -TimeoutSec 2
            if ($response.Content -match '"status":"READY"' -and $response.Content -match '"minutesAvailable":true') { $ready = $true; break }
        } catch {}
        Start-Sleep -Milliseconds 400
    }
    if (-not $ready) { throw "Meeting-minutes demo API did not become ready. Check $logs" }

    if ($NoBrowser) {
        & (Join-Path $PSScriptRoot 'start-local-preview.ps1') -NoBrowser -SkipConfiguredProviders
    } else {
        & (Join-Path $PSScriptRoot 'start-local-preview.ps1') -SkipConfiguredProviders
    }
    Write-Host 'Meeting-minutes demo is ready: http://127.0.0.1:5173/sp-ai-portal-web/tools/meeting-minutes' -ForegroundColor Green
    Write-Host 'Credentials exist only in the local API process environment. Stop that node process to clear them.' -ForegroundColor Yellow
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $old[$name], 'Process') }
    $xfyunSecret = $null
    $deepSeekKey = $null
}
