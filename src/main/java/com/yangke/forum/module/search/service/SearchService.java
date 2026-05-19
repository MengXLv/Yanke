package com.yangke.forum.module.search.service;

import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.content.dto.PostVO;
import com.yangke.forum.module.content.entity.Comment;
import com.yangke.forum.module.content.entity.Post;

public interface SearchService {

    /**
     * 索引帖子到ES
     */
    void indexPost(Post post);

    /**
     * 批量索引帖子到ES（用于启动时的全量reindex）
     */
    void indexPosts(java.util.Collection<Post> posts);

    /**
     * 更新帖子索引
     */
    void updatePost(Post post);

    /**
     * 删除帖子索引
     */
    void deletePost(Long postId);

    /**
     * 索引评论到ES
     */
    void indexComment(Comment comment);

    /**
     * 全文检索帖子（Bool查询 + 字段加权 + 高亮）
     */
    PageResult<PostVO> searchPosts(String keyword, int page, int size);

    /**
     * 全文检索评论
     */
    PageResult<Comment> searchComments(String keyword, int page, int size);
}
