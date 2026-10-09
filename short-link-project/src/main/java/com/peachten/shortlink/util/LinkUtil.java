package com.peachten.shortlink.util;

/**
 * 短链拼接工具
 */
public final class LinkUtil {

    private LinkUtil() {
    }

    /**
     * 拼接完整短链：{domain}/{gid}/{shortUri}
     *
     * @param domain   域名，如 http://localhost:8000（末尾带不带 / 都可）
     * @param gid      分组标识
     * @param shortUri 短链后缀
     */
    public static String buildFullShortUrl(String domain, String gid, String shortUri) {
        String base = domain.endsWith("/") ? domain.substring(0, domain.length() - 1) : domain;
        return base + "/" + gid + "/" + shortUri;
    }
}
