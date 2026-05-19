package com.yangke.forum.module.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_comment")
public class Comment {

    @TableId
    private Long id;

    private Long postId;
    private Long userId;
    private Long parentId;         // 父评论ID，一级评论为0
    private Long replyToUserId;    // 被回复的用户ID

    private String content;

    private Integer likeCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
