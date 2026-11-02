package com.coding.agent.workflow.agent;

/**
 * 完成度评估结果：由独立的评估器 sub-agent 产出，用于决定是否结束 ReAct 循环。
 */
public record EvalResult(boolean done, String reason, String next) {

    public static EvalResult notDone(String reason, String next) {
        return new EvalResult(false, reason, next);
    }

    public static EvalResult done(String reason) {
        return new EvalResult(true, reason, "");
    }
}
