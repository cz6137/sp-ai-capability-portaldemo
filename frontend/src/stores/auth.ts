import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '../api/portal'
import { setAccessToken } from '../api/http'
import { PUBLIC_DEMO } from '../constants'
import type { UserSession } from '../types'

const EMPLOYEE_VIEW_KEY = 'portal_employee_view'
const DEMO_IDENTITY: UserSession = {
  id: 'public-demo-admin',
  username: 'demo',
  displayName: '管理员',
  roles: [{ authority: 'ROLE_ADMIN' }],
  accessToken: 'public-demo',
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserSession | null>(null)
  const initialized = ref(false)
  const authenticated = computed(() => !!user.value)

  /**
   * 右上角「切换为员工」是本地视角开关，不是登录身份变更：
   * 请求仍然带管理员令牌，后端看到的还是管理员，因此它不构成权限边界。
   * Demo 环境（scripts/feature-preview-server.mjs）对任何登录都只返回同一个 ADMIN 身份，
   * 没有第二个账号可切，所以这里用视角切换而不是重新登录。
   */
  const employeeView = ref(sessionStorage.getItem(EMPLOYEE_VIEW_KEY) === '1')
  const hasAdminRole = computed(() => user.value?.roles.some(role => ['ROLE_ADMIN', 'ADMIN'].includes(role.authority)) === true)
  /** 页面按「有效身份」渲染：管理员切到员工视角后，管理入口一律按员工处理。 */
  const isAdmin = computed(() => hasAdminRole.value && !employeeView.value)

  function setEmployeeView(on: boolean) {
    employeeView.value = on
    if (on) sessionStorage.setItem(EMPLOYEE_VIEW_KEY, '1')
    else sessionStorage.removeItem(EMPLOYEE_VIEW_KEY)
  }

  async function restore() {
    if (initialized.value) return
    if (PUBLIC_DEMO) {
      setAccessToken(DEMO_IDENTITY.accessToken || '')
      user.value = DEMO_IDENTITY
      initialized.value = true
      return
    }
    try { user.value = await authApi.me() } catch {
      try {
        const session = await authApi.refresh()
        setAccessToken(session.accessToken || '')
        user.value = session
      } catch { setAccessToken(''); user.value = null }
    }
    initialized.value = true
  }
  async function login(username: string, password: string) {
    const session = await authApi.login(username, password)
    setAccessToken(session.accessToken || '')
    user.value = session
    setEmployeeView(false)
  }
  async function logout() {
    try { await authApi.logout() } finally { setAccessToken(''); user.value = null; setEmployeeView(false) }
  }
  return { user, initialized, authenticated, employeeView, hasAdminRole, isAdmin, setEmployeeView, restore, login, logout }
})
