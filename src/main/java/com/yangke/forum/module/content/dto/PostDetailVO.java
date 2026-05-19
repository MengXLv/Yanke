package com.yangke.forum.module.content.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostDetailVO {

    private Long id;
    private Long userId;
    private String username;
    private String userAvatar;
    private String title;
    private String content;
    private Long categoryId;
    private String categoryName;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer isHot;
    private Integer isTop;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 关联评论
    private List<CommentVO> comments;
}
