package com.yangke.forum.module.content.controller;

import com.yangke.forum.common.BusinessException;
import com.yangke.forum.common.IdentifyBy;
import com.yangke.forum.common.PageResult;
import com.yangke.forum.common.RateLimit;
import com.yangke.forum.common.RequestContext;
import com.yangke.forum.common.Result;
import com.yangke.forum.module.content.dto.PostDTO;
import com.yangke.forum.module.content.dto.PostDetailVO;
import com.yangke.forum.module.content.dto.PostVO;
import com.yangke.forum.module.content.dto.CommentVO;
import com.yangke.forum.module.content.service.CommentService;
import com.yangke.forum.module.content.service.PostService;
import com.yangke.forum.util.IpUtil;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/post")
public class PostController {

    @Resource
    private PostService postService;

    @Resource
    private CommentService commentService;

    @PostMapping
    @RateLimit(prefix = "post:create", max = 10, window = 60, by = IdentifyBy.USER)
    public Result<Long> create(@Valid @RequestBody PostDTO dto, HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(postService.createPost(dto, userId));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody PostDTO dto,
                               HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        postService.updatePost(id, dto, userId);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        postService.deletePost(id, userId);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<PostDetailVO> detail(@PathVariable Long id, HttpServletRequest request) {
        postService.recordView(id, IpUtil.getClientIp(request));
        PostDetailVO vo = postService.getPostDetail(id);
        // 附带评论
        vo.setComments(commentService.getCommentsByPostId(id));
        return Result.ok(vo);
    }

    @GetMapping
    public Result<PageResult<PostVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "latest") String orderBy) {
        return Result.ok(postService.listPosts(page, size, categoryId, orderBy));
    }

    @GetMapping("/hot")
    public Result<List<PostVO>> hot(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(postService.getHotPosts(limit));
    }

    @GetMapping("/search")
    public Result<PageResult<PostVO>> search(@RequestParam String keyword,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(postService.search(keyword, page, size));
    }

    // ==================== 草稿箱 ====================

    @PostMapping("/draft")
    public Result<Long> saveDraft(@Valid @RequestBody PostDTO dto,
                                   @RequestParam(required = false) Long draftId,
                                   HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(postService.saveDraft(dto, userId, draftId));
    }

    @GetMapping("/draft")
    public Result<PageResult<PostVO>> listDrafts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(postService.listDrafts(userId, page, size));
    }

    @PostMapping("/draft/{draftId}/publish")
    public Result<Void> publishDraft(@PathVariable Long draftId, HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        postService.publishDraft(draftId, userId);
        return Result.ok();
    }
}
