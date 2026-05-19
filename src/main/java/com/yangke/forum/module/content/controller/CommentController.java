package com.yangke.forum.module.content.controller;

import com.yangke.forum.common.IdentifyBy;
import com.yangke.forum.common.PageResult;
import com.yangke.forum.common.RateLimit;
import com.yangke.forum.common.RequestContext;
import com.yangke.forum.common.Result;
import com.yangke.forum.module.content.dto.CommentDTO;
import com.yangke.forum.module.content.dto.CommentVO;
import com.yangke.forum.module.content.service.CommentService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/comment")
public class CommentController {

    @Resource
    private CommentService commentService;

    @RateLimit(prefix = "comment:create", max = 20, window = 60, by = IdentifyBy.USER, message = "评论太频繁，请稍后再试")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody CommentDTO dto, HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(commentService.createComment(dto, userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        commentService.deleteComment(id, userId);
        return Result.ok();
    }

    @GetMapping("/post/{postId}")
    public Result<List<CommentVO>> listByPost(@PathVariable Long postId) {
        return Result.ok(commentService.getCommentsByPostId(postId));
    }

    @GetMapping("/user/{userId}")
    public Result<PageResult<CommentVO>> listByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(commentService.getUserComments(userId, page, size));
    }
}
