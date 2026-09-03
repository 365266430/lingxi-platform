<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6"><StatCard icon="🔥" label="活跃告警" :value="summary.activeAlerts ?? '-'" color="#f56c6c" /></el-col>
      <el-col :span="6"><StatCard icon="🚨" label="P1 严重告警" :value="summary.criticalAlerts ?? '-'" color="#f97316" /></el-col>
      <el-col :span="6"><StatCard icon="💬" label="24h 对话消息" :value="summary.messages24h ?? '-'" color="#4f6ef2" /></el-col>
      <el-col :span="6"><StatCard icon="📚" label="知识文档" :value="summary.totalDocuments ?? '-'" color="#10b981" /></el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="14">
        <div class="page-card">
          <h3 style="margin-top:0">近 7 天告警趋势</h3>
          <div ref="trendRef" style="height:320px"></div>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="page-card">
          <h3 style="margin-top:0">活跃告警级别分布</h3>
          <div ref="severityRef" style="height:320px"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="10">
        <div class="page-card">
          <h3 style="margin-top:0">智能体会话分布</h3>
          <div ref="agentRef" style="height:280px"></div>
        </div>
      </el-col>
      <el-col :span="14">
        <div class="page-card">
          <h3 style="margin-top:0">平台概况</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="注册用户">{{ summary.totalUsers ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="对话总数">{{ summary.totalSessions ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="知识分块">{{ summary.totalChunks ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="AI 报告">{{ summary.totalReports ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="累计消息">{{ summary.totalMessages ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="回答反馈">
              👍 {{ summary.feedbackUp ?? 0 }} / 👎 {{ summary.feedbackDown ?? 0 }}
            </el-descriptions-item>
          </el-descriptions>
          <el-alert type="info" :closable="false" style="margin-top:14px"
            title="提示：到「智能对话」试试『订单服务 CPU 告警，帮我诊断一下』，可以完整看到 Agent 的工具调用与推理过程。" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import StatCard from '../components/StatCard.vue'
import { dashboardSummary } from '../api'

const summary = reactive<any>({})
const trendRef = ref<HTMLElement>()
const severityRef = ref<HTMLElement>()
const agentRef = ref<HTMLElement>()
const charts: echarts.ECharts[] = []

async function load() {
  const data = await dashboardSummary()
  Object.assign(summary, data || {})
  renderCharts()
}

function renderCharts() {
  if (trendRef.value) {
    const trend = summary.alertsTrend || []
    const chart = echarts.init(trendRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 40, right: 20, top: 30, bottom: 30 },
      xAxis: { type: 'category', data: trend.map((t: any) => t.day) },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        name: '告警数',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.15 },
        data: trend.map((t: any) => t.count),
        itemStyle: { color: '#4f6ef2' }
      }]
    })
    charts.push(chart)
  }
  if (severityRef.value) {
    const dist = summary.severityDist || []
    const chart = echarts.init(severityRef.value)
    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['40%', '65%'],
        data: dist.map((d: any) => ({ name: d.severity, value: d.count })),
        color: ['#f56c6c', '#e6a23c', '#909399'],
        label: { formatter: '{b}: {c}' }
      }]
    })
    charts.push(chart)
  }
  if (agentRef.value) {
    const dist = summary.agentDist || []
    const chart = echarts.init(agentRef.value)
    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: '60%',
        data: dist.map((d: any) => ({ name: agentName(d.agentType), value: d.count })),
        label: { formatter: '{b}: {c}' }
      }]
    })
    charts.push(chart)
  }
}

function agentName(code: string) {
  return ({
    supervisor: '全能管家',
    knowledge_qa: '知识问答',
    ops_diagnosis: '运维诊断',
    data_analysis: '数据分析',
    report: '报告撰写'
  } as Record<string, string>)[code] || code
}

function onResize() {
  charts.forEach((c) => c.resize())
}

onMounted(async () => {
  await load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
})
</script>
