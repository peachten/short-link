package com.peachten.shortlink.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Base62UtilTest {

    private static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    @Test
    void encode_decode_互逆() {
        long[] nums = {0, 1, 9, 10, 61, 62, 123, 3843, 56800235583L};
        for (long num : nums) {
            String encoded = Base62Util.encode(num);
            assertEquals(num, Base62Util.decode(encoded), "num=" + num + ", encoded=" + encoded);
        }
    }

    @Test
    void encode_边界与进位() {
        assertEquals("0", Base62Util.encode(0));
        assertEquals("9", Base62Util.encode(9));
        assertEquals(String.valueOf(CHARS.charAt(61)), Base62Util.encode(61));
        // 低位在前：62 应为 "01"，63 应为 "11"
        assertEquals("01", Base62Util.encode(62));
        assertEquals("11", Base62Util.encode(63));
    }

    @Test
    void random_长度为6且字符全部落在字符集内() {
        for (int i = 0; i < 10000; i++) {
            String uri = Base62Util.random(6);
            assertEquals(6, uri.length());
            for (char c : uri.toCharArray()) {
                assertTrue(CHARS.indexOf(c) >= 0, "出现非法字符: " + c);
            }
        }
    }

    @Test
    void random_10万次去重率大于99点99() {
        int total = 100_000;
        Set<String> distinct = new HashSet<>(total);
        for (int i = 0; i < total; i++) {
            distinct.add(Base62Util.random(6));
        }
        double ratio = distinct.size() * 1.0 / total;
        assertTrue(ratio > 0.9999, "去重率=" + ratio);
    }
}
