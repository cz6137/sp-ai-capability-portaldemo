import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles.css'
import { useAuthStore } from './stores/auth'
import { hydratePortalData } from './data'

async function bootstrap() {
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia).use(router).use(ElementPlus)
  const auth = useAuthStore(pinia)
  await auth.restore()
  if (auth.authenticated) { try { await hydratePortalData() } catch { /* 数据层保留可见错误，后台失败不回退历史目录 */ } }
  app.mount('#app')
}

bootstrap()
