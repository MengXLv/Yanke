package com.yangke.forum.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.rest.uris}")
    private String uris;

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        String[] hosts = uris.split(",");
        HttpHost[] httpHosts = new HttpHost[hosts.length];
        for (int i = 0; i < hosts.length; i++) {
            String uri = hosts[i].trim().replace("http://", "").replace("https://", "");
            String[] parts = uri.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;
            httpHosts[i] = new HttpHost(host, port, "http");
        }
        return new RestHighLevelClient(RestClient.builder(httpHosts));
    }
}
