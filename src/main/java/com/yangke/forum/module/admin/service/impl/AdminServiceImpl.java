package com.yangke.forum.module.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangke.forum.common.BusinessException;
import com.yangke.forum.common.Constants;
import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.admin.service.AdminService;
import com.yangke.forum.module.auth.dto.UserVO;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.content.dto.PostVO;
import com.yangke.forum.module.content.entity.Post;
import com.yangke.forum.module.content.mapper.CategoryMapper;
import com.yangke.forum.module.content.mapper.PostMapper;
import com.yangke.forum.module.content.mapper.ReportMapper;
import com.yangke.forum.module.statistics.mapper.StatisticsMapper;
import com.yangke.forum.module.statistics.service.StatisticsService;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.yangke.forum.util.RedisKeyUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PostMapper postMapper;

    @Resource
    private StatisticsService statisticsService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private StatisticsMapper statisticsMapper;

    @Resource
    private CategoryMapper categoryMapper;

    @Resource
    private ReportMapper reportMapper;

    // ==================== 用户管理 ====================

    @Override
    public PageResult<UserVO> listUsers(int page, int size, String keyword) {
        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery()
                .like(keyword != null, User::getUsername, keyword)
                .or()
                .like(keyword != null, User::getEmail, keyword)
                .orderByDesc(User::getCreateTime);

        IPage<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserVO> records = result.getRecords().stream()
                .map(u -> {
                    UserVO vo = new UserVO();
                    BeanUtil.copyProperties(u, vo);
                    vo.setToken(null);
                    return vo;
                })
                .collect(Collectors.toList());

        return new PageResult<>(result.getTotal(), page, size, records);
    }

    @Override
    public void updateUserStatus(Long userId, int status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void updateUserRole(Long userId, String role) {
        if (!Constants.ROLE_USER.equals(role)
                && !Constants.ROLE_MODERATOR.equals(role)
                && !Constants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(400, "无效的角色");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setRole(role);
        userMapper.updateById(user);
    }

    // ==================== 内容管理 ====================

    @Override
    public PageResult<PostVO> listAllPosts(int page, int size, Integer status) {
        LambdaQueryWrapper<Post> wrapper = Wrappers.<Post>lambdaQuery()
                .eq(status != null, Post::getStatus, status)
                .orderByDesc(Post::getCreateTime);

        IPage<Post> result = postMapper.selectPage(new Page<>(page, size), wrapper);
        List<PostVO> records = result.getRecords().stream()
                .map(p -> {
                    PostVO vo = new PostVO();
                    vo.setId(p.getId());
                    vo.setUserId(p.getUserId());
                    vo.setTitle(p.getTitle());
                    vo.setViewCount(p.getViewCount());
                    vo.setLikeCount(p.getLikeCount());
                    vo.setCommentCount(p.getCommentCount());
                    vo.setCreateTime(p.getCreateTime());
                    return vo;
                })
                .collect(Collectors.toList());

        return new PageResult<>(result.getTotal(), page, size, records);
    }

    @Override
    public void updatePostStatus(Long postId, int status) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "帖子不存在");
        }
        post.setStatus(status);
        postMapper.updateById(post);
    }

    @Override
    public void toggleTop(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "帖子不存在");
        }
        post.setIsTop(post.getIsTop() == 1 ? 0 : 1);
        postMapper.updateById(post);
    }

    @Override
    public void toggleHot(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "帖子不存在");
        }
        post.setIsHot(post.getIsHot() == 1 ? 0 : 1);
        postMapper.updateById(post);

        // 同步更新热帖ZSet
        if (post.getIsHot() == 1) {
            stringRedisTemplate.opsForZSet().add(
                    RedisKeyUtil.hotPostsKey(),
                    postId.toString(),
                    post.getLikeCount() * 1.0);
        } else {
            stringRedisTemplate.opsForZSet().remove(
                    RedisKeyUtil.hotPostsKey(), postId.toString());
        }
    }

    // ==================== 数据面板 ====================

    @Override
    public Map<String, Object> getDashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("todayUV", statisticsService.getTodayUV());
        dashboard.put("todayDAU", statisticsService.getTodayDAU());
        dashboard.put("totalUsers", userMapper.selectCount(null));
        dashboard.put("totalPosts", postMapper.selectCount(null));
        dashboard.put("totalCategories", categoryMapper.selectCount(null));
        long pendingReports = reportMapper.selectCount(null);
        dashboard.put("pendingReports", pendingReports);
        dashboard.put("uvTrend", statisticsService.getUVTrend(7));
        dashboard.put("dauTrend", statisticsService.getDAUTrend(7));
        // 最近7天历史数据
        dashboard.put("history", statisticsMapper.findBetween(
                java.time.LocalDate.now().minusDays(7), java.time.LocalDate.now()));
        return dashboard;
    }
}
