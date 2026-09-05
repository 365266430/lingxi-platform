<template>
  <div class="layout">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <aside class="sidebar glass-panel">
      <router-link to="/dashboard" class="brand"><span class="brand-mark">灵</span><span class="brand-copy">灵犀<small>LingXi Intelligence</small></span></router-link>
      <div class="nav-label">工作空间</div>
      <nav aria-label="主导航" class="menu">
        <router-link v-for="item in navigation" :key="item.path" :to="item.path" class="nav-item" :class="{ active: route.path === item.path }" :aria-current="route.path === item.path ? 'page' : undefined">
          <el-icon><component :is="item.icon" /></el-icon><span>{{ item.label }}</span>
        </router-link>
      </nav>
      <div class="sidebar-note"><el-icon><MagicStick /></el-icon><strong>让复杂，变简单。</strong><p>连接知识与工具，<br>把想法交给灵犀。</p><router-link to="/chat">开启智能对话 ↗</router-link></div>
      <div class="sidebar-footer">LINGXI / AI WORKSPACE</div>
    </aside>
    <div class="workspace">
      <header class="header">
        <div class="breadcrumb">工作空间 <span>/</span> <strong>{{ route.meta?.title || '灵犀' }}</strong></div>
        <div class="header-actions glass-panel">
          <button class="theme-button" :aria-label="isDark ? '切换为亮色' : '切换为暗色'" :title="isDark ? '切换为亮色' : '切换为暗色'" @click="toggleTheme"><el-icon><Moon v-if="isDark" /><Sunny v-else /></el-icon></button>
          <span class="action-divider"></span>
          <el-dropdown @command="onUserCommand">
            <button class="user-chip"><el-avatar :size="30">{{ avatarText }}</el-avatar><span class="user-name">{{ auth.user?.nickname || auth.user?.username }}</span><el-icon><ArrowDown /></el-icon></button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item command="profile">个人中心</el-dropdown-item><el-dropdown-item divided command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
        </div>
      </header>
      <main id="main-content" class="main" tabindex="-1"><router-view /></main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { Odometer, ChatDotRound, Collection, Monitor, Document, Setting, MagicStick, Moon, Sunny, ArrowDown } from '@element-plus/icons-vue'
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const isDark = ref(document.documentElement.classList.contains('dark'))
const navigation = computed(() => [
  { path: '/dashboard', label: '仪表盘', icon: Odometer },
  { path: '/chat', label: '智能对话', icon: ChatDotRound },
  { path: '/knowledge', label: '知识中心', icon: Collection },
  { path: '/ops', label: '运维中心', icon: Monitor },
  { path: '/reports', label: '报告中心', icon: Document },
  ...(auth.isAdmin ? [{ path: '/admin', label: '系统管理', icon: Setting }] : [])
])
const avatarText = computed(() => (auth.user?.nickname || auth.user?.username || 'U').slice(0, 1).toUpperCase())
function toggleTheme() {
  isDark.value = !isDark.value
  document.documentElement.classList.toggle('dark', isDark.value)
  localStorage.setItem('lingxi.theme', isDark.value ? 'dark' : 'light')
}
function onUserCommand(cmd: string) {
  if (cmd === 'logout') { auth.clear(); router.push('/login') }
  else if (cmd === 'profile') router.push('/profile')
}
</script>

