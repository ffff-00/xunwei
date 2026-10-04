# 寻味 Xunwei

城市探店点评 + 优惠券秒杀平台。

## 它解决什么问题

秒杀场景的难点从来不是"能不能写入数据库"，而是三件事：

1. **不超卖** —— 100 个人抢 10 份库存，必须恰好 10 个人成功
2. **不重复下单** —— 同一个人点 10 次、同一个消息被重放 10 次，都只能产生 1 张订单
3. **扛得住瞬时流量** —— 不能让并发请求把数据库打垮

请求 ──▶ ① Redis Lua 原子预检 ──▶ ② Redis Stream 异步削峰 ──▶ ③ 数据库事务 + 唯一索引
            判库存 / 判一人一单         单线程消费落库             建单 + 扣库存 + 兜底
                 （性能）                 （削峰）                  （正确性）

## 实测数字

| 场景 | 结果 |
| --- | --- |
| 100 个用户并发抢 10 份库存 | Redis 预检恰好放行 10 个；**落库恰好 10 张订单**；库存归零且不为负 |
| 同一用户并发提交 50 次 | 只拿到 1 次资格、只产生 1 张订单、库存只扣 1 |
| 抢购接口耗时 | 100 个请求 **206 ms**（单机本地 Redis + MySQL） |

> 上述性能数字的测试条件是**单机本地环境**。

## 技术栈

**后端**：Java 17 · Spring Boot 3.5.16 · MyBatis-Plus 3.5.17 · MySQL 8 · Redis 8 · Redisson 3.52 · Maven

**前端**：Vue 3 · Vite 6 · TypeScript · Element Plus · Pinia · Vue Router

## 目录

```
db/                    建库与演示数据 SQL
docs/                  改造记录
frontend/              Vue3 前端（12 个路由页面）
src/main/java/com/lanmei/xunwei/
  common/              Result / ErrorCode / BizException / 全局异常处理
  config/              MVC、MyBatis-Plus、Redisson、两个启动预热器
  controller/          接口层
  service/             业务层（秒杀链路在 VoucherOrderService* 与 VoucherOrderPersister）
  entity/ mapper/ dto/
src/main/resources/
  seckill.lua          秒杀脚本：判库存 + 判一人一单 + 发消息，一次原子完成
  mapper/              JOIN 类查询的 XML
```



