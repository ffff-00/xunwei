package com.lanmei.xunwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.entity.Voucher;

import java.util.List;

/**
 * 优惠券服务。
 *
 * 注意券与秒杀券是一对一的两张表：基本信息在 tb_voucher，
 * 秒杀库存与起止时间在 tb_seckill_voucher。
 * 所以凡是要展示库存/倒计时的查询都必须 JOIN，不能只查 tb_voucher。
 */
public interface IVoucherService extends IService<Voucher> {

    /** 某商铺在售的券（含秒杀券的库存与起止时间） */
    Result<List<Voucher>> queryVoucherOfShop(Long shopId);

    /** 按 id 查单张券（含秒杀信息），供秒杀页使用 */
    Result<Voucher> queryVoucherById(Long id);

    /** 新增秒杀券：写券 + 写秒杀信息 + 预热 Redis 库存，三者必须一起完成 */
    void addSeckillVoucher(Voucher voucher);
}
