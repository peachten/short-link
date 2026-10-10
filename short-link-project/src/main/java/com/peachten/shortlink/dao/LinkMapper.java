package com.peachten.shortlink.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.peachten.shortlink.entity.TLink;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 短链表 Mapper
 */
public interface LinkMapper extends BaseMapper<TLink> {

    /** 所有存在有效短链的分组标识，用于布隆过滤器预热 */
    List<String> selectAllValidGids();

    /** 按 gid 分批扫描有效短链（未删除且未禁用），用于布隆过滤器重建 */
    List<TLink> selectValidByGid(@Param("gid") String gid,
                                 @Param("offset") int offset,
                                 @Param("size") int size);
}
