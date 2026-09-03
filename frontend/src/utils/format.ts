import dayjs from 'dayjs'

export function formatDateTime(value: string | number | Date | null | undefined): string {
  if (!value) return '-'
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss')
}

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null || isNaN(Number(bytes))) return '-'
  let n = Number(bytes)
  if (n < 1024) return `${n} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let i = -1
  do {
    n /= 1024
    i++
  } while (n >= 1024 && i < units.length - 1)
  return `${n.toFixed(1)} ${units[i]}`
}

export const agentTypeText: Record<string, string> = {
  supervisor: '全能管家',
  knowledge_qa: '知识问答',
  ops_diagnosis: '运维诊断',
  data_analysis: '数据分析',
  report: '报告撰写'
}

export const severityColor: Record<string, string> = {
  P1: '#f56c6c',
  P2: '#e6a23c',
  P3: '#909399'
}

export const docStatusText: Record<string, string> = {
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  FAILED: '失败'
}
