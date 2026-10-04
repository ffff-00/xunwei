package com.lanmei.xunwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.dto.VoucherOrderVO;
import com.lanmei.xunwei.entity.VoucherOrder;

import java.util.List;

/**
 * 秒杀订单服务。
 *
 * 下单是异步的：接口只做"资格预检 + 投递消息"，真正的建单由后台消费线程完成。
 * 所以接口返回的 orderId 是"我来处理了"的凭据，不是"已经建好了"的证明 ——
 * 前端必须再调 {@link #queryOrder(Long)} 确认结果。原项目缺了这个查询接口，
 * 导致前端只能乐观地显示"下单成功"，其实订单可能还没落库（甚至最终失败）。
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀下单。成功返回订单号（订单此时可能还没落库，需轮询 {@link #queryOrder}）。
     * 库存不足 / 重复下单 / 不在时间窗内会抛对应的业务异常。
     *
     * 返回类型是 String 而不是 Long，这是刻意的：
     * 订单号是雪花 ID（60 位整数），超过 JS 的 Number.MAX_SAFE_INTEGER（2^53）。
     * 如果这里返回 Long，Jackson 会把它序列化成 JSON 数字，前端解析时末几位被静默改掉，
     * 拿着错的 id 去查订单必然查不到（实测踩过：真实 643388832540000561 → 前端得到 643388832540000500）。
     *
     * 注意：在实体字段上加 @JsonSerialize 保护不到这里 ——
     * 这是一个"裸 Long"出口，不经过实体的字段序列化。每个出口都要单独确认。
     */
    Result<String> seckillVoucher(Long voucherId);

    /**
     * 按订单号查下单结果。
     * 订单还没落库时返回 {@code SECKILL_ORDER_PROCESSING}（不是错误），前端据此继续轮询。
     */
    Result<VoucherOrder> queryOrder(Long orderId);

    /** 我的秒杀订单（含优惠券与商铺信息，供前端列表展示） */
    Result<List<VoucherOrderVO>> queryMyOrders();
}
