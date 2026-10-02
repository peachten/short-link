package com.peachten.shortlink.common.result;

import com.peachten.shortlink.filter.TraceIdFilter;
import org.slf4j.MDC;

/**
 * Result 构造工具，requestId 自动从 MDC 取，业务代码不用关心
 */
public final class Results {

    private Results() {
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
        result.setData(data);
        result.setRequestId(MDC.get(TraceIdFilter.MDC_KEY));
        return result;
    }

    public static <T> Result<T> failure(ResultCode resultCode) {
        return failure(resultCode.getCode(), resultCode.getMessage());
    }

    /** 用返回码的 code，但覆盖 message */
    public static <T> Result<T> failure(ResultCode resultCode, String message) {
        return failure(resultCode.getCode(), message);
    }

    public static <T> Result<T> failure(String code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setRequestId(MDC.get(TraceIdFilter.MDC_KEY));
        return result;
    }
}
