package com.yangke.forum.module.search.controller;

import com.yangke.forum.common.PageResult;
import com.yangke.forum.common.Result;
import com.yangke.forum.module.content.dto.PostVO;
import com.yangke.forum.module.content.entity.Comment;
import com.yangke.forum.module.search.service.SearchService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Resource
    private SearchService searchService;

    @GetMapping("/posts")
    public Result<PageResult<PostVO>> searchPosts(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(searchService.searchPosts(keyword, page, size));
    }

    @GetMapping("/comments")
    public Result<PageResult<Comment>> searchComments(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(searchService.searchComments(keyword, page, size));
    }
}
