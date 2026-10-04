package com.lanmei.xunwei.controller;

import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.entity.Voucher;
import com.lanmei.xunwei.service.IVoucherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 优惠券接口。
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    private final IVoucherService voucherService;

    public VoucherController(IVoucherService voucherService) {
        this.voucherService = voucherService;
    }

    /**
     * 某商铺在售的券（含秒杀券的库存与起止时间）。
     * 商铺详情页用这个接口渲染优惠券区块。
     */
    @GetMapping("/list/{shopId}")
    public Result<List<Voucher>> queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
        return voucherService.queryVoucherOfShop(shopId);
    }

    /**
     * 按 id 查单张券。秒杀页只有 voucherId，没有 shopId，所以需要这个接口。
     */
    @GetMapping("/{id}")
    public Result<Voucher> queryVoucherById(@PathVariable("id") Long id) {
        return voucherService.queryVoucherById(id);
    }

    /**
     * 新增秒杀券（券 + 秒杀信息 + Redis 库存预热，同一事务）。
     * 目前没有管理后台，这个接口是给演示与测试用的。
     */
    @PostMapping("/seckill")
    public Result<Long> addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /** 新增普通券 */
    @PostMapping
    public Result<Long> addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }
}
