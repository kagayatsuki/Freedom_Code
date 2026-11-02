package com.coding.agent.workflow.agent;

import org.springframework.stereotype.Component;

/**
 * ReAct 循环步数 / 重复动作护栏。
 * 使用 ThreadLocal 计数，保证多任务（多虚拟线程）并发下互不干扰。
 * 每次任务开始前调用 {@link #reset()}。
 */
@Component
public class AgentStepTracker {

    private final ThreadLocal<Integer> stepCounter = ThreadLocal.withInitial(() -> 0);
    private final ThreadLocal<String> lastAction = new ThreadLocal<>();
    private final ThreadLocal<Integer> repeatCount = ThreadLocal.withInitial(() -> 0);

    public void reset() {
        stepCounter.set(0);
        lastAction.remove();
        repeatCount.set(0);
    }

    /**
     * 每次工具调用前调用，判断是否允许继续。
     *
     * @param action    动作标识（如 "runBuild" / "reviewFile"）
     * @param maxSteps  步数上限
     * @param maxRepeat 同一动作连续重复上限
     * @return true 允许继续，false 应终止（护栏超限）
     */
    public boolean beforeStep(String action, int maxSteps, int maxRepeat) {
        int steps = stepCounter.get() + 1;
        stepCounter.set(steps);
        if (steps > maxSteps) {
            return false;
        }
        if (action.equals(lastAction.get())) {
            int r = repeatCount.get() + 1;
            repeatCount.set(r);
            if (r > maxRepeat) {
                return false;
            }
        } else {
            lastAction.set(action);
            repeatCount.set(1);
        }
        return true;
    }

    public int getSteps() {
        return stepCounter.get();
    }
}
