package com.yangke.forum.module.message.controller;

import com.yangke.forum.common.RequestContext;
import com.yangke.forum.common.Result;
import com.yangke.forum.module.message.entity.Message;
import com.yangke.forum.module.message.service.MessageService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    @Resource
    private MessageService messageService;

    /** 发送私信 */
    @PostMapping
    public Result<Message> send(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        Long receiverId = Long.valueOf(body.get("receiverId").toString());
        String content = (String) body.get("content");
        return Result.ok(messageService.send(userId, receiverId, content));
    }

    /** 对话列表 */
    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations(HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(messageService.getConversations(userId));
    }

    /** 与某人的消息记录 */
    @GetMapping("/with/{otherUserId}")
    public Result<List<Message>> messagesWith(@PathVariable Long otherUserId, HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(messageService.getMessagesWith(userId, otherUserId));
    }

    /** 未读数 */
    @GetMapping("/unread")
    public Result<Map<String, Integer>> unread(HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(Map.of("count", messageService.getUnreadCount(userId)));
    }

    /** 标记已读 */
    @PutMapping("/read/{otherUserId}")
    public Result<Void> markRead(@PathVariable Long otherUserId, HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        messageService.markRead(userId, otherUserId);
        return Result.ok();
    }
}
