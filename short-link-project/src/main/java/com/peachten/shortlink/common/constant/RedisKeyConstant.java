package com.peachten.shortlink.common.constant;

/**
 * Redis Key 统一常量（禁止在业务代码中硬编码 Key）
 * <p>
 * 命名规范：short-link:{模块}:{业务}:{标识}
 * 详见《开发文档》第 5 章 Redis Key 设计（共 17 个 Key）
 * <p>
 * 注意：用户 token 的 Key（satoken:login:token:{tokenValue}）由 Sa-Token 自行管理，
 * 不在此处定义，避免与框架内部实现耦合。
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    private static final String PREFIX = "short-link:";

    /* ---------- 短链跳转缓存：String，1 天 + 随机 0~1h，空值写 "" ---------- */
    public static String gotoCache(String domain, String shortUri) {
        return PREFIX + "goto:" + domain + ":" + shortUri;
    }

    /* ---------- 布隆过滤器：Redisson RBloomFilter，永久（期望插入量 100w，误判率 0.01） ---------- */
    public static String bloomFilter(String gid) {
        return PREFIX + "bloom-filter:" + gid;
    }

    /* ---------- 分组短链集合：Set，1 天，生成短链时查重 ---------- */
    public static String gidSet(String gid) {
        return PREFIX + "gid:set:" + gid;
    }

    /* ---------- 统计：PV(UINCR) / UV(HLL) / UIP(HLL)，均为 2 天 ---------- */
    public static String statsPv(String date, String fullShortUrl) {
        return PREFIX + "stats:pv:" + date + ":" + fullShortUrl;
    }

    public static String statsUv(String date, String fullShortUrl) {
        return PREFIX + "stats:uv:" + date + ":" + fullShortUrl;
    }

    public static String statsUip(String date, String fullShortUrl) {
        return PREFIX + "stats:uip:" + date + ":" + fullShortUrl;
    }

    /**
     * 维度统计：Hash，2 天
     *
     * @param dimension browser / os / device / locale
     */
    public static String statsDimension(String dimension, String date, String fullShortUrl) {
        return PREFIX + "stats:" + dimension + ":" + date + ":" + fullShortUrl;
    }

    /* ---------- 小时分布：Hash，field = 0~23，2 天 ---------- */
    public static String statsHour(String date, String fullShortUrl) {
        return PREFIX + "stats:hour:" + date + ":" + fullShortUrl;
    }

    /* ---------- 创建幂等锁：Redisson 锁，看门狗续期（同一分组同一长链并发创建） ---------- */
    public static String lockCreate(String gid, String urlHash) {
        return PREFIX + "lock:create:" + gid + ":" + urlHash;
    }

    /* ---------- 自定义后缀锁：Redisson 锁，看门狗续期（自定义后缀并发创建） ---------- */
    public static String lockCustomUri(String gid, String shortUri) {
        return PREFIX + "lock:custom-uri:" + gid + ":" + shortUri;
    }

    /* ---------- 定时任务锁：Redisson 锁，看门狗续期（多实例防重复执行） ---------- */
    public static String lockJob(String jobName) {
        return PREFIX + "lock:job:" + jobName;
    }

    /**
     * 限流：ZSET，Lua 滑动窗口，过期时间 = 窗口大小
     *
     * @param type ip（跳转限流） / user（创建限流）
     * @param key  ip 地址或用户名
     */
    public static String rateLimit(String type, String key) {
        return PREFIX + "rate-limit:" + type + ":" + key;
    }
}
