package com.peachten.shortlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册入参
 */
@Data
public class UserRegisterReqDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 之间")
    private String password;

    @Size(max = 64, message = "真实姓名长度不能超过 64")
    private String realName;

    @Size(max = 20, message = "手机号长度不能超过 20")
    private String phone;

    @Size(max = 64, message = "邮箱长度不能超过 64")
    private String mail;
}
