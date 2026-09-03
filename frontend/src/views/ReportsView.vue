<template>
  <div class="page-card">
    <h3 style="margin-top:0">AI 报告中心</h3>
    <el-table :data="reports" v-loading="loading" stripe>
      <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
      <el-table-column label="生成时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="view(row)">查看</el-button>
          <el-button link type="primary" @click="download(row)">下载 .md</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="current"
      :page-size="size"
      :total="total"
      layout="prev, pager, next, total"
      style="margin-top:12px;justify-content:flex-end"
      @current-change="load"
    />

    <el-drawer v-model="drawer" :title="viewing?.title" size="55%">
      <div class="msg-md" v-html="renderMarkdown(viewing?.content)"></div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getReport, listReports } from '../api'
import { formatDateTime } from '../utils/format'
import { renderMarkdown } from '../utils/markdown'

const reports = ref<any[]>([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)
const drawer = ref(false)
const viewing = ref<any>(null)

async function load() {
  loading.value = true
  try {
    const page = await listReports(current.value, size.value)
    reports.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loading.value = false
  }
}

async function view(row: any) {
  viewing.value = await getReport(row.id)
  drawer.value = true
}

function download(row: any) {
  getReport(row.id).then((detail) => {
    const blob = new Blob([detail.content || ''], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${detail.title || 'report'}.md`
    a.click()
    URL.revokeObjectURL(url)
  })
}

onMounted(load)
</script>
