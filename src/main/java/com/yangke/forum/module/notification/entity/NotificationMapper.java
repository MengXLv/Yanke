package com.yangke.forum.module.notification.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Update("UPDATE t_notification SET is_read = 1 WHERE receiver_id = #{userId} AND is_read = 0")
    int markAllRead(@Param("userId") Long userId);
}
