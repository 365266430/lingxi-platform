<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="logo">🦊</div>
      <h2>灵犀智能体平台</h2>
      <p class="subtitle">Java + AI Agent 企业级智能运维平台</p>
      <el-form :model="form" label-position="top" size="large" @keyup.enter="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>
      <div class="tips">
        <el-space wrap>
          <el-button size="small" @click="fill('admin', 'admin123')">管理员演示账号</el-button>
          <el-button size="small" @click="fill('opsuser', 'user123')">普通用户演示账号</el-button>
        </el-space>
      </div>
      <div class="footer-link">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

function fill(u: string, p: string) {
  form.username = u
  form.password = p
}

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push((route.query.redirect as string) || '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #4f6ef2 0%, #7c3aed 55%, #1f2937 100%);
}
.login-card {
  width: 400px;
  background: #fff;
  border-radius: 14px;
  padding: 34px 36px 26px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
  text-align: center;
}
.logo { font-size: 44px; }
h2 { margin: 6px 0 4px; }
.subtitle { color: #94a3b8; font-size: 13px; margin: 0 0 18px; }
.tips { margin-top: 16px; }
.footer-link { margin-top: 12px; font-size: 13px; color: #64748b; }
.footer-link a { color: #4f6ef2; }
</style>
