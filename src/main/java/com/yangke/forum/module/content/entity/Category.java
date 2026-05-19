package com.yangke.forum.module.content.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_category")
public class Category {
    @TableId
    private Long id;
    private String name;
    private String description;
    private Integer sortOrder;
    private Integer status;     // 1-启用 0-禁用
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
