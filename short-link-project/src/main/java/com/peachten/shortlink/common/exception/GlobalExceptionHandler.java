package com.peachten.shortlink.common.exception;

import com.peachten.shortlink.common.result.Result;
import com.peachten.shortlink.common.result.ResultCode;
import com.peachten.shortlink.common.result.Results;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：任何异常都转成统一响应体，HTTP 状态保持 200
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Results.failure(e.getCode(), e.getMessage());
    }

    /**
     * 唯一索引冲突：并发创建短链时可能出现，交由上层业务转成幂等返回
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一索引冲突", e);
        return Results.failure(ResultCode.DUPLICATE_KEY, "数据已存在，请勿重复提交");
    }

    /** @RequestBody 上的 @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        return Results.failure(ResultCode.PARAM_ERROR, collectFieldErrors(e.getBindingResult().getFieldErrors()));
    }

    /** 表单/参数绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        return Results.failure(ResultCode.PARAM_ERROR, collectFieldErrors(e.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleAll(Exception e) {
        log.error("系统异常", e);
        return Results.failure(ResultCode.SYSTEM_ERROR, "系统繁忙，请稍后重试");
    }

    private String collectFieldErrors(java.util.List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
    }
}
