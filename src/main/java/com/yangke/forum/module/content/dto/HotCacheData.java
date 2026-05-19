package com.yangke.forum.module.content.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点缓存包装类：物理永不过期，靠 logicalExpireTime 判断逻辑过期
 *
 * 面试要点：逻辑过期 vs 物理过期
 * - 物理过期：设置 TTL，到期自动删除，请求发现无缓存 → 击穿 DB
 * - 逻辑过期：不设 TTL，数据永久保留但带过期时间戳，发现过期后返回旧值 + 异步刷新
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HotCacheData {
    private PostDetailVO data;
    private long logicalExpireTime; // epoch millis，到期后不删除，返回旧值异步刷新
}
