package com.peachten.shortlink.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.peachten.shortlink.entity.TUser;

/**
 * 用户表 Mapper
 * <p>
 * 包路径必须和 ShortLinkApplication 上 @MapperScan("com.peachten.shortlink.dao") 一致
 */
public interface UserMapper extends BaseMapper<TUser> {
}
