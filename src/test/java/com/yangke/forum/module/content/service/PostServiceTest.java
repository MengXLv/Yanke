package com.yangke.forum.module.content.service;

import com.yangke.forum.BaseTest;
import com.yangke.forum.common.BusinessException;
import com.yangke.forum.common.Constants;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.auth.service.AuthService;
import com.yangke.forum.module.content.dto.PostDTO;
import com.yangke.forum.module.content.dto.PostDetailVO;
import com.yangke.forum.module.content.dto.PostVO;
import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.content.entity.Category;
import com.yangke.forum.module.content.mapper.CategoryMapper;
import org.junit.jupiter.api.*;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class PostServiceTest extends BaseTest {

    @Resource private PostService postService;
    @Resource private UserMapper userMapper;
    @Resource private CategoryMapper categoryMapper;

    private Long userId;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        // 创建测试用户
        User user = new User();
        user.setUsername("testuser");
        user.setEmail("test@test.com");
        user.setPassword("$2a$10$dummy");
        user.setRole(Constants.ROLE_USER);
        user.setStatus(Constants.USER_STATUS_ACTIVE);
        userMapper.insert(user);
        userId = user.getId();

        // 创建测试分类
        Category cat = new Category();
        cat.setName("测试分类");
        cat.setStatus(1);
        categoryMapper.insert(cat);
        categoryId = cat.getId();
    }

    @Test
    void createPostSuccess() {
        PostDTO dto = new PostDTO();
        dto.setTitle("测试帖子标题");
        dto.setContent("这是正常的测试内容，没有任何违规词汇。");
        dto.setCategoryId(categoryId);

        Long postId = postService.createPost(dto, userId);
        assertNotNull(postId);

        PostDetailVO detail = postService.getPostDetail(postId);
        assertEquals("测试帖子标题", detail.getTitle());
        assertEquals(userId, detail.getUserId());
    }

    @Test
    void createPostWithSensitiveWord() {
        PostDTO dto = new PostDTO();
        dto.setTitle("正常标题");
        dto.setContent("这里有毒品的交易信息");
        dto.setCategoryId(categoryId);

        Long postId = postService.createPost(dto, userId);
        assertNotNull(postId);
        PostDetailVO detail = postService.getPostDetail(postId);
        assertNotNull(detail);
    }

    @Test
    void createPostWithoutSensitive() {
        PostDTO dto = new PostDTO();
        dto.setTitle("正常标题");
        dto.setContent("完全正常的内容");
        dto.setCategoryId(categoryId);

        Long postId = postService.createPost(dto, userId);
        assertNotNull(postId);
    }

    @Test
    void updatePostPermissionCheck() {
        PostDTO dto = new PostDTO();
        dto.setTitle("标题");
        dto.setContent("正常内容");
        dto.setCategoryId(categoryId);
        Long postId = postService.createPost(dto, userId);

        PostDTO update = new PostDTO();
        update.setTitle("尝试篡改");
        update.setContent("别人的帖子");
        update.setCategoryId(categoryId);

        assertThrows(BusinessException.class,
                () -> postService.updatePost(postId, update, userId + 999),
                "非作者无权修改");
    }

    @Test
    void listPosts() {
        for (int i = 0; i < 3; i++) {
            PostDTO dto = new PostDTO();
            dto.setTitle("帖子 " + i);
            dto.setContent("内容 " + i);
            dto.setCategoryId(categoryId);
            postService.createPost(dto, userId);
        }

        PageResult<PostVO> result = postService.listPosts(1, 10, null, "latest");
        assertTrue(result.getRecords().size() >= 3);
    }

    @Test
    void deletePostPermissionCheck() {
        PostDTO dto = new PostDTO();
        dto.setTitle("要删除的帖子");
        dto.setContent("正常内容");
        dto.setCategoryId(categoryId);
        Long postId = postService.createPost(dto, userId);

        assertThrows(BusinessException.class,
                () -> postService.deletePost(postId, userId + 999),
                "非作者无权删除");
    }

    @Test
    void getPostDetailNotFound() {
        assertThrows(BusinessException.class,
                () -> postService.getPostDetail(99999L),
                "不存在的帖子应抛404");
    }

    @Test
    void saveAndPublishDraft() {
        PostDTO draft = new PostDTO();
        draft.setTitle("草稿标题");
        draft.setContent("草稿内容");
        draft.setCategoryId(categoryId);

        Long draftId = postService.saveDraft(draft, userId, null);
        assertNotNull(draftId);

        // 草稿不显示在列表中
        postService.publishDraft(draftId, userId);

        PostDetailVO detail = postService.getPostDetail(draftId);
        assertNotNull(detail);
    }
}
