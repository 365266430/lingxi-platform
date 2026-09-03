import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue') },
    { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue') },
    {
      path: '/',
      component: () => import('../layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '仪表盘' } },
        { path: 'chat', name: 'chat', component: () => import('../views/ChatView.vue'), meta: { title: '智能对话' } },
        { path: 'knowledge', name: 'knowledge', component: () => import('../views/KnowledgeView.vue'), meta: { title: '知识中心' } },
        { path: 'ops', name: 'ops', component: () => import('../views/OpsView.vue'), meta: { title: '运维中心' } },
        { path: 'reports', name: 'reports', component: () => import('../views/ReportsView.vue'), meta: { title: '报告中心' } },
        { path: 'profile', name: 'profile', component: () => import('../views/ProfileView.vue'), meta: { title: '个人中心' } },
        { path: 'admin', name: 'admin', component: () => import('../views/AdminView.vue'), meta: { title: '系统管理', adminOnly: true } }
      ]
    },
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue') }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  const requiresAuth = to.matched.every((r) => r.name !== 'login' && r.name !== 'register') && to.name !== 'notfound'
  if (requiresAuth && to.path !== '/login' && to.path !== '/register') {
    if (!auth.accessToken) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    if (to.meta?.adminOnly && auth.user?.role !== 'ADMIN') {
      return { path: '/dashboard' }
    }
  }
  return true
})

export default router
