package com.yangke.forum.module.points.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_points_record")
public class PointsRecord {

    @TableId
    private Long id;
    private Long userId;
    private Integer points;          // 变动积分（正=获得，负=消费）
    private String reason;           // 原因：register/checkin/like/comment/post/redeem
    private Long relatedId;          // 关联ID（帖子/商品/秒杀活动）

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
