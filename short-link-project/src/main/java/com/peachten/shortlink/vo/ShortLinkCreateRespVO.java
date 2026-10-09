package com.peachten.shortlink.vo;

import lombok.Data;

/**
 * 创建短链出参
 */
@Data
public class ShortLinkCreateRespVO {

    private String fullShortUrl;

    private String shortUri;

    private String originUrl;

    /** true 表示命中了已存在的短链（幂等复用） */
    private Boolean idempotent;
}
