package com.yangke.forum.module.admin.controller;

import com.yangke.forum.common.Constants;
import com.yangke.forum.common.PageResult;
import com.yangke.forum.common.RequestContext;
import com.yangke.forum.common.Result;
import com.yangke.forum.module.admin.service.AdminService;
import com.yangke.forum.module.auth.dto.UserVO;
import com.yangke.forum.module.content.dto.PostVO;
import com.yangke.forum.module.content.entity.Report;
import com.yangke.forum.module.content.service.ReportService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Resource
    private AdminService adminService;

    @Resource
    private ReportService reportService;

    // ==================== 用户管理 ====================

    @GetMapping("/users")
    public Result<PageResult<UserVO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(adminService.listUsers(page, size, keyword));
    }

    @PutMapping("/users/{userId}/status")
    public Result<Void> updateUserStatus(@PathVariable Long userId,
                                          @RequestParam int status) {
        adminService.updateUserStatus(userId, status);
        return Result.ok();
    }

    @PutMapping("/users/{userId}/role")
    public Result<Void> updateUserRole(@PathVariable Long userId,
                                        @RequestParam String role) {
        adminService.updateUserRole(userId, role);
        return Result.ok();
    }

    // ==================== 内容管理 ====================

    @GetMapping("/posts")
    public Result<PageResult<PostVO>> listAllPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status) {
        return Result.ok(adminService.listAllPosts(page, size, status));
    }

    @PutMapping("/posts/{postId}/status")
    public Result<Void> updatePostStatus(@PathVariable Long postId,
                                          @RequestParam int status) {
        adminService.updatePostStatus(postId, status);
        return Result.ok();
    }

    @PutMapping("/posts/{postId}/top")
    public Result<Void> toggleTop(@PathVariable Long postId) {
        adminService.toggleTop(postId);
        return Result.ok();
    }

    @PutMapping("/posts/{postId}/hot")
    public Result<Void> toggleHot(@PathVariable Long postId) {
        adminService.toggleHot(postId);
        return Result.ok();
    }

    // ==================== 统计面板 ====================

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        return Result.ok(adminService.getDashboard());
    }

    // ==================== 举报管理 ====================

    @GetMapping("/reports")
    public Result<PageResult<Report>> listReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(reportService.listPending(page, size));
    }

    @PutMapping("/reports/{reportId}")
    public Result<Void> handleReport(@PathVariable Long reportId,
                                     @RequestBody Map<String, Object> body,
                                     HttpServletRequest request) {
        Long handlerId = RequestContext.requireLogin(request);
        Integer status = Integer.valueOf(body.get("status").toString());
        String note = (String) body.get("note");
        reportService.handle(reportId, handlerId, status, note);
        return Result.ok();
    }

    @GetMapping("/reports/stats")
    public Result<Map<String, Object>> reportStats() {
        return Result.ok(reportService.stats());
    }
}
