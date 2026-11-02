package com.coding.agent.exception;

/**
 * 未登录/未授权异常 — 匿名（guest）用户或无有效登录态时抛出
 */
public class UnauthorizedException extends BaseException {

    public UnauthorizedException(String message) {
        super(401, message);
    }

    @Override
    public int getStatus() {
        return 401; // HTTP 401 Unauthorized
    }
}
