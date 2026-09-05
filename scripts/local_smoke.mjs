// Node.js 22+. Run against a local development backend:
// node scripts/local_smoke.mjs
// Overrides: BASE_URL, SMOKE_USERNAME, SMOKE_PASSWORD.
// Creates one temporary document and chat session and deletes both in finally.
import assert from 'node:assert/strict'
import { setTimeout as delay } from 'node:timers/promises'

const base = process.env.BASE_URL || 'http://127.0.0.1:8080'
const target = new URL(base)
assert.ok(['localhost', '127.0.0.1', '[::1]'].includes(target.hostname), 'Only local development backends are supported')
let token = ''
let documentId
let sessionId
let spaceId
let passed = 0
function pass(label) { passed++; console.log('PASS ' + label) }
async function request(path, options = {}) {
  return fetch(base + path, { ...options, headers: { ...(token ? { Authorization: 'Bearer ' + token } : {}), ...options.headers }, signal: AbortSignal.timeout(40000) })
}
async function api(path, method = 'GET', body) {
  const response = await request('/api' + path, { method, ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }) })
  assert.equal(response.status, 200, method + ' ' + path)
  const result = await response.json()
  assert.equal(result.code, 0, path + ': ' + result.message)
  return result.data
}
async function waitForDocument() {
  for (let attempt = 0; attempt < 40; attempt++) {
    const documents = await api('/kb/documents?spaceId=' + spaceId)
    const document = documents.find(item => String(item.id) === String(documentId))
    assert.ok(document, 'Uploaded document must exist')
    assert.notEqual(document.status, 'FAILED', document.errorMessage || 'Document ingestion failed')
    if (document.status === 'COMPLETED') return document
    await delay(500)
  }
  throw new Error('Document ingestion did not finish within 20 seconds')
}
try {
  assert.equal((await (await request('/actuator/health')).json()).status, 'UP')
  pass('health including database / Redis / RabbitMQ')
  const schema = await (await request('/v3/api-docs')).json()
  assert.ok(schema.openapi?.startsWith('3.'), 'OpenAPI must return a schema, not a business error')
  assert.ok(schema.paths?.['/api/chat/stream'])
  pass('OpenAPI generation with application exception advice')
  const swagger = await request('/swagger-ui/index.html')
  assert.equal(swagger.status, 200)
  assert.ok((await swagger.text()).includes('Swagger UI'))
  pass('Swagger UI')
  assert.equal((await request('/api/dashboard/summary')).status, 401)
  pass('anonymous access denied')
  const auth = await api('/auth/login', 'POST', { username: process.env.SMOKE_USERNAME || 'admin', password: process.env.SMOKE_PASSWORD || 'admin123' })
  token = auth.accessToken
  assert.ok(token)
  pass('login')
  const summary = await api('/dashboard/summary')
  assert.match(String(summary.activeAlerts), /^[0-9]+$/)
  pass('dashboard')
  assert.ok((await api('/agents')).some(agent => agent.code === 'ops_diagnosis'))
  pass('agent registry')
  const spaces = await api('/kb/spaces')
  assert.ok(spaces.length, 'A knowledge space is required; enable demo seed or create one')
  spaceId = spaces[0].id
  const content = '灵犀启动回归检查：上传原件存入 MinIO，RabbitMQ 触发解析，Qdrant 保存知识向量。检查标记 ' + Date.now()
  const form = new FormData()
  form.append('spaceId', String(spaceId))
  form.append('file', new Blob([content], { type: 'text/plain' }), 'local-startup-smoke.txt')
  const uploaded = await (await request('/api/kb/documents/upload', { method: 'POST', body: form })).json()
  // An upload can leave a FAILED row even when the response is an error.
  if (uploaded.data?.id) documentId = uploaded.data.id
  assert.equal(uploaded.code, 0, 'upload: ' + uploaded.message)
  assert.ok(documentId)
  const document = await waitForDocument()
  assert.ok(document.chunkCount > 0)
  pass('upload and document ingestion')
  const download = await request('/api/kb/documents/' + documentId + '/download')
  assert.equal(download.status, 200)
  assert.equal(await download.text(), content)
  pass('MinIO download round trip')
  const hits = await api('/kb/search', 'POST', { spaceId, query: content, topK: 5 })
  assert.ok(hits.hits.some(hit => String(hit.documentId) === String(documentId)))
  pass('Qdrant retrieval of uploaded document')
  await api('/kb/documents/' + documentId + '/reingest', 'POST')
  assert.equal((await waitForDocument()).chunkCount, document.chunkCount)
  pass('document reingestion')
  const session = await api('/chat/sessions', 'POST', { title: '本地启动回归检查', agentType: 'ops_diagnosis' })
  sessionId = session.id
  const response = await request('/api/chat/stream?agentType=ops_diagnosis&sessionId=' + sessionId, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content: '订单服务 CPU 告警，帮我诊断一下' }) })
  assert.equal(response.status, 200)
  const stream = await response.text()
  for (const event of ['tool_call', 'tool_result', 'done']) assert.match(stream, new RegExp('event:\\s*' + event))
  assert.doesNotMatch(stream, /event:\s*error/)
  pass('SSE diagnosis with tool calls, results and completion')
  assert.ok((await api('/chat/sessions/' + sessionId + '/messages')).some(message => message.role === 'assistant' && message.content))
  pass('chat history persistence')
  const reports = await api('/reports')
  assert.match(String(reports.total), /^[0-9]+$/)
  pass('report listing')
} finally {
  const cleanupErrors = []
  for (const [path, id] of [['/kb/documents/', documentId], ['/chat/sessions/', sessionId]]) {
    if (id != null) {
      try { await api(path + id, 'DELETE') }
      catch (error) { cleanupErrors.push(error); console.error('Cleanup failed for ' + path + id) }
    }
  }
  if (cleanupErrors.length) throw new AggregateError(cleanupErrors, 'Failed to clean up smoke test resources')
}
console.log('All ' + passed + ' checks passed; temporary document and session removed.')
