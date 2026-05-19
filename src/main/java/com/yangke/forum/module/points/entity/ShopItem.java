package com.yangke.forum.module.points.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_shop_item")
public class ShopItem {

    @TableId
    private Long id;
    private String name;
    private String description;
    private Integer price;           // 所需积分
    private Integer stock;           // 库存（-1=无限）
    private Integer sold;            // 已售数量
    private Integer status;          // 0-下架 1-上架 2-秒杀中
    private LocalDateTime seckillStart;  // 秒杀开始时间
    private LocalDateTime seckillEnd;    // 秒杀结束时间
    private Integer seckillPrice;   // 秒杀价

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
