<template>
  <el-container class="layout">
    <el-aside width="220px" class="sidebar">
      <div class="brand">🦊 灵犀智能体平台</div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#1f2937"
        text-color="#cbd5e1"
        active-text-color="#ffffff"
        class="menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon><span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon><span>智能对话</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Collection /></el-icon><span>知识中心</span>
        </el-menu-item>
        <el-menu-item index="/ops">
          <el-icon><Monitor /></el-icon><span>运维中心</span>
        </el-menu-item>
        <el-menu-item index="/reports">
          <el-icon><Document /></el-icon><span>报告中心</span>
        </el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin">
          <el-icon><Setting /></el-icon><span>系统管理</span>
        </el-menu-item>
      </el-menu>
      <div class="sidebar-footer">Java + AI Agent · v1.1.0</div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ route.meta?.title || '灵犀' }}</div>
        <div class="header-actions">
          <el-tooltip :content="isDark ? '切换为亮色' : '切换为暗色'" placement="bottom">
            <el-button text @click="toggleTheme">{{ isDark ? '🌙' : '☀️' }}</el-button>
          </el-tooltip>
          <el-dropdown @command="onUserCommand">
            <span class="user-chip">
              <el-avatar :size="28" style="background:#4f6ef2">{{ avatarText }}</el-avatar>
              <span style="margin-left:8px">{{ auth.user?.nickname || auth.user?.username }}</span>
              <el-tag v-if="auth.isAdmin" size="small" type="warning" style="margin-left:8px">管理员</el-tag>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main"><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { Odometer, ChatDotRound, Collection, Monitor, Document, Setting } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const isDark = ref(document.documentElement.classList.contains('dark'))

const activeMenu = computed(() => route.path)
const avatarText = computed(() => (auth.user?.nickname || auth.user?.username || 'U').slice(0, 1).toUpperCase())

function toggleTheme() {
  isDark.value = !isDark.value
  document.documentElement.classList.toggle('dark', isDark.value)
  localStorage.setItem('lingxi.theme', isDark.value ? 'dark' : 'light')
}

function onUserCommand(cmd: string) {
  if (cmd === 'logout') {
    auth.clear()
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.sidebar {
  background: linear-gradient(180deg, #1f2937, #111827);
  display: flex;
  flex-direction: column;
}
.brand {
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  padding: 20px 18px 14px;
  letter-spacing: 0.5px;
}
.menu { border-right: none; flex: 1; }
.menu :deep(.el-menu-item.is-active) { background: #4f6ef2 !important; }
.sidebar-footer { color: #64748b; font-size: 12px; padding: 14px 18px; }
.header {
  background: var(--el-bg-color, #fff);
  border-bottom: 1px solid var(--el-border-color-lighter, #eef0f5);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-actions { display: flex; align-items: center; gap: 8px; }
.header-title { font-size: 16px; font-weight: 600; }
.user-chip { display: flex; align-items: center; cursor: pointer; outline: none; }
.main { padding: 18px; overflow: auto; }
</style>
