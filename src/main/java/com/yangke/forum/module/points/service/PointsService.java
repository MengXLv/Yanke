package com.yangke.forum.module.points.service;

import com.yangke.forum.module.points.entity.ShopItem;
import com.yangke.forum.module.points.entity.UserPoints;
import java.util.Map;

public interface PointsService {

    /** 初始化用户积分记录（注册时调用） */
    void initPoints(Long userId);

    /** 注册奖励 +10 */
    void awardRegister(Long userId);

    /** 每日签到 +1（同一天只能签一次） */
    int dailyCheckin(Long userId);

    /** 点赞奖励（前5次有效） */
    boolean awardLike(Long userId);

    /** 评论奖励（前10次有效） */
    boolean awardComment(Long userId);

    /** 发帖奖励 +2 */
    void awardPost(Long userId);

    /** 获取用户积分信息 */
    UserPoints getPoints(Long userId);

    /** 积分兑换商品 */
    void redeem(Long userId, Long itemId);

    /** 普通商品列表 */
    java.util.List<ShopItem> listShop();

    /** 秒杀列表 */
    java.util.List<ShopItem> listSeckill();

    /** 积分排行 Top N */
    java.util.List<Map<String, Object>> getTopUsers(int limit);

    /** 过去365天签到日期 */
    java.util.List<java.time.LocalDate> getCheckinHistory(Long userId);
}
