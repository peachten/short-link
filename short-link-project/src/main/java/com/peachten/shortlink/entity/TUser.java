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
 * 用户表
 */
@Data
@TableName("t_user")
public class TUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文，绝不能返回给前端 */
    private String password;

    private String realName;

    private String phone;

    private String mail;

    /** 注销时间戳 */
    private Long deletionTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标识，MyBatis-Plus 查询会自动追加 del_flag = 0 */
    @TableLogic
    private Integer delFlag;
}
