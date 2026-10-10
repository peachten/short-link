package com.peachten.shortlink.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.peachten.shortlink.common.constant.RedisKeyConstant;
import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.common.result.ResultCode;
import com.peachten.shortlink.dao.LinkMapper;
import com.peachten.shortlink.dto.ShortLinkCreateReqDTO;
import com.peachten.shortlink.entity.TLink;
import com.peachten.shortlink.service.BloomFilterService;
import com.peachten.shortlink.service.GroupService;
import com.peachten.shortlink.service.ShortLinkService;
import com.peachten.shortlink.util.HashUtil;
import com.peachten.shortlink.util.LinkUtil;
import com.peachten.shortlink.util.ShortUriGenerator;
import com.peachten.shortlink.vo.ShortLinkCreateRespVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    /** 等锁时间：拿不到锁说明并发创建同一长链，直接提示稍后重试 */
    private static final long LOCK_WAIT_SECONDS = 3;

    private final LinkMapper linkMapper;
    private final GroupService groupService;
    private final ShortUriGenerator shortUriGenerator;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final BloomFilterService bloomFilterService;

    @Value("${short-link.domain.default:http://localhost:8000}")
    private String defaultDomain;

    @Override
    public ShortLinkCreateRespVO create(ShortLinkCreateReqDTO req) {
        // 0. 基础校验：分组归属 + 有效期/后缀等参数
        groupService.checkOwnership(req.getGid());
        validCreateParam(req);

        String originUrlHash = HashUtil.md5(req.getOriginUrl());

        // 1. 快路径：同一分组同一长链已存在，直接返回（幂等）
        TLink exist = findExist(req.getGid(), originUrlHash);
        if (exist != null) {
            return buildResp(exist, true);
        }

        // 2. 慢路径：Redisson 分布式锁 + 锁内双重检查
        RLock lock = redissonClient.getLock(RedisKeyConstant.lockCreate(req.getGid(), originUrlHash));
        boolean locked = false;
        try {
            // 二参版本，不指定 leaseTime，交给看门狗自动续期
            locked = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new BizException("创建请求过于频繁，请稍后重试");
            }

            exist = findExist(req.getGid(), originUrlHash);   // 双重检查
            if (exist != null) {
                log.info("锁内双重检查命中，幂等返回, gid={}, originUrlHash={}", req.getGid(), originUrlHash);
                return buildResp(exist, true);
            }

            return doCreate(req, originUrlHash);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("创建被中断，请重试");
        } finally {
            // 必须判断持有者，避免业务超时后释放了别人的锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 真正落库。唯一索引 (gid, origin_url_hash) / (gid, short_uri) 是最后的兜底。
     */
    private ShortLinkCreateRespVO doCreate(ShortLinkCreateReqDTO req, String originUrlHash) {
        boolean custom = StringUtils.hasText(req.getCustomShortUri());
        String shortUri = custom
                ? req.getCustomShortUri()
                : shortUriGenerator.generateUniqueShortUri(req.getGid());

        int validDateType = req.getValidDateType() == null ? 0 : req.getValidDateType();

        TLink link = new TLink();
        link.setDomain(defaultDomain);
        link.setShortUri(shortUri);
        link.setFullShortUrl(LinkUtil.buildFullShortUrl(defaultDomain, req.getGid(), shortUri));
        link.setOriginUrl(req.getOriginUrl());
        link.setOriginUrlHash(originUrlHash);
        link.setGid(req.getGid());
        link.setEnableStatus(0);                                            // 0 启用
        link.setCreatedType(req.getCreatedType() == null ? 0 : req.getCreatedType());
        link.setValidDateType(validDateType);
        link.setValidDate(validDateType == 1 ? req.getValidDate() : null);  // 0 永久 1 自定义
        link.setDescription(req.getDescribe());
        link.setClickNum(0);

        try {
            linkMapper.insert(link);
        } catch (DuplicateKeyException e) {
            // 并发下被其他请求抢先插入：按幂等键查回，转为幂等返回
            TLink dup = findExist(req.getGid(), originUrlHash);
            if (dup != null) {
                return buildResp(dup, true);
            }
            // 幂等键查不到，说明撞的是 uk_short_uri：自定义后缀已被占用
            if (custom) {
                throw new BizException(ResultCode.SHORT_URI_EXIST);
            }
            throw new BizException("短链创建失败，请重试");
        }

        // 写入分组 Set（1 天过期），供后续后缀查重使用
        String setKey = RedisKeyConstant.gidSet(req.getGid());
        stringRedisTemplate.opsForSet().add(setKey, shortUri);
        stringRedisTemplate.expire(setKey, Duration.ofDays(1));

        // 写入布隆过滤器，跳转时用于拦截不存在的短链（防缓存穿透）
        bloomFilterService.add(req.getGid(), shortUri);

        return buildResp(link, false);
    }

    /**
     * 参数校验：自定义有效期必须晚于当前时间。
     * URL 协议、后缀字符集等已由 DTO 上的校验注解拦截。
     */
    private void validCreateParam(ShortLinkCreateReqDTO req) {
        if (Integer.valueOf(1).equals(req.getValidDateType())) {
            if (req.getValidDate() == null) {
                throw new BizException("自定义有效期必须填写失效时间");
            }
            if (!req.getValidDate().isAfter(LocalDateTime.now())) {
                throw new BizException("失效时间必须晚于当前时间");
            }
        }
    }

    private TLink findExist(String gid, String originUrlHash) {
        return linkMapper.selectOne(Wrappers.lambdaQuery(TLink.class)
                .eq(TLink::getGid, gid)
                .eq(TLink::getOriginUrlHash, originUrlHash));
    }

    private ShortLinkCreateRespVO buildResp(TLink link, boolean idempotent) {
        ShortLinkCreateRespVO resp = new ShortLinkCreateRespVO();
        resp.setFullShortUrl(link.getFullShortUrl());
        resp.setShortUri(link.getShortUri());
        resp.setOriginUrl(link.getOriginUrl());
        resp.setIdempotent(idempotent);
        return resp;
    }
}
