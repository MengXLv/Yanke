package com.yangke.forum.module.points.controller;

import com.yangke.forum.common.IdentifyBy;
import com.yangke.forum.common.RateLimit;
import com.yangke.forum.common.RequestContext;
import com.yangke.forum.common.Result;
import com.yangke.forum.module.points.entity.ShopItem;
import com.yangke.forum.module.points.entity.UserPoints;
import com.yangke.forum.module.points.service.PointsService;
import com.yangke.forum.module.points.service.impl.SeckillService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/points")
public class PointsController {

    @Resource private PointsService pointsService;
    @Resource private SeckillService seckillService;

    /** 获取我的积分 */
    @GetMapping("/me")
    public Result<UserPoints> myPoints(HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(pointsService.getPoints(userId));
    }

    /** 每日签到 */
    @RateLimit(prefix = "points:checkin", max = 2, window = 60, by = IdentifyBy.USER, message = "签到太频繁")
    @PostMapping("/checkin")
    public Result<Integer> checkin(HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(pointsService.dailyCheckin(userId));
    }

    /** 积分排行 */
    @GetMapping("/top")
    public Result<List<Map<String, Object>>> top(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(pointsService.getTopUsers(limit));
    }

    /** 兑换商品 */
    @RateLimit(prefix = "points:redeem", max = 5, window = 60, by = IdentifyBy.USER, message = "兑换太频繁，请稍后再试")
    @PostMapping("/redeem/{itemId}")
    public Result<Void> redeem(@PathVariable Long itemId, HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        // 秒杀商品走 Redis 原子扣库存
        ShopItem item = seckillService.getItem(itemId);
        if (item != null && item.getStatus() == 2) {
            seckillService.doSeckill(userId, itemId);
        } else {
            pointsService.redeem(userId, itemId);
        }
        return Result.ok();
    }

    /** 普通商品列表 */
    @GetMapping("/shop")
    public Result<List<ShopItem>> shop() {
        return Result.ok(pointsService.listShop());
    }

    /** 秒杀列表 */
    @GetMapping("/seckill")
    public Result<List<ShopItem>> seckill() {
        return Result.ok(pointsService.listSeckill());
    }

    /** 签到日历 */
    @GetMapping("/checkin-history")
    public Result<List<java.time.LocalDate>> checkinHistory(HttpServletRequest request) {
        Long userId = RequestContext.requireLogin(request);
        return Result.ok(pointsService.getCheckinHistory(userId));
    }
}
