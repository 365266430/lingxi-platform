import { defineStore } from 'pinia'
import http from '../api/http'

export interface UserInfo {
  id: string | number
  username: string
  nickname: string
  role: string
}

const STORAGE_KEY = 'lingxi.auth'

function loadSaved(): { token: string; refresh: string; user: UserInfo | null } {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return JSON.parse(raw)
  } catch (e) {
    /* ignore */
  }
  return { token: '', refresh: '', user: null }
}

export const useAuthStore = defineStore('auth', {
  state: () => {
    const saved = loadSaved()
    return {
      accessToken: saved.token,
      refreshToken: saved.refresh,
      user: saved.user as UserInfo | null
    }
  },
  getters: {
    isLoggedIn: (state) => !!state.accessToken,
    isAdmin: (state) => state.user?.role === 'ADMIN'
  },
  actions: {
    persist() {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ token: this.accessToken, refresh: this.refreshToken, user: this.user })
      )
    },
    clear() {
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem(STORAGE_KEY)
    },
    async login(username: string, password: string) {
      const resp = await http.post('/auth/login', { username, password })
      this.applyAuth(resp)
      return resp
    },
    async register(username: string, password: string, nickname?: string) {
      const resp = await http.post('/auth/register', { username, password, nickname })
      this.applyAuth(resp)
      return resp
    },
    async doRefresh() {
      const resp = await http.raw().post('/auth/refresh', { refreshToken: this.refreshToken })
      this.applyAuth(resp.data.data)
      return resp.data.data
    },
    applyAuth(data: any) {
      this.accessToken = data.accessToken
      this.refreshToken = data.refreshToken
      this.user = data.user
      this.persist()
    },
    async logout() {
      this.clear()
    }
  }
})
