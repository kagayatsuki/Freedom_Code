package com.coding.agent.workflow.agent;

import com.coding.agent.utils.WorkflowContextHolder;
import com.coding.agent.workflow.WorkflowContext;
import com.coding.agent.workflow.impl.EnhanceNode;
import com.coding.agent.workflow.impl.InitNode;
import com.coding.agent.workflow.impl.RouteNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ReAct 同步执行入口（普通方法返回，不走 SSE / Flux / 虚拟线程）。
 *
 * 用途：调试与验证 ReAct 显式循环是否真正进入、工具是否被调用。
 * 直接在调用线程内依次执行 Init → Enhance → Route 三个前置节点，
 * 然后同步调用 {@link ReActCodingAgent#run(WorkflowContext)}，拿到终态 {@link AgentResult}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReActSyncService {

    private final InitNode initNode;
    private final EnhanceNode enhanceNode;
    private final RouteNode routeNode;
    private final ReActCodingAgent reActCodingAgent;

    public AgentResult run(WorkflowContext context) {
        WorkflowContextHolder.setContext(context);
        try {
            // 前置节点：填充 projectRootPath / enhancedPrompt / appType（与状态机顺序一致）
            initNode.execute(context);
            enhanceNode.execute(context);
            routeNode.execute(context);

            log.info("[ReActSync] 前置节点完成 | taskId: {} | appType: {} | rootPath: {}",
                    context.getTaskId(), context.getAppType(), context.getProjectRootPath());

            // 同步执行显式 ReAct 循环，返回终态结果
            return reActCodingAgent.run(context);
        } catch (Exception e) {
            log.error("[ReActSync] 任务 {} 同步执行异常", context.getTaskId(), e);
            return AgentResult.failed("同步执行异常: " + e.getMessage(), 0, context.getTokenUsed());
        } finally {
            WorkflowContextHolder.clear();
        }
    }
}
