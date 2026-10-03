package com.peachten.shortlink.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.peachten.shortlink.entity.TGroup;
import com.peachten.shortlink.vo.GroupRespVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分组表 Mapper
 */
public interface GroupMapper extends BaseMapper<TGroup> {

    /** 当前用户的分组列表，并统计每个分组下的短链数量 */
    List<GroupRespVO> selectGroupWithLinkCount(@Param("username") String username);

    /** 统计分组下未删除的短链数量，用于删除前校验 */
    int countLinkByGid(@Param("gid") String gid);
}
