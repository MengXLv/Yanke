package com.yangke.forum.module.admin.service;

import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.auth.dto.UserVO;
import com.yangke.forum.module.content.dto.PostVO;

import java.util.Map;

public interface AdminService {

    // 用户管理
    PageResult<UserVO> listUsers(int page, int size, String keyword);
    void updateUserStatus(Long userId, int status);
    void updateUserRole(Long userId, String role);

    // 内容管理
    PageResult<PostVO> listAllPosts(int page, int size, Integer status);
    void updatePostStatus(Long postId, int status);
    void toggleTop(Long postId);
    void toggleHot(Long postId);

    // 数据面板
    Map<String, Object> getDashboard();
}
