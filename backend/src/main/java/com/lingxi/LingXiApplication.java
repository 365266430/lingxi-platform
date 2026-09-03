package com.lingxi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 灵犀智能体平台（LingXi AI Agent Platform）启动入口。
 * Mapper 接口通过 @Mapper 注解被自动发现。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class LingXiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LingXiApplication.class, args);
    }
}
