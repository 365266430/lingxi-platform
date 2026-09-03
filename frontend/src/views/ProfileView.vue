<template>
  <el-row :gutter="16" justify="center">
    <el-col :span="10">
      <div class="page-card">
        <h3 style="margin-top:0">👤 个人中心</h3>
        <el-descriptions :column="1" border style="margin-bottom:20px">
          <el-descriptions-item label="用户名">{{ profile?.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag size="small" :type="profile?.role === 'ADMIN' ? 'warning' : 'info'">
              {{ profile?.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ formatDateTime(profile?.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <h4>修改昵称</h4>
        <el-form label-width="80" style="max-width:360px">
          <el-form-item label="昵称">
            <el-input v-model="nickname" maxlength="32" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingName" @click="saveNickname">保存昵称</el-button>
          </el-form-item>
        </el-form>

        <el-divider />
        <h4>修改密码</h4>
        <el-form label-width="80" style="max-width:360px">
          <el-form-item label="原密码">
            <el-input v-model="pwdForm.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-64 位" />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="pwdForm.confirm" type="password" show-password />
          </el-form-item>
          <el-form-item>
            <el-button type="warning" :loading="savingPwd" @click="savePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword, myProfile, updateProfile } from '../api'
import { useAuthStore } from '../stores/auth'
import { formatDateTime } from '../utils/format'

const router = useRouter()
const auth = useAuthStore()
const profile = ref<any>(null)
const nickname = ref('')
const savingName = ref(false)
const savingPwd = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

async function load() {
  profile.value = await myProfile()
  nickname.value = profile.value?.nickname || ''
}

async function saveNickname() {
  if (!nickname.value.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  savingName.value = true
  try {
    profile.value = await updateProfile(nickname.value.trim())
    if (auth.user) auth.user.nickname = profile.value.nickname
    auth.persist()
    ElMessage.success('昵称已更新')
  } finally {
    savingName.value = false
  }
}

async function savePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写原密码与新密码')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  savingPwd.value = true
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码已修改，请重新登录')
    auth.clear()
    router.push('/login')
  } finally {
    savingPwd.value = false
  }
}

onMounted(load)
</script>
