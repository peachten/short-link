package com.peachten.shortlink.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.common.result.ResultCode;
import com.peachten.shortlink.dao.GroupMapper;
import com.peachten.shortlink.dto.GroupSaveReqDTO;
import com.peachten.shortlink.dto.GroupUpdateReqDTO;
import com.peachten.shortlink.entity.TGroup;
import com.peachten.shortlink.service.GroupService;
import com.peachten.shortlink.util.Base62Util;
import com.peachten.shortlink.vo.GroupRespVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    /** gid 生成重试次数，防止随机撞上已存在的 gid */
    private static final int GID_RETRY = 3;

    private final GroupMapper groupMapper;

    @Override
    public String saveGroup(GroupSaveReqDTO req) {
        TGroup group = new TGroup();
        group.setGid(generateGid());
        group.setName(req.getName());
        // 归属当前登录用户，后续所有操作都以此做数据隔离
        group.setUsername(StpUtil.getLoginIdAsString());
        group.setSortOrder(0);
        groupMapper.insert(group);
        return group.getGid();
    }

    @Override
    public List<GroupRespVO> listGroup() {
        return groupMapper.selectGroupWithLinkCount(StpUtil.getLoginIdAsString());
    }

    @Override
    public void updateGroup(GroupUpdateReqDTO req) {
        TGroup group = checkOwnership(req.getGid());
        group.setName(req.getName());
        groupMapper.updateById(group);
    }

    @Override
    public void deleteGroup(String gid) {
        TGroup group = checkOwnership(gid);
        // 分组下还有短链时拒绝删除，避免短链变成"无主"
        if (groupMapper.countLinkByGid(gid) > 0) {
            throw new BizException(ResultCode.GROUP_HAS_LINK);
        }
        groupMapper.deleteById(group.getId());
    }

    @Override
    public long countGroup() {
        return groupMapper.selectCount(
                Wrappers.lambdaQuery(TGroup.class).eq(TGroup::getUsername, StpUtil.getLoginIdAsString()));
    }

    /**
     * 归属校验：分组必须存在且属于当前登录用户。
     * 两种情况返回同一个提示，避免暴露"该分组属于别人"这一信息。
     */
    private TGroup checkOwnership(String gid) {
        TGroup group = groupMapper.selectOne(
                Wrappers.lambdaQuery(TGroup.class).eq(TGroup::getGid, gid));
        if (group == null || !group.getUsername().equals(StpUtil.getLoginIdAsString())) {
            throw new BizException(ResultCode.GROUP_NOT_FOUND);
        }
        return group;
    }

    /**
     * 生成 6 位 gid。
     * 用 Base62 随机后缀（不可枚举），撞上已存在的 gid 时重试，唯一索引兜底。
     */
    private String generateGid() {
        for (int i = 0; i < GID_RETRY; i++) {
            String gid = Base62Util.random(6);
            boolean exists = groupMapper.selectCount(
                    Wrappers.lambdaQuery(TGroup.class).eq(TGroup::getGid, gid)) > 0;
            if (!exists) {
                return gid;
            }
        }
        throw new BizException("分组标识生成失败，请重试");
    }
}
