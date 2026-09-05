<template>
  <div class="dashboard">
    <section class="welcome">
      <div><div class="eyebrow">YOUR INTELLIGENT WORKSPACE</div><h1>一切尽在掌握<span>。</span></h1><p>你好，{{ auth.user?.nickname || auth.user?.username || '欢迎回来' }}。从一个问题开始，让工作更进一步。</p></div>
      <div class="date-chip glass-panel"><el-icon><Calendar /></el-icon>{{ today }}</div>
    </section>
    <section class="assistant-panel glass-panel" aria-labelledby="assistant-heading">
      <div class="assistant-intro"><div class="assistant-symbol"><el-icon><MagicStick /></el-icon></div><div><h2 id="assistant-heading">今天，有什么可以帮你？</h2><p>诊断告警、查找知识，或生成一份巡检报告。</p></div><span class="assistant-caption">灵犀 AI</span></div>
      <form class="ask-form" @submit.prevent="ask">
        <input v-model="question" aria-label="向灵犀提问" placeholder="试试：订单服务 CPU 告警，帮我诊断一下" maxlength="4000" />
        <button class="ask-button" type="submit" :disabled="!question.trim()" aria-label="发送问题"><el-icon><Top /></el-icon></button>
      </form>
      <div class="quick-actions"><button v-for="action in actions" :key="action.title" @click="startChat(action.prompt, action.agent)"><el-icon><component :is="action.icon" /></el-icon>{{ action.title }}<span>↗</span></button></div>
    </section>
    <div class="section-heading"><h2>平台脉搏 <span>OVERVIEW</span></h2><button class="refresh-button" :disabled="loading" @click="load"><el-icon :class="{ spinning: loading }"><Refresh /></el-icon>{{ loading ? '正在更新' : '刷新数据' }}</button></div>
    <div v-if="error" class="load-error" role="alert">暂时无法获取平台数据，请稍后点击「刷新数据」重试。</div>
    <div class="stats-grid" :aria-busy="loading">
      <router-link v-for="stat in stats" :key="stat.label" :to="stat.path" class="stat-link"><StatCard :icon="stat.icon" :label="stat.label" :value="summary[stat.key] ?? '—'" :color="stat.color" /></router-link>
    </div>
    <div class="charts-grid" :aria-busy="loading">
      <section class="page-card trend-card"><div class="card-heading"><div><h2>告警趋势</h2><p>掌握系统变化，及时发现异常</p></div><span class="period">近 7 天</span></div><div class="chart-wrap"><div ref="trendRef" class="chart" role="img" :aria-label="trendDescription"></div><span v-if="!loading && !summary.alertsTrend?.length" class="chart-empty">暂无趋势数据</span></div></section>
      <section class="page-card"><div class="card-heading"><div><h2>告警分布</h2><p>活跃告警 · 按严重程度</p></div><router-link to="/ops" aria-label="查看运维告警">↗</router-link></div><div class="chart-wrap"><div ref="severityRef" class="chart" role="img" :aria-label="severityDescription"></div><span v-if="!loading && !summary.severityDist?.length" class="chart-empty">暂无活跃告警</span></div></section>
      <section class="page-card"><div class="card-heading"><div><h2>智能体协作</h2><p>每一份专长，各尽其用</p></div><router-link to="/chat" aria-label="进入智能对话">↗</router-link></div><div class="chart-wrap"><div ref="agentRef" class="chart lower-chart" role="img" :aria-label="agentDescription"></div><span v-if="!loading && !summary.agentDist?.length" class="chart-empty">暂无会话数据</span></div></section>
      <section class="page-card platform-card"><div class="card-heading"><div><h2>每一次积累，都有价值</h2><p>知识、对话与成果，在这里连接</p></div><el-icon><Connection /></el-icon></div><dl class="platform-metrics"><div v-for="metric in metrics" :key="metric.key"><dt>{{ metric.label }}</dt><dd>{{ summary[metric.key] ?? '—' }}</dd></div><div><dt>回答反馈</dt><dd class="feedback">{{ summary.feedbackUp ?? '—' }} 赞同 / {{ summary.feedbackDown ?? '—' }} 待改进</dd></div></dl><router-link to="/reports" class="report-link">前往报告中心，查看 AI 的工作成果 <span>↗</span></router-link></section>
    </div>
    <footer class="dashboard-footer"><span>灵犀 · 让智能融入每一天</span><span>多智能体协作 / 知识增强 / 智能运维</span></footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { Calendar, MagicStick, Top, Monitor, Collection, Document, Refresh, Connection } from '@element-plus/icons-vue'
