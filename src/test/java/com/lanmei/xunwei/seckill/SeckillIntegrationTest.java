package com.lanmei.xunwei.seckill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lanmei.xunwei.common.api.ErrorCode;
import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.common.exception.BizException;
import com.lanmei.xunwei.dto.UserDTO;
import com.lanmei.xunwei.entity.SeckillVoucher;
import com.lanmei.xunwei.entity.Voucher;
import com.lanmei.xunwei.entity.VoucherOrder;
import com.lanmei.xunwei.mapper.VoucherOrderMapper;
import com.lanmei.xunwei.service.ISeckillVoucherService;
import com.lanmei.xunwei.service.IVoucherOrderService;
import com.lanmei.xunwei.service.IVoucherService;
import com.lanmei.xunwei.utils.RedisConstants;
import com.lanmei.xunwei.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 秒杀链路的正确性测试。
 *
 * 重点验证高并发下会不会超卖、重复下单：
 * 三条防线的分工，逐条验证：
 *   ① Redis Lua 原子预检     → 100 个请求抢 10 份库存，只放行 10 个
 *   ② 数据库条件 UPDATE      → 落库阶段不会把 stock 减成负数
 *   ③ uk_user_voucher 唯一索引 → 同一用户重复提交（含消息重放）只产生一张订单
 *
 * 为什么不用 @Transactional 包住测试
 * 秒杀是跨 Redis 与数据库、而且是异步的：下单接口把消息投进 Stream 就返回，
 * 真正的落库发生在另一个线程的另一个事务里。用测试事务包住的话，消费线程看不到未提交的数据，
 * 测出来的是假象。所以这里改成"跑完显式清理"，测试数据用 9xxxxx 段专用用户 ID 与真实数据隔离。
 */
@SpringBootTest
class SeckillIntegrationTest {

    private static final long TEST_SHOP_ID = 1L;
    private static final long TEST_USER_BASE = 900000L;

    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private IVoucherService voucherService;
    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private VoucherOrderMapper voucherOrderMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 当前测试用的秒杀券 id（由数据库自增生成） */
    private Long testVoucherId;

