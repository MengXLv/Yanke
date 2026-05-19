package com.yangke.forum.module.points.service.impl;

import com.yangke.forum.common.BusinessException;
import com.yangke.forum.module.points.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * 秒杀防超卖：Redis Lua 原子扣减 + 异步同步 MySQL
 *
 * 核心流程：
 * 1. 秒杀开始前，将库存加载到 Redis key: seckill:stock:{itemId}
 * 2. 下单时执行 Lua 脚本：原子检查库存 > 0 → 扣减 1 → 返回成功
 * 3. 扣减成功后，异步更新 MySQL（sold+1）
 * 4. 用户积分扣减在业务层单独处理
 */
@Slf4j
@Service
public class SeckillService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopItemMapper shopItemMapper;

    @Resource
    private PointsMapper pointsMapper;

    @Resource
    private PointsRecordMapper recordMapper;

    private static final String STOCK_KEY_PREFIX = "seckill:stock:";

    /**
     * Lua 脚本：原子检查 + 扣减库存
     * KEYS[1] = stock key
     * 返回：1=扣减成功, 0=库存不足, -1=key不存在
     */
    private static final String LUA_DEDUCT_STOCK =
            "local stock = redis.call('get', KEYS[1]) " +
            "if stock == false then return -1 end " +
            "if tonumber(stock) <= 0 then return 0 end " +
            "redis.call('decr', KEYS[1]) " +
            "return 1";

    private final DefaultRedisScript<Long> deductScript;

    public SeckillService() {
        deductScript = new DefaultRedisScript<>();
        deductScript.setScriptText(LUA_DEDUCT_STOCK);
        deductScript.setResultType(Long.class);
    }

    /**
     * 秒杀扣减库存（原子操作）
     * @return true=成功, false=库存不足
     */
    public boolean deductStock(Long itemId) {
        String key = STOCK_KEY_PREFIX + itemId;
        Long result = stringRedisTemplate.execute(
                deductScript,
                Collections.singletonList(key)
        );
        if (result == null || result == -1) {
            // Key 不存在 → 尝试从 MySQL 加载
            initStockToRedis(itemId);
            result = stringRedisTemplate.execute(deductScript, Collections.singletonList(key));
        }
        if (result == null) return false;
        if (result == 0) return false;

        // 异步更新 MySQL（简化：同步更新）
        try {
            shopItemMapper.addSold(itemId, 1);
        } catch (Exception e) {
            log.error("Failed to sync sold count for item {}", itemId, e);
        }
        return true;
    }

    /**
     * 将商品库存加载到 Redis
     * 无限库存（stock=-1）设为一个大值
     */
    public void initStockToRedis(Long itemId) {
        ShopItem item = shopItemMapper.selectById(itemId);
        if (item == null || item.getStatus() == 0) return;
        int redisStock = item.getStock() < 0 ? 999999 : Math.max(0, item.getStock() - item.getSold());
        String key = STOCK_KEY_PREFIX + itemId;
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(redisStock));
    }

    /**
     * 秒杀兑换：先校验积分 → 扣库存 → 扣积分 → 记录流水
     */
    public void doSeckill(Long userId, Long itemId) {
        ShopItem item = shopItemMapper.selectById(itemId);
        if (item == null || item.getStatus() == 0) throw new BusinessException(400, "商品不存在或已下架");
        if (item.getStatus() != 2) throw new BusinessException(400, "该商品未开启秒杀");

        int price = item.getSeckillPrice() != null ? item.getSeckillPrice() : item.getPrice();

        // 1. 先校验积分（避免积分不足时库存已被扣减）
        UserPoints up = pointsMapper.selectByUserId(userId);
        if (up == null || up.getTotalPoints() < price) {
            throw new BusinessException(400, "积分不足，当前积分：" + (up != null ? up.getTotalPoints() : 0) + "，需要：" + price);
        }

        // 2. 扣库存（Redis 原子操作，防超卖）
        if (!deductStock(itemId)) {
            throw new BusinessException(400, "手慢了，已抢光");
        }

        // 3. 扣积分（此处不会变负数，因为第1步已校验）
        pointsMapper.addPoints(userId, -price);

        // 4. 记录流水
        PointsRecord r = new PointsRecord();
        r.setUserId(userId);
        r.setPoints(-price);
        r.setReason("seckill");
        r.setRelatedId(itemId);
        recordMapper.insert(r);
    }

    /** 查询商品信息 */
    public ShopItem getItem(Long itemId) {
        return shopItemMapper.selectById(itemId);
    }

    /**
     * 查询 Redis 中剩余库存
     */
    public int getRedisStock(Long itemId) {
        String s = stringRedisTemplate.opsForValue().get(STOCK_KEY_PREFIX + itemId);
        return s != null ? Integer.parseInt(s) : -1;
    }
}
