package com.peachten.shortlink.controller;

import com.peachten.shortlink.common.result.Result;
import com.peachten.shortlink.common.result.Results;
import com.peachten.shortlink.dto.UserLoginReqDTO;
import com.peachten.shortlink.dto.UserRegisterReqDTO;
import com.peachten.shortlink.service.UserService;
import com.peachten.shortlink.vo.UserRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户模块")
@RestController
@RequestMapping("/api/short-link/admin/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterReqDTO req) {
        userService.register(req);
        return Results.success();
    }

    @Operation(summary = "用户登录，返回 token")
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody UserLoginReqDTO req) {
        return Results.success(userService.login(req));
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Results.success();
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/current")
    public Result<UserRespVO> current() {
        return Results.success(userService.getCurrentUser());
    }

    @Operation(summary = "用户名是否可用，true 表示可用")
    @GetMapping("/has-username")
    public Result<Boolean> hasUsername(@RequestParam String username) {
        return Results.success(!userService.hasUsername(username));
    }
}
