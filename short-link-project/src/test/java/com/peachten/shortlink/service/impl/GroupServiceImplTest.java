package com.peachten.shortlink.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.common.result.ResultCode;
import com.peachten.shortlink.dao.GroupMapper;
import com.peachten.shortlink.dto.GroupSaveReqDTO;
import com.peachten.shortlink.dto.GroupUpdateReqDTO;
import com.peachten.shortlink.entity.TGroup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分组核心逻辑单测，重点覆盖数据隔离（归属校验）与删除保护。
 * 纯 Mockito，StpUtil 的静态方法用 mockStatic 拦截，不依赖 Redis。
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private GroupMapper groupMapper;

    @InjectMocks
    private GroupServiceImpl groupService;

    private TGroup group(long id, String gid, String username) {
        TGroup g = new TGroup();
        g.setId(id);
        g.setGid(gid);
        g.setUsername(username);
        return g;
    }

    @Test
    void updateGroup_分组属于他人_抛分组不存在() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("me");
            when(groupMapper.selectOne(any())).thenReturn(group(1L, "abc123", "other"));

            GroupUpdateReqDTO req = new GroupUpdateReqDTO();
            req.setGid("abc123");
            req.setName("renamed");

            BizException ex = assertThrows(BizException.class, () -> groupService.updateGroup(req));
            assertEquals(ResultCode.GROUP_NOT_FOUND.getCode(), ex.getCode());
            verify(groupMapper, never()).updateById(any(TGroup.class));
        }
    }

    @Test
    void updateGroup_分组不存在_抛分组不存在() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("me");
            when(groupMapper.selectOne(any())).thenReturn(null);

            GroupUpdateReqDTO req = new GroupUpdateReqDTO();
            req.setGid("missing");
            req.setName("renamed");

            BizException ex = assertThrows(BizException.class, () -> groupService.updateGroup(req));
            assertEquals(ResultCode.GROUP_NOT_FOUND.getCode(), ex.getCode());
        }
    }

    @Test
    void deleteGroup_分组下有短链_拒绝删除() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("me");
            when(groupMapper.selectOne(any())).thenReturn(group(1L, "abc123", "me"));
            when(groupMapper.countLinkByGid("abc123")).thenReturn(3);

            BizException ex = assertThrows(BizException.class, () -> groupService.deleteGroup("abc123"));
            assertEquals(ResultCode.GROUP_HAS_LINK.getCode(), ex.getCode());
            verify(groupMapper, never()).deleteById(any(Long.class));
        }
    }

    @Test
    void deleteGroup_空分组_删除成功() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("me");
            when(groupMapper.selectOne(any())).thenReturn(group(7L, "abc123", "me"));
            when(groupMapper.countLinkByGid("abc123")).thenReturn(0);

            groupService.deleteGroup("abc123");

            verify(groupMapper).deleteById(7L);
        }
    }

    @Test
    void saveGroup_生成6位gid并绑定当前用户() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("me");
            // gid 不重复
            when(groupMapper.selectCount(any())).thenReturn(0L);

            GroupSaveReqDTO req = new GroupSaveReqDTO();
            req.setName("工作");

            String gid = groupService.saveGroup(req);

            ArgumentCaptor<TGroup> captor = ArgumentCaptor.forClass(TGroup.class);
            verify(groupMapper).insert(captor.capture());
            TGroup saved = captor.getValue();
            assertEquals(6, gid.length());
            assertEquals(gid, saved.getGid());
            assertEquals("me", saved.getUsername());
            assertEquals("工作", saved.getName());
        }
    }

    @Test
    void listGroup_查询当前用户的分组() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("me");
            when(groupMapper.selectGroupWithLinkCount("me")).thenReturn(List.of());

            assertTrue(groupService.listGroup().isEmpty());
        }
    }
}
