param(
    [string]$XfyunAppId = $env:PORTAL_XFYUN_APP_ID,
    [string]$DeepSeekBaseUrl = 'https://api.deepseek.com',
    [string]$DeepSeekModel = 'deepseek-flash'
)

function Read-PlainSecret([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

if ([string]::IsNullOrWhiteSpace($XfyunAppId)) { $XfyunAppId = Read-Host 'iFlytek AppID' }

$env:PORTAL_MEETING_MINUTES_ENABLED = 'true'
$env:PORTAL_XFYUN_APP_ID = $XfyunAppId
$env:PORTAL_XFYUN_SECRET_KEY = Read-PlainSecret 'iFlytek transcription SecretKey'
$env:PORTAL_AI_BASE_URL = $DeepSeekBaseUrl.TrimEnd('/')
$env:PORTAL_AI_KEY = Read-PlainSecret 'DeepSeek API Key'
$env:PORTAL_AI_MODEL = $DeepSeekModel

Write-Host 'Meeting-minutes credentials are loaded in this PowerShell process. Keep this window open.' -ForegroundColor Green
