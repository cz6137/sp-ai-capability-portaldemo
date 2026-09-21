<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

/**
 * 右上角的身份切换：管理员 ↔ 员工。
 * 这是本地视角开关，不是登录身份变更——请求仍带管理员令牌，
 * 后端看到的还是管理员。见 stores/auth.ts 里的 employeeView 说明。
 */
const auth = useAuthStore()
const router = useRouter()

/** 只有管理员账号需要这个开关；真正的员工账号看不到它。 */
const visible = computed(() => auth.hasAdminRole)
const toEmployee = computed(() => !auth.employeeView)

async function toggle() {
  const next = toEmployee.value
  auth.setEmployeeView(next)
  await router.replace(next ? '/my/capabilities' : '/admin/capabilities')
}
</script>

<template>
  <span v-if="visible" class="employee-switch">
    <el-button size="small" plain :aria-label="toEmployee ? '切换到员工视角' : '返回管理员视角'" @click="toggle">
      {{ toEmployee ? '切换为员工' : '返回管理员' }}
    </el-button>
  </span>
</template>

<style scoped>
.employee-switch{display:inline-flex}
@media (max-width:600px){.employee-switch{display:none}}
</style>
