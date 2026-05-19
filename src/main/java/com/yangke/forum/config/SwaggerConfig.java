package com.yangke.forum.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI forumOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("羊羊网论坛 API")
                        .description("基于 Spring Boot 的社区论坛系统 — 内容创作 / 社交互动 / 全文检索 / 后台治理")
                        .version("1.0.0")
                        .contact(new Contact().name("YangKe")));
    }
}
