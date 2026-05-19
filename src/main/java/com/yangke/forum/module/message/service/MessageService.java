package com.yangke.forum.module.message.service;

import com.yangke.forum.module.message.entity.Message;
import java.util.List;
import java.util.Map;

public interface MessageService {

    /** 发送私信 */
    Message send(Long senderId, Long receiverId, String content);

    /** 对话列表（每个用户的最后一条消息） */
    List<Map<String, Object>> getConversations(Long userId);

    /** 与某人的完整对话 */
    List<Message> getMessagesWith(Long userId, Long otherUserId);

    /** 未读私信数 */
    int getUnreadCount(Long userId);

    /** 标记已读 */
    void markRead(Long userId, Long otherUserId);
}
