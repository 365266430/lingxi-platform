<template>
  <div class="chat-page">
    <!-- 左侧：会话列表 -->
    <div class="session-panel page-card">
      <el-button type="primary" style="width:100%" @click="newChat">＋ 新对话</el-button>
      <el-input v-model="searchText" placeholder="搜索会话标题" clearable size="default" style="margin-top:10px">
        <template #prefix>🔍</template>
      </el-input>
      <div class="session-list">
        <div
          v-for="s in filteredSessions"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === currentSessionId }"
          @click="selectSession(s)"
        >
          <div class="session-title">{{ s.title || '未命名对话' }}</div>
          <div class="session-meta">
            <span>{{ agentName(s.agentType) }}</span>
            <span>
              <el-button link size="small" type="primary" @click.stop="exportSession(s)">导出</el-button>
              <el-button link size="small" type="danger" @click.stop="removeSession(s)">删除</el-button>
            </span>
          </div>
        </div>
        <el-empty v-if="!filteredSessions.length" :description="searchText ? '没有匹配的会话' : '暂无会话'" :image-size="60" />
      </div>
    </div>

    <!-- 右侧：对话区 -->
    <div class="chat-panel page-card">
      <div class="chat-toolbar">
        <el-select v-model="selectedAgent" style="width:180px" :disabled="sending" size="default">
          <el-option v-for="a in agents" :key="a.code" :value="a.code" :label="`${a.icon} ${a.name}`" />
        </el-select>
        <el-popover placement="bottom-start" :width="320" trigger="click">
          <template #reference>
            <el-button size="default" plain>⚡ 快捷指令</el-button>
          </template>
          <div class="tpl-list">
            <div v-for="t in templates" :key="t.id" class="tpl-item" @click="applyTemplate(t)">
              <span class="tpl-icon">{{ t.icon }}</span>
              <span>
                <div class="tpl-title">{{ t.title }}</div>
                <div class="tpl-content">{{ t.content }}</div>
              </span>
            </div>
            <el-empty v-if="!templates.length" description="暂无模板" :image-size="50" />
          </div>
        </el-popover>
        <span v-if="liveInfo" class="live-agent">{{ liveInfo }}</span>
        <span style="flex:1"></span>
        <el-button v-if="sending" size="default" type="danger" plain @click="stopStream">停止生成</el-button>
      </div>

      <div ref="msgListRef" class="msg-list">
        <template v-for="(m, idx) in renderedMessages" :key="idx">
          <!-- 用户消息 -->
          <div v-if="m.kind === 'user'" class="row user-row">
            <div class="chat-bubble-user">{{ m.text }}</div>
          </div>

          <!-- 助手文本 -->
          <div v-else-if="m.kind === 'assistant'" class="row ai-row">
            <div class="chat-bubble-ai"><div class="msg-md" v-html="renderMarkdown(m.text)"></div></div>
            <div v-if="m.id" class="feedback-row">
              <el-button
                link size="small"
                :type="m.feedback === 1 ? 'primary' : 'info'"
                @click="sendFeedback(m, 1)"
              >👍 {{ m.feedback === 1 ? '有帮助' : '' }}</el-button>
              <el-button
                link size="small"
                :type="m.feedback === -1 ? 'danger' : 'info'"
                @click="sendFeedback(m, -1)"
              >👎 {{ m.feedback === -1 ? '没帮助' : '' }}</el-button>
            </div>
          </div>

          <!-- 工具调用（历史） -->
          <div v-else-if="m.kind === 'toolcall'" class="row ai-row">
            <ToolCallCard :name="m.name" :arguments="m.args" />
          </div>
          <div v-else-if="m.kind === 'toolresult'" class="row ai-row">
            <ToolCallCard :name="m.name" :result="m.result" />
          </div>
        </template>

        <!-- 实时流块 -->
        <template v-if="live">
          <template v-for="(block, bi) in live.blocks" :key="'live-' + bi">
            <div v-if="block.type === 'text'" class="row ai-row">
              <div class="chat-bubble-ai">
                <div class="msg-md" v-html="renderMarkdown(block.text)"></div>
                <span v-if="block.cursor" class="cursor">▌</span>
              </div>
            </div>
            <div v-else class="row ai-row">
              <ToolCallCard
                v-for="(call, ci) in block.calls"
                :key="ci"
                :name="call.name"
                :arguments="call.arguments"
                :result="call.result"
              />
            </div>
          </template>
        </template>

        <div v-if="!renderedMessages.length && !live" class="empty-chat">
          <h3>🦊 你好，我是灵犀智能体</h3>
          <p>点击快捷指令或直接输入问题：</p>
          <el-space wrap>
            <el-button
              v-for="t in templates.slice(0, 4)" :key="t.id"
              size="small" round @click="quickSend(t.content)"
            >{{ t.icon }} {{ t.title }}</el-button>
          </el-space>
        </div>
      </div>

      <div class="input-area">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          :disabled="sending"
          placeholder="输入消息，Enter 发送 / Shift+Enter 换行"
          @keydown.enter.exact.prevent="send"
        />
        <el-button type="primary" :loading="sending" class="send-btn" @click="send">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteSession as apiDeleteSession,
  listAgents,
  listMessages,
  listPromptTemplates,
  listSessions,
  messageFeedback
} from '../api'
import { streamChat } from '../utils/sse'
import { renderMarkdown } from '../utils/markdown'
import { agentTypeText } from '../utils/format'
import ToolCallCard from '../components/ToolCallCard.vue'
import http from '../api/http'

