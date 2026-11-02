package com.coding.agent.workflow;

import java.time.LocalDateTime;

/**
 * 任务实时状态快照，供前端刷新页面后查询任务是否仍在运行。
 */
public record TaskStatusInfo(
        String taskId,
        WorkflowState state,
        boolean active,
        String message,
        LocalDateTime updatedAt
) {

    public static TaskStatusInfo notFound(String taskId) {
        return new TaskStatusInfo(taskId, null, false, "任务不存在或已过期", LocalDateTime.now());
    }
}
