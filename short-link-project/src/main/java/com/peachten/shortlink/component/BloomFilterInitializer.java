package com.peachten.shortlink.component;

import com.peachten.shortlink.service.BloomFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动后预热布隆过滤器
 * <p>
 * 布隆数据在 Redis 里是永久的（无 TTL），正常情况下重启后仍可用；
 * 这里做一次重建，保证「数据库有、布隆没有」这种不一致能被自动修复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterInitializer implements ApplicationRunner {

    private final BloomFilterService bloomFilterService;

    @Override
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        int total = bloomFilterService.rebuildAll();
        log.info("布隆过滤器预热完成, 共 {} 条, 耗时 {} ms", total, System.currentTimeMillis() - start);
    }
}