import StatCard from '../components/StatCard.vue'
import { dashboardSummary } from '../api'
import { useAuthStore } from '../stores/auth'
import { agentTypeText } from '../utils/format'

interface Summary {
  [key: string]: any
  alertsTrend?: { day: string; count: number }[]
  severityDist?: { severity: string; count: number }[]
  agentDist?: { agentType: string; count: number }[]
}
const router = useRouter()
const auth = useAuthStore()
const summary = ref<Summary>({})
const question = ref('')
const loading = ref(false)
const error = ref(false)
const today = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
const actions = [
  { title: '诊断服务告警', icon: Monitor, prompt: '订单服务 CPU 告警，帮我诊断一下', agent: 'ops_diagnosis' },
  { title: '查找知识手册', icon: Collection, prompt: '磁盘空间不足的处置手册是什么', agent: 'knowledge_qa' },
  { title: '生成巡检报告', icon: Document, prompt: '帮我生成一份巡检报告', agent: 'report' }
]
const stats = [
  { key: 'activeAlerts', icon: '◎', label: '活跃告警', color: '#b56c38', path: '/ops' },
  { key: 'criticalAlerts', icon: '!', label: 'P1 严重告警', color: '#c3566b', path: '/ops' },
  { key: 'messages24h', icon: '↗', label: '24h 对话消息', color: '#557dce', path: '/chat' },
  { key: 'totalDocuments', icon: '▤', label: '知识文档', color: '#38867e', path: '/knowledge' }
]
const metrics = [
  { key: 'totalUsers', label: '注册用户' }, { key: 'totalSessions', label: '对话总数' },
  { key: 'totalChunks', label: '知识分块' }, { key: 'totalReports', label: 'AI 报告' },
  { key: 'totalMessages', label: '累计消息' }
]
function startChat(prompt: string, agent = 'supervisor') { router.push({ path: '/chat', query: { prompt, agentType: agent } }) }
function ask() { if (question.value.trim()) startChat(question.value.trim()) }
const trendRef = ref<HTMLElement>()
const severityRef = ref<HTMLElement>()
const agentRef = ref<HTMLElement>()
const charts: echarts.ECharts[] = []
let disposed = false
let resizeObserver: ResizeObserver | undefined
let themeObserver: MutationObserver | undefined
const trendDescription = computed(() => '近七天告警趋势：' + (summary.value.alertsTrend?.map(d => d.day + '：' + d.count).join('；') || '暂无数据'))
const severityDescription = computed(() => '活跃告警分布：' + (summary.value.severityDist?.map(d => d.severity + '：' + d.count).join('；') || '暂无数据'))
const agentDescription = computed(() => '智能体会话分布：' + (summary.value.agentDist?.map(d => (agentTypeText[d.agentType] || d.agentType) + '：' + d.count).join('；') || '暂无数据'))
async function load() {
  if (loading.value) return
  loading.value = true
  error.value = false
  try {
    const data = await dashboardSummary()
    if (disposed) return
    summary.value = data || {}
    renderCharts()
  } catch {
    if (!disposed) error.value = true
  } finally {
    if (!disposed) loading.value = false
  }
}
function renderCharts() {
  if (disposed || charts.length !== 3) return
  const styles = getComputedStyle(document.documentElement)
  const text = styles.getPropertyValue('--lingxi-muted').trim()
  const line = styles.getPropertyValue('--lingxi-line').trim()
  const animation = !window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const common = { animation, textStyle: { color: text, fontFamily: 'sans-serif' }, backgroundColor: 'transparent' }
  const trend = summary.value.alertsTrend || []
  charts[0].setOption({
    ...common, tooltip: { trigger: 'axis' }, grid: { left: 36, right: 18, top: 24, bottom: 28 },
    xAxis: { type: 'category', boundaryGap: false, data: trend.map(d => d.day), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: text, fontSize: 10 } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: line, type: 'dashed' } }, axisLabel: { color: text, fontSize: 10 } },
    series: [{ name: '告警数', type: 'line', smooth: true, symbolSize: 7, showSymbol: false, lineStyle: { width: 3 }, itemStyle: { color: '#638fe3' }, areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'#7fa9eb50'},{offset:1,color:'#7fa9eb00'}]) }, data: trend.map(d => d.count) }]
  }, true)
  const pie = (data: { name: string; value: number }[], colors: string[]) => ({
    ...common, color: colors, tooltip: { trigger: 'item' }, legend: { bottom: 0, icon: 'circle', itemWidth: 7, itemHeight: 7, textStyle: { color: text, fontSize: 10 } },
    series: [{ type: 'pie', radius: ['49%', '70%'], center: ['50%', '43%'], avoidLabelOverlap: true, label: { show: false }, itemStyle: { borderRadius: 5, borderWidth: 3, borderColor: styles.getPropertyValue('--lingxi-bg').trim() }, data }]
  })
  const severityColors: Record<string, string> = { P1: '#d77d88', P2: '#dcad69', P3: '#709ad5', P4: '#84b6ac' }
  const severity = summary.value.severityDist || []
  charts[1].setOption(pie(severity.map(d => ({ name: d.severity, value: d.count })), severity.map(d => severityColors[d.severity] || '#94a3b8')), true)
  charts[2].setOption(pie((summary.value.agentDist || []).map(d => ({ name: agentTypeText[d.agentType] || d.agentType, value: d.count })), ['#779cdb','#9c91c7','#7ab4ac','#d5b079','#d28e9f']), true)
}
onMounted(() => {
  for (const element of [trendRef.value, severityRef.value, agentRef.value]) if (element) charts.push(echarts.init(element))
  resizeObserver = new ResizeObserver(() => charts.forEach(chart => chart.resize()))
  for (const element of [trendRef.value, severityRef.value, agentRef.value]) if (element) resizeObserver.observe(element)
  themeObserver = new MutationObserver(renderCharts)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
  renderCharts()
  load()
})
onBeforeUnmount(() => { disposed = true; resizeObserver?.disconnect(); themeObserver?.disconnect(); charts.forEach(chart => chart.dispose()) })
</script>

