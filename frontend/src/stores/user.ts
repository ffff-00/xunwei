import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { userApi } from '@/api'
import { TOKEN_KEY } from '@/api/request'
import type { UserInfo } from '@/api/types'

/**
 * 登录状态。
 *
 * 令牌存 localStorage（刷新页面不掉），用户信息每次进入应用时拉一次 ——
 * 不把用户信息也缓存到 localStorage：那是会过期的副本，
 * 改了昵称之后本地还显示旧的，这类不一致很难排查。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const info = ref<UserInfo | null>(null)
  /** 是否已经尝试过恢复登录态（避免路由守卫在恢复完成前误判为未登录） */
  const restored = ref(false)

  const isLoggedIn = computed(() => !!token.value)

  function setToken(value: string) {
    token.value = value
    localStorage.setItem(TOKEN_KEY, value)
  }

  function clear() {
    token.value = ''
    info.value = null
    localStorage.removeItem(TOKEN_KEY)
  }

  /** 进入应用时调用：有令牌就用它换用户信息，换不到说明令牌已失效 */
  async function restore() {
    if (!token.value) {
      restored.value = true
      return
    }
    try {
      const res = await userApi.me()
      info.value = res.data
    } catch {
      // 令牌失效（后端返回 401 时拦截器已清掉本地令牌）
      clear()
    } finally {
      restored.value = true
    }
  }

  async function login(phone: string, code: string) {
    const res = await userApi.login(phone, code)
    setToken(res.data)
    const me = await userApi.me()
    info.value = me.data
  }

  async function logout() {
    try {
      await userApi.logout()
    } finally {
      // 即使后端调用失败也要清本地状态：不然用户会觉得"点了登出没反应"
      clear()
    }
  }

  return { token, info, restored, isLoggedIn, setToken, clear, restore, login, logout }
})
