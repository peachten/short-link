-- =============================================================
-- 分布式短链接系统 · 数据库初始化脚本
-- 对应《开发文档》第 4 章 数据库设计
-- 建表顺序：t_user -> t_group -> t_link -> t_link_goto -> t_link_access_logs -> 6 张统计表
-- 字符集：utf8mb4 / utf8mb4_general_ci
-- =============================================================

CREATE DATABASE IF NOT EXISTS short_link DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE short_link;

-- ---------- 1. 用户表 ----------
CREATE TABLE t_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)  NOT NULL COMMENT '用户名',
    password      VARCHAR(100) NOT NULL COMMENT '密码（BCrypt）',
    real_name     VARCHAR(64)           DEFAULT NULL COMMENT '真实姓名',
    phone         VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    mail          VARCHAR(64)           DEFAULT NULL COMMENT '邮箱',
    deletion_time BIGINT                DEFAULT 0 COMMENT '注销时间戳',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    del_flag      TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标识 0正常 1删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB COMMENT ='用户表';

-- ---------- 2. 分组表 ----------
CREATE TABLE t_group (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    gid         VARCHAR(32)  NOT NULL COMMENT '分组标识',
    name        VARCHAR(64)  NOT NULL COMMENT '分组名称',
    username    VARCHAR(64)  NOT NULL COMMENT '所属用户',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    del_flag    TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标识 0正常 1删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gid (gid),
    KEY idx_username (username)
) ENGINE = InnoDB COMMENT ='短链分组表';

-- ---------- 3. 短链表 ----------
CREATE TABLE t_link (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    domain          VARCHAR(128)          DEFAULT NULL COMMENT '域名',
    short_uri       VARCHAR(16)           DEFAULT NULL COMMENT '短链后缀（Base62）',
    full_short_url  VARCHAR(128)          DEFAULT NULL COMMENT '完整短链',
    origin_url      VARCHAR(512)  NOT NULL COMMENT '原始链接',
    origin_url_hash CHAR(32)      NOT NULL COMMENT 'MD5(origin_url)，用于唯一索引',
    click_num       INT          NOT NULL DEFAULT 0 COMMENT '累计点击次数',
    gid             VARCHAR(32)  NOT NULL DEFAULT 'default' COMMENT '分组标识',
    enable_status   TINYINT      NOT NULL DEFAULT 0 COMMENT '0启用 1禁用',
    created_type    TINYINT      NOT NULL DEFAULT 0 COMMENT '0接口 1控制台',
    valid_date_type TINYINT      NOT NULL DEFAULT 0 COMMENT '有效期类型 0永久 1自定义',
    valid_date      DATETIME              DEFAULT NULL COMMENT '失效时间',
    description     VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    del_flag        TINYINT      NOT NULL DEFAULT 0 COMMENT '删除标识 0正常 1删除',
    PRIMARY KEY (id),
    -- 幂等核心：同一分组下同一长链只允许一条记录（MySQL 原生幂等兜底）
    UNIQUE KEY uk_gid_origin (gid, origin_url_hash),
    UNIQUE KEY uk_short_uri (gid, short_uri),
    KEY idx_create_time (create_time),
    KEY idx_full_short_url (full_short_url)
) ENGINE = InnoDB COMMENT ='短链表';

-- ---------- 4. 跳转表（高并发拆分预留，四周时间紧可先不启用）----------
-- 设计意图：把「写多读少的管理信息」和「读多写少的跳转信息」拆开，
-- 跳转只查这张小表，避免 t_link 宽表 + 二级索引的影响。面试可讲。
CREATE TABLE t_link_goto (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    full_short_url VARCHAR(128) NOT NULL COMMENT '完整短链',
    gid            VARCHAR(32)  NOT NULL COMMENT '分组标识',
    origin_url     VARCHAR(512) NOT NULL COMMENT '原始链接',
    PRIMARY KEY (id),
    UNIQUE KEY uk_full_short_url (full_short_url)
) ENGINE = InnoDB COMMENT ='短链跳转表';

