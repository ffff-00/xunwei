package com.lanmei.xunwei.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lanmei.xunwei.dto.VoucherOrderVO;
import com.lanmei.xunwei.entity.VoucherOrder;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 秒杀订单 Mapper。
 * CRUD 走 MyBatis-Plus；需要 JOIN 出展示字段的查询写 XML（resources/mapper/VoucherOrderMapper.xml）。
 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    /**
     * 我的订单列表（已 JOIN 优惠券与商铺，一次取齐展示字段）。
     * 不用 MP 分页：这是个人订单，量小；真需要分页时换成 IPage 参数即可。
     */
    List<VoucherOrderVO> selectMyOrders(@Param("userId") Long userId);
}
