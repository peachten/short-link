package com.peachten.shortlink.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Base62 编解码与随机后缀生成
 * <p>
 * 字符集 0-9a-zA-Z 共 62 个字符，6 位可表示 62^6 ≈ 568 亿个短链。
 * <p>
 * 说明：本项目短链后缀走 {@link #random(int)} 随机生成（不可枚举，更安全），
 * encode / decode 仅作编解码演示。encode 从低位往高位 append，结果与常规 Base62
 * 记法相反，decode 按同一约定解析以保证编解码互逆。
 */
public final class Base62Util {

    /** encode / decode / random 共用同一字符集，顺序不可改 */
    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = CHARS.length();

    private Base62Util() {
    }

    /** 十进制 -> Base62（低位在前） */
    public static String encode(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(CHARS.charAt((int) (num % BASE)));
            num /= BASE;
        }
        return sb.length() == 0 ? String.valueOf(CHARS.charAt(0)) : sb.toString();
    }

    /** Base62 -> 十进制，与 {@link #encode(long)} 互逆 */
    public static long decode(String str) {
        long num = 0;
        long power = 1;
        // encode 低位在前，故从左到右依次乘以 62 的递增次幂
        for (int i = 0; i < str.length(); i++) {
            int index = CHARS.indexOf(str.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("非法 Base62 字符: " + str.charAt(i));
            }
            num += (long) index * power;
            power *= BASE;
        }
        return num;
    }

    /** 生成指定长度的随机短链后缀 */
    public static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(random.nextInt(BASE)));
        }
        return sb.toString();
    }
}
