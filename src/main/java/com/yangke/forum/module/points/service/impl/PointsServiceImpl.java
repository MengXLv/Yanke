package com.yangke.forum.module.points.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangke.forum.common.BusinessException;
import com.yangke.forum.module.auth.entity.User;
import com.yangke.forum.module.auth.mapper.UserMapper;
import com.yangke.forum.module.points.entity.*;
import com.yangke.forum.module.points.service.PointsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PointsServiceImpl implements PointsService {

    @Resource private PointsMapper pointsMapper;
    @Resource private PointsRecordMapper recordMapper;
    @Resource private ShopItemMapper shopItemMapper;
    @Resource private UserMapper userMapper;

    @Override
    public void initPoints(Long userId) {
        UserPoints up = pointsMapper.selectByUserId(userId);
        if (up == null) {
            up = new UserPoints();
            up.setUserId(userId);
            up.setTotalPoints(0);
            up.setTodayLikes(0);
            up.setTodayComments(0);
            up.setStatDate(LocalDate.now());
            pointsMapper.insert(up);
        }
    }

    @Override
    @Transactional
    public void awardRegister(Long userId) {
        initPoints(userId);
        pointsMapper.addPoints(userId, 10);
        addRecord(userId, 10, "register", null);
    }

    @Override
    @Transactional
    public int dailyCheckin(Long userId) {
        UserPoints up = getOrCreate(userId);
        if (up.getLastCheckin() != null && up.getLastCheckin().equals(LocalDate.now())) {
            throw new BusinessException(400, "今天已经签到过了");
        }
        up.setLastCheckin(LocalDate.now());
        pointsMapper.updateById(up);
        pointsMapper.addPoints(userId, 1);
        addRecord(userId, 1, "checkin", null);
        return up.getTotalPoints() + 1;
    }

    @Override
    @Transactional
    public boolean awardLike(Long userId) {
        UserPoints up = getOrCreate(userId);
        if (!up.getStatDate().equals(LocalDate.now())) resetDaily(up);
        if (up.getTodayLikes() >= 5) return false;
        up.setTodayLikes(up.getTodayLikes() + 1);
        pointsMapper.updateById(up);
        pointsMapper.addPoints(userId, 1);
        addRecord(userId, 1, "like", null);
        return true;
    }

    @Override
    @Transactional
    public boolean awardComment(Long userId) {
        UserPoints up = getOrCreate(userId);
        if (!up.getStatDate().equals(LocalDate.now())) resetDaily(up);
        if (up.getTodayComments() >= 10) return false;
        up.setTodayComments(up.getTodayComments() + 1);
        pointsMapper.updateById(up);
        pointsMapper.addPoints(userId, 1);
        addRecord(userId, 1, "comment", null);
        return true;
    }

    @Override
    @Transactional
    public void awardPost(Long userId) {
        getOrCreate(userId);
        pointsMapper.addPoints(userId, 2);
        addRecord(userId, 2, "post", null);
    }

    @Override
    public UserPoints getPoints(Long userId) {
        return getOrCreate(userId);
    }

    @Override
    @Transactional
    public void redeem(Long userId, Long itemId) {
        ShopItem item = shopItemMapper.selectById(itemId);
        if (item == null || item.getStatus() == 0) throw new BusinessException(400, "商品不存在或已下架");
        if (item.getStock() > 0 && item.getSold() >= item.getStock()) throw new BusinessException(400, "库存不足");
        int price = item.getStatus() == 2 && item.getSeckillPrice() != null ? item.getSeckillPrice() : item.getPrice();
        UserPoints up = getOrCreate(userId);
        if (up.getTotalPoints() < price) throw new BusinessException(400, "积分不足");
        pointsMapper.addPoints(userId, -price);
        addRecord(userId, -price, "redeem", itemId);
        item.setSold(item.getSold() + 1);
        shopItemMapper.updateById(item);
    }

    @Override
    public List<ShopItem> listShop() {
        return shopItemMapper.selectList(Wrappers.<ShopItem>lambdaQuery()
                .eq(ShopItem::getStatus, 1)
                .orderByAsc(ShopItem::getPrice));
    }

    @Override
    public List<ShopItem> listSeckill() {
        // 返回所有秒杀商品（含即将开始和进行中），前端按时间区分展示
        return shopItemMapper.selectList(Wrappers.<ShopItem>lambdaQuery()
                .eq(ShopItem::getStatus, 2)
                .ge(ShopItem::getSeckillEnd, LocalDateTime.now().minusHours(1))
                .orderByAsc(ShopItem::getSeckillStart));
    }

    @Override
    public List<LocalDate> getCheckinHistory(Long userId) {
        try {
            return recordMapper.findCheckinDates(userId, LocalDateTime.now().minusDays(365));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> getTopUsers(int limit) {
        LambdaQueryWrapper<UserPoints> wrapper = Wrappers.<UserPoints>lambdaQuery()
                .orderByDesc(UserPoints::getTotalPoints);
        List<UserPoints> list = pointsMapper.selectPage(
                new Page<>(1, limit), wrapper).getRecords();
        Map<Long, User> userMap = new HashMap<>();
        if (!list.isEmpty()) {
            userMap = userMapper.selectBatchIds(
                    list.stream().map(UserPoints::getUserId).collect(Collectors.toList()))
                    .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserPoints up : list) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("userId", up.getUserId());
            User u = userMap.get(up.getUserId());
            entry.put("username", u != null ? u.getUsername() : null);
            entry.put("points", up.getTotalPoints());
            result.add(entry);
        }
        return result;
    }

    private UserPoints getOrCreate(Long userId) {
        UserPoints up = pointsMapper.selectByUserId(userId);
        if (up == null) { initPoints(userId); up = pointsMapper.selectByUserId(userId); }
        return up;
    }

    private void resetDaily(UserPoints up) {
        up.setTodayLikes(0);
        up.setTodayComments(0);
        up.setStatDate(LocalDate.now());
    }

    private void addRecord(Long userId, int points, String reason, Long relatedId) {
        PointsRecord r = new PointsRecord();
        r.setUserId(userId);
        r.setPoints(points);
        r.setReason(reason);
        r.setRelatedId(relatedId);
        recordMapper.insert(r);
    }
}
