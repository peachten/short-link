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
 * 短链分组表
 */
@Data
@TableName("t_group")
public class TGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分组标识，对外暴露，全局唯一 */
    private String gid;

    private String name;

    /** 所属用户，数据隔离的依据 */
    private String username;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标识 */
    @TableLogic
    private Integer delFlag;
}