interface SessionItem { id: string | number; title: string; agentType: string; messageCount: number; updatedAt: string }
interface LiveBlock {
  type: 'text' | 'tool'
  text?: string
  cursor?: boolean
  calls?: { id: string; name: string; arguments: string; result?: string }[]
}

const route = useRoute()
const sessions = ref<SessionItem[]>([])
const agents = ref<any[]>([])
const templates = ref<any[]>([])
const searchText = ref('')
const currentSessionId = ref<string | number | null>(null)
const selectedAgent = ref('supervisor')
const messages = ref<any[]>([])
const input = ref('')
const sending = ref(false)
const live = ref<{ blocks: LiveBlock[] } | null>(null)
const liveInfo = ref('')
const msgListRef = ref<HTMLElement>()
let abortFn: (() => void) | null = null

const filteredSessions = computed(() => {
  const kw = searchText.value.trim().toLowerCase()
  if (!kw) return sessions.value
  return sessions.value.filter((s) => (s.title || '').toLowerCase().includes(kw))
})

const renderedMessages = computed(() => {
  const out: any[] = []
  for (const m of messages.value) {
    if (m.role === 'user') {
      out.push({ kind: 'user', text: m.content })
    } else if (m.role === 'assistant') {
      if (m.toolCallsJson) {
        try {
          for (const call of JSON.parse(m.toolCallsJson)) {
            out.push({ kind: 'toolcall', name: call.name, args: call.arguments })
          }
        } catch (e) {
          /* ignore */
        }
      }
      if (m.content) {
        out.push({ kind: 'assistant', text: m.content, id: m.id, feedback: m.feedback })
      }
    } else if (m.role === 'tool' && m.content) {
      try {
        for (const r of JSON.parse(m.content)) {
          out.push({ kind: 'toolresult', name: r.name, result: r.responseData })
        }
      } catch (e) {
        /* ignore */
      }
    }
  }
  return out
})

function agentName(code: string) {
  return agentTypeText[code] || code || '-'
}

async function loadSessions() {
  const page = await listSessions()
  sessions.value = page?.records || []
}

async function loadAgents() {
  agents.value = (await listAgents()) || []
  if (!agents.value.find((a) => a.code === selectedAgent.value)) {
    selectedAgent.value = 'supervisor'
  }
}

