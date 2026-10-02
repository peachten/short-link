package com.peachten.shortlink.service;

import com.peachten.shortlink.dto.UserLoginReqDTO;
import com.peachten.shortlink.dto.UserRegisterReqDTO;
import com.peachten.shortlink.vo.UserRespVO;

public interface UserService {

    /** 用户名是否已被占用 */
    boolean hasUsername(String username);

    /** 注册：查重 -> BCrypt 加密 -> 落库 */
    void register(UserRegisterReqDTO req);

    /** 登录成功返回 token */
    String login(UserLoginReqDTO req);

    /** 当前登录用户信息（不含密码） */
    UserRespVO getCurrentUser();

    void logout();
}
