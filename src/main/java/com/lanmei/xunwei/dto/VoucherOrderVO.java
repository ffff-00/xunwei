package com.lanmei.xunwei.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀订单的展示对象。
 *
 * 为什么不直接把 VoucherOrder 实体丢给前端：实体里只有 voucherId / shopId，
 * 前端要展示"优惠券名称、商铺名"，只能拿着 id 再发一次请求 —— 列表页会变成 N+1 次请求。
 * 用一个 JOIN 出来的展示对象，一次查询把要显示的字段凑齐。
 */
@Data
public class VoucherOrderVO {

    /**
     * 同样的原因必须按字符串给前端：这是雪花 ID，超过 JS 的 2^53 安全整数范围，
     * 按数字返回会被静默改掉末尾几位（详见 VoucherOrder.id 的注释）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    private Long voucherId;
    /** 优惠券标题 */
    private String voucherTitle;
    private String voucherSubTitle;
    private Long shopId;
    /** 商铺名 */
    private String shopName;
    /** 支付金额（分） */
    private Long payValue;
    /** 面值（分） */
    private Long actualValue;
    /** 订单状态：1 未支付 / 2 已支付 / 3 已核销 / 4 已取消 / 5 退款中 / 6 已退款 */
    private Integer status;
    private LocalDateTime createTime;
}
