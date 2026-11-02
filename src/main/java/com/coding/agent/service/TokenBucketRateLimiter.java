package com.coding.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 令牌桶限流器：每个用户每分钟最多提交 N 次请求，在请求进入工作流队列之前拦截。
 * <p>
 * 基于 Redis + Lua 脚本保证原子性（多实例/分布式安全），每个用户一个独立的令牌桶。
 * 容量 = CAPACITY，按 REFILL_SECONDS 周期匀速补充令牌。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBucketRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;

    /** 令牌桶容量：每个用户每分钟最多提交次数 */
    private static final int CAPACITY = 10;
    /** 补充周期（秒） */
    private static final int REFILL_SECONDS = 60;
    /** 每秒补充的令牌数 = CAPACITY / REFILL_SECONDS */
    private static final double REFILL_RATE = (double) CAPACITY / REFILL_SECONDS;

    private static final String KEY_PREFIX = "ratelimit:submit:";

    /**
     * 原子令牌桶：hget/hset 记录 tokens 与 last 刷新时间，单次执行保证并发安全。
     * KEYS[1]=bucket key；ARGV[1]=容量 ARGV[2]=每秒补充速率 ARGV[3]=当前秒 ARGV[4]=本次请求令牌数 ARGV[5]=TTL
     */
    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT =
            new DefaultRedisScript<>("""
                    local key = KEYS[1]
                    local capacity = tonumber(ARGV[1])
                    local rate = tonumber(ARGV[2])
                    local now = tonumber(ARGV[3])
                    local requested = tonumber(ARGV[4])
                    local ttl = tonumber(ARGV[5])

                    local tokens = tonumber(redis.call('hget', key, 'tokens'))
                    local last = tonumber(redis.call('hget', key, 'last'))
                    if tokens == nil then
                        tokens = capacity
                        last = now
                    end

                    local newTokens = math.min(capacity, math.max(0, tokens + (now - last) * rate))

                    local allowed = 0
                    if newTokens >= requested then
                        newTokens = newTokens - requested
                        allowed = 1
                    end

                    redis.call('hset', key, 'tokens', tostring(newTokens))
                    redis.call('hset', key, 'last', tostring(now))
                    redis.call('expire', key, ttl)

                    return allowed
                    """, Long.class);

    /**
     * 尝试消费一个提交令牌。
     *
     * @param userName 用户标识（匿名用户为 "guest"）
     * @return true 放行；false 超出每分钟提交上限
     */
    public boolean tryAcquire(String userName) {
        String key = KEY_PREFIX + userName;
        long nowSeconds = System.currentTimeMillis() / 1000L;
        Long allowed;
        try {
            allowed = stringRedisTemplate.execute(
                    TOKEN_BUCKET_SCRIPT,
                    List.of(key),
                    String.valueOf(CAPACITY),
                    String.valueOf(REFILL_RATE),
                    String.valueOf(nowSeconds),
                    "1",
                    String.valueOf(REFILL_SECONDS * 2));
        } catch (Exception e) {
            // Redis 异常时降级放行，避免限流组件自身拖垮主流程
            log.warn("[令牌桶] Redis 执行异常，降级放行: {}", e.getMessage());
            return true;
        }
        return allowed != null && allowed == 1L;
    }
}
