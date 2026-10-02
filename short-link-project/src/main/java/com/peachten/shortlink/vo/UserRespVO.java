package com.peachten.shortlink.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息出参
 * <p>
 * 刻意不含 password 字段，避免靠人肉记忆"别把实体直接返回"
 */
@Data
public class UserRespVO {

    private Long id;

    private String username;

    private String realName;

    private String phone;

    private String mail;

    private LocalDateTime createTime;
}
