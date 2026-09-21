param(
    [string]$OutputPath = '',
    [switch]$NoStart
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $projectRoot 'outputs\local-secrets\meeting-minutes.local.json'
}

function Read-PlainSecret([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

$appId = Read-Host 'iFlytek AppID'
$xfyunSecret = Read-PlainSecret 'iFlytek transcription SecretKey'
$deepSeekKey = Read-PlainSecret 'DeepSeek API Key'
$node = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
if (-not (Test-Path -LiteralPath $node)) { $node = (Get-Command node -ErrorAction Stop).Source }

$old = @{
    AppId = $env:PORTAL_XFYUN_APP_ID
    XfyunSecret = $env:PORTAL_XFYUN_SECRET_KEY
    AiKey = $env:PORTAL_AI_KEY
    AiBaseUrl = $env:PORTAL_AI_BASE_URL
    AiModel = $env:PORTAL_AI_MODEL
}
$saved = $false

try {
    $env:PORTAL_XFYUN_APP_ID = $appId
    $env:PORTAL_XFYUN_SECRET_KEY = $xfyunSecret
    $env:PORTAL_AI_KEY = $deepSeekKey
    $env:PORTAL_AI_BASE_URL = 'https://api.deepseek.com'
    $env:PORTAL_AI_MODEL = 'deepseek-flash'
    & $node (Join-Path $PSScriptRoot 'test-external-providers.mjs')
    if ($LASTEXITCODE -ne 0) { throw "External provider test failed; private config was not written." }

    $directory = Split-Path $OutputPath -Parent
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    [ordered]@{
        enabled = $true
        xfyunAppId = $appId
        xfyunSecretKey = $xfyunSecret
        aiBaseUrl = 'https://api.deepseek.com'
        aiKey = $deepSeekKey
        aiModel = 'deepseek-flash'
        maxFileSizeMb = 25
    } | ConvertTo-Json | Set-Content -LiteralPath $OutputPath -Encoding UTF8
    $saved = $true
    Write-Host "Private demo config saved: $OutputPath" -ForegroundColor Green
    Write-Host 'This file contains plaintext credentials. Share it separately and never add it to Git or a capability package.' -ForegroundColor Yellow
} finally {
    $env:PORTAL_XFYUN_APP_ID = $old.AppId
    $env:PORTAL_XFYUN_SECRET_KEY = $old.XfyunSecret
    $env:PORTAL_AI_KEY = $old.AiKey
    $env:PORTAL_AI_BASE_URL = $old.AiBaseUrl
    $env:PORTAL_AI_MODEL = $old.AiModel
    $xfyunSecret = $null
    $deepSeekKey = $null
}

if ($saved -and -not $NoStart) {
    & (Join-Path $PSScriptRoot 'start-meeting-minutes-demo.ps1') -ConfigPath $OutputPath -SkipProviderTest
}
