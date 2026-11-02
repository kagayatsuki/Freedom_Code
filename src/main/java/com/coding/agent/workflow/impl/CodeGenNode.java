package com.coding.agent.workflow.impl;

import com.coding.agent.config.RedisChatMemoryStore;
import com.coding.agent.domain.po.ChatHistory;
import com.coding.agent.service.FileService;
import com.coding.agent.service.IChatHistoryService;
import com.coding.agent.tools.CodingAiService;
import com.coding.agent.tools.CodingAiServiceFactory;
import com.coding.agent.workflow.WorkflowContext;
import com.coding.agent.workflow.WorkflowNode;
import com.coding.agent.workflow.WorkflowState;
import dev.langchain4j.service.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Component
@ConditionalOnProperty(name = "agent.react.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class CodeGenNode implements WorkflowNode {
    private final CodingAiServiceFactory codingAiServiceFactory;
    private final FileService fileService;
    private final IChatHistoryService chatHistoryService;
    private final RedisChatMemoryStore redisChatMemoryStore;
    private static final String BASE_ROOT = "D:\\code";
    @Override
    public WorkflowState execute(WorkflowContext context) {
        String projectRootPath = context.getProjectRootPath();
        if (projectRootPath == null) {
            context.addErrorLog("No project root path specified");
            return WorkflowState.FAILED;
        }
        try {
            log.info("CodeGenNode 开始执行 -代码生成 | 任务: {}| 项目类型：{}"
                    ,context.getTaskId(),context.getAppType());
            CodingAiService codingAiService=codingAiServiceFactory.createService(context);

            StringBuilder prompt = new StringBuilder(context.getEnhancedPrompt());
            String errorLogs="";
            if(!context.getErrorLogs().isEmpty()){
                errorLogs = String.join("\n", context.getErrorLogs());
                prompt.append("\n\n之前生成存在以下问题，请修复这些错误并重新生成完整代码：\n")
                        .append(errorLogs);
                context.clearErrorLogs();
            }
            log.info("当前图片生成状态：{}",
                    context.isUseImageSearch()
            );
            if (context.isUseImageSearch()) {
                prompt.append("""
          \n【图片资源】：
          如果你需要在页面中插入图片，请使用 searchImages 工具搜索真实图片 URL。
          调用格式：searchImages(英文关键词, 需要的数量)
          严禁编造图片地址或使用占位图。
          """);
            }
            // 🛡️ 强行在 Java 层进行动态文本替换，砸碎大模型的花括号幻觉
            String finalUserPrompt = """
                    【当前项目环境执行上下文（绝对事实，必须严格遵守）】：
                    - 当前操作的真实用户名 (userName) 是: %s
                    - 当前操作的真实项目名 (projectName) 是: %s
                    
                    【你的任务】：
                    请基于上述用户名和项目名，调用 writeFile 工具。
                    调用工具时，userName 参数必须填写 "%s"，projectName 参数必须填写 "%s"。
                    path 参数只允许填写相对路径（如 "index.html"），绝对不要在 path 里拼接用户名和项目名。
                    
                    【用户真实需求】：
                    %s
                    """.formatted(
                    context.getUserName(),
                    context.getProjectName(),
                    context.getUserName(),
                    context.getProjectName(),
                    context.getEnhancedPrompt()
            );
            String finalModifyPrompt = """
    【当前项目环境执行上下文（必须严格遵守）】：
    - 当前操作的真实用户名: %s
    - 当前操作的真实项目名: %s
    - 项目绝对根路径: %s
    
    【当前项目完整文件结构（最新）】：
    %s
    【该项目的历史对话上下文（请据此了解项目背景）】：
      %s
    
    【你的操作模式】：这是 **修改模式**（项目已存在）
    1. 你**只能修改已存在的文件**，禁止新增不在上面文件树中的新文件（除非用户明确要求新增）。
    2. path 参数必须使用**相对路径**，且必须是上面文件树中已存在的文件。
    3. 如果要修改某个文件，请直接输出该文件的完整新内容。
    4. 不要输出未被修改的文件内容。
    
    
    
    【用户本次真实需求】：
    %s
    """.formatted(
                    context.getUserName(),
                    context.getProjectName(),
                    context.getProjectRootPath(),
                    fileService.getTree(context.getUserName(),context.getProjectName()),
                   getConversationHistory(context),
                   // getConversationHistory(context),   // 新增
                    context.getEnhancedPrompt()
            );

            log.info("正在调用 AI 生成代码...");
            log.info(finalUserPrompt);
            log.info("InitNode 执行完成 | 任务ID: {} | 用户: {} | 项目: {} | 路径: {}",
                    context.getTaskId(), context.getUserName(), context.getProjectName(), context.getProjectRootPath());
            // 调用 AI 服务生成代码（流式）
            String safeUserPrompt = finalUserPrompt
                    .replace("{{", "{ {")
                    .replace("}}", "} }");
            String safeModifyPrompt = finalModifyPrompt
                    .replace("{{", "{ {")
                    .replace("}}", "} }");
            String generateCode;
            boolean isNewProject = needsGeneration(context.getUserName(), context.getProjectName());
            if (isNewProject) {
                chatHistoryService.saveMessage(
                    context.getUserName(),
                    context.getProjectName(),
                    context.getTaskId(),
                    context.getEnhancedPrompt(),
                    "USER_CREATE"
            );
                 Result<String> genResult = codingAiService.generateCode(
                        context.getTaskId(), // memoryId
                        safeUserPrompt
                );
                 context.addTokenUsed(genResult);
                 generateCode = genResult.content();
                 chatHistoryService.saveMessage(
                         context.getUserName(),
                         context.getProjectName(),
                         context.getTaskId(),
                         generateCode,
                         "AI_CREATE"
                 );
                 log.info(finalUserPrompt);
            }else {
                chatHistoryService.saveMessage(
                        context.getUserName(),
                        context.getProjectName(),
                        context.getTaskId(),
                        context.getEnhancedPrompt(),
                        "USER_MODIFY"
                );
                 Result<String> genResult = codingAiService.modifyCode(
                        context.getTaskId(), // memoryId
                        safeModifyPrompt
                );
                 context.addTokenUsed(genResult);
                 generateCode = genResult.content();
                chatHistoryService.saveMessage(
                        context.getUserName(),
                        context.getProjectName(),
                        context.getTaskId(),
                        generateCode,
                        "AI_MODIFY"
                );
                 log.info(finalModifyPrompt);
            }

            if (generateCode != null && !generateCode.trim().isEmpty()) {
                log.info("=================================== AI 生成的代码内容 ===================================");
                log.info("\n{}", generateCode);
                log.info("=================================== AI 代码结束 ===================================");
                log.info("AI 返回内容总长度: {} 字符", generateCode.length());
            } else {
                log.warn("⚠️ AI 返回内容为空！");
            }

            log.info("{}",generateCode);


            log.info("CodeGenNode AI 代码生成执行完毕 | 项目路径: {}", projectRootPath);
            if(isNewProject){
                return WorkflowState.BUILDING;
            }else {
                return WorkflowState.QC_CHECKING;
            }


        }catch (Exception e) {
            log.error("CodeGenNode 执行异常", e);
            // 超时/异常时清除本次任务的对话记忆，防止残留的 user 消息在下次生成时被注入导致提示词重复
            try {
                redisChatMemoryStore.deleteMessages(context.getTaskId());
            } catch (Exception ex) {
                log.warn("清理任务记忆失败: {}", ex.getMessage());
            }
            context.addErrorLog("代码生成阶段异常: " + e.getMessage());

            return WorkflowState.GENERATING; // 重试
        }
    }


    @Override
    public WorkflowState getTargetState() {
        return WorkflowState.GENERATING;
    }

    private void writeFile(Path root, String relativePath, String content) throws IOException {
        Path filePath = root.resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
        log.info("生成文件: {}", relativePath);
    }
    private boolean needsGeneration(String userName, String projectName) {
        Path projectDir = Path.of(BASE_ROOT, userName, projectName);

        if (!Files.exists(projectDir)) {
            return true;  // 目录都不存在，肯定是新建
        }

        try (var stream = Files.list(projectDir)) {
            return stream.findAny().isEmpty();  // 目录为空 → 新建；有文件 → 修改
        } catch (IOException e) {
            return true;  // 读取出错，保守走新建
        }
    }
    private String getConversationHistory(WorkflowContext context) {
        List<ChatHistory> history = chatHistoryService.getRecentMessages(
                context.getUserName(),
                context.getProjectName(),
                20
        );
        if (history.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【该项目的历史对话上下文，请在修改时参考】\n");
        for (ChatHistory h : history) {
            String label = switch (h.getMessageType()) {
                case "USER_CREATE" -> "📋 初始需求";
                case "AI_CREATE"   -> "🤖 当时生成的代码";
                case "USER_MODIFY" -> "🔧 修改指令";
                case "AI_MODIFY"   -> "✅ 修改结果";
                default -> "💬";
            };
            String content;
            boolean isAiMsg = "AI_CREATE".equals(h.getMessageType())
                    || "AI_MODIFY".equals(h.getMessageType());
            if (isAiMsg && h.getSummary() != null && !h.getSummary().isBlank()) {
                content = h.getSummary();                           // ← 一行摘要
            } else if (isAiMsg) {
                content = truncate(h.getMessage(), 150);             // ← 截断
            } else {
                content = h.getMessage();                            // ← 用户消息完整
            }

            sb.append(label).append(": ").append(content).append("\n");
        }
        return sb.toString();
    }

    private String truncate(String msg, int maxLen) {
        if (msg == null || msg.length() <= maxLen) return msg;
        return msg.substring(0, maxLen) + "...";
    }



}
