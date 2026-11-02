package com.coding.agent.workflow.agent;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.coding.agent.domain.po.ChatHistory;
import com.coding.agent.service.FileService;
import com.coding.agent.service.IChatHistoryService;
import com.coding.agent.tools.CodingAiServiceFactory;
import com.coding.agent.workflow.WorkflowContext;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ReAct 编码 Agent（multi-agent 显式循环版）。
 *
 * 循环由 Java 显式控制（不再是 AiServices 的隐式循环）：
 * 每一轮「主 Agent 单步决策 → 执行工具 → 独立评估器判断是否完成」，
 * 评估器判定完成（done=true）才退出循环，否则带反馈继续。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReActCodingAgent {

    private static final String BASE_ROOT = "D:\\code";

    private static final String SYSTEM_PROMPT = """
            /no_think

            你是一位资深前端工程 Agent，负责独立完成一个可运行的前端项目的开发、自检、构建与修复。

            【工作方式】
            1. 每轮你通过调用工具执行动作（写文件、读文件、修改文件、审查、构建等）。
            2. 工具执行后，系统里一个独立的「评估器」会判断项目是否已完成目标：
               - 若未完成，评估器会给你反馈与建议，请据此继续下一步。
               - 若已完成，任务结束。
            3. 当你认为项目已经完成时，这一轮不要调用任何工具，直接输出一句完成说明即可，评估器会做最终确认。

            【可用工具】
            1. writeFile / readFile / modifyFile / getProjectTree —— 读写项目文件。
            2. reviewFile(path) —— 写完关键文件后主动调用，审查语法/逻辑错误。
            3. runBuild() —— 项目文件齐全后主动调用，执行 npm install 与 npm run build。
            4. searchWeb / searchImages —— 需要外部资料或真实图片时调用（仅开启时可用）。

            【铁律】
            1. 每个文件单独调用一次 writeFile，严禁合并；path 只用相对路径，禁止盘符与 ../ 逃逸。
            2. Vue 项目必须纯 JavaScript，严禁 TypeScript；package.json 的 build 只能写 "vite build"。
            3. build 或 review 返回失败时，精准定位报错文件，用 readFile + modifyFile 修复，严禁无脑全量重写。
            4. 同一动作不要连续重复超过 3 次。
            """;

    private final CodingAiServiceFactory codingAiServiceFactory;
    private final FileService fileService;
    private final IChatHistoryService chatHistoryService;
    private final AgentLoopConfig config;
    private final AgentStepTracker stepTracker;
    private final ChatLanguageModel chatModel;
    private final TaskEvaluator evaluator;

    public AgentResult run(WorkflowContext ctx) {
        stepTracker.reset();

        try {
            // 1. 工具注册：ToolSpecification + 工具名 -> 方法映射
            List<Object> tools = codingAiServiceFactory.resolveTools(ctx);
            ToolRegistry toolRegistry = buildToolRegistry(tools);

            // 2. 初始消息
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(SYSTEM_PROMPT));
            messages.add(UserMessage.from(buildTaskPrompt(ctx)));

            StringBuilder actionLog = new StringBuilder();

            // 3. 显式 ReAct 循环
            int step = 0;
            for (; step < config.maxSteps(); step++) {
                if (ctx.isCancelled()) {
                    return AgentResult.failed("任务已被用户取消", step, ctx.getTokenUsed());
                }
                if (ctx.getTokenUsed() > config.maxTokens()) {
                    return AgentResult.failed("token 预算超限(" + ctx.getTokenUsed() + ")", step, ctx.getTokenUsed());
                }

                // 3.1 主 Agent 单步决策（同步，超时由 chat-model.timeout=120s 控制）
                Response<AiMessage> response = chatModel.generate(messages, toolRegistry.specs());
                ctx.addTokenUsed(response.tokenUsage());
                AiMessage aiMessage = response.content();
                logStep(step, aiMessage, response.tokenUsage());

                // 3.2 无工具调用：主 Agent 认为完成 → 评估器确认
                if (!aiMessage.hasToolExecutionRequests()) {
                    EvalResult eval = evaluator.evaluate(ctx, actionLog.toString());
                    log.info("[ReAct][step {}] 无工具调用，评估器：done={} | reason={}", step, eval.done(), truncate(eval.reason(), 120));
                    if (eval.done()) {
                        log.info("[ReAct] 任务 {} 评估器判定完成（step={}）: {}", ctx.getTaskId(), step, eval.reason());
                        return AgentResult.success(eval.reason(), step, ctx.getTokenUsed());
                    }
                    messages.add(aiMessage);
                    messages.add(UserMessage.from(buildEvaluatorFeedback(eval, "尚未完成")));
                    continue;
                }

                // 3.3 执行本轮所有工具；写入上下文前裁剪 writeFile 的 content，防止完整代码永久累积导致 token 爆炸
                List<ToolExecutionResultMessage> toolResults = new ArrayList<>();
                List<ToolExecutionRequest> compactRequests = new ArrayList<>();
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    String result = executeTool(toolRegistry.registry(), request);
                    log.info("[ReAct][step {}] 工具 {} → {}", step, request.name(), truncate(result, 120));
                    actionLog.append("[工具 ").append(request.name()).append("] ")
                            .append(truncate(result, 200)).append('\n');

                    ToolExecutionRequest compact = ToolExecutionRequest.builder()
                            .id(request.id())
                            .name(request.name())
                            .arguments(compactArgs(request.arguments()))
                            .build();
                    compactRequests.add(compact);
                    toolResults.add(ToolExecutionResultMessage.from(compact, result));
                }
                messages.add(AiMessage.from(compactRequests));
                messages.addAll(toolResults);

                // 3.4 每次行动后：独立评估器强制判断
                EvalResult eval = evaluator.evaluate(ctx, actionLog.toString());
                log.info("[ReAct][step {}] 评估器：done={} | reason={} | next={}",
                        step, eval.done(), truncate(eval.reason(), 120), truncate(eval.next(), 120));
                if (eval.done()) {
                    log.info("[ReAct] 任务 {} 评估器判定完成（step={}）: {}", ctx.getTaskId(), step, eval.reason());
                    return AgentResult.success(eval.reason(), step, ctx.getTokenUsed());
                }
                messages.add(UserMessage.from(buildEvaluatorFeedback(eval, "继续")));

                // 3.5 上下文裁剪：保留 system + 初始 user + 最近消息，防止超出模型上下文
                if (messages.size() > 40) {
                    List<ChatMessage> trimmed = new ArrayList<>();
                    trimmed.add(messages.get(0));
                    trimmed.add(messages.get(1));
                    trimmed.addAll(messages.subList(messages.size() - 38, messages.size()));
                    messages = trimmed;
                }
            }

            return AgentResult.failed("达到最大轮次限制(" + config.maxSteps() + ")", step, ctx.getTokenUsed());
        } catch (Exception e) {
            log.error("[ReAct] 任务 {} 循环异常", ctx.getTaskId(), e);
            throw e; // 交给 ReActCodingNode 处理（转 GENERATING 重试）
        }
    }

    /** 拆解并打印本步 LLM 返回：工具调用 or 纯文本，用于定位「模型有没有正常产出 tool call」。 */
    private void logStep(int step, AiMessage aiMessage, TokenUsage usage) {
        String tok = usage != null && usage.totalTokenCount() != null ? String.valueOf(usage.totalTokenCount()) : "?";
        if (aiMessage.hasToolExecutionRequests()) {
            List<ToolExecutionRequest> reqs = aiMessage.toolExecutionRequests();
            String toolDesc = reqs.stream()
                    .map(r -> r.name() + "(" + summarizeArgs(r.arguments()) + ")")
                    .collect(Collectors.joining(", "));
            log.info("[ReAct][step {}] LLM 返回 {} 个工具调用 | token={} | {}", step, reqs.size(), tok, toolDesc);
        } else {
            log.info("[ReAct][step {}] LLM 返回纯文本（无工具调用）| token={} | 内容：{}",
                    step, tok, truncate(aiMessage.text(), 300));
        }
    }

    /** 精简工具参数：优先展示 path 与 content 长度，避免打印整段代码刷屏。 */
    private String summarizeArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return "";
        }
        try {
            JSONObject json = JSONUtil.parseObj(argumentsJson);
            String path = json.getStr("path");
            String content = json.getStr("content");
            if (path != null) {
                int len = content == null ? 0 : content.length();
                return len > 0 ? "path=" + path + ", content长度=" + len : "path=" + path;
            }
        } catch (Exception ignored) {
            // fallthrough
        }
        return truncate(argumentsJson, 120);
    }

    /**
     * 裁剪写入上下文的工具参数，防止 writeFile/modifyFile 的完整代码永久累积在 messages 里导致 token 爆炸。
     * content/oldContent/newContent 只保留前 200 字符，需要完整内容时模型会用 readFile 重新读取。
     */
    private String compactArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return argumentsJson;
        }
        try {
            JSONObject json = JSONUtil.parseObj(argumentsJson);
            boolean changed = false;
            for (String key : new String[]{"content", "oldContent", "newContent"}) {
                String value = json.getStr(key);
                if (value != null && value.length() > 200) {
                    json.set(key, truncate(value, 200) + " ...[已截断，原长 " + value.length() + "]");
                    changed = true;
                }
            }
            return changed ? json.toString() : argumentsJson;
        } catch (Exception e) {
            return argumentsJson;
        }
    }

    private ToolRegistry buildToolRegistry(List<Object> tools) {
        List<ToolSpecification> specs = new ArrayList<>();
        Map<String, ToolMethod> registry = new HashMap<>();
        for (Object tool : tools) {
            specs.addAll(ToolSpecifications.toolSpecificationsFrom(tool));
            for (Method method : tool.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    String name = method.getAnnotation(Tool.class).name();
                    if (name == null || name.isBlank()) {
                        name = method.getName();
                    }
                    registry.put(name, new ToolMethod(tool, method));
                }
            }
        }
        return new ToolRegistry(specs, registry);
    }

    private String executeTool(Map<String, ToolMethod> registry, ToolExecutionRequest request) {
        ToolMethod toolMethod = registry.get(request.name());
        if (toolMethod == null) {
            return "错误：未知工具 " + request.name();
        }
        try {
            Object[] args = resolveArgs(toolMethod.method(), request.arguments());
            Object result = toolMethod.method().invoke(toolMethod.instance(), args);
            return result == null ? "" : result.toString();
        } catch (Exception e) {
            log.warn("[ReAct] 工具 {} 执行失败", request.name(), e);
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return "错误：工具执行失败 - " + cause.getMessage();
        }
    }

    private Object[] resolveArgs(Method method, String argumentsJson) {
        Parameter[] params = method.getParameters();
        if (params.length == 0) {
            return new Object[0];
        }
        JSONObject json = (argumentsJson == null || argumentsJson.isBlank())
                ? new JSONObject() : JSONUtil.parseObj(argumentsJson);
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            args[i] = convert(json.get(params[i].getName()), params[i].getType());
        }
        return args;
    }

    private Object convert(Object raw, Class<?> type) {
        if (raw == null) {
            if (type == int.class || type == long.class) return 0;
            if (type == boolean.class) return false;
            return null;
        }
        if (type == String.class) return raw.toString();
        if (type == int.class || type == Integer.class) return Integer.valueOf(raw.toString());
        if (type == long.class || type == Long.class) return Long.valueOf(raw.toString());
        if (type == boolean.class || type == Boolean.class) return Boolean.valueOf(raw.toString());
        return raw;
    }

    private String buildEvaluatorFeedback(EvalResult eval, String prefix) {
        StringBuilder sb = new StringBuilder();
        sb.append("【评估器判定】").append(prefix).append("：").append(eval.reason());
        if (eval.next() != null && !eval.next().isBlank()) {
            sb.append("\n【下一步建议】").append(eval.next());
        }
        sb.append("\n请继续执行。");
        return sb.toString();
    }

    private String buildTaskPrompt(WorkflowContext ctx) {
        boolean isNewProject = needsGeneration(ctx.getUserName(), ctx.getProjectName());
        String fileTree = JSONUtil.toJsonStr(fileService.getTree(ctx.getUserName(), ctx.getProjectName()));
        String history = getConversationHistory(ctx);

        String modeDesc = isNewProject
                ? "新建模式（项目为空，请从零构建完整项目）"
                : "修改模式（项目已存在）";
        String modeRule = isNewProject
                ? ""
                : "1. 你只能修改已存在的文件，禁止新增不在上面文件树中的新文件（除非用户明确要求）。\n"
                + "2. path 参数必须使用相对路径，且必须是文件树中已存在的文件。\n";

        return """
                【当前项目环境执行上下文（绝对事实，必须严格遵守）】：
                - 当前操作的真实用户名 (userName) 是: %s
                - 当前操作的真实项目名 (projectName) 是: %s
                - 项目绝对根路径: %s
                - 项目类型 (appType): %s

                【当前项目完整文件结构（最新）】：
                %s

                【该项目的历史对话上下文（请据此了解项目背景）】：
                %s

                【操作模式】：%s
                %s
                【用户真实需求】：
                %s
                """.formatted(
                ctx.getUserName(),
                ctx.getProjectName(),
                ctx.getProjectRootPath(),
                ctx.getAppType(),
                fileTree,
                history,
                modeDesc,
                modeRule,
                ctx.getEnhancedPrompt()
        );
    }

    private boolean needsGeneration(String userName, String projectName) {
        Path projectDir = Path.of(BASE_ROOT, userName, projectName);
        if (!Files.exists(projectDir)) {
            return true;
        }
        try (var stream = Files.list(projectDir)) {
            return stream.findAny().isEmpty();
        } catch (IOException e) {
            return true;
        }
    }

    private String getConversationHistory(WorkflowContext context) {
        List<ChatHistory> history = chatHistoryService.getRecentMessages(
                context.getUserName(), context.getProjectName(), 20);
        if (history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【该项目的历史对话上下文，请在修改时参考】\n");
        for (ChatHistory h : history) {
            String label = switch (h.getMessageType()) {
                case "USER_CREATE" -> "📋 初始需求";
                case "AI_CREATE" -> "🤖 当时生成的代码";
                case "USER_MODIFY" -> "🔧 修改指令";
                case "AI_MODIFY" -> "✅ 修改结果";
                default -> "💬";
            };
            boolean isAiMsg = "AI_CREATE".equals(h.getMessageType()) || "AI_MODIFY".equals(h.getMessageType());
            String content;
            if (isAiMsg && h.getSummary() != null && !h.getSummary().isBlank()) {
                content = h.getSummary();
            } else if (isAiMsg) {
                content = truncate(h.getMessage(), 150);
            } else {
                content = h.getMessage();
            }
            sb.append(label).append(": ").append(content).append("\n");
        }
        return sb.toString();
    }

    private String truncate(String msg, int maxLen) {
        if (msg == null || msg.length() <= maxLen) {
            return msg;
        }
        return msg.substring(0, maxLen) + "...";
    }

    private record ToolMethod(Object instance, Method method) {}

    private record ToolRegistry(List<ToolSpecification> specs, Map<String, ToolMethod> registry) {}
}
