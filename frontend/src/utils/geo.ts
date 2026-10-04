/**
 * 定位工具。
 *
 * 附近商铺依赖经纬度。浏览器定位需要用户授权，而且 http 下大部分浏览器直接拒绝
 * （只有 https 或 localhost 才允许），所以必须有兜底，否则本地跑起来"附近"永远是空的。
 *
 * 兜底坐标用杭州武林商圈（种子数据里的商铺都在杭州），这样不授权也能看到合理的距离排序。
 */

const FALLBACK = { x: 120.149192, y: 30.316078 }

export interface Coordinate {
  x: number
  y: number
  /** 是否用了兜底坐标 —— 界面上要据此提示用户"这不是你的真实位置" */
  fallback: boolean
}

let cached: Coordinate | null = null

export function locate(timeout = 3000): Promise<Coordinate> {
  if (cached) return Promise.resolve(cached)

  return new Promise((resolve) => {
    if (!('geolocation' in navigator)) {
      cached = { ...FALLBACK, fallback: true }
      resolve(cached)
      return
    }

    // 定位失败、超时、用户拒绝 —— 一律用兜底坐标，不让"附近"功能整个不可用
    const timer = window.setTimeout(() => {
      cached = { ...FALLBACK, fallback: true }
      resolve(cached)
    }, timeout)

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        window.clearTimeout(timer)
        cached = { x: pos.coords.longitude, y: pos.coords.latitude, fallback: false }
        resolve(cached)
      },
      () => {
        window.clearTimeout(timer)
        cached = { ...FALLBACK, fallback: true }
        resolve(cached)
      },
      { timeout, enableHighAccuracy: false },
    )
  })
}
