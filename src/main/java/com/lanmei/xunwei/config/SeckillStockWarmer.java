package com.lanmei.xunwei.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lanmei.xunwei.entity.SeckillVoucher;
import com.lanmei.xunwei.service.ISeckillVoucherService;
import com.lanmei.xunwei.utils.RedisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀库存预热。
 *
 * 为什么需要它
 * 秒杀脚本的第一步是 `redis.call('get', 'seckill:stock:{id}')`。如果这个 key 不存在，
 * Lua 里拿到的是 false，`tonumber(false)` 是 nil，`nil <= 0` 直接报错 ——
 * 整个秒杀功能会以一个"看起来像脚本 bug"的异常挂掉。
 *
 * 而库存键现在有两处来源：
 *   ① 通过 `POST /voucher/seckill` 新建秒杀券时写入（VoucherServiceImpl 里做了）
 *   ② 直接往数据库塞活动数据时没有任何人写 Redis（比如 db/seed-seckill.sql）
 * 再加上 Redis 被清空、换机器调试等情况，启动时就该把"进行中的秒杀"补齐。
 *
 * 为什么用 setIfAbsent 而不是直接 set
 * 这是本类最关键的一句话：秒杀期间 Redis 里的库存是实时的剩余量，而数据库里的库存是滞后的。
 * 一笔订单先在 Redis 扣减、再排队异步落库，所以同一时刻 Redis 的库存通常小于数据库的。
 * 如果每次重启都拿数据库的值覆盖回去，就等于把"已经承诺出去的名额"又发了一遍 —— 会超卖。
 * 所以只在 key 不存在时初始化，已存在就完全不动。
 *
 * 那万一 Redis 真的丢了库存键呢
 * 会按数据库的值重建，确实可能把这些名额多发一次。但不会真的超卖：
 * 落库时 `VoucherOrderPersister` 用的是条件 UPDATE `stock = stock - 1 WHERE stock > 0`，
 * 数据库库存见底后多出来的请求会被这一句拒掉。
 * 这正是"缓存用来扛量，数据库才是最后一道正确性防线"的具体体现。
 */
@Component
public class SeckillStockWarmer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillStockWarmer.class);

    private final ISeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;

    public SeckillStockWarmer(ISeckillVoucherService seckillVoucherService,
                             StringRedisTemplate stringRedisTemplate) {
        this.seckillVoucherService = seckillVoucherService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillVoucher> active = seckillVoucherService.list(new LambdaQueryWrapper<SeckillVoucher>()
                .le(SeckillVoucher::getBeginTime, now)
                .ge(SeckillVoucher::getEndTime, now));

        if (active.isEmpty()) {
            log.info("没有进行中的秒杀活动，跳过库存预热");
            return;
        }

        for (SeckillVoucher sv : active) {
            String key = RedisConstants.SECKILL_STOCK_KEY + sv.getVoucherId();
            Boolean created = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, String.valueOf(sv.getStock()));
            if (Boolean.TRUE.equals(created)) {
                log.info("预热秒杀库存：voucherId={} stock={}（DB 值）", sv.getVoucherId(), sv.getStock());
            } else {
                log.info("秒杀库存已存在，保持不动：voucherId={} 当前 Redis 值={}",
                        sv.getVoucherId(), stringRedisTemplate.opsForValue().get(key));
            }
        }
    }
}
