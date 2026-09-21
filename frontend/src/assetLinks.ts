import type { Asset } from './types'
import { IMA_URL } from './constants.ts'

/** 保留来源知识库和目录标识；公开分享页可能忽略 folderId，不能声称网页直达。 */
export function assetImaUrl(asset: Asset): string | undefined {
  let source: URL | undefined
  if (asset.source_url) {
    try {
      const url = new URL(asset.source_url)
      if (url.protocol === 'https:' && url.hostname === 'ima.qq.com' && !url.username && !url.password && url.pathname === '/wiki/') source = url
    } catch { /* 不使用非法来源地址 */ }
  }
  let metadata = asset.source_metadata
  if (typeof metadata === 'string') { try { metadata = JSON.parse(metadata) } catch { metadata = undefined } }
  const metadataFolder = metadata && typeof metadata === 'object' ? metadata.folderId : undefined
  const folder = asset.folder_id || (typeof metadataFolder === 'string' ? metadataFolder : undefined) || source?.searchParams.get('folderId')
  if (!folder && !asset.media_id && !source) return undefined
  const url = new URL(IMA_URL)
  if (source?.searchParams.get('shareId')) url.searchParams.set('shareId', source.searchParams.get('shareId')!)
  if (folder) url.searchParams.set('folderId', folder)
  else if (asset.media_id) {
    url.searchParams.set('action', 'autoOpenMedia')
    url.searchParams.set('mediaId', asset.media_id)
  }
  return url.href
}

export function assetImaClientUrl(asset: Asset): string | undefined {
  const source = assetImaUrl(asset)
  if (!source || !asset.media_id) return undefined
  const url = new URL(source)
  url.searchParams.delete('folderId')
  url.searchParams.set('action', 'autoOpenMedia')
  url.searchParams.set('mediaId', asset.media_id)
  return url.href
}

export function assetImaLocation(asset: Asset): string {
  const stageName = asset.stage_name?.replace(/^\d+[-－]\s*/, '') || ''
  const stage = Number.isInteger(asset.stage_num) && stageName ? `${asset.stage_num}-${stageName}` : stageName
  // 旧快照 sub_number 是页面排序号，不能当作 IMA 文件夹名称中的编号。
  const sub = asset.sub_name
  return [stage, sub].filter(Boolean).join(' → ') || '请在对应知识库中按文件名查找'
}

export function assetImaHomeUrl(asset: Asset): string | undefined {
  const source = assetImaUrl(asset)
  if (!source) return undefined
  const url = new URL(source)
  for (const name of ['folderId', 'action', 'mediaId']) url.searchParams.delete(name)
  return url.href
}
