package com.peachten.shortlink.util;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.peachten.shortlink.common.exception.BizException;
import com.peachten.shortlink.dao.LinkMapper;
import com.peachten.shortlink.entity.TLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短链后缀生成器：随机 6 位 + 查重重试
 * <p>
 * 当前查重直接走 DB（t_link 的 uk_short_uri 唯一索引）；
 * W2D3 引入布隆过滤器后优化为「先布隆、再 DB」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortUriGenerator {

    /** 后缀长度，62^6 ≈ 568 亿 */
    private static final int LENGTH = 6;
    /** 冲突重试次数上限 */
    private static final int MAX_RETRY = 3;

    private final LinkMapper linkMapper;

    /**
     * 生成分组内唯一的 6 位短链后缀
     *
     * @param gid 分组标识（唯一索引是 (gid, short_uri)，按分组隔离）
     */
    public String generateUniqueShortUri(String gid) {
        for (int i = 0; i < MAX_RETRY; i++) {
            String shortUri = Base62Util.random(LENGTH);
            boolean exists = linkMapper.selectCount(Wrappers.lambdaQuery(TLink.class)
                    .eq(TLink::getGid, gid)
                    .eq(TLink::getShortUri, shortUri)) > 0;
            if (!exists) {
                return shortUri;
            }
            log.warn("短链后缀冲突, gid={}, shortUri={}, 重试第 {} 次", gid, shortUri, i + 1);
        }
        throw new BizException("短链生成失败，请重试");
    }
}
