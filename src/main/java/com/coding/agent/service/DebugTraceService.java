package com.coding.agent.service;

import com.coding.agent.workflow.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug 链路追踪服务。
 * <p>
 * 将一次工作流任务的完整链路信息写入 log/{yyyyMMdd_HHmmss}.txt，内容包括：
 * 1. 初始提示词；2. 增强后提示词；3. 图片/联网搜索开关及内容；4. 路由选择；
 * 5. 写入的文件；6. RAG 检索结果；7. 代码审查报告与结果。
 * <p>
 * 设计约束：不修改项目内已有的 log.info、不拦截/提取工具内部调用、不改变原有业务逻辑；
 * 仅依赖 WorkflowContext 中已采集的数据 + 工作流收尾时的一次快照生成文件。
 * Debug 链路本身失败不影响主流程。
 */
@Slf4j
@Component
public class DebugTraceService {

    private static final String LOG_DIR = "log";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String SEP = "\n" + "=".repeat(80) + "\n";

    public void dump(WorkflowContext ctx) {
        if (ctx == null) {
            return;
        }
        try {
            Path dir = Paths.get(LOG_DIR);
            Files.createDirectories(dir);
            LocalDateTime ts = ctx.getCreateTime() != null ? ctx.getCreateTime() : LocalDateTime.now();
            Path file = dir.resolve(ts.format(TS) + ".txt");
            Files.writeString(file, buildTrace(ctx), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("[DebugTrace] 调试链路文件已写入: {}", file.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[DebugTrace] 调试链路写入失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private String buildTrace(WorkflowContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append(SEP).append("工作流 Debug 链路\n");
        sb.append("taskId      : ").append(nvl(ctx.getTaskId())).append("\n");
        sb.append("userName    : ").append(nvl(ctx.getUserName())).append("\n");
        sb.append("projectName : ").append(nvl(ctx.getProjectName())).append("\n");
        sb.append("项目类型    : ").append(nvl(ctx.getAppType())).append("\n");
        sb.append("最终状态    : ").append(ctx.getCurrentState() == null ? "N/A" : ctx.getCurrentState().getName()).append("\n");
        sb.append("生成时间    : ").append(LocalDateTime.now().format(TS)).append("\n");

        sb.append(SEP).append("【1. 初始提示词】\n").append(nvl(ctx.getOriginalPrompt())).append("\n");

        sb.append(SEP).append("【2. 提示词增强后的提示词】\n").append(nvl(ctx.getEnhancedPrompt())).append("\n");

        sb.append(SEP).append("【3. 图片增强 / 联网搜索】\n");
        sb.append("使用图片增强: ").append(ctx.isUseImageSearch()).append("\n");
        sb.append("使用联网搜索: ").append(ctx.isUseWebSearch()).append("\n");
        sb.append("联网搜索内容:\n").append(formatList(ctx.getWebSearchContent(), "  （未采集到搜索内容）")).append("\n");

        sb.append(SEP).append("【4. 路由选择】\n").append(nvl(ctx.getAppType())).append("\n");

        sb.append(SEP).append("【5. 写入的文件】\n").append(collectWrittenFiles(ctx)).append("\n");

        sb.append(SEP).append("【6. RAG 检索结果】\n").append(formatList(ctx.getRagResults(), "  （无相似案例）")).append("\n");

        sb.append(SEP).append("【7. 审查报告与结果】\n").append(formatList(ctx.getReviewReports(), "  （未执行代码审查）")).append("\n");

        sb.append(SEP);
        return sb.toString();
    }

    private String collectWrittenFiles(WorkflowContext ctx) {
        String rootStr = ctx.getProjectRootPath();
        if (rootStr == null || rootStr.isBlank()) {
            return "  （无项目根路径）";
        }
        Path root = Paths.get(rootStr);
        if (!Files.exists(root)) {
            return "  （项目目录不存在）";
        }
        List<String> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if ("node_modules".equals(name) || "dist".equals(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    files.add(root.relativize(file).toString().replace("\\", "/"));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "  （扫描文件失败: " + e.getMessage() + "）";
        }
        if (files.isEmpty()) {
            return "  （无文件）";
        }
        StringBuilder sb = new StringBuilder();
        for (String f : files) {
            sb.append("  - ").append(f).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private String formatList(List<String> list, String emptyMsg) {
        if (list == null || list.isEmpty()) {
            return emptyMsg;
        }
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append("  - ").append(s).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "（未采集）" : s;
    }
}
