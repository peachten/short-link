package com.peachten.shortlink.service.impl;

import com.peachten.shortlink.common.constant.RedisKeyConstant;
import com.peachten.shortlink.dao.LinkMapper;
import com.peachten.shortlink.entity.TLink;
import com.peachten.shortlink.service.BloomFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 布隆过滤器实现，按分组各持有一个 RBloomFilter
 * <p>
 * 分组维度隔离的好处：某分组的短链量增长不会影响其他分组的误判率；
 * 重建时也只重建该分组，不必全量重来。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BloomFilterServiceImpl implements BloomFilterService {

    /** 期望插入量：100 万条 + 0.01 误判率约 1.2MB 内存 */
    private static final long EXPECTED_INSERTIONS = 1_000_000L;
    private static final double FALSE_PROBABILITY = 0.01;
    /** 预热时每批读取条数，避免百万级数据一次读进内存 */
    private static final int BATCH_SIZE = 1000;

    private final RedissonClient redissonClient;
    private final LinkMapper linkMapper;

    @Override
    public void add(String gid, String shortUri) {
        getFilter(gid).add(shortUri);
    }

    @Override
    public boolean mightContain(String gid, String shortUri) {
        return getFilter(gid).contains(shortUri);
    }

    @Override
    public int rebuild(String gid) {
        RBloomFilter<String> filter = redissonClient.getBloomFilter(RedisKeyConstant.bloomFilter(gid));
        // 参数变化时 tryInit 会抛异常，先删掉旧结构再初始化
        filter.delete();
        filter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);

        int total = 0;
        int offset = 0;
        while (true) {
            List<TLink> batch = linkMapper.selectValidByGid(gid, offset, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            batch.forEach(link -> filter.add(link.getShortUri()));
            total += batch.size();
            if (batch.size() < BATCH_SIZE) {
                break;
            }
            offset += BATCH_SIZE;
        }
        log.info("布隆过滤器重建完成, gid={}, 共 {} 条", gid, total);
        return total;
    }

    @Override
    public int rebuildAll() {
        List<String> gids = linkMapper.selectAllValidGids();
        int total = 0;
        for (String gid : gids) {
            total += rebuild(gid);
        }
        return total;
    }

    /**
     * 获取分组的布隆过滤器并确保已初始化。
     * tryInit 是幂等的：已初始化会返回 false，不会抛异常。
     */
    private RBloomFilter<String> getFilter(String gid) {
        RBloomFilter<String> filter = redissonClient.getBloomFilter(RedisKeyConstant.bloomFilter(gid));
        filter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        return filter;
    }
}
