package com.yangke.forum.module.message.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_message")
public class Message {

    @TableId
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String content;
    private Integer isRead;    // 0-未读 1-已读

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
