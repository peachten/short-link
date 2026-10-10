package com.peachten.shortlink.service.impl;

import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.dao.LinkMapper;
import com.peachten.shortlink.dto.ShortLinkCreateReqDTO;
import com.peachten.shortlink.entity.TGroup;
import com.peachten.shortlink.entity.TLink;
import com.peachten.shortlink.service.BloomFilterService;
import com.peachten.shortlink.service.GroupService;
import com.peachten.shortlink.util.ShortUriGenerator;
import com.peachten.shortlink.vo.ShortLinkCreateRespVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 创建短链单测：纯 Mockito，覆盖幂等（三段式）、过期、禁用/启用默认值
 */
@ExtendWith(MockitoExtension.class)
class ShortLinkServiceImplTest {

    private static final String GID = "Ab3xK9";
    private static final String DOMAIN = "http://localhost:8000";
    private static final String SHORT_URI = "9aK2mZ";
    private static final String ORIGIN_URL = "https://example.com/very/long/path?x=1";

    @Mock
    private LinkMapper linkMapper;
    @Mock
    private GroupService groupService;
    @Mock
    private ShortUriGenerator shortUriGenerator;
    @Mock
    private BloomFilterService bloomFilterService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private ShortLinkServiceImpl shortLinkService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shortLinkService, "defaultDomain", DOMAIN);
        when(groupService.checkOwnership(GID)).thenReturn(new TGroup());
    }

    /** ── 幂等：两次创建同一长链，只落一条 ── */
    @Test
    void create_同一分组同一长链_两次创建只落一条且第二次幂等() throws InterruptedException {
        stubLock();
        when(shortUriGenerator.generateUniqueShortUri(GID)).thenReturn(SHORT_URI);

        AtomicReference<TLink> saved = new AtomicReference<>();
        AtomicInteger selectTimes = new AtomicInteger();
        // 第 1 次创建：快路径 null、双重检查 null；第 2 次创建：快路径直接命中
        when(linkMapper.selectOne(any())).thenAnswer(inv ->
                selectTimes.incrementAndGet() <= 2 ? null : saved.get());
        when(linkMapper.insert(any(TLink.class))).thenAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return 1;
        });
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        ShortLinkCreateRespVO first = shortLinkService.create(buildReq());
        ShortLinkCreateRespVO second = shortLinkService.create(buildReq());

        assertFalse(first.getIdempotent());
        assertTrue(second.getIdempotent());
        assertEquals(DOMAIN + "/" + GID + "/" + SHORT_URI, first.getFullShortUrl());
        assertEquals(first.getFullShortUrl(), second.getFullShortUrl());
        // 两条请求，只插入了一条
        verify(linkMapper, times(1)).insert(any(TLink.class));
    }

    /** ── 幂等：锁内双重检查命中（并发下被其他线程抢先创建） ── */
    @Test
    void create_锁内双重检查命中_不再插入() throws InterruptedException {
        stubLock();
        TLink exist = existLink();
        when(linkMapper.selectOne(any())).thenReturn(null, exist);

        ShortLinkCreateRespVO resp = shortLinkService.create(buildReq());

        assertTrue(resp.getIdempotent());
        verify(linkMapper, never()).insert(any(TLink.class));
    }

    /** ── 幂等：唯一索引兜底，insert 撞唯一索引后转幂等返回 ── */
    @Test
    void create_插入撞唯一索引_转幂等返回不抛异常() throws InterruptedException {
        stubLock();
        when(shortUriGenerator.generateUniqueShortUri(GID)).thenReturn(SHORT_URI);
        when(linkMapper.selectOne(any())).thenReturn(null, null, existLink());
        when(linkMapper.insert(any(TLink.class))).thenThrow(new DuplicateKeyException("dup"));

        ShortLinkCreateRespVO resp = shortLinkService.create(buildReq());

        assertTrue(resp.getIdempotent());
    }

    /** ── 过期：自定义有效期早于当前时间，直接拒绝 ── */
    @Test
    void create_失效时间早于当前时间_抛业务异常() {
        ShortLinkCreateReqDTO req = buildReq();
        req.setValidDateType(1);
        req.setValidDate(LocalDateTime.now().minusDays(1));

        BizException ex = assertThrows(BizException.class, () -> shortLinkService.create(req));

        assertTrue(ex.getMessage().contains("晚于当前时间"));
        verify(linkMapper, never()).insert(any(TLink.class));
        verify(shortUriGenerator, never()).generateUniqueShortUri(anyString());
    }

    /** ── 过期：自定义有效期晚于当前时间，正常创建并写入失效时间 ── */
    @Test
    void create_自定义有效期_落库写入失效时间() throws InterruptedException {
        stubLock();
        LocalDateTime validDate = LocalDateTime.now().plusDays(30);
        stubInsertOk();

        ShortLinkCreateReqDTO req = buildReq();
        req.setValidDateType(1);
        req.setValidDate(validDate);

        shortLinkService.create(req);

        TLink link = captureInserted();
        assertEquals(1, link.getValidDateType().intValue());
        assertEquals(validDate, link.getValidDate());
    }

    /** ── 禁用：新建短链默认是启用状态（enable_status = 0）且永久有效 ── */
    @Test
    void create_新建短链默认启用且永久有效() throws InterruptedException {
        stubLock();
        stubInsertOk();

        ShortLinkCreateRespVO resp = shortLinkService.create(buildReq());

        assertFalse(resp.getIdempotent());
        TLink link = captureInserted();
        assertEquals(0, link.getEnableStatus().intValue());     // 0 启用（未禁用）
        assertEquals(0, link.getValidDateType().intValue());    // 0 永久
        assertNull(link.getValidDate());
        assertEquals(0, link.getCreatedType().intValue());
        assertEquals(0, link.getClickNum().intValue());
        assertEquals(DOMAIN, link.getDomain());
        assertEquals(GID, link.getGid());
        verify(setOperations).add(anyString(), eq(SHORT_URI));
    }

    // ---------- helpers ----------

    private ShortLinkCreateReqDTO buildReq() {
        ShortLinkCreateReqDTO req = new ShortLinkCreateReqDTO();
        req.setOriginUrl(ORIGIN_URL);
        req.setGid(GID);
        return req;
    }

    private TLink existLink() {
        TLink link = new TLink();
        link.setId(1L);
        link.setGid(GID);
        link.setShortUri(SHORT_URI);
        link.setOriginUrl(ORIGIN_URL);
        link.setFullShortUrl(DOMAIN + "/" + GID + "/" + SHORT_URI);
        return link;
    }

    private void stubLock() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(3, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    private void stubInsertOk() {
        when(shortUriGenerator.generateUniqueShortUri(GID)).thenReturn(SHORT_URI);
        when(linkMapper.selectOne(any())).thenReturn(null);
        when(linkMapper.insert(any(TLink.class))).thenReturn(1);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    }

    private TLink captureInserted() {
        ArgumentCaptor<TLink> captor = ArgumentCaptor.forClass(TLink.class);
        verify(linkMapper).insert(captor.capture());
        return captor.getValue();
    }
}
