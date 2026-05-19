package com.yangke.forum.module.content.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CommentDTO {

    @NotNull(message = "帖子ID不能为空")
    private Long postId;

    private Long parentId;         // 父评论ID，一级评论传null

    private Long replyToUserId;    // 被回复的用户ID

    @NotBlank(message = "评论内容不能为空")
    private String content;
}
