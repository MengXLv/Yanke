package com.yangke.forum.module.notification.controller;

import com.yangke.forum.common.RequestContext;
import com.yangke.forum.common.Result;
import com.yangke.forum.module.notification.entity.Notification;
import com.yangke.forum.module.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    @Resource
    private NotificationService notificationService;

    @GetMapping("/unread")
    public Result<List<Notification>> unread(HttpServletRequest request,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(notificationService.getUnreadNotifications(userId, page, size));
    }

    @GetMapping("/all")
    public Result<List<Notification>> all(HttpServletRequest request,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(notificationService.getAllNotifications(userId, page, size));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount(HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    @PutMapping("/read-all")
    public Result<Void> readAll(HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        notificationService.markAllRead(userId);
        return Result.ok();
    }

    @PutMapping("/read/{id}")
    public Result<Void> read(@PathVariable Long id) {
        notificationService.markRead(id);
        return Result.ok();
    }
}
