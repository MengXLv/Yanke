package com.yangke.forum.module.points.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_user_points")
public class UserPoints {

    @TableId
    private Long id;
    private Long userId;
    private Integer totalPoints;     // 总积分
    private LocalDate lastCheckin;   // 上次签到日期
    private Integer todayLikes;      // 今日点赞次数
    private Integer todayComments;   // 今日评论次数
    private LocalDate statDate;      // 统计日期

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
