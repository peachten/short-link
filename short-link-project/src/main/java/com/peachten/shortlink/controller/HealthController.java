package com.peachten.shortlink.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口，用于验证 Spring Boot + Knife4j 链路是否通
 */
@Tag(name = "健康检查")
@RestController
@RequestMapping("/api/short-link/admin/v1")
public class HealthController {

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
