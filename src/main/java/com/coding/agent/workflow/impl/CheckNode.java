package com.coding.agent.workflow.impl;

import com.coding.agent.service.FileService;
import com.coding.agent.tools.CodingAiService;
import com.coding.agent.tools.CodingAiServiceFactory;
import com.coding.agent.tools.PromptAiService;
import com.coding.agent.workflow.WorkflowContext;
import com.coding.agent.workflow.WorkflowNode;
import com.coding.agent.workflow.WorkflowState;
import dev.langchain4j.service.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "agent.react.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class CheckNode implements WorkflowNode {
    private final FileService fileService;
    private final CodingAiServiceFactory codingAiServiceFactory;
    private final PromptAiService promptAiService;
    @Override
    public WorkflowState execute(WorkflowContext context) {
        CodingAiService codingAiService= codingAiServiceFactory.createService(context);
        String taskId = null;
        String userName = null;
        String projectName = null;
        try {
            
            taskId = context.getTaskId();
            userName = context.getUserName();
            projectName = context.getProjectName();
            File distHtmlFile = new File(String.format("D:/code/%s/%s/dist/index.html", userName, projectName));

            if (distHtmlFile.exists()) {
                log.info("🛡️ [安全门禁] 检测到打包产物 dist/index.html 存在，启动物理路径强锁...");
                String htmlContent = Files.readString(distHtmlFile.toPath(), StandardCharsets.UTF_8);

                // 强行把所有带前导斜杠的资源全部清洗成相对路径
                String cleanedHtml = htmlContent
                        .replaceAll("src=\"/assets/", "src=\"assets/")
                        .replaceAll("href=\"/assets/", "href=\"assets/")
                        .replaceAll("src='/assets/", "src='assets/")
                        .replaceAll("href='/assets/", "href='assets/")
                        .replaceAll("href=\"/vite.svg\"", "href=\"vite.svg\""); // 兼容可能存在的 svg icon

                // 物理覆盖写入，确保百分之百不会写错到 anonymous 幽灵目录
                Files.writeString(distHtmlFile.toPath(), cleanedHtml, StandardCharsets.UTF_8);
                log.info("✅ [安全门禁] 物理清洗完成！当前项目的绝对路径已被彻底剿灭。");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            // 🚀 降维打击：直接拿到已经被底层洗得干干净净的核心资产相对路径列表
            List<String> checkFiles = fileService.getTree4Check(userName, projectName);

            if (checkFiles.isEmpty()) {
                log.info("{}:✨ 未检测到任何需要审计的核心前端资产，自动放行。",taskId );
                return WorkflowState.COMPLETED;
            }

            boolean hasError = false;

            // 逐个核心文件定向爆破审计
            for (String relativePath : checkFiles) {
                log.info(taskId, "📄 正在精准审计核心资产: " + relativePath);

                String fileContent = fileService.readFile(userName, projectName, relativePath);
                String userMessage = String.format("【相对路径】: %s\n\n【内容】:\n%s", relativePath, fileContent);


// 🛡️ 核心修复：将单大括号转义为双大括号，防止 LangChain4j 误认为是 Prompt 模板变量


// 🤖 传入安全处理后的文本
                Result<String> review = codingAiService.reviewCode(fileContent);
                context.addTokenUsed(review);
                String reviewResult = review.content();
                context.addReviewReport("文件: " + relativePath + "\n" + reviewResult);
                System.out.println(taskId + ":<UNK> <UNK>: " + " <UNK>: " + reviewResult);
//                String escapedReport = reviewResult
//                        .replace("{{", "{ {")
//                        .replace("}}", "} }");
//
//                String finalReviewResult = promptAiService.checkSuccess(escapedReport);
                if (reviewResult.contains("FAIL")) {
                    log.warn("🚨 资产 [{}] 质量审计未通过！", relativePath);
                    log.info(taskId, "❌ 质量拦截：文件 [" + relativePath + "] 存在不合规隐患。");

                    context.addErrorLog(reviewResult);
                    log.info(reviewResult, "");
                   // context.setTargetRepairPath(relativePath);
                    hasError = true;
                    break; // 只要查到一个硬伤，立刻熔断去修复，提高流转效率
                }
            }

            if (hasError) {
                log.info(taskId, "🔄 正在将工作流打回至 [GENERATING] 节点，启动 AI 物理自愈程序...");
                return WorkflowState.GENERATING;
            }

            log.info(taskId, "✅ 恭喜！全量核心资产通过合规性审计，准备挺进打包/构建阶段。");
            return WorkflowState.COMPLETED;

        } catch (Exception e) {
            log.error("QC 崩溃", e);
            return WorkflowState.FAILED;
        }

    }

    @Override
    public WorkflowState getTargetState() {
        return WorkflowState.QC_CHECKING;
    }
}

