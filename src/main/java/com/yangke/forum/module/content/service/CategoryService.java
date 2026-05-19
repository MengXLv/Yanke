package com.yangke.forum.module.content.service;

import com.yangke.forum.module.content.entity.Category;
import java.util.List;

public interface CategoryService {
    List<Category> listAll();
    List<Category> listEnabled();
    Category getById(Long id);
    Category create(Category category);
    Category update(Long id, Category category);
    void delete(Long id);
    String getCategoryName(Long id);
}
