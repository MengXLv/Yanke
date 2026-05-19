package com.yangke.forum.module.message.service.impl;

import com.yangke.forum.common.BusinessException;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.message.entity.Message;
import com.yangke.forum.module.message.entity.MessageMapper;
import com.yangke.forum.module.message.service.MessageService;
import com.yangke.forum.module.notification.service.WebSocketPushService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessageServiceImpl implements MessageService {

    @Resource
    private MessageMapper messageMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private WebSocketPushService wsPushService;

    @Override
    public Message send(Long senderId, Long receiverId, String content) {
        if (senderId.equals(receiverId)) throw new BusinessException(400, "不能给自己发消息");
        if (content == null || content.trim().isEmpty()) throw new BusinessException(400, "内容不能为空");
        Message msg = new Message();
        msg.setSenderId(senderId);
        msg.setReceiverId(receiverId);
        msg.setContent(content.trim());
        msg.setIsRead(0);
        messageMapper.insert(msg);
        // WebSocket实时推送
        wsPushService.pushMessage(msg);
        return msg;
    }

    @Override
    public List<Map<String, Object>> getConversations(Long userId) {
        List<Map<String, Object>> rows = messageMapper.findConversationUserIdsRaw(userId);
        if (rows.isEmpty()) return Collections.emptyList();

        List<Long> userIds = rows.stream()
                .map(r -> Long.valueOf(r.get("uid").toString()))
                .collect(Collectors.toList());

        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long uid : userIds) {
            Message last = messageMapper.findLastMessageBetween(userId, uid);
            if (last == null) continue;
            int unread = messageMapper.countUnreadFrom(userId, uid);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userId", uid);
            User other = userMap.get(uid);
            entry.put("username", other != null ? other.getUsername() : null);
            entry.put("avatar", other != null ? other.getAvatar() : null);
            entry.put("lastContent", last.getContent().length() > 50
                    ? last.getContent().substring(0, 50) + "..." : last.getContent());
            entry.put("lastTime", last.getCreateTime());
            entry.put("unread", unread);
            result.add(entry);
        }
        return result;
    }

    @Override
    public List<Message> getMessagesWith(Long userId, Long otherUserId) {
        return messageMapper.findMessagesBetween(userId, otherUserId);
    }

    @Override
    public int getUnreadCount(Long userId) {
        return messageMapper.countUnread(userId);
    }

    @Override
    public void markRead(Long userId, Long otherUserId) {
        messageMapper.markReadFrom(userId, otherUserId);
    }
}
