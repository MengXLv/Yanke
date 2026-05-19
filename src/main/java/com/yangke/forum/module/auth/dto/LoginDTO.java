package com.yangke.forum.module.auth.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class LoginDTO {

    @NotBlank(message = "用户名/邮箱不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "验证码不能为空")
    private String captcha;

    @NotBlank(message = "验证码唯一标识不能为空")
    private String captchaKey;
}
