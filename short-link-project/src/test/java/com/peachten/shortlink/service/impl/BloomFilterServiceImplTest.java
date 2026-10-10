package com.peachten.shortlink.service.impl;

import com.peachten.shortlink.common.constant.RedisKeyConstant;
import com.peachten.shortlink.dao.LinkMapper;
import com.peachten.shortlink.entity.TLink;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 布隆过滤器单测：验证 add / mightContain / rebuild 的委托与分组隔离
 */
@ExtendWith(MockitoExtension.class)
class BloomFilterServiceImplTest {

    private static final String GID = "Ab3xK9";
    private static final String SHORT_URI = "a1b2c3";

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RBloomFilter<String> bloomFilter;
    @Mock
    private LinkMapper linkMapper;

    @InjectMocks
    private BloomFilterServiceImpl bloomFilterService;

    @Test
    void add后的元素_mightContain为true() {
        stubFilterInit();
        when(bloomFilter.contains(SHORT_URI)).thenReturn(true);

        bloomFilterService.add(GID, SHORT_URI);

        verify(bloomFilter).add(SHORT_URI);
        assertTrue(bloomFilterService.mightContain(GID, SHORT_URI));
        // 必须按分组维度取过滤器，避免不同分组互相污染
        verify(redissonClient, atLeastOnce()).getBloomFilter(RedisKeyConstant.bloomFilter(GID));
    }

    @Test
    void 未加入的元素_mightContain为false() {
        stubFilterInit();
        when(bloomFilter.contains("zzzzzz")).thenReturn(false);

        assertFalse(bloomFilterService.mightContain(GID, "zzzzzz"));
    }

    @Test
    void rebuild_先清空再分批重灌() {
        when(redissonClient.<String>getBloomFilter(anyString())).thenReturn(bloomFilter);
        when(bloomFilter.tryInit(1_000_000L, 0.01)).thenReturn(true);
        when(linkMapper.selectValidByGid(GID, 0, 1000))
                .thenReturn(List.of(link("a1b2c3"), link("d4e5f6")));

        int total = bloomFilterService.rebuild(GID);

        assertEquals(2, total);
        verify(bloomFilter).delete();
        verify(bloomFilter).add("a1b2c3");
        verify(bloomFilter).add("d4e5f6");
    }

    @Test
    void rebuildAll_遍历所有有效分组() {
        when(redissonClient.<String>getBloomFilter(anyString())).thenReturn(bloomFilter);
        when(bloomFilter.tryInit(1_000_000L, 0.01)).thenReturn(true);
        when(linkMapper.selectAllValidGids()).thenReturn(List.of("g1", "g2"));
        when(linkMapper.selectValidByGid("g1", 0, 1000)).thenReturn(List.of(link("aaa111")));
        when(linkMapper.selectValidByGid("g2", 0, 1000)).thenReturn(List.of());

        assertEquals(1, bloomFilterService.rebuildAll());
    }

    // ---------- helpers ----------

    private void stubFilterInit() {
        when(redissonClient.<String>getBloomFilter(anyString())).thenReturn(bloomFilter);
        when(bloomFilter.tryInit(1_000_000L, 0.01)).thenReturn(true);
    }

    private TLink link(String shortUri) {
        TLink link = new TLink();
        link.setShortUri(shortUri);
        return link;
    }
}