-- ---------- 5. 访问日志明细表 ----------
CREATE TABLE t_link_access_logs (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    msg_id         VARCHAR(64)  NOT NULL COMMENT 'MQ 消息唯一 ID（消费幂等）',
    link_id        BIGINT UNSIGNED       DEFAULT NULL COMMENT '短链 ID',
    full_short_url VARCHAR(128) NOT NULL COMMENT '完整短链',
    gid            VARCHAR(32)           DEFAULT NULL COMMENT '分组标识',
    short_uri      VARCHAR(16)           DEFAULT NULL COMMENT '短链后缀',
    user           VARCHAR(64)           DEFAULT NULL COMMENT '访问者标识（uv 去重键）',
    ip             VARCHAR(64)           DEFAULT NULL COMMENT '访问 IP',
    browser        VARCHAR(64)           DEFAULT NULL COMMENT '浏览器',
    os             VARCHAR(64)           DEFAULT NULL COMMENT '操作系统',
    device_type    VARCHAR(32)           DEFAULT NULL COMMENT '设备类型',
    locale         VARCHAR(64)           DEFAULT NULL COMMENT '地区（按 IP 段/浏览器语言粗粒度）',
    access_time    DATETIME     NOT NULL COMMENT '访问时间',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_id (msg_id),
    KEY idx_full_short_url_time (full_short_url, access_time),
    KEY idx_gid_time (gid, access_time)
) ENGINE = InnoDB COMMENT ='访问日志明细表';
-- 注意：这是唯一会持续膨胀的表。第 3 周加一个定时任务清理 90 天前的数据。

-- ---------- 6. 短链访问统计表（按天）----------
CREATE TABLE t_link_access_stats (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    full_short_url VARCHAR(128) NOT NULL COMMENT '完整短链',
    date           DATE         NOT NULL COMMENT '统计日期',
    pv             INT          NOT NULL DEFAULT 0 COMMENT '访问量',
    uv             INT          NOT NULL DEFAULT 0 COMMENT '独立访客(HyperLogLog)',
    uip            INT          NOT NULL DEFAULT 0 COMMENT '独立 IP(HyperLogLog)',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_full_short_url_date (full_short_url, date)
) ENGINE = InnoDB COMMENT ='短链访问统计表';

-- ---------- 7. 24 小时访问分布 ----------
CREATE TABLE t_link_access_hour_stats (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    full_short_url VARCHAR(128) NOT NULL COMMENT '完整短链',
    date           DATE         NOT NULL COMMENT '统计日期',
    hour_00        INT NOT NULL DEFAULT 0 COMMENT '0 点访问量', hour_01 INT NOT NULL DEFAULT 0 COMMENT '1 点访问量',
    hour_02        INT NOT NULL DEFAULT 0 COMMENT '2 点访问量', hour_03 INT NOT NULL DEFAULT 0 COMMENT '3 点访问量',
    hour_04        INT NOT NULL DEFAULT 0 COMMENT '4 点访问量', hour_05 INT NOT NULL DEFAULT 0 COMMENT '5 点访问量',
    hour_06        INT NOT NULL DEFAULT 0 COMMENT '6 点访问量', hour_07 INT NOT NULL DEFAULT 0 COMMENT '7 点访问量',
    hour_08        INT NOT NULL DEFAULT 0 COMMENT '8 点访问量', hour_09 INT NOT NULL DEFAULT 0 COMMENT '9 点访问量',
    hour_10        INT NOT NULL DEFAULT 0 COMMENT '10 点访问量', hour_11 INT NOT NULL DEFAULT 0 COMMENT '11 点访问量',
    hour_12        INT NOT NULL DEFAULT 0 COMMENT '12 点访问量', hour_13 INT NOT NULL DEFAULT 0 COMMENT '13 点访问量',
    hour_14        INT NOT NULL DEFAULT 0 COMMENT '14 点访问量', hour_15 INT NOT NULL DEFAULT 0 COMMENT '15 点访问量',
    hour_16        INT NOT NULL DEFAULT 0 COMMENT '16 点访问量', hour_17 INT NOT NULL DEFAULT 0 COMMENT '17 点访问量',
    hour_18        INT NOT NULL DEFAULT 0 COMMENT '18 点访问量', hour_19 INT NOT NULL DEFAULT 0 COMMENT '19 点访问量',
    hour_20        INT NOT NULL DEFAULT 0 COMMENT '20 点访问量', hour_21 INT NOT NULL DEFAULT 0 COMMENT '21 点访问量',
    hour_22        INT NOT NULL DEFAULT 0 COMMENT '22 点访问量', hour_23 INT NOT NULL DEFAULT 0 COMMENT '23 点访问量',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_full_short_url_date (full_short_url, date)
) ENGINE = InnoDB COMMENT ='24 小时访问分布';

