<template>
  <div class="admin-page">
    <div class="page-card agent-manager">
      <div class="section-head">
        <div>
          <h3>Agent 管理</h3>
          <p>配置企业 Agent 的人设、工具权限和上线状态，变更即时影响新对话。</p>
        </div>
        <el-button type="primary" @click="openCreate">＋ 新建 Agent</el-button>
      </div>
      <el-table :data="agents" v-loading="loadingAgents" stripe>
        <el-table-column label="Agent" min-width="190">
          <template #default="{ row }"><span class="agent-title">{{ row.icon }} {{ row.name }}</span><small>{{ row.code }}</small></template>
        </el-table-column>
        <el-table-column prop="description" label="职责说明" min-width="240" show-overflow-tooltip />
        <el-table-column label="工具权限" min-width="260">
          <template #default="{ row }"><el-tag v-for="tool in row.tools || []" :key="tool" size="small" effect="plain" class="tool-tag">{{ tool }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-switch :model-value="row.enabled === 1" @change="(v: any) => changeAgentStatus(row, v)" /></template>
        </el-table-column>
        <el-table-column label="操作" width="145">
          <template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button v-if="row.builtin !== 1" link type="danger" @click="removeAgent(row)">删除</el-button></template>
        </el-table-column>
      </el-table>
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="page-card">
          <h3 style="margin-top:0">用户管理</h3>
          <el-table :data="users" v-loading="loadingUsers" stripe>
            <el-table-column prop="username" label="用户名" min-width="120" />
            <el-table-column prop="nickname" label="昵称" min-width="110" />
            <el-table-column label="角色" width="130"><template #default="{ row }"><el-select :model-value="row.role" size="small" @change="(v: string) => changeRole(row, v)"><el-option label="ADMIN" value="ADMIN" /><el-option label="USER" value="USER" /></el-select></template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="{ row }"><el-switch :model-value="row.enabled === 1" :disabled="String(row.id) === String(auth.user?.id)" @change="(v: any) => changeStatus(row, v)" /></template></el-table-column>
            <el-table-column label="注册时间" width="170"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="page-card">
          <h3 style="margin-top:0">模型配置（热更新）</h3>
          <el-form label-width="130" v-loading="loadingConfig">
            <el-form-item label="供应商"><el-radio-group v-model="config.provider"><el-radio-button value="mock">Mock（离线演示）</el-radio-button><el-radio-button value="openai">OpenAI 兼容</el-radio-button></el-radio-group></el-form-item>
            <el-form-item label="覆盖环境配置"><el-switch v-model="config.runtimeOverride" /><span class="hint">开启后以下配置优先于 application.yml</span></el-form-item>
            <el-form-item label="Base URL"><el-input v-model="config.baseUrl" placeholder="如 https://api.deepseek.com" /></el-form-item>
            <el-form-item label="API Key"><el-input v-model="config.apiKey" type="password" show-password placeholder="留空保持不变" /></el-form-item>
            <el-form-item label="模型"><el-input v-model="config.model" placeholder="如 deepseek-chat / qwen-plus" /></el-form-item>
            <el-form-item label="Temperature"><el-slider v-model="config.temperature" :min="0" :max="2" :step="0.1" style="width:220px" /></el-form-item>
            <el-form-item label="Embedding URL"><el-input v-model="config.embeddingBaseUrl" placeholder="可选，缺省用离线词袋向量" /></el-form-item>
            <el-form-item label="Embedding Key"><el-input v-model="config.embeddingApiKey" type="password" show-password placeholder="可选" /></el-form-item>
            <el-form-item label="Embedding 模型"><el-input v-model="config.embeddingModel" placeholder="如 text-embedding-v3" /></el-form-item>
            <el-form-item><el-button type="primary" :loading="saving" @click="save">保存并热更新</el-button><el-button :loading="testing" @click="test">连通性测试</el-button></el-form-item>
          </el-form>
          <el-alert v-if="testResult" :type="testResult.ok ? 'success' : 'error'" :closable="false" :title="testResult.ok ? `连通正常：${testResult.latencyMs}ms，模型回复：${(testResult.reply || '').slice(0, 60)}` : `连接失败：${testResult.message}`" />
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑 Agent' : '新建 Agent'" width="680px" destroy-on-close>
      <el-form :model="agentForm" label-width="105px">
        <el-row :gutter="12"><el-col :span="16"><el-form-item label="名称" required><el-input v-model="agentForm.name" placeholder="例如：客服质检助手" /></el-form-item></el-col><el-col :span="8"><el-form-item label="图标"><el-input v-model="agentForm.icon" placeholder="🤖" /></el-form-item></el-col></el-row>
        <el-form-item label="编码" required><el-input v-model="agentForm.code" :disabled="editing" placeholder="仅小写字母、数字、下划线" /></el-form-item>
        <el-form-item label="职责说明"><el-input v-model="agentForm.description" /></el-form-item>
        <el-form-item label="系统提示词" required><el-input v-model="agentForm.systemPrompt" type="textarea" :rows="7" placeholder="定义 Agent 的角色、边界和回答规范" /></el-form-item>
        <el-form-item label="工具权限" required><el-checkbox-group v-model="agentForm.tools"><el-checkbox v-for="tool in toolOptions" :key="tool.value" :label="tool.value">{{ tool.label }}</el-checkbox></el-checkbox-group></el-form-item>
        <el-form-item label="启用"><el-switch v-model="agentForm.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="savingAgent" @click="saveAgent">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminAgents, adminCreateAgent, adminDeleteAgent, adminUpdateAgent, adminUpdateAgentStatus, adminUpdateUserRole, adminUpdateUserStatus, adminUsers, getModelConfig, saveModelConfig, testModelConfig } from '../api'
