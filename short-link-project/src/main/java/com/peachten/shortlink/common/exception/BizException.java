package com.peachten.shortlink.common.exception;

import com.peachten.shortlink.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：由业务代码主动抛出，GlobalExceptionHandler 会转成统一响应体
 */
@Getter
public class BizException extends RuntimeException {

    private final String code;

    /** 默认归为客户端错误（A0001） */
    public BizException(String message) {
        super(message);
        this.code = ResultCode.CLIENT_ERROR.getCode();
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }
}
