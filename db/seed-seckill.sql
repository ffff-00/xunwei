-- =============================================================================
-- 寻味 —— 秒杀活动种子数据（可重复执行）
--
-- 与 db/init.sql 的分工：
--   init.sql         表结构 + 基础业务数据（商铺/探店/用户），是一次性导入的原始数据
--   本文件           秒杀活动数据，用相对时间而不是写死的日期 ——
--                    否则过几天再跑，所有秒杀都变成"已结束"，演示直接废掉
--
-- 执行：mysql -uroot -p xunwei < db/seed-seckill.sql
-- =============================================================================

-- 故意造三种时间窗，用来验证"秒杀时间校验"这条规则（原项目的最终版本把这段校验弄丢了）：
--   id=2  进行中     → 能下单
--   id=3  尚未开始   → 应被拒
--   id=4  已结束     → 应被拒
DELETE FROM tb_seckill_voucher WHERE voucher_id IN (2, 3, 4);
DELETE FROM tb_voucher WHERE id IN (2, 3, 4);

-- 金额单位：pay_value / actual_value 存的是分（5000 = 50 元），
--   与 tb_shop.avg_price（存元）不是同一套单位 —— 这是原始数据模型的历史遗留，
--   写种子数据时特别容易搞错，前端也因此要分开格式化。
INSERT INTO tb_voucher (id, shop_id, title, sub_title, rules, pay_value, actual_value, type, status, create_time, update_time)
VALUES (2, 1, '50元代金券（限时秒杀）', '每人限购 1 张，先到先得', '全场通用、周末可用、不兑现不找零、仅限堂食', 4500, 5000, 1, 1, NOW(), NOW()),
       (3, 2, '双人套餐券（即将开始）', '明天 10 点开抢', '需提前一天预约、节假日不可用', 8800, 15600, 1, 1, NOW(), NOW()),
       (4, 3, '8折代金券（已结束）', '往期活动，仅用于演示时间校验', '全场通用', 8000, 10000, 1, 1, NOW(), NOW());

INSERT INTO tb_seckill_voucher (voucher_id, stock, create_time, begin_time, end_time, update_time)
VALUES (2, 100, NOW(), DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 7 DAY), NOW()),
       (3, 50, NOW(), DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), NOW()),
       (4, 20, NOW(), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW());

-- 普通券（type=0）：给商铺详情页兜底，保证"没有秒杀活动的店"也有券可展示
INSERT INTO tb_voucher (id, shop_id, title, sub_title, rules, pay_value, actual_value, type, status, create_time, update_time)
VALUES (11, 1, '95元代金券', '随时可买', '全场通用', 9500, 10000, 0, 1, NOW(), NOW()),
       (12, 2, '满 200 减 30', '随时可买', '每桌限用一张', 0, 3000, 0, 1, NOW(), NOW()),
       (13, 4, '下午茶套餐', '14:00-17:00 可用', '不与其他优惠同享', 3800, 5800, 0, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE update_time = NOW();
