package com.coding.agent.workflow.agent;

/**
 * ReAct 循环的终态结果。
 */
public record AgentResult(
        AgentStatus status,   // 终态：成功 / 需要外层重试 / 失败
        String summary,       // 结果摘要（用于 SSE 与日志）
        int stepsUsed,        // 实际消耗的步数
        long tokensUsed       // 实际消耗的 token
) {

    public enum AgentStatus {
        SUCCESS,   // Agent 判定完成 -> 外层转 COMPLETED
        RETRY,     // 触发外层重试一次（等价旧 retryCount 语义）
        FAILED     // 护栏超限或不可恢复 -> 外层转 FAILED
    }

    public static AgentResult success(String summary, int steps, long tokens) {
        return new AgentResult(AgentStatus.SUCCESS, summary, steps, tokens);
    }

    public static AgentResult retry(String summary, int steps, long tokens) {
        return new AgentResult(AgentStatus.RETRY, summary, steps, tokens);
    }

    public static AgentResult failed(String summary, int steps, long tokens) {
        return new AgentResult(AgentStatus.FAILED, summary, steps, tokens);
    }
}
