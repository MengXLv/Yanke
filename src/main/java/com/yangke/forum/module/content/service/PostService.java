package com.yangke.forum.module.content.service;

import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.content.dto.PostDTO;
import com.yangke.forum.module.content.dto.PostDetailVO;
import com.yangke.forum.module.content.dto.PostVO;

import java.util.List;

public interface PostService {

    Long createPost(PostDTO dto, Long userId);

    void updatePost(Long postId, PostDTO dto, Long userId);

    void deletePost(Long postId, Long userId);

    /**
     * 帖子详情（Cache-Aside: Caffeine → Redis → MySQL）
     */
    PostDetailVO getPostDetail(Long postId);

    /**
     * 分页列表（按发布时间倒序）
     */
    PageResult<PostVO> listPosts(int page, int size, Long categoryId, String orderBy);

    /**
     * 热帖列表（Redis ZSet 按热度分数排序）
     */
    List<PostVO> getHotPosts(int limit);

    /**
     * 搜索帖子（走ES全文检索）
     */
    PageResult<PostVO> search(String keyword, int page, int size);

    /**
     * 记录浏览量（Redis HyperLogLog + 异步同步DB）
     */
    void recordView(Long postId, String ip);

    // ==================== 草稿箱 ====================

    /** 保存草稿（有id则更新，无id则新建） */
    Long saveDraft(PostDTO dto, Long userId, Long draftId);

    /** 草稿列表 */
    PageResult<PostVO> listDrafts(Long userId, int page, int size);

    /** 发布草稿 */
    void publishDraft(Long draftId, Long userId);
}
