package com.coding.agent.workflow;

import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.Result;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 工作流上下文 - 承载整个任务生命周期的所有状态数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext {

    /**
     * 任务唯一ID
     */
    private String taskId;
    /**
     * 当前登录用户名（从 SecurityContext 获取）
     */
    private String userName;

    /**
     * 项目名称（由用户在前端输入，或默认生成）
     */
    private String projectName;
    /**
     * 用户原始需求 Prompt
     */
    private String originalPrompt;

    /**
     * 增强后的 Prompt（架构设计 + 技术选型）
     */
    private String enhancedPrompt;

    /**
     * 项目类型：VUE / HTML
     */
    private String appType;

    /**
     * 项目根目录（本地临时目录）
     */
    private String projectRootPath;

    /**
     * 当前状态
     */
    private WorkflowState currentState;

    /**
     * 重试次数（QC、Build、Validate 失败后回滚重试）
     */
    private int retryCount = 0;

    /**
     * 错误日志收集（用于让 AI 后续修复）
     */
    @Builder.Default
    private List<String> errorLogs = new CopyOnWriteArrayList<>();

    /**
     * 额外元数据（可扩展）
     */
    @Builder.Default
    private Map<String, Object> metadata = new ConcurrentHashMap<>();

    /**
     * 任务创建时间
     */
    private LocalDateTime createTime;

    /**
     * 任务完成时间
     */
    private LocalDateTime completeTime;

    //是否勾选高级图片搜索
    private boolean useImageSearch;

    private boolean useWebSearch;

    /**
     * 是否为新建项目（限流计数用）
     */
    private boolean isNewProject;

    /**
     * 本次任务累计消耗的 LLM token（输入+输出，含工具调用轮次）
     */
    @Builder.Default
    private long tokenUsed = 0;

    /**
     * 取消标志：true 表示用户已请求取消，节点/工具应在下一次检查点退出。
     */
    private volatile boolean cancelled;

    public void cancel() {
        this.cancelled = true;
    }

    private Consumer<String> fileWriteNotifier;
    public void notifyFileWritten(String message) {
        if (fileWriteNotifier != null) {
            fileWriteNotifier.accept(message);
        }
    }

    /**
     * ReAct 循环内部的通用事件回调（如「正在构建」「正在审查」），
     * 与 fileWriteNotifier 并列，由工具侧按需调用。
     */
    private Consumer<NotificationEvent> agentEventNotifier;

    public void notifyAgentEvent(WorkflowState state, String message) {
        if (agentEventNotifier != null) {
            agentEventNotifier.accept(NotificationEvent.of(taskId, state, message, appType));
        }
    }

    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * 添加错误日志
     */
    public void addErrorLog(String log) {
        this.errorLogs.add(log);
    }

    /**
     * 清空错误日志（修复后使用）
     */
    public void clearErrorLogs() {
        this.errorLogs.clear();
    }

    /**
     * 从一次 AI 调用结果中累计 token 用量（输入+输出）
     */
    public void addTokenUsed(Result<?> result) {
        if (result == null) return;
        addTokenUsed(result.tokenUsage());
    }

    /**
     * 从流式调用的 TokenUsage 中累计 token 用量
     */
    public void addTokenUsed(TokenUsage usage) {
        if (usage != null && usage.totalTokenCount() != null) {
            this.tokenUsed += usage.totalTokenCount();
        }
    }

    // ==================== Debug 链路采集字段（仅用于 /log 调试文件，不参与业务逻辑） ====================

    @Builder.Default
    private List<String> webSearchContent = new CopyOnWriteArrayList<>();

    @Builder.Default
    private List<String> ragResults = new CopyOnWriteArrayList<>();

    @Builder.Default
    private List<String> reviewReports = new CopyOnWriteArrayList<>();

    public void addWebSearchContent(String content) {
        if (this.webSearchContent == null) this.webSearchContent = new CopyOnWriteArrayList<>();
        this.webSearchContent.add(content);
    }

    public void addRagResult(String result) {
        if (this.ragResults == null) this.ragResults = new CopyOnWriteArrayList<>();
        this.ragResults.add(result);
    }

    public void addReviewReport(String report) {
        if (this.reviewReports == null) this.reviewReports = new CopyOnWriteArrayList<>();
        this.reviewReports.add(report);
    }
}
