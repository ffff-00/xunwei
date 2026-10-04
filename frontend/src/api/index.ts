import { request } from './request'
import type {
  Blog,
  ScrollResult,
  Shop,
  ShopType,
  UserInfo,
  UserProfile,
  Voucher,
  VoucherOrder,
  VoucherOrderVO,
} from './types'

/**
 * 所有后端接口的调用入口。
 * 集中在一个文件里，好处是"前端到底依赖后端哪些接口"一眼可见 ——
 * 接口变了只改这里，不用在十几个页面里翻。
 */

// ── 用户 ──

export const userApi = {
  /** 发送手机验证码（本地开发时验证码在后端日志里） */
  sendCode: (phone: string) => request<void>({ url: '/user/code', method: 'post', params: { phone } }),

  /** 手机号 + 验证码登录，成功返回令牌 */
  login: (phone: string, code: string) =>
    request<string>({ url: '/user/login', method: 'post', data: { phone, code } }),

  /** 登出（后端会删掉 Redis 里的登录态） */
  logout: () => request<void>({ url: '/user/logout', method: 'post' }),

  /** 当前登录用户 */
  me: () => request<UserInfo>({ url: '/user/me' }),

  /** 按 id 查用户基本信息 */
  getUser: (id: number) => request<UserInfo>({ url: `/user/${id}` }),

  /** 用户详情（城市、简介、粉丝数等） */
  getProfile: (id: number) => request<UserProfile>({ url: `/user/info/${id}` }),

  /** 签到 */
  sign: () => request<void>({ url: '/user/sign', method: 'post' }),

  /** 连续签到天数 */
  signCount: () => request<number>({ url: '/user/sign/count' }),

  /**
   * 保存我的资料。
   * 注意路径里不带用户 id：改的只能是自己，id 由后端从登录态取，
   * 带 id 反而给了越权的口子。所以这里也不需要传 id。
   */
  saveProfile: (profile: Partial<UserProfile>) =>
    request<void>({ url: '/user/info', method: 'put', data: profile }),
}

// ── 商铺 ──

export const shopApi = {
  /** 商铺分类列表 */
  types: () => request<ShopType[]>({ url: '/shop-type/list' }),

  /** 商铺详情 */
  detail: (id: number) => request<Shop>({ url: `/shop/${id}` }),

  /**
   * 按分类查商铺。
   * 带上 x / y 时后端会走 Redis GEO 返回距离，并附带附近优先的排序。
   */
  byType: (params: { typeId: number; current?: number; x?: number; y?: number }) =>
    request<Shop[]>({ url: '/shop/of/type', params }),

  /** 按名称搜索 */
  byName: (params: { name: string; current?: number }) =>
    request<Shop[]>({ url: '/shop/of/name', params }),
}

// ── 优惠券与秒杀 ──

export const voucherApi = {
  /** 某商铺的优惠券（含秒杀券） */
  ofShop: (shopId: number) => request<Voucher[]>({ url: `/voucher/list/${shopId}` }),

  /** 按 id 查单张券（秒杀页用：那里只有 voucherId，没有 shopId） */
  detail: (id: number) => request<Voucher>({ url: `/voucher/${id}` }),
}

export const seckillApi = {
  /**
   * 抢购。成功返回订单号 —— 注意此时订单还没落库，要去 queryOrder 确认。
   * 订单号是字符串：雪花 ID 超过 JS 安全整数范围，按 number 收会丢精度。
   */
  seckill: (voucherId: number) =>
    request<string>({ url: `/voucher-order/seckill/${voucherId}`, method: 'post' }),

  /**
   * 查询下单结果。
   * 订单还在异步落库时后端返回 44005（会以 BizError 抛出），调用方据此继续轮询。
   */
  queryOrder: (orderId: string) => request<VoucherOrder>({ url: `/voucher-order/${orderId}` }),

  /** 我的订单 */
  myOrders: () => request<VoucherOrderVO[]>({ url: '/voucher-order/of/me' }),
}

// ── 探店笔记 ──

export const blogApi = {
  /** 热门笔记分页 */
  hot: (current = 1) => request<Blog[]>({ url: '/blog/hot', params: { current } }),

  /** 我的笔记分页 */
  ofMe: (current = 1) => request<Blog[]>({ url: '/blog/of/me', params: { current } }),

  /** 某个用户的笔记分页 */
  ofUser: (userId: number, current = 1) =>
    request<Blog[]>({ url: '/blog/of/user', params: { id: userId, current } }),

  /** 笔记详情 */
  detail: (id: number) => request<Blog>({ url: `/blog/${id}` }),

  /**
   * 关注流（滚动分页）。
   * 不用页码而用 minTime + offset：关注流是不断有新内容的，
   * 用页码会"翻页时内容错位"（第 2 页挤进了刚发布的新笔记）。
   */
  ofFollow: (params: { lastId?: number; offset?: number }) =>
    request<ScrollResult<Blog>>({ url: '/blog/of/follow', params }),

  /** 点赞 / 取消点赞，返回是否已点赞 */
  like: (id: number) => request<boolean>({ url: `/blog/like/${id}`, method: 'put' }),

  /** 点赞该笔记的用户列表 */
  likes: (id: number) => request<UserInfo[]>({ url: `/blog/likes/${id}` }),

  /** 发布笔记 */
  save: (blog: Partial<Blog>) => request<number>({ url: '/blog', method: 'post', data: blog }),

  /** 上传笔记图片，返回图片地址 */
  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<string>({
      url: '/upload/blog',
      method: 'post',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

// ── 关注 ──

export const followApi = {
  /** 关注 / 取关 */
  follow: (userId: number, isFollow: boolean) =>
    request<void>({ url: `/follow/${userId}/${isFollow}`, method: 'put' }),

  /** 是否已关注 */
  isFollow: (userId: number) => request<boolean>({ url: `/follow/or/not/${userId}` }),

  /** 共同关注 */
  commons: (userId: number) => request<UserInfo[]>({ url: `/follow/common/${userId}` }),
}
