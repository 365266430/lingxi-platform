<template>
  <div class="page-card">
    <el-tabs v-model="tab">
      <el-tab-pane label="告警中心" name="alerts">
        <div class="flex-between" style="margin-bottom:12px">
          <el-space>
            <el-select v-model="alertFilter.status" style="width:130px" @change="loadAlerts">
              <el-option label="全部状态" value="" />
              <el-option label="活跃" value="ACTIVE" />
              <el-option label="已恢复" value="RESOLVED" />
            </el-select>
            <el-select v-model="alertFilter.severity" style="width:110px" @change="loadAlerts">
              <el-option label="全部级别" value="" />
              <el-option label="P1" value="P1" />
              <el-option label="P2" value="P2" />
              <el-option label="P3" value="P3" />
            </el-select>
          </el-space>
          <el-tag type="info">活跃告警 {{ total }}</el-tag>
        </div>
        <el-table :data="alerts" v-loading="loadingAlerts" stripe>
          <el-table-column label="级别" width="80">
            <template #default="{ row }">
              <el-tag :color="severityColor[row.severity]" style="color:#fff;border:none" size="small">
                {{ row.severity }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="告警标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="serviceName" label="服务" width="140" />
          <el-table-column prop="hostName" label="主机" width="140" />
          <el-table-column label="指标" min-width="150">
            <template #default="{ row }">{{ row.metricName }} = {{ row.metricValue }}</template>
          </el-table-column>
          <el-table-column label="开始时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'ACTIVE'">
                <el-button
                  type="primary" size="small" :loading="diagnosingId === row.id"
                  @click="diagnose(row)"
                >🤖 AI 诊断</el-button>
                <el-button size="small" :loading="resolvingId === row.id" @click="resolve(row)">恢复</el-button>
              </template>
              <el-tag v-else size="small" type="success">已恢复</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="alertFilter.current"
          :page-size="alertFilter.size"
          :total="total"
          layout="prev, pager, next, total"
          style="margin-top:12px;justify-content:flex-end"
          @current-change="loadAlerts"
        />
      </el-tab-pane>

      <el-tab-pane label="服务拓扑 (CMDB)" name="services">
        <el-table :data="services" stripe>
          <el-table-column prop="serviceName" label="服务" min-width="150" />
          <el-table-column prop="owner" label="负责人" width="110" />
          <el-table-column label="核心等级" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.tier === 1 ? 'danger' : row.tier === 2 ? 'warning' : 'info'">
                T{{ row.tier }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="hostNames" label="部署主机" min-width="220" />
          <el-table-column prop="repo" label="代码仓库" min-width="220" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="主机资产" name="hosts">
        <el-table :data="hosts" stripe>
          <el-table-column prop="hostName" label="主机名" min-width="140" />
          <el-table-column prop="ip" label="IP" width="140" />
          <el-table-column label="配置" width="140">
            <template #default="{ row }">{{ row.cpuCores }}C / {{ row.memoryGb }}G / {{ row.diskGb }}G</template>
          </el-table-column>
          <el-table-column prop="env" label="环境" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'ONLINE' ? 'success' : 'danger'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="指标监控" name="metrics">
        <div class="flex-between" style="margin-bottom:12px">
          <el-space>
            <el-select v-model="metricService" style="width:200px" placeholder="选择服务" @change="loadMetrics">
              <el-option v-for="s in serviceNames" :key="s" :label="s" :value="s" />
            </el-select>
            <el-select v-model="metricHours" style="width:120px" @change="loadMetrics">
              <el-option :value="6" label="最近 6 小时" />
              <el-option :value="24" label="最近 24 小时" />
              <el-option :value="48" label="最近 48 小时" />
            </el-select>
          </el-space>
        </div>
        <div ref="metricRef" style="height:380px"></div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import {
  diagnoseAlert,
  listAlerts,
  listHosts,
  listServices,
  metricSeries,
  resolveAlert
} from '../api'
import { ElMessage } from 'element-plus'
import { formatDateTime, severityColor } from '../utils/format'

const router = useRouter()
const tab = ref('alerts')
const alerts = ref<any[]>([])
const total = ref(0)
const loadingAlerts = ref(false)
const alertFilter = ref({ current: 1, size: 10, status: 'ACTIVE', severity: '' })
const diagnosingId = ref<string | number | null>(null)
const resolvingId = ref<string | number | null>(null)
const hosts = ref<any[]>([])
const services = ref<any[]>([])
const serviceNames = ref<string[]>([])
const metricService = ref('')
const metricHours = ref(24)
const metricRef = ref<HTMLElement>()
let metricChart: echarts.ECharts | null = null

async function loadAlerts() {
  loadingAlerts.value = true
  try {
    const page = await listAlerts({
      current: alertFilter.value.current,
      size: alertFilter.value.size,
      status: alertFilter.value.status || undefined,
      severity: alertFilter.value.severity || undefined
    })
    alerts.value = page?.records || []
    total.value = page?.total || 0
  } finally {
    loadingAlerts.value = false
  }
}

async function diagnose(row: any) {
  diagnosingId.value = row.id
  try {
    const data = await diagnoseAlert(row.id)
    router.push({
      path: '/chat',
      query: { sessionId: data.sessionId, prompt: data.prompt }
    })
  } finally {
    diagnosingId.value = null
  }
}

async function resolve(row: any) {
  resolvingId.value = row.id
  try {
    await resolveAlert(row.id)
    ElMessage.success(`告警「${row.title}」已标记恢复`)
    await loadAlerts()
  } finally {
    resolvingId.value = null
  }
}

async function loadStatics() {
  hosts.value = (await listHosts()) || []
  services.value = (await listServices()) || []
  serviceNames.value = services.value.map((s) => s.serviceName)
  if (!metricService.value && serviceNames.value.length) {
    metricService.value = serviceNames.value.includes('order-service')
      ? 'order-service'
      : serviceNames.value[0]
    await loadMetrics()
  }
}

async function loadMetrics() {
  if (!metricService.value) return
  const series = (await metricSeries({ serviceName: metricService.value, hours: metricHours.value })) || []
  const target = series[0]
  if (!metricChart && metricRef.value) {
    metricChart = echarts.init(metricRef.value)
  }
  if (metricChart && target) {
    const points = target.points || []
    metricChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['cpu_usage', 'mem_usage'], bottom: 0 },
      grid: { left: 46, right: 20, top: 30, bottom: 56 },
      xAxis: { type: 'category', data: points.map((p: any) => p.time) },
      yAxis: { type: 'value', axisLabel: { formatter: '{value}%' }, max: 100 },
      series: [
        {
          name: 'cpu_usage',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: points.map((p: any) => p.cpu_usage),
          itemStyle: { color: '#f56c6c' },
          areaStyle: { opacity: 0.08 }
        },
        {
          name: 'mem_usage',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: points.map((p: any) => p.mem_usage),
          itemStyle: { color: '#4f6ef2' }
        }
      ]
    })
  }
}

onMounted(async () => {
  await loadAlerts()
  await loadStatics()
  window.addEventListener('resize', onResize)
})

function onResize() {
  metricChart?.resize()
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  metricChart?.dispose()
})
</script>
