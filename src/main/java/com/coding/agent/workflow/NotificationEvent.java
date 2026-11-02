package com.coding.agent.workflow;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SSE 实时推送给前端的事件对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class NotificationEvent {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 当前工作流状态
     */
    private WorkflowState state;

    /**
     * 状态中文名称（前端展示用）
     */
    private String stateName;

    /**
     * 推送的消息内容（日志、进度说明等）
     */
    private String message;

    /**
     * 项目类型（VUE / HTML），路由决策后确定，前端用于预览路径
     */
    private String appType;

    /**
     * 事件发生时间
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 便捷构造方法
     */
    public static NotificationEvent of(String taskId, WorkflowState state, String message) {
        return NotificationEvent.builder()
                .taskId(taskId)
                .state(state)
                .stateName(state != null ? state.getName() : "INFO")
                .message(message)
                .build();
    }

    /**
     * 便捷构造方法（含项目类型）
     */
    public static NotificationEvent of(String taskId, WorkflowState state, String message, String appType) {
        return NotificationEvent.builder()
                .taskId(taskId)
                .state(state)
                .stateName(state.getName())
                .message(message)
                .appType(appType)
                .build();
    }
}
