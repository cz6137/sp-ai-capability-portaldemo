param(
    [string]$XfyunAppId = $env:PORTAL_XFYUN_APP_ID,
    [string]$DeepSeekBaseUrl = 'https://api.deepseek.com',
    [string]$DeepSeekModel = 'deepseek-flash'
)

$ErrorActionPreference = 'Stop'

function Read-PlainSecret([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

if ([string]::IsNullOrWhiteSpace($XfyunAppId)) { $XfyunAppId = Read-Host 'iFlytek AppID' }
$xfyunSecret = if ($env:PORTAL_XFYUN_SECRET_KEY) { $env:PORTAL_XFYUN_SECRET_KEY } else { Read-PlainSecret 'iFlytek transcription SecretKey' }
$deepSeekKey = if ($env:PORTAL_AI_KEY) { $env:PORTAL_AI_KEY } else { Read-PlainSecret 'DeepSeek API Key' }

$old = @{
    AppId = $env:PORTAL_XFYUN_APP_ID
    XfyunSecret = $env:PORTAL_XFYUN_SECRET_KEY
    AiKey = $env:PORTAL_AI_KEY
    AiBaseUrl = $env:PORTAL_AI_BASE_URL
    AiModel = $env:PORTAL_AI_MODEL
}

try {
    $env:PORTAL_XFYUN_APP_ID = $XfyunAppId
    $env:PORTAL_XFYUN_SECRET_KEY = $xfyunSecret
    $env:PORTAL_AI_KEY = $deepSeekKey
    $env:PORTAL_AI_BASE_URL = $DeepSeekBaseUrl.TrimEnd('/')
    $env:PORTAL_AI_MODEL = $DeepSeekModel
    & node (Join-Path $PSScriptRoot 'test-external-providers.mjs')
    if ($LASTEXITCODE -ne 0) { throw "External provider test failed (exit code $LASTEXITCODE)" }
} finally {
    $env:PORTAL_XFYUN_APP_ID = $old.AppId
    $env:PORTAL_XFYUN_SECRET_KEY = $old.XfyunSecret
    $env:PORTAL_AI_KEY = $old.AiKey
    $env:PORTAL_AI_BASE_URL = $old.AiBaseUrl
    $env:PORTAL_AI_MODEL = $old.AiModel
    $xfyunSecret = $null
    $deepSeekKey = $null
}