async function loadTemplates() {
  templates.value = (await listPromptTemplates()) || []
}

async function selectSession(s: SessionItem) {
  currentSessionId.value = s.id
  selectedAgent.value = s.agentType || 'supervisor'
  messages.value = (await listMessages(s.id)) || []
  live.value = null
  scrollBottom()
}

function newChat() {
  currentSessionId.value = null
  messages.value = []
  live.value = null
  liveInfo.value = ''
}

async function removeSession(s: SessionItem) {
  await ElMessageBox.confirm('确认删除该会话及其全部消息？', '提示', { type: 'warning' })
  await apiDeleteSession(s.id)
  if (currentSessionId.value === s.id) newChat()
  await loadSessions()
}

function exportSession(s: SessionItem) {
  http.raw().get(`/chat/sessions/${s.id}/export`, { responseType: 'blob' }).then((resp) => {
    const blob = new Blob([resp.data], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `会话记录-${(s.title || '未命名').slice(0, 20)}.md`
    a.click()
    URL.revokeObjectURL(url)
  })
}

async function sendFeedback(m: any, val: number) {
  const next = m.feedback === val ? 0 : val
  await messageFeedback(m.id, next)
  m.feedback = next === 0 ? null : next
  ElMessage.success(next === 0 ? '已取消评价' : next > 0 ? '感谢反馈 👍' : '已记录反馈，我们会改进 👎')
}

function applyTemplate(t: any) {
  input.value = t.content
  document.querySelector<HTMLTextAreaElement>('.input-area textarea')?.focus()
}

function ensureLive() {
  if (!live.value) live.value = { blocks: [] }
  return live.value
}

function lastTextBlock(): LiveBlock {
  const l = ensureLive()
  const last = l.blocks[l.blocks.length - 1]
  if (last && last.type === 'text') return last
  const block: LiveBlock = { type: 'text', text: '', cursor: true }
  l.blocks.push(block)
  return block
}

async function send() {
  const content = input.value.trim()
  if (!content || sending.value) return
  input.value = ''
  await doSend(content)
}

function quickSend(text: string) {
  if (sending.value) return
  doSend(text)
}

async function doSend(content: string) {
  sending.value = true
  live.value = { blocks: [] }
  liveInfo.value = ''
  scrollBottom()

  abortFn = await streamChat({
    sessionId: currentSessionId.value,
    agentType: selectedAgent.value,
    content,
    onEvent: async (type, payload) => {
      if (type === 'start') {
        if (payload?.sessionId) currentSessionId.value = payload.sessionId
        liveInfo.value = `🤖 ${payload?.agentName || agentName(selectedAgent.value)} 正在思考…`
      } else if (type === 'message') {
        const block = lastTextBlock()
        block.text = (block.text || '') + (payload?.delta || '')
        scrollBottom()
      } else if (type === 'tool_call') {
        const l = ensureLive()
        if (l.blocks.length && l.blocks[l.blocks.length - 1].type === 'text') {
          l.blocks[l.blocks.length - 1].cursor = false
        }
        const calls = (payload?.calls || []).map((c: any) => ({
          id: c.id,
          name: c.name,
          arguments: c.arguments,
          result: undefined as string | undefined
        }))
        l.blocks.push({ type: 'tool', calls })
        liveInfo.value = `🔧 正在调用工具：${calls.map((c: any) => c.name).join(', ')}`
        scrollBottom()
      } else if (type === 'tool_result') {
        const l = ensureLive()
        const toolBlock = [...l.blocks].reverse().find((b) => b.type === 'tool') as LiveBlock | undefined
        if (toolBlock) {
          for (const r of payload?.results || []) {
            const call = (toolBlock.calls || []).find((c) => c.id === r.id || c.name === r.name)
            if (call) call.result = r.responseData
          }
        }
        liveInfo.value = '🤖 已获取工具结果，继续推理…'
        scrollBottom()
      } else if (type === 'done') {
        liveInfo.value = `✅ 完成（${payload?.rounds || 1} 轮推理 · ${payload?.costMs || 0}ms）`
        await refreshAfterDone()
      } else if (type === 'error') {
        ElMessage.error(payload?.message || '推理失败')
        liveInfo.value = ''
        const block = lastTextBlock()
        block.text = (block.text || '') + '\n\n⚠️ ' + (payload?.message || '推理失败')
        sending.value = false
      }
    }
  })
}

async function refreshAfterDone() {
  if (currentSessionId.value != null) {
    try {
      messages.value = (await listMessages(currentSessionId.value)) || []
    } catch (e) {
      /* ignore */
    }
  }
  await loadSessions()
  live.value = null
  liveInfo.value = ''
  sending.value = false
  scrollBottom()
}

function stopStream() {
  abortFn?.()
  abortFn = null
  sending.value = false
  liveInfo.value = '已停止生成'
}

function scrollBottom() {
  nextTick(() => {
    const el = msgListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

onMounted(async () => {
  await Promise.all([loadSessions(), loadAgents(), loadTemplates()])
  // 从运维中心「一键诊断」跳转过来：自动打开会话并发送预置提问
  const prompt = route.query.prompt as string
  const sessionId = route.query.sessionId as string
  if (sessionId) {
    const target = sessions.value.find((s) => String(s.id) === String(sessionId))
    if (target) {
      await selectSession(target)
      if (prompt) await doSend(prompt)
      return
    }
  }
  if (prompt) {
    if (route.query.agentType) selectedAgent.value = String(route.query.agentType)
    await doSend(prompt)
  }
})

onBeforeUnmount(() => {
  abortFn?.()
})
</script>

<style scoped>
.chat-page {
  display: flex;
  gap: 16px;
  height: calc(100vh - 100px);
}
.session-panel { width: 260px; flex-shrink: 0; display: flex; flex-direction: column; }
.session-list { margin-top: 12px; overflow-y: auto; flex: 1; }
.session-item {
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
}
.session-item:hover { background: #f3f5fb; }
.session-item.active { background: #eef1ff; }
.session-title { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-meta { display: flex; justify-content: space-between; align-items: center; color: #94a3b8; font-size: 12px; margin-top: 4px; }
.chat-panel { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-toolbar { display: flex; align-items: center; gap: 10px; border-bottom: 1px solid var(--el-border-color-lighter, #eef0f5); padding-bottom: 10px; }
.live-agent { color: var(--el-text-color-secondary, #64748b); font-size: 13px; }
.msg-list { flex: 1; overflow-y: auto; padding: 14px 4px; }
.row { display: flex; margin-bottom: 10px; }
.user-row { justify-content: flex-end; }
.ai-row { justify-content: flex-start; flex-direction: column; align-items: flex-start; }
.feedback-row { margin: -4px 0 0 6px; }
.feedback-row .el-button { padding: 2px 4px; }
.cursor { animation: blink 1s infinite; color: #4f6ef2; }
@keyframes blink { 50% { opacity: 0; } }
.empty-chat { text-align: center; color: var(--el-text-color-secondary, #94a3b8); margin-top: 12vh; }
.input-area { display: flex; gap: 10px; align-items: flex-end; padding-top: 10px; border-top: 1px solid var(--el-border-color-lighter, #eef0f5); }
.send-btn { height: 60px; width: 90px; }
.tpl-list { max-height: 320px; overflow-y: auto; }
.tpl-item { display: flex; gap: 10px; padding: 8px; border-radius: 8px; cursor: pointer; }
.tpl-item:hover { background: var(--el-fill-color-light, #f3f5fb); }
.tpl-icon { font-size: 20px; }
.tpl-title { font-size: 13px; font-weight: 600; }
.tpl-content { font-size: 12px; color: var(--el-text-color-secondary, #94a3b8); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 250px; }
</style>
