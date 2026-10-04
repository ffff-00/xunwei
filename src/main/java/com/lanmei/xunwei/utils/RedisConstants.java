package com.lanmei.xunwei.utils;

/**
 * Redis key 常量。
 *
 * 有几处 key 在 Java 和 Lua 脚本里各写了一份（seckill.lua 用不到 Java 常量）。
 * 这类"跨语言共享的字面量"改一处必须改另一处，所以在这里显式声明并标注，
 * 让它至少成为一个有名字的约定，而不是散落在各处的裸字符串。
 */
public class RedisConstants {

    // 登录
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    // 商铺缓存
    public static final Long CACHE_NULL_TTL = 2L;
    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    // 秒杀（与 seckill.lua 共享，改动需同步）
    /** 秒杀库存，String 类型。Lua 里：`'seckill:stock:' .. voucherId` */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    /** 已下单用户集合，Set 类型（SISMEMBER 判一人一单）。Lua 里：`'seckill:order:' .. voucherId` */
    public static final String SECKILL_ORDER_KEY = "seckill:order:";

    // 内容与社交
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String USER_SIGN_KEY = "sign:";

    // 地理位置
    public static final String SHOP_GEO_KEY = "shop:geo:";
}
