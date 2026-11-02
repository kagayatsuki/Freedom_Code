package com.coding.agent.workflow.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * ReAct 循环边界护栏配置，对应 application.yaml 的 agent.react.*。
 */
@ConfigurationProperties(prefix = "agent.react")
public record AgentLoopConfig(
        @DefaultValue("false") boolean enabled,   // 灰度开关：true 走 ReAct，false 走旧状态机
        @DefaultValue("40") int maxSteps,         // 最大工具调用步数
        @DefaultValue("200000") long maxTokens,   // 单任务 token 预算
        @DefaultValue("15") int timeoutMinutes,   // 循环超时（分钟）
        @DefaultValue("3") int maxConsecutiveRepeat, // 同一动作连续重复上限（防死循环）
        @DefaultValue("true") boolean enableBuild,   // 是否允许 Agent 主动构建
        @DefaultValue("true") boolean enableReview   // 是否允许 Agent 主动审查
) {}
