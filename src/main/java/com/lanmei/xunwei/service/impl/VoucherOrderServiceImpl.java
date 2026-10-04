package com.lanmei.xunwei.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lanmei.xunwei.common.api.ErrorCode;
import com.lanmei.xunwei.common.api.Result;
import com.lanmei.xunwei.common.exception.BizException;
import com.lanmei.xunwei.dto.UserDTO;
import com.lanmei.xunwei.dto.VoucherOrderVO;
import com.lanmei.xunwei.entity.SeckillVoucher;
import com.lanmei.xunwei.entity.VoucherOrder;
import com.lanmei.xunwei.mapper.VoucherOrderMapper;
import com.lanmei.xunwei.service.ISeckillVoucherService;
import com.lanmei.xunwei.service.IVoucherOrderService;
import com.lanmei.xunwei.utils.RedisIdWorker;
import com.lanmei.xunwei.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 秒杀下单服务。
 *
 * 整体链路
 *   ① 前端点"立即抢购"
 *   ② 服务端先校验秒杀时间窗（开始/结束时间）
 *   ③ 执行 Lua 脚本，一次原子操作完成三件事：
 *        - 判断库存是否充足（GET seckill:stock:{voucherId}）
 *        - 判断该用户是否已经下过单（SISMEMBER seckill:order:{voucherId} userId）
 *        - 扣库存 + 记录用户 + XADD 把下单消息投进 Redis Stream
 *      → 为什么必须原子：这三步之间被别的请求插进来，就会超卖或重复下单
 *   ④ 接口立刻返回订单号（不阻塞等数据库），前端据 orderId 轮询结果
 *   ⑤ 后台单线程消费 Stream，把消息落成数据库里的订单
 *        → 为什么串行单线程就够：真正的过滤已经在 Redis 做完了，到这里的量很小
 *   ⑥ 落库时由 {@link VoucherOrderPersister} 保证"建单 + 扣库存"的事务性，
 *      并由唯一索引兜住一人一单
 *
 * 相比原项目的实现，这里改掉了四件事：
 *   P1 `@Transactional` 自调用失效           → 拆出 VoucherOrderPersister 独立 Bean
 *   P2 消费组从未创建，启动就刷 NOGROUP      → ensureConsumerGroup() 用 XGROUP CREATE ... MKSTREAM
 *   P3 XACK 把流名写成了组名 s1，消息永不确认 → 改用 SECKILL_STREAM 常量
 *   P4 异常分支形成忙循环，刷爆日志           → 读失败退避重试；处理失败区分"该不该重试"
 *   另外补回了被原项目弄丢的秒杀时间窗校验，以及原项目没有的订单结果查询接口。
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final Logger log = LoggerFactory.getLogger(VoucherOrderServiceImpl.class);

    /** 异步下单用的 Stream 与消费组。key 必须与 seckill.lua 里的 XADD 保持一致 */
    private static final String SECKILL_STREAM = "stream.orders";
    private static final String CONSUMER_GROUP = "g1";
    private static final String CONSUMER_NAME = "c1";

    /** 一次 pending 回收最多循环多少轮，避免坏消息把这一轮卡死 */
    private static final int PENDING_MAX_ROUNDS = 50;
    /** 主循环每空转多少轮做一次 pending 回收（每轮 BLOCK 2 秒，30 轮 ≈ 1 分钟） */
    private static final int IDLE_ROUNDS_BEFORE_PENDING_RECOVER = 30;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 消费线程。单线程是刻意的：Redis 预检已经把绝大多数无效请求挡在外面，
     * 真正需要落库的量很小，单线程串行反而避免了并发写同一张库存表的锁竞争。
     * 代价是单点：真要做多消费者时，靠的是数据库唯一索引 + 条件 UPDATE 保证正确性，
     * 而不是"只有一个线程所以不会冲突"这个脆弱的前提。
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "seckill-order-consumer");
        t.setDaemon(true);
        return t;
    });

    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private final ISeckillVoucherService seckillVoucherService;
    private final VoucherOrderPersister voucherOrderPersister;

    public VoucherOrderServiceImpl(RedisIdWorker redisIdWorker,
                                   StringRedisTemplate stringRedisTemplate,
                                   ISeckillVoucherService seckillVoucherService,
                                   VoucherOrderPersister voucherOrderPersister) {
        this.redisIdWorker = redisIdWorker;
        this.stringRedisTemplate = stringRedisTemplate;
        this.seckillVoucherService = seckillVoucherService;
        this.voucherOrderPersister = voucherOrderPersister;
    }

    @PostConstruct
    private void init() {
        ensureConsumerGroup();
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // =========================================================================
    // 对外接口
    // =========================================================================

    @Override
    public Result<String> seckillVoucher(Long voucherId) {
        Long userId = currentUserId();

        // ① 时间窗校验。原项目的最终实现把这段丢了（只在被注释掉的旧版本里存在），
        //    结果是「已结束的秒杀券照样能下单」—— 用 db/seed-seckill.sql 里 id=4 那张过期券可直接复现。
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            throw new BizException(ErrorCode.VOUCHER_NOT_SECKILL, "voucherId=" + voucherId);
        }
        LocalDateTime now = LocalDateTime.now();
        if (seckillVoucher.getBeginTime() != null && now.isBefore(seckillVoucher.getBeginTime())) {
            throw new BizException(ErrorCode.SECKILL_NOT_START, "开始时间 " + seckillVoucher.getBeginTime());
        }
        if (seckillVoucher.getEndTime() != null && now.isAfter(seckillVoucher.getEndTime())) {
            throw new BizException(ErrorCode.SECKILL_ENDED, "结束时间 " + seckillVoucher.getEndTime());
        }

        // ② 订单号先算出来，它是消息的一部分（Redis 里只放必要字段，不把整个订单对象塞进去）
        long orderId = redisIdWorker.nextId("order");

        // ③ Lua：判库存 + 判一人一单 + 投消息，三步原子
        Long scriptResult = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = scriptResult == null ? -1 : scriptResult.intValue();
        if (r == 1) {
            throw new BizException(ErrorCode.SECKILL_STOCK_EMPTY);
        }
        if (r == 2) {
            throw new BizException(ErrorCode.SECKILL_REPEAT);
        }
        if (r != 0) {
            // 脚本返回了预期外的值：说明 Lua 脚本本身有问题（改了脚本没改这里）
            log.error("秒杀脚本返回预期外的值 {}：voucherId={} userId={}", r, voucherId, userId);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "秒杀脚本返回 " + r);
        }

        // ④ 立刻返回订单号（转成字符串，见 IVoucherOrderService 的说明：雪花 ID 超过 JS 精度范围）。
        //    此时订单还没落库，前端要拿 orderId 去轮询 queryOrder 才知道结果。
        //    这里不能假装成功 —— 原项目缺了这个查询接口，前端只能乐观显示"下单成功"，
        //    订单万一落库失败（库存对不上、DB 抖动），用户就永远等不到那张券。
        return Result.ok(String.valueOf(orderId));
    }

    @Override
    public Result<VoucherOrder> queryOrder(Long orderId) {
        if (orderId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "orderId 不能为空");
        }
        VoucherOrder order = getById(orderId);
        if (order == null) {
            // 还没落库。这不是错误，前端继续轮询（给一个可区分的业务码，别让它去匹配文案）
            return Result.fail(ErrorCode.SECKILL_ORDER_PROCESSING);
        }
        // 越权校验：只能看自己的订单
        if (!currentUserId().equals(order.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "订单不属于当前用户 orderId=" + orderId);
        }
        // 注意：这里返回的实体里，id 字段带 @JsonSerialize(ToStringSerializer)，
        // 所以序列化出去仍然是字符串，前端不会丢精度。
        return Result.ok(order);
    }

    @Override
    public Result<List<VoucherOrderVO>> queryMyOrders() {
        return Result.ok(baseMapper.selectMyOrders(currentUserId()));
    }

    // =========================================================================
    // 消费线程
    // =========================================================================

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            // 启动先回收一次：上次进程崩溃时"读走但没确认"的消息会留在 pending 列表里，
            // 而 `>`（lastConsumed）只投递"从未投递过"的消息，不主动捞 pending，不扫就永远补不上。
            handlePendingList();

            int idleRounds = 0;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    List<MapRecord<String, Object, Object>> records = readNewMessages();
                    if (records == null || records.isEmpty()) {
                        // 空闲时周期性回收 pending（运行中处理失败的消息不会被 ACK，只能靠这里重放）
                        if (++idleRounds >= IDLE_ROUNDS_BEFORE_PENDING_RECOVER) {
                            idleRounds = 0;
                            handlePendingList();
                        }
                        continue;
                    }
                    idleRounds = 0;
                    consumeAndAck(records);

                } catch (Exception e) {
                    // 读消息失败（Redis 抖动、Stream 被删等）。
                    // 原项目在这里直接调 handlePendingList()，而它内部也是 while(true) 且会撞同一个异常，
                    // 于是变成"异常 → 进 pending 循环 → 再异常"的忙循环：日志刷爆 + 吃满一个 CPU 核。
                    // 正确做法是退避后重试。
                    log.error("读取秒杀消息失败，2 秒后重试", e);
                    sleepQuietly();
                }
            }
        }
    }

    /**
     * 确保消费组存在。
     *
     * 这一步不能省：XREADGROUP 去读一个不存在的组时，Redis 直接报
     * `NOGROUP No such key 'stream.orders' or consumer group 'g1'`。而消费线程是 while(true) 的，
     * 于是启动之后就开始无限刷这条错误 —— 原项目没有这一步，实测启动即刷屏（每秒几十条）。
     *
     * 用底层连接是为了带上 MKSTREAM：Stream 还不存在时一并创建，
     * 否则 XGROUP CREATE 会因为 key 不存在而失败，组永远建不出来。
     */
    private void ensureConsumerGroup() {
        try {
            stringRedisTemplate.execute((RedisConnection connection) ->
                    connection.streamCommands().xGroupCreate(
                            SECKILL_STREAM.getBytes(StandardCharsets.UTF_8),
                            CONSUMER_GROUP,
                            ReadOffset.latest(),
                            true));
            log.info("已创建秒杀订单消费组 {}@{}", CONSUMER_GROUP, SECKILL_STREAM);
        } catch (Exception e) {
            // BUSYGROUP：组已存在，属正常情况（重启后再次执行必然走到这里）
            log.info("消费组 {}@{} 已存在，跳过创建", CONSUMER_GROUP, SECKILL_STREAM);
        }
    }

    private List<MapRecord<String, Object, Object>> readNewMessages() {
        return stringRedisTemplate.opsForStream().read(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                StreamOffset.create(SECKILL_STREAM, ReadOffset.lastConsumed()));
    }

    private List<MapRecord<String, Object, Object>> readPendingMessages() {
        return stringRedisTemplate.opsForStream().read(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamReadOptions.empty().count(10),
                StreamOffset.create(SECKILL_STREAM, ReadOffset.from("0")));
    }

    /**
     * XACK 的第一个参数是流名，不是消费组名。
     * 原项目写的是 `acknowledge("s1", "g1", ...)` —— s1 是组名，流叫 stream.orders，
     * 于是消息永远确认不掉、全堆在 pending 列表里被反复重放。
     */
    private void ack(MapRecord<String, Object, Object> record) {
        stringRedisTemplate.opsForStream().acknowledge(SECKILL_STREAM, CONSUMER_GROUP, record.getId());
    }

    /** 消费一批消息，按结果决定是否 ACK */
    private void consumeAndAck(List<MapRecord<String, Object, Object>> records) {
        for (MapRecord<String, Object, Object> record : records) {
            if (consumeRecord(record)) {
                ack(record);
            }
        }
    }

    /**
     * 处理一条消息。
     *
     * @return true = 处理完毕，可以 ACK；false = 失败但值得重试，留在 pending 里下次再来
     *
     * 为什么"没 ACK"和"ACK"要分得这么细
     * ACK 掉就再也看不到这条消息了。所以只有两类情况能 ACK：
     *   ① 成功了
     *   ② 失败了但重试也没用（库存真没了、用户确实重复下单）—— 这类留着也只会无限重放
     * 其余的（数据库连不上、超时）必须留 pending 等重试，否则这笔订单就凭空消失了。
     */
    private boolean consumeRecord(MapRecord<String, Object, Object> record) {
        VoucherOrder order = BeanUtil.fillBeanWithMap(record.getValue(), new VoucherOrder(), true);
        try {
            VoucherOrderPersister.Outcome outcome = voucherOrderPersister.persist(order);
            log.info("秒杀订单处理完成 orderId={} userId={} voucherId={} 结果={}",
                    order.getId(), order.getUserId(), order.getVoucherId(), outcome);
            return true;
        } catch (BizException e) {
            log.warn("秒杀订单业务失败，重试也不会成功，直接确认消息 orderId={} 原因={}",
                    order.getId(), e.getMessage());
            return true;
        } catch (Exception e) {
            log.error("秒杀订单处理失败（系统原因），不确认消息，留待重放 orderId={}", order.getId(), e);
            return false;
        }
    }

    /**
     * 回收 pending 列表里未被确认的消息。
     * 内部循环有上限：坏消息（每次都失败）不会被无限重放，本轮处理 PENDING_MAX_ROUNDS 轮就退出。
     */
    private void handlePendingList() {
        for (int round = 0; round < PENDING_MAX_ROUNDS; round++) {
            List<MapRecord<String, Object, Object>> records;
            try {
                records = readPendingMessages();
            } catch (Exception e) {
                log.error("读取 pending 消息失败，本轮放弃", e);
                return;
            }
            if (records == null || records.isEmpty()) {
                return;
            }
            consumeAndAck(records);
        }
        log.warn("pending 回收达到上限 {} 轮，本轮中止（可能仍有堆积）", PENDING_MAX_ROUNDS);
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(Duration.ofSeconds(2).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 取当前登录用户；拦截器保证进到业务方法时一定已登录，这里的判空是防御性的 */
    private Long currentUserId() {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user.getId();
    }
}
