package com.peachten.shortlink.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("短链接系统 API")
                        .description("分布式短链接生成与访问统计系统")
                        .version("v1.0"))
                .components(new Components().addSecuritySchemes("Authorization",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")));
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("01-管理端接口")
                .pathsToMatch("/api/short-link/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi projectApi() {
        return GroupedOpenApi.builder()
                .group("02-短链跳转接口")
                .pathsToMatch("/**")
                .pathsToExclude("/api/**", "/doc.html", "/webjars/**", "/v3/**", "/swagger-ui/**", "/error")
                .build();
    }
}
