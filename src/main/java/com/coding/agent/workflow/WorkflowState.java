package com.coding.agent.workflow;


import lombok.Getter;

/**
 * AI 代码生成工作流状态枚举
 * <p>
 * 对应设计文档中的 8 个核心节点 + 终止状态
 */
@Getter
public enum WorkflowState {

    /**
     * 1. 初始化节点
     * - 创建项目空间、Trace ID、初始化上下文
     */
    INIT("初始化", "任务已创建，正在初始化项目空间"),

    /**
     * 2. 提示词增强节点
     * - 使用大模型补全架构设计、技术选型、组件拆分等
     */
    PROMPT_ENHANCED("提示词增强", "已完成需求分析与提示词工程化"),

    /**
     * 3. 智能路由节点
     * - 决定走 Vue 链路还是纯 HTML 静态链路
     */
    ROUTED("路由决策", "已确定项目类型（Vue/HTML）"),

    /**
     * 4. 代码生成节点
     * - 通过 Tool Calling 让 AI 自主写入/修改代码文件
     */
    GENERATING("代码生成", "AI 正在生成项目源码"),

    /**
     * 5. 质量检查节点
     * - 静态分析 + 大模型 Review
     */
    QC_CHECKING("质量检查", "正在进行代码质量检查"),

    /**
     * 6. 项目构建节点
     * - 执行 npm run build 等编译命令
     */
    BUILDING("项目构建", "正在编译构建前端项目"),

    /**
     * 7. 语义验证节点
     * - 无头浏览器截图 + 多模态验证
     */
    VALIDATING("语义验证", "正在进行运行时语义验证"),

    /**
     * 8. 成功完成
     */
    COMPLETED("已完成", "项目生成与验证全部成功"),

    /**
     * 失败/终止状态
     */
    FAILED("已失败", "工作流执行失败");

    /**
     * 中文名称（用于前端展示）
     */
    private final String name;

    /**
     * 默认描述（用于日志/SSE 推送）
     */
    private final String defaultDescription;

    WorkflowState(String name, String defaultDescription) {
        this.name = name;
        this.defaultDescription = defaultDescription;
    }

    /**
     * 是否为最终状态（不可继续流转）
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    /**
     * 是否允许回滚重试（QC/BUILD/VALIDATE 失败后可回到 GENERATING）
     */
    public boolean isRetryable() {
        return this == QC_CHECKING || this == BUILDING || this == VALIDATING;
    }

    /**
     * 判断当前状态是否处于生成阶段（广义）
     */
    public boolean isGeneratingPhase() {
        return this == GENERATING || this == QC_CHECKING || this == BUILDING || this == VALIDATING;
    }

}