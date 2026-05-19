package com.yangke.forum.module.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_report")
public class Report {
    @TableId
    private Long id;
    private Long reporterId;     // 举报人
    private Long targetId;       // 被举报的帖子/评论ID
    private Integer targetType;  // 1-帖子 2-评论
    private String reason;       // 举报原因
    private Integer status;      // 0-待处理 1-已处理 2-驳回
    private Long handlerId;      // 处理人
    private String handlerNote;  // 处理备注
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
