package com.peachten.shortlink.service;

/**
 * 布隆过滤器：缓存穿透的第一道防线
 * <p>
 * 按分组隔离（key = short-link:bloom-filter:{gid}）。
 * 判断 false 表示「一定不存在」，可以不查库直接 404；
 * 判断 true 只表示「可能存在」，仍需查缓存/DB（误判会穿透到 DB）。
 */
public interface BloomFilterService {

    /** 短链创建成功后写入布隆 */
    void add(String gid, String shortUri);

    /** true 表示可能存在，false 表示一定不存在 */
    boolean mightContain(String gid, String shortUri);

    /**
     * 清空并重建指定分组的布隆过滤器
     *
     * @return 重建后灌入的元素个数
     */
    int rebuild(String gid);

    /**
     * 重建所有分组的布隆过滤器（应用启动预热用）
     *
     * @return 灌入的元素总数
     */
    int rebuildAll();
}
