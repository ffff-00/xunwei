package com.lanmei.xunwei.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lanmei.xunwei.common.api.ErrorCode;
import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.entity.SeckillVoucher;
import com.lanmei.xunwei.entity.Voucher;
import com.lanmei.xunwei.mapper.VoucherMapper;
import com.lanmei.xunwei.service.ISeckillVoucherService;
import com.lanmei.xunwei.service.IVoucherService;
import com.lanmei.xunwei.utils.RedisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    private static final Logger log = LoggerFactory.getLogger(VoucherServiceImpl.class);

    private final ISeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;

    public VoucherServiceImpl(ISeckillVoucherService seckillVoucherService,
                              StringRedisTemplate stringRedisTemplate) {
        this.seckillVoucherService = seckillVoucherService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result<List<Voucher>> queryVoucherOfShop(Long shopId) {
        return Result.ok(getBaseMapper().queryVoucherOfShop(shopId));
    }

    @Override
    public Result<Voucher> queryVoucherById(Long id) {
        Voucher voucher = getBaseMapper().queryVoucherById(id);
        if (voucher == null) {
            return Result.fail(ErrorCode.VOUCHER_NOT_FOUND, "voucherId=" + id);
        }
        return Result.ok(voucher);
    }

    /**
     * 新增秒杀券。
     *
     * 三件事必须一起成功：写券、写秒杀信息、预热 Redis 库存。
     * 少了第三件，秒杀脚本第一步 `redis.call('get', stockKey)` 就会因为 key 不存在而报错
     * （Lua 里 tonumber(false) 是 nil，`nil <= 0` 直接抛异常）——
     * 表现是"券创建成功但抢购一直失败"，很难往"忘了预热"这个方向排查。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addSeckillVoucher(Voucher voucher) {
        save(voucher);

        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        stringRedisTemplate.opsForValue()
                .set(RedisConstants.SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
        log.info("新建秒杀券 voucherId={} stock={} 已预热 Redis 库存", voucher.getId(), voucher.getStock());
    }
}
