/**
 * 展示层格式化工具。
 * 后端为了简单，把多张图片拼成一个逗号分隔的字符串、金额一律用"分"。
 * 这些格式转换集中放这里，避免每个组件各写一份（早晚有一处忘了除 100）。
 */

/** 逗号分隔的图片字段 → 数组 */
export function splitImages(images?: string | null): string[] {
  if (!images) return []
  return images
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

/** 取第一张图，用作封面 */
export function firstImage(images?: string | null): string {
  return splitImages(images)[0] ?? ''
}

/** 分 → 元。整数不显示小数位（1000 → 10，1050 → 10.5） */
export function fen2yuan(fen?: number | null): string {
  if (fen == null) return '0'
  const yuan = fen / 100
  return Number.isInteger(yuan) ? String(yuan) : yuan.toFixed(2)
}

/**
 * 商铺人均消费。
 *
 * 这里不能复用 fen2yuan —— 原始数据模型里金额的单位是不统一的：
 *   - tb_shop.avg_price        存的是元（种子数据里 80 就是 80 元/人）
 *   - tb_voucher.pay_value     存的是分（4750 是 47.5 元）
 *   - tb_voucher.actual_value  同上，分
 * 一开始我统一按分处理，结果商铺显示成 "¥0.80/人"（实测出来的）。
 * 正确做法是后端统一单位，这里先把两套分别处理并标明来源。
 */
export function shopAvgPrice(yuan?: number | null): string {
  if (yuan == null) return '0'
  return String(yuan)
}

/** 距离：小于 1 公里用米，否则用公里 */
export function formatDistance(meters?: number | null): string {
  if (meters == null) return ''
  if (meters < 1000) return `${Math.round(meters)}m`
  return `${(meters / 1000).toFixed(1)}km`
}

/** 时间字符串 → 相对时间（刚刚 / 3 分钟前 / 2 天前 / 具体日期） */
export function fromNow(time?: string | null): string {
  if (!time) return ''
  const target = parseTime(time)
  if (!target) return time

  const diff = Date.now() - target.getTime()
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 30 * day) return `${Math.floor(diff / day)} 天前`
  return time.slice(0, 10)
}

/** 秒杀倒计时文字 */
export function formatCountdown(ms: number): string {
  if (ms <= 0) return '00:00:00'
  const total = Math.floor(ms / 1000)
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return days > 0
    ? `${days} 天 ${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
    : `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
}

/**
 * 解析后端返回的时间。
 * 后端 LocalDateTime 序列化成 "2026-09-30T18:59:40"，Safari 对带 T 的格式解析不稳，
 * 统一换成空格形式再交给 Date，避免"同一个页面 Chrome 正常 Safari 显示 Invalid Date"。
 */
function parseTime(time: string): Date | null {
  const normalized = time.includes('T') ? time.replace('T', ' ') : time
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}
