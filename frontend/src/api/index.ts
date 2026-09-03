import http from './http'

/* ============ 认证 ============ */
export const login = (username: string, password: string) => http.post('/auth/login', { username, password })
export const register = (username: string, password: string, nickname?: string) =>
  http.post('/auth/register', { username, password, nickname })
export const fetchMe = () => http.get('/auth/me')

/* ============ 智能体 ============ */
export const listAgents = () => http.get('/agents')

/* ============ 对话 ============ */
export const listSessions = (current = 1, size = 50) => http.get('/chat/sessions', { current, size })
export const createSession = (title?: string, agentType?: string) =>
  http.post('/chat/sessions', { title, agentType })
export const listMessages = (sessionId: string | number) => http.get(`/chat/sessions/${sessionId}/messages`)
export const deleteSession = (sessionId: string | number) => http.delete(`/chat/sessions/${sessionId}`)
export const messageFeedback = (id: string | number, feedback: number) =>
  http.post(`/chat/messages/${id}/feedback`, { feedback })

/* ============ 快捷提示词 ============ */
export const listPromptTemplates = () => http.get('/prompt-templates')

/* ============ 个人中心 ============ */
export const myProfile = () => http.get('/users/me')
export const updateProfile = (nickname: string) => http.put('/users/me', { nickname })
export const changePassword = (oldPassword: string, newPassword: string) =>
  http.put('/users/me/password', { oldPassword, newPassword })

/* ============ 知识库 ============ */
export const listSpaces = () => http.get('/kb/spaces')
export const createSpace = (name: string, description?: string) =>
  http.post('/kb/spaces', { name, description })
export const listDocuments = (spaceId?: string | number) =>
  http.get('/kb/documents', spaceId ? { spaceId } : undefined)
export const deleteDocument = (id: string | number) => http.delete(`/kb/documents/${id}`)
export const reingestDocument = (id: string | number) => http.post(`/kb/documents/${id}/reingest`)
export const kbSearch = (spaceId: string | number | null, query: string, topK = 4) =>
  http.post('/kb/search', { spaceId, query, topK })

/* ============ 运维 ============ */
export const listAlerts = (params: any) => http.get('/ops/alerts', params)
export const diagnoseAlert = (id: string | number) => http.post(`/ops/alerts/${id}/diagnose`)
export const resolveAlert = (id: string | number) => http.post(`/ops/alerts/${id}/resolve`)
export const listHosts = () => http.get('/ops/hosts')
export const listServices = () => http.get('/ops/services')
export const metricSeries = (params: { serviceName?: string; hours?: number }) =>
  http.get('/ops/metrics', params)

/* ============ 报告 ============ */
export const listReports = (current = 1, size = 10) => http.get('/reports', { current, size })
export const getReport = (id: string | number) => http.get(`/reports/${id}`)

/* ============ 仪表盘 ============ */
export const dashboardSummary = () => http.get('/dashboard/summary')

/* ============ 系统管理 ============ */
export const adminUsers = (current = 1, size = 20, keyword?: string) =>
  http.get('/admin/users', { current, size, keyword })
export const adminUpdateUserStatus = (id: string | number, enabled: number) =>
  http.put(`/admin/users/${id}/status`, { enabled })
export const adminUpdateUserRole = (id: string | number, role: string) =>
  http.put(`/admin/users/${id}/role`, { role })
export const getModelConfig = () => http.get('/admin/model-config')
export const saveModelConfig = (config: any) => http.put('/admin/model-config', config)
export const testModelConfig = () => http.post('/admin/model-config/test')