<style scoped>
.dashboard { max-width:1440px; margin:0 auto; }
.welcome { display:flex; justify-content:space-between; align-items:center; margin-bottom:26px; gap:18px; }
.eyebrow { color:var(--lingxi-muted); font-size:9px; letter-spacing:2.8px; margin-bottom:12px; }
h1 { font-size:34px; letter-spacing:-1px; font-weight:650; margin:0; }
h1 span { color:#6789ca; }
.welcome p { margin:12px 0 0; color:var(--lingxi-muted); font-size:12px; line-height:1.7; }
.date-chip { padding:11px 15px; border-radius:22px; display:flex; align-items:center; gap:8px; font-size:11px; white-space:nowrap; color:var(--lingxi-muted); }
.assistant-panel { padding:24px 28px 18px; border-radius:26px; background:linear-gradient(115deg,#b5d7ed26,#c6b4ed20),var(--lingxi-glass); }
.assistant-intro { display:flex; align-items:center; gap:14px; }
.assistant-symbol { width:46px; height:46px; display:grid; place-items:center; background:linear-gradient(140deg,#fff9,#91b8e74a); border:1px solid var(--lingxi-edge); border-radius:16px; box-shadow:0 4px 16px #5784c31a,inset 0 1px 1px #fff9; color:var(--lingxi-primary); font-size:24px; }
h2 { font-size:15px; font-weight:600; margin:0; }
.assistant-intro p { color:var(--lingxi-muted); font-size:11px; margin:6px 0 0; }
.assistant-caption { margin-left:auto; color:var(--lingxi-muted); font-size:10px; letter-spacing:2px; }
.ask-form { display:flex; background:var(--lingxi-card); border:1px solid var(--lingxi-edge); padding:8px 8px 8px 18px; border-radius:18px; margin-top:21px; box-shadow:0 4px 15px #5875a108; }
.ask-form input { width:100%; min-width:0; border:none; background:none; outline:none; font:inherit; font-size:12px; color:var(--lingxi-dark); }
.ask-form:focus-within { outline:2px solid var(--lingxi-primary); outline-offset:3px; }
.ask-form input::placeholder { color:var(--lingxi-muted); }
.ask-button { width:36px; height:36px; flex-shrink:0; display:grid; place-items:center; border:0; border-radius:12px; background:#4775cd; color:white; font-size:20px; cursor:pointer; }
.ask-button:disabled { opacity:.45; cursor:default; }
.quick-actions { display:flex; flex-wrap:wrap; gap:10px; margin-top:13px; }
.quick-actions button { display:flex; gap:8px; align-items:center; border:1px solid var(--lingxi-line); border-radius:18px; padding:8px 12px; background:var(--lingxi-hover); font-size:10px; color:var(--lingxi-dark); cursor:pointer; }
.quick-actions button:hover { border-color:var(--lingxi-primary); }
.quick-actions span { color:var(--lingxi-muted); margin-left:8px; }
.section-heading { display:flex; align-items:center; justify-content:space-between; margin:27px 2px 15px; }
.section-heading h2 { font-size:14px; }.section-heading h2 span { font-size:8px; letter-spacing:1.5px; color:var(--lingxi-muted); margin-left:10px; font-weight:400; }
.refresh-button { background:none; border:0; font-size:10px; color:var(--lingxi-muted); display:flex; align-items:center; gap:6px; cursor:pointer; }
.stats-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:14px; }
.stat-link { text-decoration:none; color:inherit; border-radius:22px; transition:transform .2s; }.stat-link:hover { transform:translateY(-3px); }
.charts-grid { display:grid; grid-template-columns:minmax(0,1.45fr) minmax(0,1fr); gap:16px; margin-top:18px; }
.card-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.card-heading h2 { font-size:13px; }.card-heading p { font-size:10px; color:var(--lingxi-muted); margin:8px 0 0; }
.card-heading a { font-size:20px; color:var(--lingxi-muted); text-decoration:none; }
.period { font-size:10px; border:1px solid var(--lingxi-line); color:var(--lingxi-muted); padding:6px 10px; border-radius:12px; }
.chart { height:228px; width:100%; }.chart-wrap { position:relative; }.lower-chart { height:214px; }
.chart-empty { position:absolute; inset:0; display:grid; place-items:center; color:var(--lingxi-muted); font-size:12px; pointer-events:none; }
.platform-metrics { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); margin:26px 0 20px; row-gap:24px; column-gap:8px; }
.platform-metrics dt { font-size:10px; color:var(--lingxi-muted); }.platform-metrics dd { font-size:21px; margin:8px 0 0; font-weight:600; font-variant-numeric:tabular-nums; }.platform-metrics dd.feedback { font-size:10px; line-height:25px; }
.report-link { border-top:1px solid var(--lingxi-line); padding-top:16px; text-decoration:none; color:var(--lingxi-primary); display:flex; justify-content:space-between; font-size:10px; gap:10px; }
.dashboard-footer { display:flex; justify-content:space-between; color:var(--lingxi-muted); font-size:9px; padding:22px 4px 0; letter-spacing:.5px; }
.load-error { padding:12px; border:1px solid #cd8b70; border-radius:12px; margin-bottom:12px; font-size:12px; }
.spinning { animation:spin 1s linear infinite; }@keyframes spin { to { transform:rotate(360deg); } }
@media(max-width:1100px) { .stats-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.welcome h1 { font-size:29px; }.date-chip { display:none; } }
@media(max-width:760px) { .welcome { margin-bottom:20px; }.welcome h1 { font-size:28px; }.welcome p { font-size:11px; }.assistant-panel { padding:20px 16px 16px; }.assistant-caption { display:none; }.assistant-intro h2 { font-size:14px; }.assistant-intro p { font-size:10px; }.quick-actions { gap:7px; }.quick-actions button { padding:8px 9px; }.charts-grid { grid-template-columns:minmax(0,1fr); }.stats-grid { gap:10px; }.dashboard-footer span:last-child { display:none; } }
</style>