-- ---------- 8. 浏览器维度统计 ----------
CREATE TABLE t_link_browser_stats (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    full_short_url VARCHAR(128) NOT NULL COMMENT '完整短链',
    date           DATE         NOT NULL COMMENT '统计日期',
    browser        VARCHAR(64)  NOT NULL COMMENT '浏览器名',
    cnt            INT          NOT NULL DEFAULT 0 COMMENT '访问次数',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_url_date_browser (full_short_url, date, browser)
) ENGINE = InnoDB COMMENT ='浏览器维度统计';

-- ---------- 9. 操作系统维度统计 ----------
CREATE TABLE t_link_os_stats (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    full_short_url VARCHAR(128) NOT NULL COMMENT '完整短链',
    date           DATE         NOT NULL COMMENT '统计日期',
    os             VARCHAR(64)  NOT NULL COMMENT '操作系统名',
    cnt            INT          NOT NULL DEFAULT 0 COMMENT '访问次数',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_url_date_os (full_short_url, date, os)
) ENGINE = InnoDB COMMENT ='操作系统维度统计';

-- ---------- 10. 设备类型维度统计 ----------
CREATE TABLE t_link_device_stats (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    full_short_url VARCHAR(128) NOT NULL COMMENT '完整短链',
    date           DATE         NOT NULL COMMENT '统计日期',
    device         VARCHAR(64)  NOT NULL COMMENT '设备类型',
    cnt            INT          NOT NULL DEFAULT 0 COMMENT '访问次数',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_url_date_device (full_short_url, date, device)
) ENGINE = InnoDB COMMENT ='设备类型维度统计';

-- ---------- 11. 地区维度统计 ----------
CREATE TABLE t_link_locale_stats (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    full_short_url VARCHAR(128) NOT NULL COMMENT '完整短链',
    date           DATE         NOT NULL COMMENT '统计日期',
    locale         VARCHAR(64)  NOT NULL COMMENT '地区',
    cnt            INT          NOT NULL DEFAULT 0 COMMENT '访问次数',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_url_date_locale (full_short_url, date, locale)
) ENGINE = InnoDB COMMENT ='地区维度统计';

-- =============================================================
-- 测试数据（D2 验收用：确认字段可用）
-- =============================================================
-- 密码为 BCrypt 密文，明文是 123456，可直接用于登录联调。
-- 需要其他密码时用注册接口创建，或用 Hutool 生成：BCrypt.hashpw("你的密码")
INSERT INTO t_user (username, password, real_name, mail)
VALUES ('peachten', '$2a$10$pj03XKFS0OOgCN1BuaRPx.Eua1QaDG52KDHOk7AsRmGaV73r.bwLu', '测试用户', 'peachten@example.com');

INSERT INTO t_group (gid, name, username, sort_order)
VALUES ('testGp', '默认分组', 'peachten', 0);

-- 验收查询
-- SELECT id, username, real_name, create_time, del_flag FROM t_user;
-- SELECT id, gid, name, username, del_flag FROM t_group;
