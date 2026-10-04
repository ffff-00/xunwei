import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { BizCode, type ApiResult } from './types'

export const TOKEN_KEY = 'xunwei_token'

/**
 * 业务异常。
 *
 * 为什么要单独一个类而不是直接 reject 一个 Error：
 * 调用方需要拿到业务码做分支。最典型的是秒杀下单 ——
 * 返回 44005（订单处理中）不是错误，而是"再等等"，必须和"真失败"区分开。
 * 靠 message 文案判断是不可靠的（改一个字就失效），所以把 code 带出来。
 */
export class BizError extends Error {
  constructor(
    public readonly code: number,
    message: string,
  ) {
    super(message)
    this.name = 'BizError'
  }
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// ── 请求拦截：带上登录令牌 ──
http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.authorization = token
  }
  return config
})

// ── 响应拦截：统一处理业务码与网络错误 ──
http.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult
    if (result.code === BizCode.SUCCESS) {
      // 返回完整的 ApiResult 而不是只返回 data：分页接口的总数在顶层 total 上
      return result as any
    }
    // 业务失败：交给调用方按 code 处理
    return Promise.reject(new BizError(result.code, result.message ?? '请求失败'))
  },
  (error) => {
    const status = error?.response?.status
    if (status === BizCode.UNAUTHORIZED) {
      // 令牌失效：清掉本地状态，回到登录页。
      // 用 location 而不是 router，避免在拦截器里引入对 router 的循环依赖。
      localStorage.removeItem(TOKEN_KEY)
      ElMessage.warning('登录已过期，请重新登录')
      if (!location.pathname.startsWith('/login')) {
        location.href = `/login?redirect=${encodeURIComponent(location.pathname + location.search)}`
      }
      return Promise.reject(new BizError(BizCode.UNAUTHORIZED, '未登录或登录已过期'))
    }
    const message = error?.response?.data?.message ?? error.message ?? '网络异常，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(new BizError(status ?? -1, message))
  },
)

/** 让 TypeScript 知道我们的拦截器已经把响应换成了 ApiResult */
export function request<T = unknown>(config: AxiosRequestConfig): Promise<ApiResult<T>> {
  return http.request(config) as unknown as Promise<ApiResult<T>>
}

export default http
