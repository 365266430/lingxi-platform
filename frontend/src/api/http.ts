import axios from 'axios'
import { ElMessage } from 'element-plus'

const STORAGE_KEY = 'lingxi.auth'

function readToken(): string {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')?.token || ''
  } catch (e) {
    return ''
  }
}

/** 统一业务实例：自动携带 token、自动解包 Result<T>、401 自动刷新重放 */
const service = axios.create({ baseURL: '/api', timeout: 60000 })

/** 不解包 Result 的实例（登录/刷新等特殊场景） */
const rawInstance = axios.create({ baseURL: '/api', timeout: 60000 })

service.interceptors.request.use((config) => {
  const token = readToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshing: Promise<string> | null = null

async function refreshOnce(): Promise<string> {
  const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
  if (!saved.refresh) throw new Error('NO_REFRESH_TOKEN')
  const resp = await rawInstance.post('/auth/refresh', { refreshToken: saved.refresh })
  const data = resp.data?.data
  if (!data?.accessToken) throw new Error('REFRESH_FAILED')
  saved.token = data.accessToken
  saved.refresh = data.refreshToken
  saved.user = data.user
  localStorage.setItem(STORAGE_KEY, JSON.stringify(saved))
  return data.accessToken
}

function redirectToLogin() {
  localStorage.removeItem(STORAGE_KEY)
  if (!location.pathname.startsWith('/login')) {
    location.href = '/login'
  }
}

service.interceptors.response.use(
  (resp) => resp?.data?.data,
  async (error) => {
    const resp = error.response
    const config = error.config || {}
    if (resp?.status === 401 && !config._retried) {
      try {
        if (!refreshing) {
          refreshing = refreshOnce().finally(() => {
            refreshing = null
          })
        }
        const token = await refreshing
        config._retried = true
        config.headers = { ...(config.headers || {}), Authorization: `Bearer ${token}` }
        return service.request(config)
      } catch (e) {
        redirectToLogin()
        ElMessage.error('登录已过期，请重新登录')
        return Promise.reject(error)
      }
    }
    const message = resp?.data?.message || error.message || '请求失败'
    if (resp?.status !== 401) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default {
  /** 解包后的业务数据 */
  get: <T = any>(url: string, params?: any) => service.get<any, T>(url, { params }),
  post: <T = any>(url: string, data?: any, config?: any) => service.post<any, T>(url, data, config),
  put: <T = any>(url: string, data?: any, config?: any) => service.put<any, T>(url, data, config),
  delete: <T = any>(url: string, config?: any) => service.delete<any, T>(url, config),
  /** 原始 axios 实例（返回完整 Result） */
  raw: () => rawInstance,
  readToken
}
