package com.lanmei.xunwei.controller;

import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.dto.VoucherOrderVO;
import com.lanmei.xunwei.entity.VoucherOrder;
import com.lanmei.xunwei.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 秒杀订单接口。
 *
 * 三个接口的分工（前端就是按这个顺序用的）
 *   POST /voucher-order/seckill/{id}   抢购：返回订单号，此时订单还没落库
 *   GET  /voucher-order/{orderId}      轮询这张订单建好了没有
 *   GET  /voucher-order/of/me          我的订单列表
 *
 * 原项目只有第一个接口。少了第二个，前端就无从知道异步下单到底成没成，
 * 只能乐观地提示"下单成功"—— 这是个真实的体验缺陷，不是可有可无的锦上添花。
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    private final IVoucherOrderService voucherOrderService;

    public VoucherOrderController(IVoucherOrderService voucherOrderService) {
        this.voucherOrderService = voucherOrderService;
    }

    /**
     * 抢购。成功返回订单号（字符串形式，见 IVoucherOrderService 的说明：雪花 ID 超过 JS 精度范围），
     * 前端需轮询下方接口确认结果。
     */
    @PostMapping("/seckill/{id}")
    public Result<String> seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /** 查询下单结果。返回 44005 表示"还在处理中"，前端继续轮询即可，不是错误 */
    @GetMapping("/{orderId}")
    public Result<VoucherOrder> queryOrder(@PathVariable("orderId") Long orderId) {
        return voucherOrderService.queryOrder(orderId);
    }

    /** 我的订单列表 */
    @GetMapping("/of/me")
    public Result<List<VoucherOrderVO>> queryMyOrders() {
        return voucherOrderService.queryMyOrders();
    }
}
