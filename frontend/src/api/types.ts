/**
 * 后端接口的数据类型。
 * 这些类型与后端的实体 / DTO 一一对应，改动后端字段时这里要同步改。
 */

/** 统一响应结构。code=200 表示成功，其余见后端 ErrorCode */
export interface ApiResult<T = unknown> {
  code: number
  message?: string
  data: T
  /** 分页接口的总数 */
  total?: number
}

/** 业务状态码（与后端 ErrorCode 保持一致） */
export const BizCode = {
  SUCCESS: 200,
  PARAM_ERROR: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  PHONE_FORMAT_ERROR: 41001,
  LOGIN_CODE_ERROR: 41002,
  USER_NOT_FOUND: 41004,
  SHOP_NOT_FOUND: 42001,
  VOUCHER_NOT_FOUND: 43001,
  VOUCHER_NOT_SECKILL: 43002,
  SECKILL_NOT_START: 44001,
  SECKILL_ENDED: 44002,
  SECKILL_STOCK_EMPTY: 44003,
  SECKILL_REPEAT: 44004,
  /** 秒杀订单还在异步落库，前端应继续轮询 */
  SECKILL_ORDER_PROCESSING: 44005,
  ORDER_NOT_FOUND: 44006,
  BLOG_NOT_FOUND: 45001,
  FOLLOW_SELF_NOT_ALLOWED: 45002,
} as const

/** 登录用户（后端 UserDTO） */
export interface UserInfo {
  id: number
  nickName: string
  icon: string
}

/** 商铺分类 */
export interface ShopType {
  id: number
  name: string
  icon: string
  sort: number
}

/** 商铺。distance 只在带经纬度查询附近商铺时才有值 */
export interface Shop {
  id: number
  name: string
  typeId: number
  /** 图片地址用逗号分隔 */
  images: string
  area: string
  address: string
  x: number
  y: number
  /** 人均消费（分） */
  avgPrice: number
  sold: number
  comments: number
  score: number
  openHours: string
  /** 米，仅附近查询返回 */
  distance?: number
}

/** 优惠券。type: 0 普通券 / 1 秒杀券 */
export interface Voucher {
  id: number
  shopId: number
  title: string
  subTitle: string
  rules: string
  /** 支付金额（分） */
  payValue: number
  /** 面值（分） */
  actualValue: number
  type: number
  status: number
  /** 以下三个字段只在秒杀券上存在 */
  stock?: number
  beginTime?: string
  endTime?: string
}

/** 探店笔记 */
export interface Blog {
  id: number
  shopId: number
  userId: number
  /** 作者头像，列表接口返回 */
  icon?: string
  /** 作者昵称，列表接口返回 */
  name?: string
  /** 当前用户是否点过赞 */
  isLike?: boolean
  title: string
  /** 图片地址用逗号分隔 */
  images: string
  content: string
  liked: number
  comments: number
  createTime: string
}

/** 秒杀订单（后端 VoucherOrder） */
export interface VoucherOrder {
  /**
   * 字符串而不是 number。
   * 后端订单号是雪花 ID（60 位整数），超过 JS 的 Number.MAX_SAFE_INTEGER（2^53），
   * 按 number 接收会被静默改掉末尾几位 —— 实测：真实 id 643388269899284784，
   * 按 number 解析变成 643388269899284700，拿这个值去查订单永远查不到。
   * 所以后端用 ToStringSerializer 序列化成字符串，前端也必须按字符串传。
   */
  id: string
  userId: number
  voucherId: number
  payType: number
  /** 1 未支付 / 2 已支付 / 3 已核销 / 4 已取消 / 5 退款中 / 6 已退款 */
  status: number
  createTime: string
}

/** 订单列表项（后端 JOIN 出来的展示对象） */
export interface VoucherOrderVO {
  /** 同 VoucherOrder.id：雪花 ID，必须按字符串处理 */
  orderId: string
  voucherId: number
  voucherTitle: string
  voucherSubTitle: string
  shopId: number
  shopName: string
  payValue: number
  actualValue: number
  status: number
  createTime: string
}

/** 滚动分页结果（关注流用） */
export interface ScrollResult<T> {
  list: T[]
  minTime: number
  offset: number
}

/** 用户详情（tb_user_info）。字段类型与后端实体严格对齐，别用 any 图省事 */
export interface UserProfile {
  userId: number
  city: string
  introduce: string
  /** 后端是 Boolean：false=男、true=女，null=未填写 */
  gender: boolean | null
  /** 后端是 LocalDate，序列化成 "yyyy-MM-dd" */
  birthday: string | null
  credits: number
  level: number
  fans: number
  followee: number
}
