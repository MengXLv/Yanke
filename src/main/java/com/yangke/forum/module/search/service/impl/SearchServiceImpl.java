package com.yangke.forum.module.search.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.content.dto.PostVO;
import com.yangke.forum.module.content.entity.Comment;
import com.yangke.forum.module.content.entity.Post;
import com.yangke.forum.module.search.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

/**
 * Elasticsearch 全文检索服务
 * - 索引名: forum_post / forum_comment
 * - 分词器: IK (ik_max_word 索引, ik_smart 搜索)
 * - 查询策略: Bool Query 多字段加权
 *   - 标题匹配权重 3.0
 *   - 内容匹配权重 1.0
 *   - 用户名匹配权重 2.0
 * - 高亮: 匹配片段 <em>标签包裹
 */
@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    private static final String INDEX_POST = "forum_post";
    private static final String INDEX_COMMENT = "forum_comment";

    @Resource
    private RestHighLevelClient esClient;

    @Resource
    private UserMapper userMapper;

    // ==================== 索引管理 ====================

    @Override
    public void indexPost(Post post) {
        indexPosts(Collections.singletonList(post));
    }

    @Override
    public void indexPosts(Collection<Post> posts) {
        if (posts.isEmpty()) return;
        // 批量加载用户名
        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(new ArrayList<>(userIds)).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        BulkRequest bulk = new BulkRequest();
        for (Post post : posts) {
            IndexRequest request = new IndexRequest(INDEX_POST)
                    .id(post.getId().toString())
                    .source(buildPostDocWithUser(post, userMap), XContentType.JSON);
            bulk.add(request);
        }

        try {
            esClient.bulk(bulk, RequestOptions.DEFAULT);
            log.debug("Bulk indexed {} posts", posts.size());
        } catch (IOException e) {
            log.error("Failed to bulk index posts", e);
        }
    }

    @Override
    public void updatePost(Post post) {
        try {
            UpdateRequest request = new UpdateRequest(INDEX_POST, post.getId().toString())
                    .doc(buildPostDoc(post), XContentType.JSON);
            esClient.update(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("Failed to update post index: {}", post.getId(), e);
        }
    }

    @Override
    public void deletePost(Long postId) {
        try {
            esClient.delete(new DeleteRequest(INDEX_POST, postId.toString()),
                    RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("Failed to delete post index: {}", postId, e);
        }
    }

    @Override
    public void indexComment(Comment comment) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", comment.getId());
            doc.put("postId", comment.getPostId());
            doc.put("userId", comment.getUserId());
            doc.put("content", comment.getContent());
            doc.put("createTime", comment.getCreateTime() != null
                    ? comment.getCreateTime().toString() : null);

            IndexRequest request = new IndexRequest(INDEX_COMMENT)
                    .id(comment.getId().toString())
                    .source(doc, XContentType.JSON);
            esClient.index(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            log.error("Failed to index comment: {}", comment.getId(), e);
        }
    }

    // ==================== 全文检索 ====================

    @Override
    public PageResult<PostVO> searchPosts(String keyword, int page, int size) {
        SearchRequest searchRequest = new SearchRequest(INDEX_POST);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // Bool Query：多字段加权查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery("title", keyword).boost(3.0f))
                .should(QueryBuilders.matchQuery("content", keyword).boost(1.0f))
                .should(QueryBuilders.matchQuery("username", keyword).boost(2.0f))
                .minimumShouldMatch(1);

        sourceBuilder.query(boolQuery);
        sourceBuilder.from((page - 1) * size);
        sourceBuilder.size(size);

        // 高亮配置
        HighlightBuilder highlightBuilder = new HighlightBuilder()
                .field(new HighlightBuilder.Field("title")
                        .preTags("<em>").postTags("</em>")
                        .fragmentSize(100).numOfFragments(1))
                .field(new HighlightBuilder.Field("content")
                        .preTags("<em>").postTags("</em>")
                        .fragmentSize(200).numOfFragments(2));
        sourceBuilder.highlighter(highlightBuilder);

        searchRequest.source(sourceBuilder);

        try {
            SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
            List<PostVO> records = Arrays.stream(response.getHits().getHits())
                    .map(this::hitToPostVO)
                    .collect(Collectors.toList());

            // 批量填充用户名
            List<Long> userIds = records.stream()
                    .map(PostVO::getUserId).filter(Objects::nonNull).distinct()
                    .collect(Collectors.toList());
            if (!userIds.isEmpty()) {
                Map<Long, String> userMap = userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
                records.forEach(r -> {
                    if (r.getUserId() != null) {
                        r.setUsername(userMap.getOrDefault(r.getUserId(), null));
                    }
                });
            }

            return new PageResult<>(response.getHits().getTotalHits().value, page, size, records);
        } catch (IOException e) {
            log.error("ES search failed for keyword: {}", keyword, e);
            return new PageResult<>(0, page, size, Collections.emptyList());
        }
    }

    @Override
    public PageResult<Comment> searchComments(String keyword, int page, int size) {
        SearchRequest searchRequest = new SearchRequest(INDEX_COMMENT);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.matchQuery("content", keyword));

        sourceBuilder.query(boolQuery);
        sourceBuilder.from((page - 1) * size);
        sourceBuilder.size(size);

        // 高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder()
                .field(new HighlightBuilder.Field("content")
                        .preTags("<em>").postTags("</em>")
                        .fragmentSize(150).numOfFragments(2));
        sourceBuilder.highlighter(highlightBuilder);

        searchRequest.source(sourceBuilder);

        try {
            SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
            List<Comment> records = Arrays.stream(response.getHits().getHits())
                    .map(hit -> {
                        Map<String, Object> source = hit.getSourceAsMap();
                        Comment c = new Comment();
                        c.setId(Long.valueOf(hit.getId()));
                        c.setPostId(Long.valueOf(source.get("postId").toString()));
                        c.setUserId(Long.valueOf(source.get("userId").toString()));
                        c.setContent((String) source.get("content"));
                        return c;
                    })
                    .collect(Collectors.toList());

            return new PageResult<>(response.getHits().getTotalHits().value, page, size, records);
        } catch (IOException e) {
            log.error("ES comment search failed", e);
            return new PageResult<>(0, page, size, Collections.emptyList());
        }
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> buildPostDoc(Post post) {
        Map<Long, User> userMap = userMapper.selectBatchIds(
                Collections.singletonList(post.getUserId())).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        return buildPostDocWithUser(post, userMap);
    }

    private Map<String, Object> buildPostDocWithUser(Post post, Map<Long, User> userMap) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", post.getId());
        doc.put("userId", post.getUserId());
        doc.put("title", post.getTitle());
        doc.put("content", post.getContent());
        doc.put("categoryId", post.getCategoryId());
        doc.put("status", post.getStatus());
        doc.put("viewCount", post.getViewCount());
        doc.put("likeCount", post.getLikeCount());
        doc.put("commentCount", post.getCommentCount());
        doc.put("createTime", post.getCreateTime() != null
                ? post.getCreateTime().toString() : null);
        User author = userMap.get(post.getUserId());
        doc.put("username", author != null ? author.getUsername() : null);
        return doc;
    }

    private PostVO hitToPostVO(SearchHit hit) {
        Map<String, Object> source = hit.getSourceAsMap();
        PostVO vo = new PostVO();
        vo.setId(Long.valueOf(hit.getId()));
        vo.setUserId(source.get("userId") != null
                ? Long.valueOf(source.get("userId").toString()) : null);
        vo.setViewCount(source.get("viewCount") != null
                ? Integer.valueOf(source.get("viewCount").toString()) : 0);
        vo.setLikeCount(source.get("likeCount") != null
                ? Integer.valueOf(source.get("likeCount").toString()) : 0);
        vo.setCommentCount(source.get("commentCount") != null
                ? Integer.valueOf(source.get("commentCount").toString()) : 0);

        // 高亮标题优先
        HighlightField titleHighlight = hit.getHighlightFields().get("title");
        if (titleHighlight != null && titleHighlight.getFragments().length > 0) {
            vo.setTitle(titleHighlight.getFragments()[0].string());
        } else {
            vo.setTitle((String) source.get("title"));
        }

        // 高亮摘要
        HighlightField contentHighlight = hit.getHighlightFields().get("content");
        if (contentHighlight != null && contentHighlight.getFragments().length > 0) {
            StringBuilder sb = new StringBuilder();
            for (var fragment : contentHighlight.getFragments()) {
                sb.append(fragment.string()).append("...");
            }
            vo.setSummary(sb.toString());
        } else {
            String content = (String) source.get("content");
            vo.setSummary(content != null && content.length() > 200
                    ? content.substring(0, 200) + "..." : content);
        }

        return vo;
    }
}
