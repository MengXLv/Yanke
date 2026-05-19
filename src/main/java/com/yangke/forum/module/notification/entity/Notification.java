package com.yangke.forum.module.notification.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_notification")
public class Notification {

    @TableId
    private Long id;

    private Long senderId;         // 触发通知的用户ID，0表示系统
    private Long receiverId;       // 接收通知的用户ID

    private Integer type;          // 1-点赞 2-评论 3-关注 4-系统通知
    private String content;
    private Long targetId;         // 关联的帖子/评论ID
    private Integer isRead;        // 0-未读 1-已读

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
