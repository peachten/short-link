package com.peachten.shortlink.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链表
 */
@Data
@TableName("t_link")
public class TLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 域名，如 http://localhost:8000 */
    private String domain;

    /** 短链后缀（Base62，6 位） */
    private String shortUri;

    /** 完整短链 = domain + "/" + gid + "/" + shortUri */
    private String fullShortUrl;

    private String originUrl;

    /** MD5(origin_url)，与 gid 组成唯一索引 uk_gid_origin，是创建幂等的依据 */
    private String originUrlHash;

    private Integer clickNum;

    private String gid;

    /** 0 启用 1 禁用 */
    private Integer enableStatus;

    /** 0 接口 1 控制台 */
    private Integer createdType;

    /** 有效期类型 0 永久 1 自定义 */
    private Integer validDateType;

    /** 失效时间，永久时为 null */
    private LocalDateTime validDate;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标识 */
    @TableLogic
    private Integer delFlag;
}
