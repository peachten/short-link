package com.peachten.shortlink.common.result;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 统一返回码
 * <p>
 * 约定：0 成功；A 开头为客户端问题（参数、重复提交等）；B 开头为服务端问题。
 */
@Getter
@RequiredArgsConstructor
public enum ResultCode {

    SUCCESS("0", "ok"),

    /* ---------- A：客户端 ---------- */
    CLIENT_ERROR("A0001", "客户端错误"),
    PARAM_ERROR("A0002", "参数校验失败"),
    DUPLICATE_KEY("A0003", "数据已存在"),
    USERNAME_EXIST("A0004", "用户名已存在"),
    LOGIN_FAILED("A0005", "用户名或密码错误"),
    NOT_LOGIN("A0006", "未登录"),
    NO_PERMISSION("A0007", "无权限访问"),

    /* ---------- B：服务端 ---------- */
    SYSTEM_ERROR("B0001", "系统异常");

    private final String code;
    private final String message;
}
