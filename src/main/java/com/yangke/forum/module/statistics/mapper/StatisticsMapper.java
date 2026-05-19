package com.yangke.forum.module.statistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangke.forum.module.statistics.entity.Statistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StatisticsMapper extends BaseMapper<Statistics> {

    @Select("SELECT * FROM t_statistics WHERE stat_date = #{date}")
    Statistics findByDate(@Param("date") LocalDate date);

    @Select("SELECT * FROM t_statistics WHERE stat_date BETWEEN #{start} AND #{end} ORDER BY stat_date ASC")
    List<Statistics> findBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