<style scoped>
.layout { display:flex; height:100dvh; padding:16px; gap:24px; }
.sidebar { width:224px; flex-shrink:0; display:flex; flex-direction:column; padding:26px 14px 18px; border-radius:28px; }
.brand { display:flex; gap:12px; align-items:center; padding:0 12px 36px; text-decoration:none; color:var(--lingxi-dark); }
.brand-mark { display:grid; place-items:center; width:42px; height:46px; border-radius:16px; color:white; background:linear-gradient(145deg,#5da8ff,#5267da); box-shadow:inset 0 1px 2px #fff8,0 6px 16px #5f7dbd30; font-size:23px; }
.brand-copy { font-size:21px; font-weight:650; letter-spacing:2px; }
.brand-copy small { display:block; font-size:9px; letter-spacing:1px; color:var(--lingxi-muted); margin-top:5px; font-weight:400; }
.nav-label { font-size:10px; color:var(--lingxi-muted); padding:0 18px 12px; letter-spacing:2px; }
.menu { display:grid; gap:8px; }
.nav-item { display:flex; align-items:center; gap:14px; min-height:48px; padding:0 18px; border:1px solid transparent; border-radius:17px; text-decoration:none; color:var(--lingxi-muted); font-size:13px; transition:background .2s,color .2s; }
.nav-item .el-icon { font-size:19px; }
.nav-item:hover { background:var(--lingxi-hover); color:var(--lingxi-primary); }
.nav-item.active { background:var(--lingxi-selected); color:var(--lingxi-primary); border-color:var(--lingxi-edge); box-shadow:inset 0 1px 0 #fff6,0 4px 12px #5473a410; font-weight:600; }
.sidebar-note { margin:auto 10px 20px; padding-top:40px; font-size:12px; }
.sidebar-note > .el-icon { font-size:24px; color:var(--lingxi-primary); display:block; margin-bottom:15px; }
.sidebar-note strong { font-weight:600; }
.sidebar-note p { color:var(--lingxi-muted); line-height:1.9; }
.sidebar-note a { color:var(--lingxi-primary); text-decoration:none; display:block; margin-top:20px; }
.sidebar-footer { font-size:8px; color:var(--lingxi-muted); padding:20px 12px 0; letter-spacing:1.6px; border-top:1px solid var(--lingxi-line); }
.workspace { flex:1; min-width:0; display:flex; flex-direction:column; }
.header { min-height:66px; display:flex; align-items:center; justify-content:space-between; padding:0 16px 12px 6px; background:transparent; }
.breadcrumb { font-size:11px; color:var(--lingxi-muted); }
.breadcrumb span { margin:0 12px; opacity:.5; }
.breadcrumb strong { color:var(--lingxi-dark); font-weight:500; }
.header-actions { display:flex; align-items:center; gap:10px; padding:6px 10px; border-radius:25px; }
.theme-button,.user-chip { border:0; background:none; display:flex; align-items:center; justify-content:center; color:var(--lingxi-dark); cursor:pointer; }
.theme-button { width:32px; height:32px; border-radius:50%; font-size:18px; }
.theme-button:hover { background:var(--lingxi-hover); }
.action-divider { height:20px; width:1px; background:var(--lingxi-line); }
.user-chip { gap:10px; font-size:12px; border-radius:18px; padding:0; }
.user-chip .el-avatar { background:#657ca2; font-size:12px; }
.main { flex:1; min-height:0; overflow:auto; padding:14px 16px 28px 6px; }
.skip-link { position:fixed; top:-80px; left:30px; z-index:100; background:var(--lingxi-card); padding:14px; border-radius:10px; }
.skip-link:focus { top:10px; }
@media(max-width:1100px) { .layout { gap:16px; } .sidebar { width:190px; } .brand { padding-left:6px; gap:8px; } }
@media(max-width:760px) {
  .layout { padding:8px; gap:0; }
  .sidebar { position:fixed; left:10px; right:10px; bottom:max(10px,env(safe-area-inset-bottom)); width:auto; padding:7px 4px; border-radius:24px; z-index:20; }
  .brand,.nav-label,.sidebar-note,.sidebar-footer { display:none; }
  .menu { display:flex; gap:2px; }
  .nav-item { flex:1; min-width:0; min-height:52px; flex-direction:column; justify-content:center; padding:5px 0; gap:5px; font-size:9px; border-radius:17px; }
  .header { min-height:60px; padding:0 8px 6px; }
  .breadcrumb { font-size:10px; } .breadcrumb span { margin:0 6px; }
  .user-name { display:none; }
  .main { padding:14px 8px calc(100px + env(safe-area-inset-bottom)); }
}
</style>
