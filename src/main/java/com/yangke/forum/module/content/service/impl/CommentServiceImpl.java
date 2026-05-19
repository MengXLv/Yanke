package com.yangke.forum.module.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangke.forum.common.BusinessException;
import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.content.dto.CommentDTO;
import com.yangke.forum.module.content.dto.CommentVO;
import com.yangke.forum.module.content.entity.Comment;
import com.yangke.forum.module.content.filter.SensitiveWordFilter;
import com.yangke.forum.module.content.mapper.CommentMapper;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.content.mapper.PostMapper;
import com.yangke.forum.module.content.service.CommentService;
import com.yangke.forum.module.points.service.PointsService;
import com.yangke.forum.module.notification.entity.Notification;
import com.yangke.forum.module.notification.service.NotificationService;
import com.yangke.forum.module.search.service.SearchService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl implements CommentService {

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private PostMapper postMapper;

    @Resource
    private NotificationService notificationService;

    @Resource
    private SearchService searchService;

    @Resource
    private org.springframework.cache.CacheManager cacheManager;

    @Resource
    private org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Resource
    private PointsService pointsService;

    @Resource
    private UserMapper userMapper;

    @org.springframework.context.annotation.Lazy
    @Resource
    private CommentServiceImpl self;

    @Override
    @Transactional
    public Long createComment(CommentDTO dto, Long userId) {
        if (sensitiveWordFilter.containsSensitive(dto.getContent())) {
            throw new BusinessException(400, "评论包含违规内容，请修改后重试");
        }

        Comment comment = new Comment();
        comment.setPostId(dto.getPostId());
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setParentId(dto.getParentId() != null ? dto.getParentId() : 0L);
        comment.setReplyToUserId(dto.getReplyToUserId());
        comment.setLikeCount(0);
        commentMapper.insert(comment);
        postMapper.updateCommentCount(dto.getPostId(), 1);

        // 事务外交给异步：通知 + ES + 缓存 + 积分（不阻塞也不回滚主写入）
        self.afterCreateComment(comment, userId);
        return comment.getId();
    }

    @Async("notifyExecutor")
    public void afterCreateComment(Comment comment, Long userId) {
        var post = postMapper.selectById(comment.getPostId());
        if (post != null && !post.getUserId().equals(userId)) {
            Notification notification = new Notification();
            notification.setSenderId(userId);
            notification.setReceiverId(post.getUserId());
            notification.setTargetId(comment.getPostId());
            notification.setType(2);
            notification.setContent("评论了你的帖子《" + post.getTitle() + "》");
            notificationService.sendAsync(notification);
        }
        searchService.indexComment(comment);
        evictPostCache(comment.getPostId());
        try { pointsService.awardComment(userId); } catch (Exception ignored) {}
    }

    @Override
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(404, "评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除此评论");
        }
        commentMapper.deleteById(commentId);
        postMapper.updateCommentCount(comment.getPostId(), -1);
        evictPostCache(comment.getPostId());
    }

    private void evictPostCache(Long postId) {
        // 清除 Caffeine 本地缓存
        var caffeine = cacheManager.getCache("post");
        if (caffeine != null) caffeine.evict(postId);
        // 清除 Redis 分布式缓存
        redisTemplate.delete("post:" + postId);
    }

    @Override
    public List<CommentVO> getCommentsByPostId(Long postId) {
        List<Comment> allComments = commentMapper.selectList(
                Wrappers.<Comment>lambdaQuery()
                        .eq(Comment::getPostId, postId)
                        .orderByAsc(Comment::getCreateTime));
        if (allComments.isEmpty()) return Collections.emptyList();

        // 批量加载用户名
        Set<Long> userIds = allComments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = batchLoadUsers(userIds);

        // ID → CommentVO 映射
        Map<Long, CommentVO> voMap = new LinkedHashMap<>();
        List<CommentVO> roots = new ArrayList<>();

        for (Comment c : allComments) {
            CommentVO vo = toVO(c, userMap);
            voMap.put(c.getId(), vo);
        }

        // 按 parentId 挂载：parentId=0 → 根评论，否则 → 父评论的 replies
        for (Comment c : allComments) {
            Long pid = c.getParentId();
            if (pid == null || pid == 0) {
                roots.add(voMap.get(c.getId()));
            } else {
                CommentVO parentVO = voMap.get(pid);
                if (parentVO != null) {
                    if (parentVO.getReplies() == null) {
                        parentVO.setReplies(new ArrayList<>());
                    }
                    parentVO.getReplies().add(voMap.get(c.getId()));
                }
            }
        }

        return roots;
    }

    @Override
    public PageResult<CommentVO> getUserComments(Long userId, int page, int size) {
        LambdaQueryWrapper<Comment> wrapper = Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getUserId, userId)
                .orderByDesc(Comment::getCreateTime);

        IPage<Comment> result = commentMapper.selectPage(new Page<>(page, size), wrapper);
        Set<Long> uids = result.getRecords().stream().map(Comment::getUserId).collect(Collectors.toSet());
        Map<Long, User> userCommentMap = batchLoadUsers(uids);
        List<CommentVO> records = result.getRecords().stream()
                .map(c -> toVO(c, userCommentMap))
                .collect(Collectors.toList());

        return new PageResult<>(result.getTotal(), page, size, records);
    }

    private CommentVO toVO(Comment c, Map<Long, User> userMap) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setUserId(c.getUserId());
        vo.setContent(c.getContent());
        vo.setLikeCount(c.getLikeCount());
        vo.setCreateTime(c.getCreateTime());
        User author = userMap.get(c.getUserId());
        vo.setUsername(author != null ? author.getUsername() : null);
        vo.setUserAvatar(author != null ? author.getAvatar() : null);
        return vo;
    }

    private Map<Long, User> batchLoadUsers(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userMapper.selectBatchIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }
}
