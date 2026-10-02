package com.peachten.shortlink.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.common.result.ResultCode;
import com.peachten.shortlink.dao.UserMapper;
import com.peachten.shortlink.dto.UserLoginReqDTO;
import com.peachten.shortlink.dto.UserRegisterReqDTO;
import com.peachten.shortlink.entity.TUser;
import com.peachten.shortlink.service.UserService;
import com.peachten.shortlink.vo.UserRespVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public boolean hasUsername(String username) {
        return userMapper.selectCount(
                Wrappers.lambdaQuery(TUser.class).eq(TUser::getUsername, username)) > 0;
    }

    @Override
    public void register(UserRegisterReqDTO req) {
        if (hasUsername(req.getUsername())) {
            throw new BizException(ResultCode.USERNAME_EXIST);
        }
        TUser user = new TUser();
        user.setUsername(req.getUsername());
        // Hutool 的 BCrypt，避免为加密专门引入 Spring Security
        user.setPassword(BCrypt.hashpw(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setPhone(req.getPhone());
        user.setMail(req.getMail());
        userMapper.insert(user);
    }

    @Override
    public String login(UserLoginReqDTO req) {
        TUser user = userMapper.selectOne(
                Wrappers.lambdaQuery(TUser.class).eq(TUser::getUsername, req.getUsername()));
        // 用户不存在与密码错误返回同一个提示，防止用户名枚举
        if (user == null || !BCrypt.checkpw(req.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        StpUtil.login(user.getUsername());
        return StpUtil.getTokenValue();
    }

    @Override
    public UserRespVO getCurrentUser() {
        String username = StpUtil.getLoginIdAsString();
        TUser user = userMapper.selectOne(
                Wrappers.lambdaQuery(TUser.class).eq(TUser::getUsername, username));
        if (user == null) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }
        UserRespVO vo = new UserRespVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }
}
