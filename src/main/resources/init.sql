-- ============================================================
-- 杨柯社区论坛系统 - 数据库初始化脚本
-- ============================================================

CREATE DATABASE IF NOT EXISTS yangke_forum
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE yangke_forum;

-- ==================== 用户表 ====================
CREATE TABLE IF NOT EXISTS t_user (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(20)     NOT NULL UNIQUE COMMENT '用户名',
    email           VARCHAR(100)    NOT NULL UNIQUE COMMENT '邮箱',
    password        VARCHAR(64)     NOT NULL COMMENT 'MD5+盐值加密密码',
    salt            VARCHAR(32)     NOT NULL COMMENT '随机盐值',
    avatar          VARCHAR(255)    DEFAULT NULL COMMENT '头像URL',
    bio             VARCHAR(200)    DEFAULT NULL COMMENT '个人简介',
    status          TINYINT         DEFAULT 0 COMMENT '0-未激活 1-已激活 2-封禁',
    role            VARCHAR(20)     DEFAULT 'user' COMMENT 'user/moderator/admin',
    activation_code VARCHAR(64)    DEFAULT NULL COMMENT '邮件激活码',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_email (email),
    INDEX idx_username (username),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ==================== 帖子表 ====================
CREATE TABLE IF NOT EXISTS t_post (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL COMMENT '作者ID',
    category_id     BIGINT          DEFAULT NULL COMMENT '分类ID',
    title           VARCHAR(100)    NOT NULL COMMENT '标题',
    content         TEXT            NOT NULL COMMENT '内容(Markdown)',
    status          TINYINT         DEFAULT 1 COMMENT '0-草稿 1-发布 2-审核中 3-屏蔽',
    view_count      INT             DEFAULT 0 COMMENT '浏览量',
    like_count      INT             DEFAULT 0 COMMENT '点赞数',
    comment_count   INT             DEFAULT 0 COMMENT '评论数',
    is_hot          TINYINT         DEFAULT 0 COMMENT '是否热帖',
    is_top          TINYINT         DEFAULT 0 COMMENT '是否置顶',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_user_id (user_id),
    INDEX idx_category (category_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    INDEX idx_hot (is_hot, like_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子表';

-- ==================== 评论表 ====================
CREATE TABLE IF NOT EXISTS t_comment (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    post_id         BIGINT          NOT NULL COMMENT '帖子ID',
    user_id         BIGINT          NOT NULL COMMENT '评论者ID',
    parent_id       BIGINT          DEFAULT 0 COMMENT '父评论ID(0=一级评论)',
    reply_to_user_id BIGINT         DEFAULT NULL COMMENT '被回复用户ID',
    content         TEXT            NOT NULL COMMENT '评论内容',
    like_count      INT             DEFAULT 0 COMMENT '点赞数',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT         DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_post_id (post_id),
    INDEX idx_user_id (user_id),
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- ==================== 通知表 ====================
CREATE TABLE IF NOT EXISTS t_notification (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    sender_id       BIGINT          DEFAULT 0 COMMENT '发送者ID(0=系统)',
    receiver_id     BIGINT          NOT NULL COMMENT '接收者ID',
    type            TINYINT         NOT NULL COMMENT '1-点赞 2-评论 3-关注 4-系统',
    content         VARCHAR(500)    NOT NULL COMMENT '通知内容',
    target_id       BIGINT          DEFAULT NULL COMMENT '关联目标ID',
    is_read         TINYINT         DEFAULT 0 COMMENT '0-未读 1-已读',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_receiver (receiver_id, is_read),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知表';

-- ==================== 私信表 ====================
CREATE TABLE IF NOT EXISTS t_message (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    sender_id       BIGINT          NOT NULL COMMENT '发送者ID',
    receiver_id     BIGINT          NOT NULL COMMENT '接收者ID',
    content         VARCHAR(2000)   NOT NULL COMMENT '消息内容',
    is_read         TINYINT         DEFAULT 0 COMMENT '0-未读 1-已读',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sender (sender_id),
    INDEX idx_receiver (receiver_id, is_read),
    INDEX idx_dialog (sender_id, receiver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信表';

-- ==================== 积分表 ====================
CREATE TABLE IF NOT EXISTS t_user_points (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL UNIQUE COMMENT '用户ID',
    total_points    INT             DEFAULT 0 COMMENT '总积分',
    last_checkin    DATE            DEFAULT NULL COMMENT '上次签到日期',
    today_likes     INT             DEFAULT 0 COMMENT '今日点赞次数',
    today_comments  INT             DEFAULT 0 COMMENT '今日评论次数',
    stat_date       DATE            DEFAULT NULL COMMENT '每日统计重置日期',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_total_points (total_points)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分表';

-- ==================== 积分流水表 ====================
CREATE TABLE IF NOT EXISTS t_points_record (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT          NOT NULL COMMENT '用户ID',
    points          INT             NOT NULL COMMENT '变动积分(正=获得,负=消费)',
    reason          VARCHAR(20)     NOT NULL COMMENT '原因: register/checkin/like/comment/post/redeem/seckill',
    related_id      BIGINT          DEFAULT NULL COMMENT '关联ID',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_reason (user_id, reason),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表';

-- ==================== 积分商城商品表 ====================
CREATE TABLE IF NOT EXISTS t_shop_item (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(50)     NOT NULL COMMENT '商品名称',
    description     VARCHAR(500)    DEFAULT NULL COMMENT '商品描述',
    price           INT             NOT NULL COMMENT '所需积分',
    stock           INT             DEFAULT -1 COMMENT '库存(-1=无限)',
    sold            INT             DEFAULT 0 COMMENT '已售数量',
    status          TINYINT         DEFAULT 1 COMMENT '0-下架 1-上架 2-秒杀中',
    seckill_start   DATETIME        DEFAULT NULL COMMENT '秒杀开始时间',
    seckill_end     DATETIME        DEFAULT NULL COMMENT '秒杀结束时间',
    seckill_price   INT             DEFAULT NULL COMMENT '秒杀价',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分商城商品表';

-- 初始化商城商品
INSERT INTO t_shop_item (name, description, price, stock, status) VALUES
('去广告特权(7天)', '享受7天无广告浏览体验', 50, -1, 1),
('昵称修改卡', '修改一次昵称的机会', 20, -1, 1),
('帖子置顶(24h)', '让你的帖子在首页置顶24小时', 100, -1, 1),
('头像框(30天)', '获得专属头像框装饰30天', 80, 50, 1),
('签名变色卡', '将个人签名变为炫彩颜色', 30, 100, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ==================== 分类表 ====================
CREATE TABLE IF NOT EXISTS t_category (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(20)     NOT NULL COMMENT '分类名称',
    description     VARCHAR(100)    DEFAULT NULL COMMENT '分类描述',
    sort_order      INT             DEFAULT 0 COMMENT '排序',
    status          TINYINT         DEFAULT 1 COMMENT '0-禁用 1-启用',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子分类表';

-- 初始化默认分类
INSERT INTO t_category (name, description, sort_order, status) VALUES
('综合讨论', '各类话题自由交流', 1, 1),
('技术交流', '编程与技术相关讨论', 2, 1),
('生活日常', '分享日常生活趣事', 3, 1),
('游戏动漫', '游戏动漫爱好者的乐园', 4, 1),
('资源分享', '优质资源互通有无', 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- ==================== 举报表 ====================
CREATE TABLE IF NOT EXISTS t_report (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    reporter_id     BIGINT          NOT NULL COMMENT '举报人ID',
    target_id       BIGINT          NOT NULL COMMENT '被举报目标ID',
    target_type     TINYINT         NOT NULL COMMENT '1-帖子 2-评论',
    reason          VARCHAR(500)    NOT NULL COMMENT '举报原因',
    status          TINYINT         DEFAULT 0 COMMENT '0-待处理 1-已处理 2-驳回',
    handler_id      BIGINT          DEFAULT NULL COMMENT '处理人ID',
    handler_note    VARCHAR(500)    DEFAULT NULL COMMENT '处理备注',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_target (target_id, target_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户举报表';

-- ==================== 统计数据持久化表 ====================
CREATE TABLE IF NOT EXISTS t_statistics (
    id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    stat_date       DATE            NOT NULL UNIQUE COMMENT '统计日期',
    uv              BIGINT          DEFAULT 0 COMMENT '独立访客数(HyperLogLog估算)',
    dau             BIGINT          DEFAULT 0 COMMENT '日活用户数(Bitmap精确)',
    new_users       INT             DEFAULT 0 COMMENT '新增用户数',
    new_posts       INT             DEFAULT 0 COMMENT '新增帖子数',
    create_time     DATETIME        DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据统计表';
