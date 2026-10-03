package com.peachten.shortlink.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.common.result.ResultCode;
import com.peachten.shortlink.dao.UserMapper;
import com.peachten.shortlink.dto.UserLoginReqDTO;
import com.peachten.shortlink.dto.UserRegisterReqDTO;
import com.peachten.shortlink.entity.TUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户核心逻辑单测：纯 Mockito，不起 Spring 容器、不依赖 MySQL / Redis
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void register_用户名已存在_抛业务异常() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        UserRegisterReqDTO req = new UserRegisterReqDTO();
        req.setUsername("taken");
        req.setPassword("123456");

        BizException ex = assertThrows(BizException.class, () -> userService.register(req));
        assertEquals(ResultCode.USERNAME_EXIST.getCode(), ex.getCode());
        verify(userMapper, never()).insert(any(TUser.class));
    }

    @Test
    void register_密码以BCrypt密文落库() {
        when(userMapper.selectCount(any())).thenReturn(0L);

        UserRegisterReqDTO req = new UserRegisterReqDTO();
        req.setUsername("newbie");
        req.setPassword("secret123");
        userService.register(req);

        ArgumentCaptor<TUser> captor = ArgumentCaptor.forClass(TUser.class);
        verify(userMapper).insert(captor.capture());
        TUser saved = captor.getValue();
        // 明文绝不入库，且密文可用 BCrypt 校验通过
        assertNotEquals("secret123", saved.getPassword());
        assertTrue(saved.getPassword().startsWith("$2a$"));
        assertTrue(BCrypt.checkpw("secret123", saved.getPassword()));
    }

    @Test
    void login_密码错误_抛登录失败() {
        TUser user = new TUser();
        user.setUsername("bob");
        user.setPassword(BCrypt.hashpw("correct"));
        when(userMapper.selectOne(any())).thenReturn(user);

        UserLoginReqDTO req = new UserLoginReqDTO();
        req.setUsername("bob");
        req.setPassword("wrong");

        BizException ex = assertThrows(BizException.class, () -> userService.login(req));
        assertEquals(ResultCode.LOGIN_FAILED.getCode(), ex.getCode());
    }

    @Test
    void login_用户不存在_抛同一个登录失败提示() {
        when(userMapper.selectOne(any())).thenReturn(null);

        UserLoginReqDTO req = new UserLoginReqDTO();
        req.setUsername("ghost");
        req.setPassword("whatever");

        // 用户不存在与密码错误返回同一返回码，防止用户名枚举
        BizException ex = assertThrows(BizException.class, () -> userService.login(req));
        assertEquals(ResultCode.LOGIN_FAILED.getCode(), ex.getCode());
    }
}
