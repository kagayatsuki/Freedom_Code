package com.coding.agent.workflow.impl;

import com.coding.agent.tools.PromptAiService;
import com.coding.agent.workflow.WorkflowContext;
import com.coding.agent.workflow.WorkflowNode;
import com.coding.agent.workflow.WorkflowState;
import dev.langchain4j.service.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouteNode implements WorkflowNode {
    private final PromptAiService aiService;
    @Override
    public WorkflowState execute(WorkflowContext context) {
        try {
            // 修改模式：项目已有文件 → 不重新路由，从已有文件推断类型
            if (!needsGeneration(context.getUserName(), context.getProjectName())) {
                String existingType = detectExistingProjectType(context.getUserName(), context.getProjectName());
                context.setAppType(existingType);
                log.info("修改模式 → 沿用已有项目类型: {}", existingType);
                return WorkflowState.GENERATING;
            }

            String enhancedPrompt = context.getEnhancedPrompt();
            if (enhancedPrompt == null) {
                enhancedPrompt=context.getOriginalPrompt();
            }
            log.info("开始执行智能路由");
            String lowerPrompt=enhancedPrompt.toLowerCase();
            if (lowerPrompt.endsWith("vue")) {
                context.setAppType("VUE");
                log.info("路由决策 → Vue 项目");
                return WorkflowState.GENERATING;
            }
            if (lowerPrompt.endsWith("html")) {

                context.setAppType("HTML");
                log.info("路由决策 → HTML 项目");
                return WorkflowState.GENERATING;
            }
            Result<String> routeResult = aiService.routeDecision(enhancedPrompt);
            context.addTokenUsed(routeResult);
            String decision = routeResult.content();

            if (decision.toUpperCase().contains("VUE")) {
                context.setAppType("VUE");
                log.info("AI 路由决策 → Vue 项目");
            } else {
                context.setAppType("HTML");
                log.info("AI 路由决策 → HTML 项目");
            }

            return WorkflowState.GENERATING;



        } catch (Exception e) {
            log.warn("路由节点异常，使用默认 HTML 项目", e);
            context.setAppType("HTML");        // 降级处理
            context.addErrorLog("路由决策失败: " + e.getMessage());
            return WorkflowState.GENERATING;
        }
    }

    /**
     * 从已有项目文件中推断项目类型（修改模式用）
     */
    private String detectExistingProjectType(String userName, String projectName) {
        Path projectDir = Path.of("D:/code", userName, projectName);
        // 有 package.json 且含 vue 依赖 → VUE
        Path packageJson = projectDir.resolve("package.json");
        if (Files.exists(packageJson)) {
            try {
                String content = Files.readString(packageJson).toLowerCase();
                if (content.contains("\"vue\"") || content.contains("\"@vitejs/plugin-vue\"")) {
                    return "VUE";
                }
            } catch (Exception ignored) {}
        }
        // 默认 HTML
        return "HTML";
    }

    /**
     * 检查项目是否需要新建（目录为空或不存在）
     */
    private boolean needsGeneration(String userName, String projectName) {
        Path projectDir = Path.of("D:/code", userName, projectName);
        if (!Files.exists(projectDir)) {
            return true;
        }
        try (var stream = Files.list(projectDir)) {
            return stream.findAny().isEmpty();
        } catch (IOException e) {
            return true;
        }
    }

    @Override
    public WorkflowState getTargetState() {
        return WorkflowState.ROUTED;
    }
}