import { formatDateTime } from '../utils/format'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const agents = ref<any[]>([])
const users = ref<any[]>([])
const loadingAgents = ref(false)
const loadingUsers = ref(false)
const loadingConfig = ref(false)
const saving = ref(false)
const testing = ref(false)
const savingAgent = ref(false)
const dialogVisible = ref(false)
const editing = ref<any>(null)
const testResult = ref<any>(null)
const toolOptions = [
  { value: 'search_knowledge_base', label: '知识库检索' }, { value: 'query_alerts', label: '告警查询' },
  { value: 'query_metrics', label: '指标查询' }, { value: 'query_cmdb', label: 'CMDB 查询' },
  { value: 'run_readonly_sql', label: '只读 SQL' }, { value: 'web_search', label: '联网搜索' },
  { value: 'save_report', label: '保存报告' }, { value: 'send_notification', label: '发送通知' }, { value: 'get_current_time', label: '当前时间' }
]
const agentForm = ref<any>({ name: '', code: '', icon: '🤖', description: '', systemPrompt: '', tools: [], enabled: true })
const config = ref<any>({ provider: 'mock', runtimeOverride: false, baseUrl: '', apiKey: '', model: '', temperature: 0.3, embeddingBaseUrl: '', embeddingApiKey: '', embeddingModel: '' })

async function loadAgents() { loadingAgents.value = true; try { agents.value = (await adminAgents()) || [] } finally { loadingAgents.value = false } }
async function loadUsers() { loadingUsers.value = true; try { users.value = (await adminUsers(1, 50))?.records || [] } finally { loadingUsers.value = false } }
async function loadConfig() { loadingConfig.value = true; try { const data = await getModelConfig(); config.value = { ...config.value, ...(data?.config || {}), apiKey: '', embeddingApiKey: '' } } finally { loadingConfig.value = false } }
function openCreate() { editing.value = null; agentForm.value = { name: '', code: '', icon: '🤖', description: '', systemPrompt: '', tools: ['get_current_time'], enabled: true }; dialogVisible.value = true }
function openEdit(row: any) { editing.value = row; agentForm.value = { ...row, tools: [...(row.tools || [])], enabled: row.enabled === 1 }; dialogVisible.value = true }
async function saveAgent() { if (!agentForm.value.name || !agentForm.value.systemPrompt || !agentForm.value.tools.length) { ElMessage.warning('请填写名称、系统提示词并选择至少一个工具'); return }; savingAgent.value = true; try { const payload = { ...agentForm.value, enabled: agentForm.value.enabled ? 1 : 0, toolsJson: JSON.stringify(agentForm.value.tools) }; if (editing.value) await adminUpdateAgent(editing.value.id, payload); else await adminCreateAgent(payload); ElMessage.success('Agent 已保存'); dialogVisible.value = false; await loadAgents() } finally { savingAgent.value = false } }
async function changeAgentStatus(row: any, enabled: boolean) { await adminUpdateAgentStatus(row.id, enabled ? 1 : 0); ElMessage.success(enabled ? 'Agent 已启用' : 'Agent 已停用'); await loadAgents() }
async function removeAgent(row: any) { await ElMessageBox.confirm(`确认删除 Agent「${row.name}」？`, '提示', { type: 'warning' }); await adminDeleteAgent(row.id); ElMessage.success('Agent 已删除'); await loadAgents() }
async function changeRole(row: any, role: string) { await adminUpdateUserRole(row.id, role); ElMessage.success('角色已更新'); await loadUsers() }
async function changeStatus(row: any, enabled: boolean) { await adminUpdateUserStatus(row.id, enabled ? 1 : 0); ElMessage.success('状态已更新'); await loadUsers() }
async function save() { saving.value = true; try { await saveModelConfig(config.value); ElMessage.success('已保存并热更新模型实例'); await loadConfig() } finally { saving.value = false } }
async function test() { testing.value = true; testResult.value = null; try { testResult.value = await testModelConfig() } finally { testing.value = false } }
onMounted(async () => { await Promise.all([loadAgents(), loadUsers(), loadConfig()]) })
</script>

<style scoped>
.section-head { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:16px; }
.section-head h3 { margin:0 0 6px; }
.section-head p { margin:0; color:var(--lingxi-muted); font-size:12px; }
.agent-manager { margin-bottom:16px; }
.agent-title { display:block; font-weight:600; }
.agent-manager small { color:var(--lingxi-muted); font-size:11px; margin-left:23px; }
.tool-tag { margin:2px 4px 2px 0; }
.hint { color:#94a3b8; font-size:12px; margin-left:10px; }
.el-checkbox { margin-right:18px; margin-left:0; }
@media(max-width:760px) { .section-head { align-items:flex-start; flex-direction:column; } }
</style>
