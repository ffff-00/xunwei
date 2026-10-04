package com.lanmei.xunwei.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lanmei.xunwei.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 优惠券 Mapper。
 * "查券"必须 JOIN 秒杀表：库存与秒杀起止时间只存在 tb_seckill_voucher 里。
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    /** 某商铺在售的券（含秒杀信息） */
    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);

    /** 按 id 查单张券（含秒杀信息），供秒杀页使用 */
    Voucher queryVoucherById(@Param("id") Long id);
}
