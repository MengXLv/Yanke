package com.yangke.forum.module.content.service;

import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.content.dto.CommentDTO;
import com.yangke.forum.module.content.dto.CommentVO;

import java.util.List;

public interface CommentService {

    /**
     * 发表评论/回复
     */
    Long createComment(CommentDTO dto, Long userId);

    /**
     * 删除评论（仅作者可删除）
     */
    void deleteComment(Long commentId, Long userId);

    /**
     * 获取帖子的评论列表（含嵌套回复）
     */
    List<CommentVO> getCommentsByPostId(Long postId);

    /**
     * 分页获取用户评论
     */
    PageResult<CommentVO> getUserComments(Long userId, int page, int size);
}
