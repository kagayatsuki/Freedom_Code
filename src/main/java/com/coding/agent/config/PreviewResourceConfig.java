package com.coding.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Slf4j
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)   // 提高优先级
public class PreviewResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("🚀 PreviewResourceConfig 已加载 - 映射 /preview/**");

        registry.addResourceHandler("/preview/**")
                .addResourceLocations("file:D:/code/")   // 必须正斜杠
                .setCachePeriod(0)                       // 关闭缓存！！！
                .setCacheControl(null)                   // 禁用缓存控制
                .resourceChain(false);

        log.info("✅ /preview/** 映射配置完成");
    }
}
