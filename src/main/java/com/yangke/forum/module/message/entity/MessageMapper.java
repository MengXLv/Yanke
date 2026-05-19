package com.yangke.forum.module.message.entity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("SELECT CASE WHEN sender_id = #{userId} THEN receiver_id ELSE sender_id END AS uid, " +
            "MAX(create_time) AS last_time " +
            "FROM t_message WHERE sender_id = #{userId} OR receiver_id = #{userId} " +
            "GROUP BY uid ORDER BY last_time DESC")
    List<java.util.Map<String, Object>> findConversationUserIdsRaw(@Param("userId") Long userId);

    @Select("SELECT * FROM t_message WHERE " +
            "((sender_id = #{userId1} AND receiver_id = #{userId2}) " +
            "OR (sender_id = #{userId2} AND receiver_id = #{userId1})) " +
            "ORDER BY create_time ASC")
    List<Message> findMessagesBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /** 获取双方最后一条消息（用于会话列表预览） */
    @Select("SELECT * FROM t_message WHERE " +
            "((sender_id = #{userId1} AND receiver_id = #{userId2}) " +
            "OR (sender_id = #{userId2} AND receiver_id = #{userId1})) " +
            "ORDER BY create_time DESC LIMIT 1")
    Message findLastMessageBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /** 来自某用户的新消息数 */
    @Select("SELECT COUNT(*) FROM t_message WHERE receiver_id = #{receiverId} AND sender_id = #{senderId} AND is_read = 0")
    int countUnreadFrom(@Param("receiverId") Long receiverId, @Param("senderId") Long senderId);

    @Select("SELECT COUNT(*) FROM t_message WHERE receiver_id = #{userId} AND is_read = 0")
    int countUnread(@Param("userId") Long userId);

    /** 批量标记来自某用户的消息为已读 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE t_message SET is_read = 1 WHERE receiver_id = #{receiverId} AND sender_id = #{senderId} AND is_read = 0")
    int markReadFrom(@Param("receiverId") Long receiverId, @Param("senderId") Long senderId);
}
