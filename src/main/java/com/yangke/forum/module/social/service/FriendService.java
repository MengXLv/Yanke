package com.yangke.forum.module.social.service;

import com.yangke.forum.common.PageResult;
import com.yangke.forum.module.auth.dto.UserVO;

import java.util.List;

public interface FriendService {

    /** 好友 = 互相关注，取关注列表与粉丝列表的交集 */
    List<Long> getFriendIds(Long userId);

    /** 好友列表（含用户信息） */
    PageResult<UserVO> getFriends(Long userId, int page, int size);

    /** 判断是否为好友 */
    boolean isFriend(Long userId1, Long userId2);

    /** 好友数量 */
    long getFriendCount(Long userId);
}
