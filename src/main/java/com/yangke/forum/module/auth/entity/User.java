package com.yangke.forum.module.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {

    @TableId
    private Long id;

    private String username;
    private String email;
    private String password;       // MD5+盐值 加密后
    private String salt;           // 随机盐值
    private String avatar;
    private String bio;

    private Integer status;        // 0-未激活 1-已激活 2-封禁
    private String role;           // user / moderator / admin

    private String activationCode; // 邮件激活码

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
