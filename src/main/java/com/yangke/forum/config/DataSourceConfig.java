package com.yangke.forum.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 读写分离数据源配置（生产环境启用）
 *
 * 启用方式：application.yml 中设置 forum.read-write-split=true
 *
 * 面试要点：
 * - AbstractRoutingDataSource 动态路由（determineCurrentLookupKey + ThreadLocal）
 * - DataSourceAspect 检测 @Transactional(readOnly=true) → 自动路由到从库
 * - 主库 INSERT/UPDATE/DELETE，从库 SELECT（读多写少场景）
 * - @Primary 确保 MyBatis-Plus / 事务管理器使用路由数据源
 */
@Configuration
@ConditionalOnProperty(name = "forum.read-write-split", havingValue = "true")
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource masterDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.slave.hikari")
    public HikariDataSource slaveDataSource(Environment env) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(env.getProperty("spring.datasource.slave.url"));
        ds.setUsername(env.getProperty("spring.datasource.slave.username"));
        ds.setPassword(env.getProperty("spring.datasource.slave.password"));
        ds.setDriverClassName(env.getProperty("spring.datasource.slave.driver-class-name"));
        return ds;
    }

    @Bean
    @Primary
    public DataSource routingDataSource(DataSource masterDataSource, DataSource slaveDataSource) {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDataSource);
        targetDataSources.put("slave", slaveDataSource);

        AbstractRoutingDataSource routing = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return DataSourceContext.get();
            }
        };
        routing.setDefaultTargetDataSource(masterDataSource);
        routing.setTargetDataSources(targetDataSources);
        routing.afterPropertiesSet();
        return routing;
    }
}
