import { readFileSync, readdirSync, statSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { basename, resolve } from 'node:path'

const hash = data => createHash('sha256').update(data).digest('hex')

/** Only a bundled Vite development server exposes the explicitly built package index. */
export function localCapabilityDownloads() {
  let config
  const paths = () => ({
    root: resolve(config.root, '..'),
    directory: resolve(config.root, '..', 'outputs/local-capability-downloads'),
  })
  function entries() {
    const { root, directory } = paths()
    const index = JSON.parse(readFileSync(resolve(directory, '交付索引.json'), 'utf8'))
    const manifestDirectory = resolve(root, 'capabilities')
    const sources = readdirSync(manifestDirectory).map(name => resolve(manifestDirectory, name, 'capability.json')).filter(path => {
      try { return basename(path) === 'capability.json' && statSync(path).isFile() } catch { return false }
    }).map(path => readFileSync(path))
    return index.filter(item => {
      if (!/^[a-z0-9-]+$/.test(item.slug) || !/^[a-z0-9.-]+\.zip$/.test(item.file)) return false
      try {
        const source = sources.find(data => JSON.parse(data).identity.slug === item.slug)
        const manifest = JSON.parse(source)
        return manifest.governance.status !== 'ARCHIVED' && item.version === manifest.identity.version
          && item.manifestSha256 === hash(source) && statSync(resolve(directory, item.file)).size === item.bytes
      } catch { return false }
    })
  }
  return {
    name: 'local-capability-downloads',
    configResolved(value) { config = value },
    generateBundle() {
      if ((config.env.VITE_CAPABILITY_SOURCE || 'bundled') !== 'bundled' || config.env.VITE_BUNDLED_DOWNLOADS !== 'true') return
      const { directory } = paths()
      const index = entries()
      this.emitFile({ type: 'asset', fileName: '__local-capability-packages/index.json', source: JSON.stringify(index) })
      for (const entry of index) {
        const data = readFileSync(resolve(directory, entry.file))
        if (hash(data) !== entry.sha256) throw new Error(`能力包校验失败：${entry.file}`)
        this.emitFile({ type: 'asset', fileName: `__local-capability-packages/content/${entry.slug}`, source: data })
      }
    },
    configureServer(server) {
      if ((config.env.VITE_CAPABILITY_SOURCE || 'bundled') !== 'bundled') return
      const { directory } = paths()
      server.middlewares.use((req, res, next) => {
        const pathname = new URL(req.url, 'http://localhost').pathname
        const prefix = '__local-capability-packages/'
        const path = pathname.startsWith(config.base) ? pathname.slice(config.base.length) : pathname.slice(1)
        if (!path.startsWith(prefix)) return next()
        res.setHeader('Cache-Control', 'no-store')
        res.setHeader('X-Content-Type-Options', 'nosniff')
        if (req.method !== 'GET') { res.statusCode = 405; res.end(); return }
        try {
          const name = path.slice(prefix.length)
          const index = entries()
          if (name === 'index.json') {
            res.setHeader('Content-Type', 'application/json; charset=utf-8')
            res.end(JSON.stringify(index)); return
          }
          const entry = index.find(item => `content/${item.slug}` === name)
          if (!entry) { res.statusCode = 404; res.end('能力包不存在或需重新生成'); return }
          const data = readFileSync(resolve(directory, entry.file))
          if (hash(data) !== entry.sha256) throw new Error('能力包校验失败')
          // Fetch the bytes inside the page; direct ZIP attachment URLs can be taken over by download managers.
          res.setHeader('Content-Type', 'application/octet-stream')
          res.setHeader('Content-Length', data.length)
          res.end(data)
        } catch {
          res.statusCode = 503
          res.setHeader('Content-Type', 'text/plain; charset=utf-8')
          res.end('本地能力包尚未准备好，请先运行能力包构建脚本')
        }
      })
    },
  }
}
