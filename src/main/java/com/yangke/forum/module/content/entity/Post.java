package com.yangke.forum.module.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_post")
public class Post {

    @TableId
    private Long id;

    private Long userId;
    private Long categoryId;

    private String title;
    private String content;

    @TableField(exist = false)
    private String summary;        // 非持久化，列表展示用摘要

    private Integer status;        // 0-草稿 1-已发布 2-审核中 3-屏蔽
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer isHot;         // 0-否 1-是
    private Integer isTop;         // 0-否 1-置顶

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
