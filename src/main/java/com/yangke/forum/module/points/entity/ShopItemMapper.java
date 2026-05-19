package com.yangke.forum.module.points.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopItemMapper extends BaseMapper<ShopItem> {

    @Update("UPDATE t_shop_item SET sold = sold + #{delta} WHERE id = #{itemId}")
    int addSold(@Param("itemId") Long itemId, @Param("delta") int delta);
}
