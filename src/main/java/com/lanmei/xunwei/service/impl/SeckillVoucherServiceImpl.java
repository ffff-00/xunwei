package com.lanmei.xunwei.service.impl;

import com.lanmei.xunwei.entity.SeckillVoucher;
import com.lanmei.xunwei.mapper.SeckillVoucherMapper;
import com.lanmei.xunwei.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
