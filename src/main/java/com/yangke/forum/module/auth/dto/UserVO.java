package com.yangke.forum.module.auth.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String email;
    private String avatar;
    private String bio;
    private String role;
    private String token;
    private LocalDateTime createTime;
}
