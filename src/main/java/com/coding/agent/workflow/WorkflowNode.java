package com.coding.agent.workflow;

public interface WorkflowNode {
    /**
     * 执行当前节点核心逻辑
     *
     * @param context 工作流上下文
     * @return 执行完成后建议跳转的下一个状态
     */
    WorkflowState execute(WorkflowContext context);

    /**
     * 当前节点对应的目标状态
     * 用于在 WorkflowEngine 中注册节点映射
     */
    WorkflowState getTargetState();
}
