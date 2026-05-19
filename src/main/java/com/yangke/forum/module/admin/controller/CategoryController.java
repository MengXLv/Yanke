package com.yangke.forum.module.admin.controller;

import com.yangke.forum.common.Result;
import com.yangke.forum.module.content.entity.Category;
import com.yangke.forum.module.content.service.CategoryService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/admin/categories")
public class CategoryController {

    @Resource
    private CategoryService categoryService;

    @GetMapping
    public Result<List<Category>> listAll() {
        return Result.ok(categoryService.listAll());
    }

    @PostMapping
    public Result<Category> create(@RequestBody Category category) {
        return Result.ok(categoryService.create(category));
    }

    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable Long id, @RequestBody Category category) {
        return Result.ok(categoryService.update(id, category));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
