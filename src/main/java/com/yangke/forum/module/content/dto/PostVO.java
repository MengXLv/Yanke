package com.yangke.forum.module.content.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PostVO {

    private Long id;
    private Long userId;
    private String username;
    private String userAvatar;
    private String title;
    private String summary;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer isHot;
    private Integer isTop;
    private LocalDateTime createTime;
}
