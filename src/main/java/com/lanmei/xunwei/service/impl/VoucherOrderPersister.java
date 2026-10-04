package com.lanmei.xunwei.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lanmei.xunwei.common.api.ErrorCode;
import com.lanmei.xunwei.common.exception.BizException;
import com.lanmei.xunwei.entity.VoucherOrder;
import com.lanmei.xunwei.mapper.VoucherOrderMapper;
import com.lanmei.xunwei.service.ISeckillVoucherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秒杀订单的"落库"一步：建订单 + 扣库存，同一个事务。
 *
 * 为什么这个类必须单独存在（这是本项目最值得讲的一个点）
 * 原项目里这段逻辑写在 `VoucherOrderServiceImpl.createVoucherOrder(Long)` 上，方法标了
 * `@Transactional`，但事务从来没有生效过：
 *
 *   ① 同步路径：`return createVoucherOrder(voucherId);` —— 类内自调用
 *   ② 异步路径：内部类里直接调 `createVoucherOrder(order)` —— 也是类内自调用
 *
 * Spring 的事务是 AOP 代理实现的：只有从外部经过代理调用，才会开事务。
 * 类内部 `this.xxx()` 走的是原始对象，代理被绕过了，注解形同虚设。
 * 后果是"扣库存"和"建订单"并不在一个事务里 —— 扣了库存而建单失败时不会回滚，
 * 库存凭空少一张。这种 bug 不报错、不告警，只在并发或异常时偶尔体现。
 *
 * 把这段搬到一个独立 Bean，调用方通过注入的代理调用它，事务才真的生效。
 * 这也是"为什么需要分层/拆 Bean"最实在的一个理由 —— 不是为了好看，是为了注解能生效。
 */
@Service
public class VoucherOrderPersister {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderPersister.class);

    public enum Outcome {
        /** 订单建成功，库存已扣 */
        CREATED,
        /** 该用户对该券已下过单（一人一单），本次什么都没改 */
        DUPLICATE
    }

    private final ISeckillVoucherService seckillVoucherService;
    private final VoucherOrderMapper voucherOrderMapper;

    public VoucherOrderPersister(ISeckillVoucherService seckillVoucherService,
                                 VoucherOrderMapper voucherOrderMapper) {
        this.seckillVoucherService = seckillVoucherService;
        this.voucherOrderMapper = voucherOrderMapper;
    }

    /**
     * 落单。
     *
     * 为什么是"先插订单、再扣库存"这个顺序
     * 一人一单靠 `tb_voucher_order.uk_user_voucher` 唯一索引判定。
     * 先插订单的话，重复下单会在第一步就被数据库拒掉，库存完全没被动过，事务甚至连回滚都不需要。
     * 反过来先扣库存的话，重复请求会先白白扣掉一张库存，再靠回滚补回来 —— 徒增锁竞争和回滚开销。
     *
     * 为什么扣库存用条件 UPDATE
     * `SET stock = stock - 1 WHERE voucher_id = ? AND stock > 0` 把"判断有没有库存"和"减库存"
     * 合成一条语句，靠 InnoDB 的行锁保证原子性。拆成"先 SELECT 判断、再 UPDATE"两步，
     * 两个并发请求会同时读到 stock=1 然后各扣一次，直接超卖。
     *
     * 三道防线各自的职责
     *   Redis Lua 预检   挡住 99% 的无效请求，让它们根本到不了数据库   —— 性能
     *   本方法的事务     保证"建单"与"扣库存"要么都成、要么都不成     —— 原子性
     *   唯一索引 + 条件UPDATE  与并发、重试、重放无关地保证正确        —— 正确性
     * 只有第一道防线是不够的（消息会重放、进程会崩），只有后两道是够的但会打满数据库。
     *
     * @throws BizException 库存真的没了（SECKILL_STOCK_EMPTY）—— 会触发事务回滚，撤销刚插入的订单
     */
    @Transactional(rollbackFor = Exception.class)
    public Outcome persist(VoucherOrder order) {
        // ① 建订单：让唯一索引来判定一人一单
        try {
            voucherOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 重复下单。此时什么都没改过，直接返回，事务无需回滚
            log.info("一人一单命中，忽略重复消息：userId={} voucherId={}", order.getUserId(), order.getVoucherId());
            return Outcome.DUPLICATE;
        }

        // ② 扣库存：条件 UPDATE，防超卖
        boolean deducted = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", order.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!deducted) {
            // Redis 预检说有货、数据库却没有：两边不一致，而这条消息已经走到尽头（重试也一样）。
            // 抛异常让事务回滚，把①刚插进去的订单撤销掉 —— 否则会留下"有订单、没扣库存"的脏数据。
            throw new BizException(ErrorCode.SECKILL_STOCK_EMPTY,
                    "数据库库存已空，voucherId=" + order.getVoucherId());
        }

        return Outcome.CREATED;
    }

    /** 该用户是否已对该券下过单（供接口层做友好提示用，真正的判定仍靠唯一索引） */
    public boolean hasOrdered(Long userId, Long voucherId) {
        return voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId)) > 0;
    }
}
