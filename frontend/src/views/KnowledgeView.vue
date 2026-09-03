<template>
  <div class="kb-page">
    <div class="page-card" style="margin-bottom:16px">
      <div class="flex-between">
        <div>
          <el-select v-model="currentSpaceId" style="width:220px" @change="loadDocs">
            <el-option v-for="s in spaces" :key="s.id" :value="s.id" :label="s.name" />
          </el-select>
          <el-button style="margin-left:10px" @click="spaceDialog = true">＋ 新建空间</el-button>
        </div>
        <el-upload
          :show-file-list="false"
          :auto-upload="false"
          :on-change="onFileChange"
          accept=".md,.txt,.pdf,.docx,.doc,.html"
        >
          <el-button type="primary" :loading="uploading" :disabled="!currentSpaceId">
            ⬆ 上传文档（MD/TXT/PDF/DOCX）
          </el-button>
        </el-upload>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="14">
        <div class="page-card">
          <h3 style="margin-top:0">文档列表</h3>
          <el-table :data="documents" v-loading="loadingDocs" size="default" stripe>
            <el-table-column prop="filename" label="文件名" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ docStatusText[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="chunkCount" label="分块" width="70" />
            <el-table-column label="大小" width="90">
              <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
            </el-table-column>
            <el-table-column label="来源" width="90">
              <template #default="{ row }">{{ row.source === 'SEED' ? '内置' : '上传' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="download(row)">下载</el-button>
                <el-button
                  v-if="row.source !== 'SEED'" link type="warning" size="small"
                  :disabled="row.status === 'PROCESSING'" @click="reingest(row)"
                >重新解析</el-button>
                <el-button link type="danger" size="small" @click="removeDoc(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-alert
            v-if="documents.some((d) => d.status === 'PROCESSING')"
            type="info" :closable="false" style="margin-top:10px"
            title="文档正在后台解析/向量化（RabbitMQ 异步管道），状态会自动刷新。"
          />
        </div>
      </el-col>

      <el-col :span="10">
        <div class="page-card">
          <h3 style="margin-top:0">检索测试（RAG）</h3>
          <el-input v-model="searchQuery" placeholder="输入问题，如：磁盘空间不足怎么办" @keyup.enter="doSearch">
            <template #append>
              <el-button @click="doSearch" :loading="searching">检索</el-button>
            </template>
          </el-input>
          <div v-if="searchResult" class="msg-md" style="margin-top:14px" v-html="renderMarkdown(searchResult)"></div>
          <el-empty v-else description="检索结果将展示在这里" :image-size="70" style="margin-top:20px" />
        </div>
      </el-col>
    </el-row>

    <el-dialog v-model="spaceDialog" title="新建知识空间" width="420">
      <el-form label-width="70">
        <el-form-item label="名称"><el-input v-model="spaceForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="spaceForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="spaceDialog = false">取消</el-button>
        <el-button type="primary" @click="createSpaceNow">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import {
  createSpace,
  deleteDocument,
  kbSearch,
  listDocuments,
  listSpaces,
  reingestDocument
} from '../api'
import { renderMarkdown } from '../utils/markdown'
import { docStatusText, formatBytes } from '../utils/format'

const spaces = ref<any[]>([])
const currentSpaceId = ref<string | number | null>(null)
const documents = ref<any[]>([])
const loadingDocs = ref(false)
const uploading = ref(false)
const searchQuery = ref('')
const searchResult = ref('')
const searching = ref(false)
const spaceDialog = ref(false)
const spaceForm = ref({ name: '', description: '' })
let pollTimer: any = null

function statusTagType(status: string) {
  return status === 'COMPLETED' ? 'success' : status === 'FAILED' ? 'danger' : 'warning'
}

async function loadSpaces() {
  spaces.value = (await listSpaces()) || []
  if (spaces.value.length && !currentSpaceId.value) {
    currentSpaceId.value = spaces.value[0].id
  }
  await loadDocs()
}

async function loadDocs() {
  if (!currentSpaceId.value) return
  loadingDocs.value = true
  try {
    documents.value = (await listDocuments(currentSpaceId.value)) || []
  } finally {
    loadingDocs.value = false
  }
  schedulePoll()
}

function schedulePoll() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
  if (documents.value.some((d) => d.status === 'PROCESSING')) {
    pollTimer = setTimeout(loadDocs, 3000)
  }
}

async function onFileChange(file: UploadFile) {
  if (!currentSpaceId.value) {
    ElMessage.warning('请先选择知识空间')
    return
  }
  const raw = file.raw
  if (!raw) return
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', raw)
    await fetch(`/api/kb/documents/upload?spaceId=${currentSpaceId.value}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${readToken()}` },
      body: fd
    }).then(async (resp) => {
      if (!resp.ok) {
        const j = await resp.json().catch(() => null)
        throw new Error(j?.message || `HTTP ${resp.status}`)
      }
      return resp.json()
    })
    ElMessage.success('已上传，后台正在解析向量化')
    await loadDocs()
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

function readToken(): string {
  try {
    return JSON.parse(localStorage.getItem('lingxi.auth') || '{}')?.token || ''
  } catch (e) {
    return ''
  }
}

async function removeDoc(row: any) {
  await ElMessageBox.confirm(`确认删除文档「${row.filename}」及其向量？`, '提示', { type: 'warning' })
  await deleteDocument(row.id)
  ElMessage.success('已删除')
  await loadDocs()
}

async function reingest(row: any) {
  await ElMessageBox.confirm(
    `将清空「${row.filename}」的旧向量并重新解析向量化，确认继续？`, '重新解析', { type: 'warning' }
  )
  await reingestDocument(row.id)
  ElMessage.success('已提交重新解析')
  await loadDocs()
}

function download(row: any) {
  const token = readToken()
  fetch(`/api/kb/documents/${row.id}/download`, { headers: { Authorization: `Bearer ${token}` } })
    .then((r) => r.blob())
    .then((blob) => {
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = row.filename
      a.click()
      URL.revokeObjectURL(url)
    })
}

async function doSearch() {
  if (!searchQuery.value.trim()) return
  searching.value = true
  try {
    const data = await kbSearch(currentSpaceId.value, searchQuery.value.trim(), 4)
    searchResult.value = data?.markdown || '（无结果）'
  } finally {
    searching.value = false
  }
}

async function createSpaceNow() {
  if (!spaceForm.value.name.trim()) {
    ElMessage.warning('请输入空间名称')
    return
  }
  await createSpace(spaceForm.value.name.trim(), spaceForm.value.description)
  spaceDialog.value = false
  spaceForm.value = { name: '', description: '' }
  ElMessage.success('已创建')
  await loadSpaces()
}

onMounted(loadSpaces)
onBeforeUnmount(() => {
  if (pollTimer) clearTimeout(pollTimer)
})
</script>
