package com.coding.agent.tools;

import com.coding.agent.service.FileService;
import com.coding.agent.utils.WorkflowContextHolder;
import com.coding.agent.workflow.WorkflowContext;
import com.coding.agent.workflow.WorkflowState;
import com.coding.agent.workflow.agent.AgentLoopConfig;
import com.coding.agent.workflow.agent.AgentStepTracker;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 代码审查工具：由 CheckNode 抽取，结果作为 Observation 返回给 Agent。
 * 注意：内部再调 LLM 使用独立的 PromptAiService，避免与主循环 AiServices 互相嵌套递归。
 */
@Slf4j
@Component("codeReviewTool")
@RequiredArgsConstructor
public class CodeReviewTool {

    private final FileService fileService;
    private final PromptAiService promptAiService;
    private final AgentLoopConfig agentLoopConfig;
    private final AgentStepTracker stepTracker;

    @Tool("""
            审查指定相对路径文件的前端代码（语法、逻辑、运行时风险）。
            何时调用：每写完一个关键文件后，主动调用它以尽早发现错误。
            返回：该文件的问题清单；若 passed 为 true 表示通过。
            """)
    public String reviewFile(String userName, String projectName, String path) {
        if (!stepTracker.beforeStep("reviewFile", agentLoopConfig.maxSteps(), agentLoopConfig.maxConsecutiveRepeat())) {
            return "[AGENT_STOP] 已达到最大步数或重复次数限制，请立即停止并输出最终结论。";
        }

        WorkflowContext context = WorkflowContextHolder.getContext();
        if (context != null && context.isCancelled()) {
            return "[AGENT_STOP] 任务已被用户取消，请立即停止并输出最终结论。";
        }
        if (context != null) {
            context.notifyAgentEvent(WorkflowState.QC_CHECKING, "正在审查文件: " + path);
        }

        try {
            String content = fileService.readFile(userName, projectName, path);
            if (content == null || content.isBlank()) {
                return "REVIEW_EMPTY: 文件 " + path + " 内容为空或不存在";
            }
            Result<String> review = promptAiService.reviewCodeForAgent(content);
            if (context != null) {
                context.addTokenUsed(review);
                context.addReviewReport("文件: " + path + "\n" + review.content());
            }
            return "REVIEW_RESULT(path=" + path + "):\n" + review.content();
        } catch (Exception e) {
            log.error("reviewFile 执行异常", e);
            return "REVIEW_ERROR: " + e.getMessage();
        }
    }
}
