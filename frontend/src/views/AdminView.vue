<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="page-card">
          <h3 style="margin-top:0">用户管理</h3>
          <el-table :data="users" v-loading="loadingUsers" stripe size="default">
            <el-table-column prop="username" label="用户名" min-width="120" />
            <el-table-column prop="nickname" label="昵称" min-width="110" />
            <el-table-column label="角色" width="130">
              <template #default="{ row }">
                <el-select :model-value="row.role" size="small" @change="(v: string) => changeRole(row, v)">
                  <el-option label="ADMIN" value="ADMIN" />
                  <el-option label="USER" value="USER" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-switch
                  :model-value="row.enabled === 1"
                  :disabled="String(row.id) === String(auth.user?.id)"
                  @change="(v: any) => changeStatus(row, v)"
                />
              </template>
            </el-table-column>
            <el-table-column label="注册时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <el-col :span="12">
        <div class="page-card">
          <h3 style="margin-top:0">模型配置（热更新）</h3>
          <el-form label-width="130" v-loading="loadingConfig">
            <el-form-item label="供应商">
              <el-radio-group v-model="config.provider">
                <el-radio-button value="mock">Mock（离线演示）</el-radio-button>
                <el-radio-button value="openai">OpenAI 兼容</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="覆盖环境配置">
              <el-switch v-model="config.runtimeOverride" />
              <span class="hint">开启后以下配置优先于 application.yml</span>
            </el-form-item>
            <el-form-item label="Base URL">
              <el-input v-model="config.baseUrl" placeholder="如 https://api.deepseek.com" />
            </el-form-item>
            <el-form-item label="API Key">
              <el-input v-model="config.apiKey" type="password" show-password placeholder="留空保持不变" />
            </el-form-item>
            <el-form-item label="模型">
              <el-input v-model="config.model" placeholder="如 deepseek-chat / qwen-plus" />
            </el-form-item>
            <el-form-item label="Temperature">
              <el-slider v-model="config.temperature" :min="0" :max="2" :step="0.1" style="width:220px" />
            </el-form-item>
            <el-form-item label="Embedding URL">
              <el-input v-model="config.embeddingBaseUrl" placeholder="可选，缺省用离线词袋向量" />
            </el-form-item>
            <el-form-item label="Embedding Key">
              <el-input v-model="config.embeddingApiKey" type="password" show-password placeholder="可选" />
            </el-form-item>
            <el-form-item label="Embedding 模型">
              <el-input v-model="config.embeddingModel" placeholder="如 text-embedding-v3" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="save">保存并热更新</el-button>
              <el-button :loading="testing" @click="test">连通性测试</el-button>
            </el-form-item>
          </el-form>
          <el-alert
            v-if="testResult"
            :type="testResult.ok ? 'success' : 'error'"
            :closable="false"
            :title="testResult.ok
              ? `连通正常：${testResult.latencyMs}ms，模型回复：${(testResult.reply || '').slice(0, 60)}`
              : `连接失败：${testResult.message}`"
          />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  adminUpdateUserRole,
  adminUpdateUserStatus,
  adminUsers,
  getModelConfig,
  saveModelConfig,
  testModelConfig
} from '../api'
import { formatDateTime } from '../utils/format'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const users = ref<any[]>([])
const loadingUsers = ref(false)
const loadingConfig = ref(false)
const saving = ref(false)
const testing = ref(false)
const testResult = ref<any>(null)
const config = ref<any>({
  provider: 'mock',
  runtimeOverride: false,
  baseUrl: '',
  apiKey: '',
  model: '',
  temperature: 0.3,
  embeddingBaseUrl: '',
  embeddingApiKey: '',
  embeddingModel: ''
})

async function loadUsers() {
  loadingUsers.value = true
  try {
    const page = await adminUsers(1, 50)
    users.value = page?.records || []
  } finally {
    loadingUsers.value = false
  }
}

async function loadConfig() {
  loadingConfig.value = true
  try {
    const data = await getModelConfig()
    const c = data?.config || {}
    config.value = { ...config.value, ...c, apiKey: '' }
  } finally {
    loadingConfig.value = false
  }
}

async function changeRole(row: any, role: string) {
  await adminUpdateUserRole(row.id, role)
  ElMessage.success('角色已更新')
  await loadUsers()
}

async function changeStatus(row: any, enabled: boolean) {
  await adminUpdateUserStatus(row.id, enabled ? 1 : 0)
  ElMessage.success('状态已更新')
  await loadUsers()
}

async function save() {
  saving.value = true
  try {
    await saveModelConfig(config.value)
    ElMessage.success('已保存并热更新模型实例')
    await loadConfig()
  } finally {
    saving.value = false
  }
}

async function test() {
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await testModelConfig()
  } finally {
    testing.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadConfig()])
})
</script>

<style scoped>
.hint { color: #94a3b8; font-size: 12px; margin-left: 10px; }
</style>
