package com.yangke.forum.module.points.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

    @Select("SELECT DISTINCT DATE(create_time) FROM t_points_record " +
            "WHERE user_id = #{userId} AND reason = 'checkin' " +
            "AND create_time >= #{since} ORDER BY create_time DESC")
    List<LocalDate> findCheckinDates(@Param("userId") Long userId, @Param("since") java.time.LocalDateTime since);
}
