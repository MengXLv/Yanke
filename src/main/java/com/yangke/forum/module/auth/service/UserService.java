package com.yangke.forum.module.auth.service;

import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.auth.dto.UpdatePasswordDTO;
import com.yangke.forum.module.auth.dto.UpdateProfileDTO;
import com.yangke.forum.module.auth.dto.UserVO;
import com.yangke.forum.module.content.dto.CommentVO;
import com.yangke.forum.module.content.dto.PostVO;

public interface UserService {

    /**
     * 获取用户公开信息
     */
    UserVO getUserProfile(Long userId);

    /**
     * 编辑个人资料
     */
    UserVO updateProfile(Long userId, UpdateProfileDTO dto);

    /**
     * 修改密码
     */
    void updatePassword(Long userId, UpdatePasswordDTO dto);

    /**
     * 上传头像
     */
    String uploadAvatar(Long userId, byte[] imageData, String extension);

    /**
     * 获取用户帖子列表
     */
    PageResult<PostVO> getUserPosts(Long userId, int page, int size);

    /**
     * 获取用户评论列表
     */
    PageResult<CommentVO> getUserComments(Long userId, int page, int size);
}
