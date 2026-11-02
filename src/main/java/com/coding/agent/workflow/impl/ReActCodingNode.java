package com.coding.agent.workflow.impl;

import com.coding.agent.workflow.WorkflowContext;
import com.coding.agent.workflow.WorkflowNode;
import com.coding.agent.workflow.WorkflowState;
import com.coding.agent.workflow.agent.AgentResult;
import com.coding.agent.workflow.agent.ReActCodingAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ReAct 编码节点：替代 CodeGenNode + BuildNode + CheckNode 三节点的硬编码串联。
 * getTargetState() 仍为 GENERATING，通过 agent.react.enabled=true 启用后覆盖旧节点。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "agent.react.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ReActCodingNode implements WorkflowNode {

    private final ReActCodingAgent reActCodingAgent;

    @Override
    public WorkflowState execute(WorkflowContext context) {
        if (context.getProjectRootPath() == null) {
            context.addErrorLog("No project root path specified");
            return WorkflowState.FAILED;
        }
        try {
            AgentResult result = reActCodingAgent.run(context);
            log.info("[ReActCodingNode] 任务 {} 终态: {} | 步数: {} | token: {}",
                    context.getTaskId(), result.status(), result.stepsUsed(), result.tokensUsed());
            return switch (result.status()) {
                case SUCCESS -> WorkflowState.COMPLETED;
                case RETRY -> WorkflowState.GENERATING; // 复用现有回滚计数
                case FAILED -> WorkflowState.FAILED;
            };
        } catch (Exception e) {
            log.error("[ReActCodingNode] ReAct 循环异常 | 任务: {}", context.getTaskId(), e);
            context.addErrorLog("ReAct 循环异常: " + e.getMessage());
            return WorkflowState.GENERATING;
        }
    }

    @Override
    public WorkflowState getTargetState() {
        return WorkflowState.GENERATING;
    }
}
