package com.yangke.forum.module.content.controller;

import com.yangke.forum.common.Result;
import com.yangke.forum.module.content.entity.Category;
import com.yangke.forum.module.content.service.CategoryService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryPublicController {

    @Resource
    private CategoryService categoryService;

    @GetMapping
    public Result<List<Category>> listEnabled() {
        return Result.ok(categoryService.listEnabled());
    }
}
