package com.yangke.forum.module.content.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentVO {

    private Long id;
    private Long userId;
    private String username;
    private String userAvatar;
    private String content;
    private Integer likeCount;
    private LocalDateTime createTime;

    // 子回复
    private List<CommentVO> replies;
}
