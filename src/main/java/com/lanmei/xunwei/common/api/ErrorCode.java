package com.lanmei.xunwei.common.api;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。
 *
 * 约定（与项目一 InkSpace 保持一致）：
 *   - 程序性错误用 HTTP 语义（4xx/5xx），由 @RestControllerAdvice 负责映射到 HTTP 状态码
 *   - 业务性失败用 HTTP 200 + 业务 code，由前端根据 code 决定怎么提示
 *
 * 分段：41xxx 用户 42xxx 商铺 43xxx 优惠券 44xxx 秒杀 45xxx 内容 46xxx 上传
 *
 * 为什么要有错误码而不是只返回一句中文：前端要能根据 code 做分支（比如 44004 重复下单
 * 就提示"您已经买过了"，44005 处理中就继续轮询），靠字符串匹配消息是脆的——
 * 改一个字前端就失效。
 */
public enum ErrorCode {

    SUCCESS(200, "成功", HttpStatus.OK),
    PARAM_ERROR(400, "参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(401, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, "无权访问", HttpStatus.FORBIDDEN),
    NOT_FOUND(404, "资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(405, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    TOO_MANY_REQUESTS(429, "操作过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR),

    // 41xxx 用户
    PHONE_FORMAT_ERROR(41001, "手机号格式错误", HttpStatus.BAD_REQUEST),
    LOGIN_CODE_ERROR(41002, "验证码错误或已过期", HttpStatus.BAD_REQUEST),
    LOGIN_FAILED(41003, "登录失败", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(41004, "用户不存在", HttpStatus.NOT_FOUND),
    USER_INFO_NOT_FOUND(41005, "用户资料不存在", HttpStatus.NOT_FOUND),

    // 42xxx 商铺
    SHOP_NOT_FOUND(42001, "商铺不存在", HttpStatus.NOT_FOUND),
    SHOP_TYPE_NOT_FOUND(42002, "商铺类型不存在", HttpStatus.NOT_FOUND),

    // 43xxx 优惠券
    VOUCHER_NOT_FOUND(43001, "优惠券不存在", HttpStatus.NOT_FOUND),
    VOUCHER_NOT_SECKILL(43002, "该优惠券不是秒杀券", HttpStatus.BAD_REQUEST),
    VOUCHER_SOLD_OUT(43003, "优惠券已售罄", HttpStatus.BAD_REQUEST),

    // 44xxx 秒杀
    SECKILL_NOT_START(44001, "秒杀尚未开始", HttpStatus.BAD_REQUEST),
    SECKILL_ENDED(44002, "秒杀已结束", HttpStatus.BAD_REQUEST),
    SECKILL_STOCK_EMPTY(44003, "库存不足，已被抢完", HttpStatus.BAD_REQUEST),
    SECKILL_REPEAT(44004, "每人限购一张，您已经参与过", HttpStatus.BAD_REQUEST),
    /** 下单是异步的，请求返回后订单可能还没落库。不是错误，前端据此继续轮询 */
    SECKILL_ORDER_PROCESSING(44005, "订单处理中，请稍后查询", HttpStatus.OK),
    ORDER_NOT_FOUND(44006, "订单不存在", HttpStatus.NOT_FOUND),

    // 45xxx 内容（探店笔记 / 关注 / 评论）
    BLOG_NOT_FOUND(45001, "探店笔记不存在", HttpStatus.NOT_FOUND),
    FOLLOW_SELF_NOT_ALLOWED(45002, "不能关注自己", HttpStatus.BAD_REQUEST),
    COMMENT_NOT_FOUND(45003, "评论不存在", HttpStatus.NOT_FOUND),

    // 46xxx 上传
    UPLOAD_FAILED(46001, "文件上传失败", HttpStatus.INTERNAL_SERVER_ERROR),
    UPLOAD_TYPE_INVALID(46002, "仅支持 jpg/png/gif/webp 图片", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
