package com.yangke.forum.module.points.service;

import com.yangke.forum.BaseTest;
import com.yangke.forum.common.BusinessException;
import com.yangke.forum.module.points.entity.*;
import com.yangke.forum.module.points.service.impl.SeckillService;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class SeckillServiceTest extends BaseTest {

    @Resource private SeckillService seckillService;
    @Resource private ShopItemMapper shopItemMapper;
    @Resource private PointsMapper pointsMapper;
    @Resource private StringRedisTemplate stringRedisTemplate;

    private Long itemId;
    private Long userId = 999L;

    @BeforeEach
    void setUp() {
        // 创建秒杀商品
        ShopItem item = new ShopItem();
        item.setName("测试商品");
        item.setPrice(100);
        item.setSeckillPrice(10);
        item.setStock(3);
        item.setSold(0);
        item.setStatus(2); // 秒杀
        item.setSeckillStart(LocalDateTime.now().minusHours(1));
        item.setSeckillEnd(LocalDateTime.now().plusHours(1));
        shopItemMapper.insert(item);
        itemId = item.getId();

        // 初始化积分
        UserPoints up = new UserPoints();
        up.setUserId(userId);
        up.setTotalPoints(100);
        pointsMapper.insert(up);

        // 加载库存到Redis
        seckillService.initStockToRedis(itemId);
    }

    @Test
    void insufficientPointsBeforeStockDeduct() {
        // 只给5积分，秒杀需要10
        pointsMapper.addPoints(userId, -95); // total now 5
        stringRedisTemplate.opsForValue().set("seckill:stock:" + itemId, "3");

        assertThrows(BusinessException.class, () -> seckillService.doSeckill(userId, itemId),
                "积分不足时应抛出异常");

        // 库存不应被扣减
        String stock = stringRedisTemplate.opsForValue().get("seckill:stock:" + itemId);
        assertEquals("3", stock, "积分不足时库存不应被扣减");
    }

    @Test
    void successWhenSufficientPoints() {
        stringRedisTemplate.opsForValue().set("seckill:stock:" + itemId, "3");

        seckillService.doSeckill(userId, itemId);

        String stock = stringRedisTemplate.opsForValue().get("seckill:stock:" + itemId);
        assertEquals("2", stock, "秒杀成功后库存应减1");
    }

    @Test
    void outOfStock() {
        stringRedisTemplate.opsForValue().set("seckill:stock:" + itemId, "0");

        assertThrows(BusinessException.class, () -> seckillService.doSeckill(userId, itemId),
                "库存不足时应抛出异常");
    }

    @Test
    void notSeckillItem() {
        ShopItem normal = new ShopItem();
        normal.setName("普通商品");
        normal.setPrice(50);
        normal.setStatus(1);
        shopItemMapper.insert(normal);

        assertThrows(BusinessException.class, () -> seckillService.doSeckill(userId, normal.getId()));
    }
}
