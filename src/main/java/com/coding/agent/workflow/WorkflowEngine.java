package com.coding.agent.workflow;


import com.coding.agent.service.DebugTraceService;
import com.coding.agent.utils.WorkflowContextHolder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class WorkflowEngine {
    private final List<WorkflowNode> nodes;

    private final DebugTraceService debugTraceService;

    private Map<WorkflowState, WorkflowNode> nodeRegistry;

    /** 任务实时状态（内存），供前端刷新后查询 */
    private final Map<String, TaskStatusInfo> taskStatusMap = new ConcurrentHashMap<>();

    /** 运行中的任务上下文，用于响应取消请求 */
    private final Map<String, WorkflowContext> runningContexts = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        nodeRegistry=nodes.stream().collect(
                //Map<WorkflowState, WorkflowNode>
                Collectors.toMap(
                        WorkflowNode::getTargetState,node->node,
                        (existing,replacement)->replacement,
                        ConcurrentHashMap::new
                )
        );
        log.info("WorkflowEngine 初始化完成，共注册 {} 个节点", nodeRegistry.size());
    }
    public Flux<NotificationEvent> startWorkflow(String taskId, String prompt
                                , String userName, String projectName,
                                boolean isNewProject,
                                boolean useImageSearch, boolean useWebSearch) {
        WorkflowContext context = WorkflowContext.builder()
                .userName(userName)
                .projectName(projectName)
                .taskId(taskId)
                .originalPrompt(prompt)
                .currentState(WorkflowState.INIT)
                .createTime(java.time.LocalDateTime.now())
                .isNewProject(isNewProject)
                .useImageSearch(useImageSearch)
                .useWebSearch(useWebSearch)
                .retryCount(0).build();

        runningContexts.put(taskId, context);
        updateTaskStatus(context, WorkflowState.INIT, true, "任务已创建");

        return Flux.create(sink -> {
            context.setFileWriteNotifier(message -> {
                sink.next(NotificationEvent.of(taskId, null, message));
            });
            // ReAct 循环内部的通用事件（构建/审查等）直接透传给 SSE
            context.setAgentEventNotifier(sink::next);
            Thread.startVirtualThread(
                    ()->executeWorkflow(context, sink)
            );
        });
    }
    public Flux<NotificationEvent> getWorkflowStream(String taskId) {
        // 目前简单实现：如果任务正在运行，则返回对应 sink
        // 后续可使用 SinkManager 或 Redis 实现多订阅
        return Flux.empty();}

        private void executeWorkflow(WorkflowContext context, FluxSink<NotificationEvent> sink) {
        try {
            WorkflowContextHolder.setContext(context);
            log.info("开始执行任务 {}，初始状态: {}", context.getTaskId(), context.getCurrentState());

            while (!context.getCurrentState().isTerminal()) {
                // 响应用户取消：在节点边界处退出
                if (context.isCancelled()) {
                    context.setCurrentState(WorkflowState.FAILED);
                    updateTaskStatus(context, WorkflowState.FAILED, false, "任务已被用户取消");
                    sink.next(NotificationEvent.of(context.getTaskId(), WorkflowState.FAILED,
                            "⛔ 任务已被用户取消"));
                    break;
                }

                WorkflowState currentState = context.getCurrentState();
                WorkflowNode node = nodeRegistry.get(currentState);

                if (node == null) {
                    throw new IllegalStateException("未找到状态对应的节点: " + currentState);
                }

                // 推送节点开始事件
                sink.next(NotificationEvent.of(
                        context.getTaskId(),
                        currentState,
                        "开始执行：" + currentState.getName()
                ));

                // 执行当前节点
                WorkflowState nextState = node.execute(context);

                // 处理回滚与重试逻辑
                if (isRollback(currentState, nextState)) {
                    context.incrementRetryCount();
                    log.warn("任务 {} 发生回滚，重试次数: {}/3", context.getTaskId(), context.getRetryCount());

                    if (context.getRetryCount() > 3) {
                        context.setCurrentState(WorkflowState.FAILED);
                        sink.next(NotificationEvent.of(context.getTaskId(), WorkflowState.FAILED,
                                "达到最大重试次数，任务失败"));
                        break;
                    }
                }

                context.setCurrentState(nextState);

                // 推送节点完成事件（含项目类型，路由决策后生效）
                sink.next(NotificationEvent.of(
                        context.getTaskId(),
                        nextState,
                        currentState.getName() + " 执行完成",
                        context.getAppType()
                ));
                updateTaskStatus(context, nextState, !nextState.isTerminal(),
                        currentState.getName() + " 执行完成");

                // 如果下一个状态是终态，先执行终态节点再退出循环
                if (nextState.isTerminal()) {
                    WorkflowNode terminalNode = nodeRegistry.get(nextState);
                    if (terminalNode != null) {
                        log.info("执行终态节点: {}", nextState.getName());
                        terminalNode.execute(context);
                    }
                    break;
                }
            }

            // 最终 SSE 推送
            if (context.getCurrentState() == WorkflowState.COMPLETED) {
                sink.next(NotificationEvent.of(context.getTaskId(), WorkflowState.COMPLETED,
                        "🎉 项目生成成功！本次消耗 " + context.getTokenUsed() + " tokens", context.getAppType()));
            } else if (context.getCurrentState() == WorkflowState.FAILED) {
                sink.next(NotificationEvent.of(context.getTaskId(), WorkflowState.FAILED,
                        "❌ 任务执行失败", context.getAppType()));
            }

            updateTaskStatus(context, context.getCurrentState(), false,
                    "任务结束: " + context.getCurrentState().getName());
            debugTraceService.dump(context);
            sink.complete();
            log.info("任务 {} 执行结束，最终状态: {}", context.getTaskId(), context.getCurrentState());

        } catch (Exception e) {
            log.error("任务 {} 执行异常", context.getTaskId(), e);
            context.setCurrentState(WorkflowState.FAILED);
            updateTaskStatus(context, WorkflowState.FAILED, false, "系统异常: " + e.getMessage());
            sink.next(NotificationEvent.of(context.getTaskId(), WorkflowState.FAILED,
                    "系统异常: " + e.getMessage()));
            debugTraceService.dump(context);
            sink.error(e);
        } finally {
            runningContexts.remove(context.getTaskId());
            // 修复：虚拟线程复用下必须清理 ThreadLocal，防止上下文串扰与内存泄漏
            WorkflowContextHolder.clear();
        }
    }

    private boolean isRollback(WorkflowState current, WorkflowState next) {
        if (current.isRetryable() && next == WorkflowState.GENERATING) {
            return true;
        }

        // 情况2：CodeGenNode 自己返回 GENERATING 或发生异常
        if (current == WorkflowState.GENERATING && next == WorkflowState.GENERATING) {
            return true;
        }

        // 情况3：任何节点抛出异常后
        return false;
    }

    /**
     * 查询任务实时状态（供前端刷新后判断任务是否仍在运行）。
     */
    public TaskStatusInfo getTaskStatus(String taskId) {
        TaskStatusInfo info = taskStatusMap.get(taskId);
        return info != null ? info : TaskStatusInfo.notFound(taskId);
    }

    /**
     * 请求取消任务：设置取消标志，节点/工具会在下一个检查点退出。
     *
     * @return true 表示任务存在且仍在运行，已接受取消
     */
    public boolean cancelTask(String taskId) {
        WorkflowContext context = runningContexts.get(taskId);
        if (context == null) {
            return false;
        }
        context.cancel();
        log.info("任务 {} 已被请求取消", taskId);
        return true;
    }

    private void updateTaskStatus(WorkflowContext context, WorkflowState state, boolean active, String message) {
        taskStatusMap.put(context.getTaskId(), new TaskStatusInfo(
                context.getTaskId(), state, active, message, LocalDateTime.now()));
    }
}
