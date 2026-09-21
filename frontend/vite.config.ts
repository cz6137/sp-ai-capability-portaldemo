import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { localCapabilityDownloads } from './scripts/local-capability-downloads.mjs'

export default defineConfig({
  base: '/sp-ai-portal-web/',
  plugins: [vue(), localCapabilityDownloads()],
  server: {
    port: 5173,
    // 默认指向本机预览接口；本地联调时可用 VITE_API_TARGET 指向其他端口。
    proxy: { '/sp-ai-portal/api': { target: process.env.VITE_API_TARGET || 'http://localhost:8080', changeOrigin: true } },
  },
  preview: { port: 4173 },
})
