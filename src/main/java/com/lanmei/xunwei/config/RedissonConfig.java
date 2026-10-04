package com.lanmei.xunwei.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 客户端。
 *
 * 地址与密码从配置读，不再硬编码在 Java 里——原来写死了"192.168.150.101"（原项目的虚拟机 IP），
 * 换台机器跑就得改代码，这种"代码里藏环境信息"的写法会在 review 和部署时反复咬人。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port);
        if (StringUtils.hasText(password)) {
            config.useSingleServer().setPassword(password);
        }
        return Redisson.create(config);
    }
}
