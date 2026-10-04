package com.lanmei.xunwei.config;

import com.lanmei.xunwei.entity.Shop;
import com.lanmei.xunwei.service.IShopService;
import com.lanmei.xunwei.utils.RedisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商铺地理位置预热。
 *
 * 为什么需要
 * "附近商铺"用的是 Redis 的 GEO 结构：每个分类一个 key（`shop:geo:{typeId}`），
 * 成员是商铺 id、坐标是经纬度。这份数据只在 Redis 里、数据库里没有镜像，
 * 所以 Redis 一清空（换机器、调试验证、容器重建），附近功能立刻返回空列表，
 * 而表现只是"没有结果"，不像报错那样好排查 —— 这是最费时间的故障类型。
 *
 * 原项目把这段逻辑写在测试类里（`XunweiApplicationTests.loadShopData`），
 * 要靠人记得手工跑一次；漏跑就"功能不见了"。
 * 挪成启动预热之后，这条依赖变成自愈的。
 *
 * 为什么每次启动都跑也不算浪费
 * GEOADD 对同一个成员是覆盖写，天然幂等：坐标没变就是原地更新。
 * 数据量小（本项目十几家店）时这点开销可以忽略；
 * 真到几十万商铺的规模，应该改成"只在 key 不存在时预热"或离线任务，
 * 判断口径是"Redis 里有没有这个 key"，而不是"库里有没有数据"。
 */
@Component
public class ShopGeoWarmer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ShopGeoWarmer.class);

    private final IShopService shopService;
    private final StringRedisTemplate stringRedisTemplate;

    public ShopGeoWarmer(IShopService shopService, StringRedisTemplate stringRedisTemplate) {
        this.shopService = shopService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String sampleKey = RedisConstants.SHOP_GEO_KEY + 1;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(sampleKey))) {
            log.info("商铺 GEO 数据已存在，跳过预热");
            return;
        }

        List<Shop> shops = shopService.list();
        if (shops.isEmpty()) {
            log.info("库里没有商铺数据，跳过 GEO 预热");
            return;
        }

        // 按分类分组：一类一个 key，这样"查美食类附近 5 公里"只扫一个 key，不用全表扫
        Map<Long, List<Shop>> byType = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        int loaded = 0;
        for (Map.Entry<Long, List<Shop>> entry : byType.entrySet()) {
            String key = RedisConstants.SHOP_GEO_KEY + entry.getKey();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(entry.getValue().size());
            for (Shop shop : entry.getValue()) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
            loaded += locations.size();
        }
        log.info("商铺 GEO 预热完成：{} 个分类 / {} 家商铺", byType.size(), loaded);
    }
}
