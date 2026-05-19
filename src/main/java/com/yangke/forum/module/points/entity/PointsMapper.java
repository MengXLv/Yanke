package com.yangke.forum.module.points.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PointsMapper extends BaseMapper<UserPoints> {

    @Select("SELECT * FROM t_user_points WHERE user_id = #{userId}")
    UserPoints selectByUserId(@Param("userId") Long userId);

    @Update("UPDATE t_user_points SET total_points = total_points + #{delta} WHERE user_id = #{userId}")
    int addPoints(@Param("userId") Long userId, @Param("delta") int delta);
}
