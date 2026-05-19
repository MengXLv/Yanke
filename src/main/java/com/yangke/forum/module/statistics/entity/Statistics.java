package com.yangke.forum.module.statistics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_statistics")
public class Statistics {
    @TableId
    private Long id;
    private LocalDate statDate;
    private Long uv;
    private Long dau;
    private Integer newUsers;
    private Integer newPosts;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
