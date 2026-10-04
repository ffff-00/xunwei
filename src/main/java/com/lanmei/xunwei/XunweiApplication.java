package com.lanmei.xunwei;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.lanmei.xunwei.mapper")
@SpringBootApplication
public class XunweiApplication {

    public static void main(String[] args) {
        SpringApplication.run(XunweiApplication.class, args);
    }

}
