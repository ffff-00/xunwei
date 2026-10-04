package com.lanmei.xunwei.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanmei.xunwei.dto.VoucherOrderVO;
import com.lanmei.xunwei.entity.VoucherOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单号的序列化契约。
 *
 * 这个 bug 是怎么来的（真实事故，值得记住）
 * 订单号是雪花 ID，形如 643388832540000561 —— 60 位整数。
 * JavaScript 的 Number 只能精确表示到 2^53 ≈ 9.0e15，比这个数小两个数量级。
 * 于是 JSON 里的数字到了前端会被静默改写：
 *
 *     后端真实值                 643388832540000561
 *     前端 JSON.parse 之后        643388832540000500   ← 末两位被四舍五入
 *
 * 后果：前端拿着这个"近似 ID"去查订单结果，永远查不到，界面卡在"订单处理中"。
 * 而且不报任何错 —— 数字看起来还挺正常，只是末尾不一样。
 *
 * 原项目没暴露这个 bug，是因为它没有查询订单结果的接口，那个 id 从来没被回传过；
 * 前端只显示一句"下单成功"就结束了。补上轮询接口之后它立刻现形。
 * 这本身也是个教训：接口设计不完整会把 bug 藏起来。
 *
 * 为什么要有这个测试
 * "凡是可能超过 2^53 的 Long 都按字符串序列化" 这条规则，靠注释和记忆是守不住的
 * （第一版就漏了秒杀接口那个"裸 Long"出口 —— 实体字段上的注解保护不到它）。
 * 所以用测试把它变成会失败的红灯：以后谁加了一个新的 Long 出口忘了处理，这里会先红。
 */
@SpringBootTest
class OrderIdSerializationTest {

    /** 一个真实的雪花 ID 量级的值（超过 2^53） */
    private static final long SNOWFLAKE_ID = 643388832540000561L;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("超过 JS 安全整数范围的订单号，序列化后必须是带引号的字符串")
    void snowflakeIdMustBeSerializedAsString() throws Exception {
        // 前置确认：这个值确实超出了 JS 的精确表示范围，否则本测试就没有意义了
        assertThat(SNOWFLAKE_ID).isGreaterThan(9007199254740992L);

        VoucherOrder order = new VoucherOrder();
        order.setId(SNOWFLAKE_ID);
        order.setUserId(1L);
        order.setVoucherId(2L);
        order.setStatus(1);

        String json = objectMapper.writeValueAsString(order);
        assertThat(json)
                .as("实体里的雪花 ID 必须是字符串；如果这里失败，前端会拿到被四舍五入的值")
                .contains("\"id\":\"" + SNOWFLAKE_ID + "\"");

        VoucherOrderVO vo = new VoucherOrderVO();
        vo.setOrderId(SNOWFLAKE_ID);
        String voJson = objectMapper.writeValueAsString(vo);
        assertThat(voJson)
                .as("订单列表 VO 里的订单号同样必须是字符串")
                .contains("\"orderId\":\"" + SNOWFLAKE_ID + "\"");
    }

    @Test
    @DisplayName("反面对照：不带注解的裸 Long 会被序列化成数字（这就是原来踩的坑）")
    void bareLongLosesPrecision() throws Exception {
        // 用一个临时的 DTO 模拟"裸 Long 出口"，证明不加处理真的会出问题。
        // 秒杀下单接口原来是 Result<Long>，就是这种情况，所以它必须返回 String。
        String json = objectMapper.writeValueAsString(new BareLongHolder(SNOWFLAKE_ID));
        assertThat(json)
                .as("刻意断言序列化结果是【数字】—— 用来固定『不加处理就会出事』这个事实")
                .isEqualTo("{\"orderId\":" + SNOWFLAKE_ID + "}");

        // 前端实际会看到什么：模拟 JS 的 Number 解析（双精度浮点）
        double asJsNumber = (double) SNOWFLAKE_ID;
        assertThat((long) asJsNumber)
                .as("这条断言证明数字路径确实会丢精度：转成 double 再转回来已经不是原值")
                .isNotEqualTo(SNOWFLAKE_ID);
    }

    /** 仅用于本测试：一个把 Long 直接当数字暴露出去的持有者 */
    private record BareLongHolder(Long orderId) {
    }
}
