package com.peachten.shortlink.util;

import cn.hutool.crypto.digest.DigestUtil;

/**
 * 摘要工具
 */
public final class HashUtil {

    private HashUtil() {
    }

    /**
     * 32 位小写 MD5
     * <p>
     * 用于生成 origin_url_hash，是 t_link 联合唯一索引 uk_gid_origin(gid, origin_url_hash)
     * 的组成部分，也是创建短链幂等判断（同一分组同一长链只保留一条）的依据。
     */
    public static String md5(String content) {
        return DigestUtil.md5Hex(content);
    }
}
