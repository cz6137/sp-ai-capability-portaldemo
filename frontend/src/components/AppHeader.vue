<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { House, Menu, TopRight } from '@element-plus/icons-vue'
import { IMA_URL, PUBLIC_DEMO } from '../constants'
import { useAuthStore } from '../stores/auth'
import EmployeeSwitchButton from './EmployeeSwitchButton.vue'

const router = useRouter()
const route = useRoute()
const mobileOpen = ref(false)
const auth = useAuthStore()
const isAdmin = computed(() => auth.isAdmin)
const showHomeButton = computed(() => route.name !== 'home')
const accountLabel = computed(() => {
  if (auth.hasAdminRole) return auth.employeeView ? '员工视角' : '管理员'
  return auth.user?.displayName || '员工'
})

const navItems = [
  { label: '交付基线库', to: '/baseline' },
  { label: 'Skill 能力库', to: '/skills' },
  { label: 'AI 工具库', to: '/tools' },
  { label: '标杆案例库', to: '/knowledge' },
]

async function logout() { await auth.logout(); await router.push('/login') }
</script>

<template>
  <header class="site-header">
    <div class="container header-inner">
      <el-button v-if="showHomeButton" class="home-button" :icon="House" @click="router.push('/')">
        返回首页
      </el-button>
      <RouterLink class="brand" to="/" aria-label="华南大区 AI 赋能交付平台首页">
        <span class="brand-mark">SD</span>
        <span class="brand-copy"><b>华南大区</b><small>AI 赋能交付平台</small></span>
      </RouterLink>
      <nav class="desktop-nav" aria-label="主导航">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to">{{ item.label }}</RouterLink>
        <RouterLink v-if="isAdmin" to="/admin/capabilities">平台管理</RouterLink>
        <RouterLink v-if="isAdmin" to="/admin/approvals">审批工作台</RouterLink>
        <RouterLink v-if="!isAdmin && auth.authenticated" to="/my/capabilities">我的提交</RouterLink>
      </nav>
      <div class="header-actions">
        <el-button v-if="PUBLIC_DEMO && auth.user">{{ accountLabel }}</el-button>
        <el-dropdown v-else-if="auth.user" trigger="click">
          <el-button>{{ accountLabel }}</el-button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
        <EmployeeSwitchButton />
        <el-button class="ima-button" type="primary" tag="a" :href="IMA_URL" target="_blank">
          打开 IMA <el-icon><TopRight /></el-icon>
        </el-button>
        <el-button class="mobile-menu-button" circle :icon="Menu" title="打开导航" @click="mobileOpen = true" />
      </div>
    </div>
  </header>

  <el-drawer v-model="mobileOpen" title="导航" size="min(82vw, 320px)">
    <nav class="mobile-nav">
      <RouterLink v-if="showHomeButton" to="/" @click="mobileOpen = false">返回首页</RouterLink>
      <template v-if="isAdmin">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to" @click="mobileOpen = false">{{ item.label }}</RouterLink>
        <RouterLink to="/admin/capabilities" @click="mobileOpen = false">平台管理</RouterLink>
        <RouterLink to="/admin/approvals" @click="mobileOpen = false">审批工作台</RouterLink>
      </template>
      <template v-else>
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to" @click="mobileOpen = false">{{ item.label }}</RouterLink>
        <RouterLink v-if="auth.authenticated" to="/my/capabilities" @click="mobileOpen = false">我的提交</RouterLink>
      </template>
      <a :href="IMA_URL" target="_blank">打开 IMA</a>
    </nav>
  </el-drawer>
</template>
