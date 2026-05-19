package com.yangke.forum.module.content.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class PostDTO {

    @NotBlank(message = "标题不能为空")
    @Size(min = 2, max = 100)
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(min = 10, max = 50000)
    private String content;

    private Long categoryId;
    private String tags;
}
