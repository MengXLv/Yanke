package com.yangke.forum.module.content.controller;

import com.yangke.forum.common.RequestContext;
import com.yangke.forum.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    @Value("${forum.upload.avatar-dir:./uploads/avatar}")
    private String avatarDir;

    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        RequestContext.requireLogin(request);
        try {
            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase() : "png";
            // 只允许图片类型
            if (!ext.matches("jpg|jpeg|png|gif|webp|bmp")) {
                return Result.fail("不支持的图片格式");
            }
            if (file.getSize() > 10 * 1024 * 1024) {
                return Result.fail("图片大小不能超过10MB");
            }

            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            File uploadDir = new File(avatarDir, "post-images");
            if (!uploadDir.exists()) uploadDir.mkdirs();
            file.transferTo(new File(uploadDir, filename));

            String url = "/avatar/post-images/" + filename;
            return Result.ok(Map.of("url", url));
        } catch (Exception e) {
            return Result.fail("图片上传失败: " + e.getMessage());
        }
    }
}
