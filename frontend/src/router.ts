import { createRouter, createWebHistory } from 'vue-router'
import HomeView from './views/HomeView.vue'
import { useAuthStore } from './stores/auth'
import { PUBLIC_DEMO } from './constants'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/baseline', name: 'baseline', component: () => import('./views/BaselineView.vue') },
    { path: '/stage/:id(\\d+)', name: 'stage', component: () => import('./views/StageView.vue') },
    { path: '/skills', name: 'skills', component: () => import('./views/SkillsView.vue') },
    { path: '/skills/:id', name: 'skill-detail', component: () => import('./views/SkillRouteView.vue') },
    { path: '/tools', name: 'tools', component: () => import('./views/ToolsView.vue') },
    { path: '/tools/:id', name: 'tool-runtime', component: () => import('./views/ToolRuntimeView.vue') },
    { path: '/capabilities/:slug', name: 'capability-detail', component: () => import('./views/CapabilityView.vue') },
    { path: '/knowledge', name: 'knowledge', component: () => import('./views/KnowledgeView.vue') },
    { path: '/knowledge/:code', name: 'case-category', component: () => import('./views/CaseCategoryView.vue') },
    { path: '/norms', name: 'norms', component: () => import('./views/SkillWorkbenchView.vue') },
    { path: '/tasks', name: 'tasks', component: () => import('./views/WorkbenchView.vue') },
    { path: '/workbench', redirect: '/tasks' },
    { path: '/skill-guide', name: 'skill-guide', component: () => import('./views/SkillGuideView.vue') },
    { path: '/admin/skill-adapter', component: () => import('./views/CapabilityView.vue'), props: { slug: 'platform-skill-adapter' }, meta: { admin: true } },
    { path: '/admin/capabilities', name: 'capability-admin', component: () => import('./views/CapabilityAdminView.vue'), meta: { admin: true } },
    { path: '/admin/approvals', name: 'capability-approvals', component: () => import('./views/CapabilityApprovalView.vue'), meta: { admin: true } },
    { path: '/my/capabilities', name: 'my-capabilities', component: () => import('./views/CapabilityAdminView.vue'), props: { employee: true }, meta: { employee: true } },
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { public: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  if (PUBLIC_DEMO && to.name === 'login') return { name: 'home' }
  if (to.meta.public) return true
  const auth = useAuthStore()
  await auth.restore()
  if (!auth.authenticated) return { name: 'login', query: { redirect: to.fullPath } }
  const admin = auth.isAdmin
  // 管理员与员工各看各的界面：管理员不进员工提交页，员工不进平台管理页。
  if (to.meta.employee) return admin ? { name: 'capability-admin' } : true
  const adapterEntry = to.params.slug === 'platform-skill-adapter' || to.params.id === 'platform-skill-adapter'
  if ((to.meta.admin || adapterEntry) && !admin) return { name: 'home' }
  return true
})

export default router
