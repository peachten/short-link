package com.peachten.shortlink.service;

import com.peachten.shortlink.dto.GroupSaveReqDTO;
import com.peachten.shortlink.dto.GroupUpdateReqDTO;
import com.peachten.shortlink.entity.TGroup;
import com.peachten.shortlink.vo.GroupRespVO;

import java.util.List;

public interface GroupService {

    /** 新建分组，返回生成的 gid */
    String saveGroup(GroupSaveReqDTO req);

    /** 当前用户的分组列表（含分组内短链数量） */
    List<GroupRespVO> listGroup();

    /** 重命名分组（校验归属） */
    void updateGroup(GroupUpdateReqDTO req);

    /** 删除分组（校验归属 + 分组下不能有短链） */
    void deleteGroup(String gid);

    /** 当前用户的分组数量 */
    long countGroup();

    /** 校验分组存在且属于当前登录用户，返回分组；供创建短链等其他模块复用 */
    TGroup checkOwnership(String gid);
}
