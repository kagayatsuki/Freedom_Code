package com.coding.agent.workflow.agent;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.coding.agent.service.FileService;
import com.coding.agent.tools.PromptAiService;
import com.coding.agent.workflow.WorkflowContext;
import dev.langchain4j.service.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 完成度评估器（multi-agent 中的 sub-agent）。
 * 独立于主编码 Agent，使用单独的 PromptAiService（无工具、无共享记忆），
 * 在主 Agent 每轮行动结束后，判断项目是否已达成目标。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskEvaluator {

    private final PromptAiService promptAiService;
    private final FileService fileService;

    /**
     * 评估项目是否已完成。
     *
     * @param ctx           任务上下文
     * @param actionSummary 主 Agent 最近行动摘要（工具调用 + 结果摘要）
     */
    public EvalResult evaluate(WorkflowContext ctx, String actionSummary) {
        try {
            String fileTree = JSONUtil.toJsonStr(fileService.getTree(ctx.getUserName(), ctx.getProjectName()));
            String input = """
                    【用户需求】：
                    %s

                    【当前项目完整文件结构】：
                    %s

                    【主 Agent 最近行动记录】：
                    %s

                    请判断项目是否已完成目标。
                    """.formatted(
                    ctx.getEnhancedPrompt(),
                    fileTree,
                    actionSummary == null || actionSummary.isBlank() ? "(无)" : actionSummary
            );

            Result<String> result = promptAiService.evaluateTaskCompletion(input);
            ctx.addTokenUsed(result);
            return parse(result.content());
        } catch (Exception e) {
            log.warn("[Evaluator] 评估失败，按未完成处理: {}", e.getMessage());
            return EvalResult.notDone("评估器异常: " + e.getMessage(), null);
        }
    }

    private EvalResult parse(String content) {
        if (content == null || content.isBlank()) {
            return EvalResult.notDone("评估器无输出", null);
        }
        try {
            JSONObject json = JSONUtil.parseObj(content);
            boolean done = json.getBool("done", false);
            String reason = json.getStr("reason", "");
            String next = json.getStr("next", "");
            return new EvalResult(done, reason, next);
        } catch (Exception e) {
            // 兜底：JSON 解析失败时，按文本含 "done": true 判断
            boolean done = content.contains("\"done\":true") || content.contains("\"done\": true")
                    || content.contains("done\": true");
            return new EvalResult(done, content, null);
        }
    }
}
