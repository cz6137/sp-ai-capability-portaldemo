import { createHash, createHmac } from 'node:crypto'

const required = name => {
  const value = process.env[name]?.trim()
  if (!value) throw new Error(`缺少运行变量：${name}`)
  return value
}

const xfyunAppId = required('PORTAL_XFYUN_APP_ID')
const xfyunSecretKey = required('PORTAL_XFYUN_SECRET_KEY')
const deepSeekKey = required('PORTAL_AI_KEY')
const deepSeekBaseUrl = (process.env.PORTAL_AI_BASE_URL || 'https://api.deepseek.com').replace(/\/+$/, '')
const deepSeekModel = process.env.PORTAL_AI_MODEL || 'deepseek-v4-flash'

function silentWav(seconds = 5) {
  const sampleRate = 16000
  const dataSize = sampleRate * 2 * seconds
  const buffer = Buffer.alloc(44 + dataSize)
  buffer.write('RIFF', 0)
  buffer.writeUInt32LE(36 + dataSize, 4)
  buffer.write('WAVEfmt ', 8)
  buffer.writeUInt32LE(16, 16)
  buffer.writeUInt16LE(1, 20)
  buffer.writeUInt16LE(1, 22)
  buffer.writeUInt32LE(sampleRate, 24)
  buffer.writeUInt32LE(sampleRate * 2, 28)
  buffer.writeUInt16LE(2, 32)
  buffer.writeUInt16LE(16, 34)
  buffer.write('data', 36)
  buffer.writeUInt32LE(dataSize, 40)
  return buffer
}

async function deepSeekCheck() {
  console.log('1/2 检查 DeepSeek 最小 JSON 响应...')
  const response = await fetch(`${deepSeekBaseUrl}/chat/completions`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${deepSeekKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model: deepSeekModel,
      thinking: { type: 'disabled' },
      response_format: { type: 'json_object' },
      max_tokens: 32,
      messages: [
        { role: 'system', content: '只输出 JSON。' },
        { role: 'user', content: '输出 {"status":"ok"}' },
      ],
    }),
  })
  const result = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(`DeepSeek HTTP ${response.status}：${result.error?.message || '请求失败'}`)
  if (!result.choices?.[0]?.message?.content) throw new Error('DeepSeek 已响应，但没有返回正文。')
  console.log(`DeepSeek 鉴权成功，模型：${deepSeekModel}`)
}

async function xfyunCheck() {
  console.log('2/2 检查讯飞录音文件转写创建任务鉴权...')
  const audio = silentWav()
  const timestamp = Math.floor(Date.now() / 1000)
  const md5 = createHash('md5').update(`${xfyunAppId}${timestamp}`, 'utf8').digest('hex')
  const signa = createHmac('sha1', xfyunSecretKey).update(md5, 'utf8').digest('base64')
  const query = new URLSearchParams({
    appId: xfyunAppId,
    signa,
    ts: String(timestamp),
    fileSize: String(audio.length),
    fileName: 'sp-ai-portal-provider-check.wav',
    duration: '5000',
    language: 'cn',
    audioMode: 'fileStream',
    standardWav: '1',
  })
  const response = await fetch(`https://raasr.xfyun.cn/v2/api/upload?${query}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/octet-stream' },
    body: audio,
  })
  const result = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(`讯飞 HTTP ${response.status}`)
  if (String(result.code) === '26601') {
    throw new Error('讯飞鉴权失败（26601）：AppID 与 SecretKey 不属于同一应用，或误用了 APIKey、APISecret、accessKeySecret。当前接入要求“录音文件转写（标准版）”服务管理页里的 SecretKey。')
  }
  if (String(result.code) !== '000000') throw new Error(`讯飞返回 ${result.code || 'UNKNOWN'}：${result.descInfo || '请求失败'}`)
  console.log('讯飞鉴权成功，测试订单已创建。')
}

await deepSeekCheck()
await xfyunCheck()
console.log('外部服务最小验证完成；密钥未写入文件。')
