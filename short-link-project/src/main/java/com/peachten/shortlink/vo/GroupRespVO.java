package com.peachten.shortlink.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分组出参
 * <p>
 * linkCount 由列表 SQL 联表统计得到，不落库
 */
@Data
public class GroupRespVO {

    private Long id;

    private String gid;

    private String name;

    private Integer sortOrder;

    /** 分组内短链数量 */
    private Integer linkCount;

    private LocalDateTime createTime;
}
