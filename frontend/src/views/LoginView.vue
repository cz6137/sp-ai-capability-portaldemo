<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { hydratePortalData } from '../data'

const username = ref('')
const password = ref('')
const loading = ref(false)
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

async function submit() {
  if (!username.value || !password.value) return
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    try { await hydratePortalData() } catch { /* 登录成功；基线页显示独立读取错误及重试入口 */ }
    await router.replace(String(route.query.redirect || '/'))
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '登录失败')
  } finally { loading.value = false }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <RouterLink class="login-brand" to="/"><span>SD</span><div><b>华南大区</b><small>AI 赋能交付平台</small></div></RouterLink>
      <h1>登录平台</h1>
      <el-form @submit.prevent="submit">
        <el-form-item><el-input v-model="username" :prefix-icon="User" size="large" autocomplete="username" placeholder="用户名" /></el-form-item>
        <el-form-item><el-input v-model="password" :prefix-icon="Lock" size="large" type="password" show-password autocomplete="current-password" placeholder="密码" @keyup.enter="submit" /></el-form-item>
        <el-button native-type="submit" type="primary" size="large" :loading="loading" :disabled="!username || !password">登录</el-button>
      </el-form>
    </section>
  </main>
</template>

<style scoped>
.login-page{min-height:100vh;display:grid;place-items:center;padding:24px;background:#eef1f5}.login-panel{width:min(400px,100%);padding:32px;background:#fff;border:1px solid #dce1e8;border-top:4px solid var(--brand);border-radius:6px;box-shadow:0 18px 50px rgba(15,23,42,.12)}.login-brand{display:flex;align-items:center;gap:11px}.login-brand>span{width:42px;height:42px;display:grid;place-items:center;color:#fff;background:var(--brand);border-radius:5px;font-weight:800}.login-brand div{display:flex;flex-direction:column}.login-brand small{color:var(--muted);margin-top:2px}.login-panel h1{margin:34px 0 22px;font-size:24px}.el-button{width:100%}
</style>
