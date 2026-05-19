package com.yangke.forum.module.search.service.impl;

import com.yangke.forum.module.content.entity.Post;
import com.yangke.forum.module.content.mapper.PostMapper;
import com.yangke.forum.module.search.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class EsIndexInitializer implements CommandLineRunner {

    private static final String INDEX_POST = "forum_post";
    private static final String INDEX_COMMENT = "forum_comment";

    @Resource
    private RestHighLevelClient esClient;

    @Resource
    private PostMapper postMapper;

    @Resource
    private SearchService searchService;

    @Override
    public void run(String... args) {
        createIndexIfAbsent(INDEX_POST);
        createIndexIfAbsent(INDEX_COMMENT);
        reindexAllPosts();
    }

    private void createIndexIfAbsent(String indexName) {
        try {
            boolean exists = esClient.indices()
                    .exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT);
            if (!exists) {
                esClient.indices().create(new CreateIndexRequest(indexName), RequestOptions.DEFAULT);
                log.info("ES index [{}] created", indexName);
            }
        } catch (Exception e) {
            log.warn("Failed to create ES index [{}]: {}", indexName, e.getMessage());
        }
    }

    private void reindexAllPosts() {
        try {
            List<Post> posts = postMapper.selectList(null);
            if (posts.isEmpty()) return;
            // 过滤未删除的帖子
            List<Post> activePosts = new ArrayList<>();
            for (Post post : posts) {
                if (post.getDeleted() == 0) activePosts.add(post);
            }
            if (activePosts.isEmpty()) return;
            // 批量索引（内部自动批量加载用户）
            int batchSize = 200;
            for (int i = 0; i < activePosts.size(); i += batchSize) {
                int end = Math.min(i + batchSize, activePosts.size());
                searchService.indexPosts(activePosts.subList(i, end));
            }
            log.info("Re-indexed {} posts from MySQL to ES", activePosts.size());
        } catch (Exception e) {
            log.warn("Failed to reindex posts: {}", e.getMessage());
        }
    }
}
