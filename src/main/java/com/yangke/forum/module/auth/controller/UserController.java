package com.yangke.forum.module.auth.controller;

import com.yangke.forum.common.PageResult;
import com.yangke.forum.common.RequestContext;
import com.yangke.forum.common.Result;
import com.yangke.forum.module.auth.dto.UpdatePasswordDTO;
import com.yangke.forum.module.auth.dto.UpdateProfileDTO;
import com.yangke.forum.module.auth.dto.UserVO;
import com.yangke.forum.module.auth.service.UserService;
import com.yangke.forum.module.content.dto.CommentVO;
import com.yangke.forum.module.content.dto.PostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Tag(name = "用户个人中心")
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;

    @Operation(summary = "获取用户公开信息")
    @GetMapping("/{userId}")
    public Result<UserVO> profile(@Parameter(description = "用户ID") @PathVariable Long userId) {
        return Result.ok(userService.getUserProfile(userId));
    }

    @Operation(summary = "编辑个人资料")
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto,
                                         HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(userService.updateProfile(userId, dto));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordDTO dto,
                                        HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        userService.updatePassword(userId, dto);
        return Result.ok();
    }

    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file,
                                        HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        try {
            String filename = file.getOriginalFilename();
            String ext = filename != null && filename.contains(".")
                    ? filename.substring(filename.lastIndexOf('.') + 1) : "png";
            String url = userService.uploadAvatar(userId, file.getBytes(), ext);
            return Result.ok(url);
        } catch (Exception e) {
            return Result.fail("头像上传失败");
        }
    }

    @Operation(summary = "获取用户帖子列表")
    @GetMapping("/{userId}/posts")
    public Result<PageResult<PostVO>> userPosts(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.getUserPosts(userId, page, size));
    }

    @Operation(summary = "获取用户评论列表")
    @GetMapping("/{userId}/comments")
    public Result<PageResult<CommentVO>> userComments(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(userService.getUserComments(userId, page, size));
    }
}
