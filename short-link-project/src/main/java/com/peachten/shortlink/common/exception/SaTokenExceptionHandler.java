package com.peachten.shortlink.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.peachten.shortlink.common.result.Result;
import com.peachten.shortlink.common.result.ResultCode;
import com.peachten.shortlink.common.result.Results;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sa-Token 相关异常
 * <p>
 * 必须加 @Order 提到最高优先级：否则 GlobalExceptionHandler 里 @ExceptionHandler(Exception.class)
 * 的兜底方法会先被匹配到，NotLoginException 就被吞成"系统异常"了。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SaTokenExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLogin(NotLoginException e) {
        log.warn("未登录访问: {}", e.getMessage());
        return Results.failure(ResultCode.NOT_LOGIN);
    }

    @ExceptionHandler(NotPermissionException.class)
    public Result<Void> handleNotPermission(NotPermissionException e) {
        log.warn("无权限访问: {}", e.getMessage());
        return Results.failure(ResultCode.NO_PERMISSION);
    }
}
