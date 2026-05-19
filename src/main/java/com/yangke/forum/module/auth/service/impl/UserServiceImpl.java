package com.yangke.forum.module.auth.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangke.forum.common.BusinessException;
import com.yangke.forum.common.Constants;
import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.auth.dto.UpdatePasswordDTO;
import com.yangke.forum.module.auth.dto.UpdateProfileDTO;
import com.yangke.forum.module.auth.dto.UserVO;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.auth.service.UserService;
import com.yangke.forum.module.content.dto.CommentVO;
import com.yangke.forum.module.content.dto.PostVO;
import com.yangke.forum.module.content.entity.Comment;
import com.yangke.forum.module.content.entity.Post;
import com.yangke.forum.module.content.mapper.CommentMapper;
import com.yangke.forum.module.content.mapper.PostMapper;
import com.yangke.forum.util.MD5Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PostMapper postMapper;

    @Resource
    private CommentMapper commentMapper;

    @Value("${forum.upload.avatar-dir:./uploads/avatar}")
    private String avatarDir;

    @Value("${forum.upload.avatar-url-prefix:/avatar/}")
    private String avatarUrlPrefix;

    @Override
    public UserVO getUserProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeleted() != 0) {
            throw new BusinessException(404, "用户不存在");
        }
        return toVO(user);
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (dto.getUsername() != null && !dto.getUsername().equals(user.getUsername())) {
            // 检查用户名是否已被占用
            User exist = userMapper.selectByAccount(dto.getUsername());
            if (exist != null && !exist.getId().equals(userId)) {
                throw new BusinessException(400, "用户名已被占用");
            }
            user.setUsername(dto.getUsername());
        }
        if (dto.getBio() != null) {
            user.setBio(dto.getBio());
        }
        userMapper.updateById(user);
        return toVO(user);
    }

    @Override
    public void updatePassword(Long userId, UpdatePasswordDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 验证原密码
        if (!MD5Util.verify(dto.getOldPassword(), user.getSalt(), user.getPassword())) {
            throw new BusinessException(400, "原密码错误");
        }

        // 更新密码
        String newSalt = MD5Util.generateSalt();
        String newEncrypted = MD5Util.md5WithSalt(dto.getNewPassword(), newSalt);
        user.setSalt(newSalt);
        user.setPassword(newEncrypted);
        userMapper.updateById(user);
    }

    @Override
    public String uploadAvatar(Long userId, byte[] imageData, String extension) {
        if (imageData == null || imageData.length == 0) {
            throw new BusinessException(400, "图片数据为空");
        }

        String filename = IdUtil.fastSimpleUUID() + "." + (extension != null ? extension : "png");
        Path dir = Paths.get(avatarDir);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), imageData);

            String avatarUrl = avatarUrlPrefix + filename;
            User user = userMapper.selectById(userId);
            user.setAvatar(avatarUrl);
            userMapper.updateById(user);

            return avatarUrl;
        } catch (IOException e) {
            log.error("Failed to save avatar", e);
            throw new BusinessException("头像上传失败");
        }
    }

    @Override
    public PageResult<PostVO> getUserPosts(Long userId, int page, int size) {
        LambdaQueryWrapper<Post> wrapper = Wrappers.<Post>lambdaQuery()
                .eq(Post::getUserId, userId)
                .eq(Post::getStatus, Constants.POST_STATUS_PUBLISHED)
                .orderByDesc(Post::getCreateTime);

        IPage<Post> result = postMapper.selectPage(new Page<>(page, size), wrapper);
        List<PostVO> records = result.getRecords().stream()
                .map(p -> {
                    PostVO vo = new PostVO();
                    vo.setId(p.getId());
                    vo.setTitle(p.getTitle());
                    vo.setSummary(p.getContent() != null && p.getContent().length() > 200
                            ? p.getContent().substring(0, 200) + "..." : p.getContent());
                    vo.setViewCount(p.getViewCount());
                    vo.setLikeCount(p.getLikeCount());
                    vo.setCommentCount(p.getCommentCount());
                    vo.setIsHot(p.getIsHot());
                    vo.setIsTop(p.getIsTop());
                    vo.setCreateTime(p.getCreateTime());
                    return vo;
                })
                .collect(Collectors.toList());

        return new PageResult<>(result.getTotal(), page, size, records);
    }

    @Override
    public PageResult<CommentVO> getUserComments(Long userId, int page, int size) {
        LambdaQueryWrapper<Comment> wrapper = Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getUserId, userId)
                .orderByDesc(Comment::getCreateTime);

        IPage<Comment> result = commentMapper.selectPage(new Page<>(page, size), wrapper);
        List<CommentVO> records = result.getRecords().stream()
                .map(c -> {
                    CommentVO vo = new CommentVO();
                    vo.setId(c.getId());
                    vo.setUserId(c.getUserId());
                    vo.setContent(c.getContent());
                    vo.setLikeCount(c.getLikeCount());
                    vo.setCreateTime(c.getCreateTime());
                    return vo;
                })
                .collect(Collectors.toList());

        return new PageResult<>(result.getTotal(), page, size, records);
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        vo.setToken(null);
        return vo;
    }
}
