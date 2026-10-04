package com.lanmei.xunwei.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_voucher_order")
public class VoucherOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键，雪花 ID。
     *
     * 必须序列化成字符串给前端。
     * 雪花 ID 是 60 位整数（约 6.4e17），而 JavaScript 的 Number 只能精确表示到 2^53（约 9.0e15）——
     * 直接把数字塞进 JSON，前端解析时会静默丢掉末尾几位。
     *
     * 这个坑是实测出来的：订单真实 id 是 643388269899284784，
     * 前端拿到的是 643388269899284700（末两位被四舍五入），
     * 拿着错的 id 去查订单结果，永远查不到，表现成"订单一直处理中"。
     *
     * 原项目没暴露这个问题，是因为它根本没有"查询订单结果"的接口，
     * 前端只显示一句"下单成功"，这个 id 从来没被回传过。
     * 补上轮询接口之后立刻现形 —— 这也是"接口设计不完整会掩盖 bug"的典型例子。
     *
     * 规矩：凡是可能超过 2^53 的 Long，一律按字符串序列化给前端。
     * 自增主键（商铺、笔记、用户）目前远小于 2^53，暂时保持数字即可，
     * 但一旦换成雪花 ID，就必须一起改。
     */
    @TableId(value = "id", type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 下单的用户id
     */
    private Long userId;

    /**
     * 购买的代金券id
     */
    private Long voucherId;

    /**
     * 支付方式 1：余额支付；2：支付宝；3：微信
     */
    private Integer payType;

    /**
     * 订单状态，1：未支付；2：已支付；3：已核销；4：已取消；5：退款中；6：已退款
     */
    private Integer status;

    /**
     * 下单时间
     */
    private LocalDateTime createTime;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 核销时间
     */
    private LocalDateTime useTime;

    /**
     * 退款时间
     */
    private LocalDateTime refundTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
