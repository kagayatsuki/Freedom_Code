package com.coding.agent.exception;

/**
 * 限流异常 — 用户当日配额用完时抛出
 */
public class RateLimitException extends BaseException {

    public RateLimitException(String message) {
        super(429, message);
    }

    @Override
    public int getStatus() {
        return 429; // HTTP 429 Too Many Requests
    }
}
