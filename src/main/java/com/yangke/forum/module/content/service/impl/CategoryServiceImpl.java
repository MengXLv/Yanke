package com.yangke.forum.module.content.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yangke.forum.common.BusinessException;
import com.yangke.forum.module.content.entity.Category;
import com.yangke.forum.module.content.mapper.CategoryMapper;
import com.yangke.forum.module.content.service.CategoryService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Resource
    private CategoryMapper categoryMapper;

    @Override
    @Cacheable(value = "category", key = "'all'")
    public List<Category> listAll() {
        return categoryMapper.selectList(
                Wrappers.<Category>lambdaQuery().orderByAsc(Category::getSortOrder));
    }

    @Override
    public List<Category> listEnabled() {
        return categoryMapper.selectList(
                Wrappers.<Category>lambdaQuery()
                        .eq(Category::getStatus, 1)
                        .orderByAsc(Category::getSortOrder));
    }

    @Override
    public Category getById(Long id) {
        Category c = categoryMapper.selectById(id);
        if (c == null) throw new BusinessException(404, "分类不存在");
        return c;
    }

    @Override
    @CacheEvict(value = "category", key = "'all'")
    public Category create(Category category) {
        category.setStatus(category.getStatus() != null ? category.getStatus() : 1);
        category.setSortOrder(category.getSortOrder() != null ? category.getSortOrder() : 0);
        categoryMapper.insert(category);
        return category;
    }

    @Override
    @CacheEvict(value = "category", key = "'all'")
    public Category update(Long id, Category category) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) throw new BusinessException(404, "分类不存在");
        if (category.getName() != null) existing.setName(category.getName());
        if (category.getDescription() != null) existing.setDescription(category.getDescription());
        if (category.getSortOrder() != null) existing.setSortOrder(category.getSortOrder());
        if (category.getStatus() != null) existing.setStatus(category.getStatus());
        categoryMapper.updateById(existing);
        return existing;
    }

    @Override
    @CacheEvict(value = "category", key = "'all'")
    public void delete(Long id) {
        categoryMapper.deleteById(id);
    }

    @Override
    public String getCategoryName(Long id) {
        if (id == null) return null;
        Category c = categoryMapper.selectById(id);
        return c != null ? c.getName() : null;
    }
}
