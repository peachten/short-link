package com.peachten.shortlink.common.result;

import lombok.Data;

/**
 * 统一响应体
 * <p>
 * 格式：{ "code": "0", "message": "ok", "data": {}, "requestId": "traceId" }
 */
@Data
public class Result<T> {

    private String code;

    private String message;

    private T data;

    /** 链路追踪 ID，由 TraceIdFilter 生成 */
    private String requestId;
}
