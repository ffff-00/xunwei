-- 一次性数据清洗：把 rules 里的转义残留换成真正的换行。
-- 原始数据是 MySQL 5.6 导出的，分隔符写成了转义序列（"\\n" 与单独的反斜杠），
-- 前端按 pre-wrap 渲染时会原样显示成 \n，看着像功能坏了。
--
-- 用显式 CONCAT + CHAR(10) 重写，而不是继续用 REPLACE 去跟多层转义较劲：
-- 目标是"让这行数据变成正确的值"，不是"构造一个巧妙的表达式"。
-- 这个文件跑过一次就行；新库由 db/init.sql 直接写 CHAR(10)，不会再产生脏值。
UPDATE tb_voucher
SET rules = CONCAT('全场通用', CHAR(10), '无需预约', CHAR(10),
                   '可无限叠加', CHAR(10), '不兑现、不找零', CHAR(10), '仅限堂食')
WHERE id = 1;

SELECT id,
       (LENGTH(rules) - LENGTH(REPLACE(rules, CHAR(10), ''))) AS newlines,
       (LENGTH(rules) - LENGTH(REPLACE(rules, CHAR(92), ''))) AS leftover_backslash
FROM tb_voucher
WHERE id = 1;