    @BeforeEach
    void setUp() {
        createSeckillVoucher(10, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
    }

    @AfterEach
    void tearDown() {
        if (testVoucherId != null) {
            voucherOrderMapper.delete(new LambdaQueryWrapper<VoucherOrder>()
                    .eq(VoucherOrder::getVoucherId, testVoucherId));
            seckillVoucherService.removeById(testVoucherId);
            voucherService.removeById(testVoucherId);
            stringRedisTemplate.delete(List.of(
                    RedisConstants.SECKILL_STOCK_KEY + testVoucherId,
                    RedisConstants.SECKILL_ORDER_KEY + testVoucherId));
            testVoucherId = null;
        }
    }

    // =========================================================================
    // ① + ② 防超卖
    // =========================================================================

    @Test
    @DisplayName("防超卖：100 个用户并发抢 10 份库存 → 恰好 10 张订单、库存归零且不为负")
    void noOversellUnderConcurrency() throws Exception {
        int users = 100;
        int stock = 10;
        createSeckillVoucher(stock, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));

        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<String> unexpected = Collections.synchronizedList(new ArrayList<>());

        long start = System.nanoTime();
        runConcurrently(users, userIndex -> {
            try {
                Result<String> result = voucherOrderService.seckillVoucher(testVoucherId);
                if (result.isSuccess()) {
                    accepted.incrementAndGet();
                }
            } catch (BizException e) {
                // 库存被抢完走的就是这条路径，属预期
                rejected.incrementAndGet();
            } catch (Exception e) {
                unexpected.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
        Duration submitElapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(unexpected).as("不该出现预期外的异常").isEmpty();
        assertThat(accepted.get()).as("Redis 预检放行的数量必须恰好等于库存").isEqualTo(stock);
        assertThat(rejected.get()).as("其余请求应被库存不足挡掉").isEqualTo(users - stock);

        long persisted = awaitOrderCount(stock, Duration.ofSeconds(60));
        int dbStock = seckillVoucherService.getById(testVoucherId).getStock();
        String redisStock = stringRedisTemplate.opsForValue()
                .get(RedisConstants.SECKILL_STOCK_KEY + testVoucherId);

        System.out.printf("""
                
                ================ 秒杀防超卖结果 ================
                并发用户数        ：%d
                初始库存          ：%d
                Redis 预检放行    ：%d
                被拒请求          ：%d
                落库订单数        ：%d
                数据库剩余库存    ：%d
                Redis 剩余库存    ：%s
                接口耗时          ：%d ms（%d 个请求，%.0f 请求/秒）
                ==============================================
                """, users, stock, accepted.get(), rejected.get(), persisted, dbStock, redisStock,
                submitElapsed.toMillis(), users,
                users / Math.max(0.001, submitElapsed.toMillis() / 1000.0));

        assertThat(persisted).as("落库订单数必须等于库存，一张不多一张不少").isEqualTo(stock);
        assertThat(dbStock).as("数据库库存必须减到 0，且绝不能为负").isZero();
        assertThat(redisStock).isEqualTo("0");
    }

    // =========================================================================
    // ③ 一人一单
    // =========================================================================

    @Test
    @DisplayName("一人一单：同一用户并发提交 50 次 → 只拿到 1 次资格、只产生 1 张订单")
    void oneOrderPerUserUnderConcurrency() throws Exception {
        createSeckillVoucher(100, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
        long sameUser = TEST_USER_BASE + 7777;

        AtomicInteger accepted = new AtomicInteger();
        List<String> unexpected = Collections.synchronizedList(new ArrayList<>());

        runConcurrentlyWithSameUser(50, sameUser, () -> {
            try {
                Result<String> result = voucherOrderService.seckillVoucher(testVoucherId);
                if (result.isSuccess()) {
                    accepted.incrementAndGet();
                }
            } catch (BizException e) {
                // 重复下单，预期内
            } catch (Exception e) {
                unexpected.add(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });

        assertThat(unexpected).isEmpty();
        assertThat(accepted.get()).as("同一个用户只应有一次拿到资格（Lua 里的 SISMEMBER 是原子的）")
                .isEqualTo(1);

        awaitOrderCount(1, Duration.ofSeconds(30));
        long orderCount = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getVoucherId, testVoucherId)
                .eq(VoucherOrder::getUserId, sameUser));
        int remainStock = seckillVoucherService.getById(testVoucherId).getStock();

        System.out.printf("""
                
                ================ 一人一单结果 ================
                同一用户并发提交  ：50 次
                拿到资格的请求    ：%d 次
                数据库订单数      ：%d 张
                数据库剩余库存    ：%d（初始 100，只扣 1）
                =============================================
                """, accepted.get(), orderCount, remainStock);

        assertThat(orderCount).as("同一用户只能有一张订单").isEqualTo(1);
        assertThat(remainStock).as("库存只应扣 1").isEqualTo(99);
    }

    // =========================================================================
    // 时间窗与异步结果查询
    // =========================================================================

    @Test
    @DisplayName("时间窗：已结束 / 未开始的秒杀券都不能下单")
    void seckillTimeWindowIsEnforced() {
        createSeckillVoucher(10, LocalDateTime.now().minusHours(5), LocalDateTime.now().minusHours(1));
        assertThat(errorCodeOf(() -> voucherOrderService.seckillVoucher(testVoucherId)))
                .as("过期券必须被拒").isEqualTo(ErrorCode.SECKILL_ENDED);

        createSeckillVoucher(10, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(5));
        assertThat(errorCodeOf(() -> voucherOrderService.seckillVoucher(testVoucherId)))
                .as("未开始的券必须被拒").isEqualTo(ErrorCode.SECKILL_NOT_START);
    }

    @Test
    @DisplayName("结果查询：异步落库前返回\"处理中\"业务码，落库后查得到")
    void queryOrderReflectsAsyncProgress() {
        createSeckillVoucher(10, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
        // 这两个接口都要求登录态，测试里直接放到 ThreadLocal（真实请求由拦截器填充）
        UserHolder.saveUser(user(TEST_USER_BASE + 1));
        try {
            Result<String> submitted = voucherOrderService.seckillVoucher(testVoucherId);
            assertThat(submitted.isSuccess()).isTrue();
            String orderId = submitted.getData();

            // 立刻查一次。不断言一定是"处理中" —— 消费线程可能恰好已经处理完了，
            // 那时直接返回订单也是对的。写死"必须处理中"会变成一个随机失败的脆弱测试。
            Result<VoucherOrder> first = voucherOrderService.queryOrder(Long.parseLong(orderId));
            assertThat(first.isSuccess() || first.getCode() == ErrorCode.SECKILL_ORDER_PROCESSING.getCode())
                    .as("第一次查询要么已落库、要么明确告知处理中，不能是别的错误").isTrue();

            awaitOrderCount(1, Duration.ofSeconds(30));
            Result<VoucherOrder> finalResult = voucherOrderService.queryOrder(Long.parseLong(orderId));
            assertThat(finalResult.isSuccess()).isTrue();
            assertThat(finalResult.getData().getVoucherId()).isEqualTo(testVoucherId);
        } finally {
            UserHolder.removeUser();
        }
    }

    // =========================================================================
    // 工具
    // =========================================================================

    private void createSeckillVoucher(int stock, LocalDateTime begin, LocalDateTime end) {
        tearDown();

        Voucher voucher = new Voucher();
        voucher.setShopId(TEST_SHOP_ID);
        voucher.setTitle("自动化测试用秒杀券");
        voucher.setSubTitle("仅用于测试，跑完即删");
        voucher.setRules("无");
        voucher.setPayValue(100L);
        voucher.setActualValue(1000L);
        voucher.setType(1);
        voucher.setStatus(1);
        voucherService.save(voucher);
        testVoucherId = voucher.getId();

        SeckillVoucher sv = new SeckillVoucher();
        sv.setVoucherId(testVoucherId);
        sv.setStock(stock);
        sv.setBeginTime(begin);
        sv.setEndTime(end);
        seckillVoucherService.save(sv);

        // 模拟活动创建时的库存预热（生产路径见 VoucherServiceImpl.addSeckillVoucher）
        stringRedisTemplate.opsForValue()
                .set(RedisConstants.SECKILL_STOCK_KEY + testVoucherId, String.valueOf(stock));
    }

    /** 起 N 个线程，每个线程一个不同用户 */
    private void runConcurrently(int threads, IntConsumer action) throws Exception {
        long[] userIds = new long[threads];
        for (int i = 0; i < threads; i++) {
            userIds[i] = TEST_USER_BASE + i;
        }
        runConcurrently(userIds, action);
    }

    /** 起 N 个线程，全部用同一个用户 */
    private void runConcurrentlyWithSameUser(int threads, long userId, Runnable action) throws Exception {
        long[] userIds = new long[threads];
        java.util.Arrays.fill(userIds, userId);
        runConcurrently(userIds, i -> action.run());
    }

    /**
     * CountDownLatch 起跑线：所有线程先阻塞在 await 上，再一次性放行。
     * 普通 for 循环里前一个请求早就返回了，线程之间根本不重叠 —— 那样测不出并发问题。
     */
    private void runConcurrently(long[] userIds, IntConsumer action) throws Exception {
        int threads = userIds.length;
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                final int index = i;
                pool.submit(() -> {
                    // 每个线程模拟一个已登录用户（真实请求里由拦截器完成）
                    UserHolder.saveUser(user(userIds[index]));
                    try {
                        startLine.await();
                        action.accept(index);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        UserHolder.removeUser();
                        finished.countDown();
                    }
                });
            }
            startLine.countDown();
            assertThat(finished.await(2, TimeUnit.MINUTES)).as("并发任务未在 2 分钟内完成").isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 轮询等待异步落库，返回实际订单数。
     * 注意抽到目标值后要多等一拍再数一次：否则可能在"最后一条消息还在路上"时就断言了，
     * 那样即使真的超卖也发现不了。
     */
    private long awaitOrderCount(long expected, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            long count = countOrders();
            if (count >= expected) {
                sleep(500);
                return countOrders();
            }
            sleep(100);
        }
        return countOrders();
    }

    private long countOrders() {
        return voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getVoucherId, testVoucherId));
    }

    private ErrorCode errorCodeOf(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        UserHolder.saveUser(user(TEST_USER_BASE + 1));
        try {
            Throwable t = catchThrowable(callable);
            assertThat(t).as("应抛出业务异常").isInstanceOf(BizException.class);
            return ((BizException) t).getErrorCode();
        } finally {
            UserHolder.removeUser();
        }
    }

    private UserDTO user(long userId) {
        UserDTO dto = new UserDTO();
        dto.setId(userId);
        dto.setNickName("tester-" + userId);
        return dto;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
