<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="logo">🦊</div>
      <h2>注册账号</h2>
      <p class="subtitle">加入灵犀智能体平台</p>
      <el-form :model="form" label-position="top" size="large" @keyup.enter="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="字母/数字/下划线，3-32 位" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="选填" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="form.confirm" type="password" show-password placeholder="再次输入密码" />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="submit">
          注 册
        </el-button>
      </el-form>
      <div class="footer-link">
        已有账号？<router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', nickname: '', password: '', confirm: '' })

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写用户名与密码')
    return
  }
  if (form.password !== form.confirm) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await auth.register(form.username, form.password, form.nickname)
    ElMessage.success('注册成功，已自动登录')
    router.push('/dashboard')
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
  padding: 30px 36px 24px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
  text-align: center;
}
.logo { font-size: 40px; }
h2 { margin: 4px 0; }
.subtitle { color: #94a3b8; font-size: 13px; margin: 0 0 16px; }
.footer-link { margin-top: 12px; font-size: 13px; color: #64748b; }
.footer-link a { color: #4f6ef2; }
</style>
