package com.coding.agent.tools;

import com.coding.agent.utils.WorkflowContextHolder;
import com.coding.agent.workflow.WorkflowContext;
import com.coding.agent.workflow.WorkflowState;
import com.coding.agent.workflow.agent.AgentLoopConfig;
import com.coding.agent.workflow.agent.AgentStepTracker;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * 构建工具：由 BuildNode 抽取，结果作为 Observation 返回给 Agent。
 */
@Slf4j
@Component("buildTool")
@RequiredArgsConstructor
public class BuildTool {

    private static final String BASE_ROOT = "D:\\code";
    private static final int BUILD_TIMEOUT_MINUTES = 5;

    private final AgentLoopConfig agentLoopConfig;
    private final AgentStepTracker stepTracker;

    @Tool("""
            对指定项目执行 npm install 与 npm run build，返回构建成功或失败信息。
            何时调用：项目文件齐全后主动调用，确认可交付。
            失败处理：根据返回的报错日志，用 readFile + modifyFile 精准修复后再次调用。
            """)
    public String runBuild(String userName, String projectName) {
        if (!stepTracker.beforeStep("runBuild", agentLoopConfig.maxSteps(), agentLoopConfig.maxConsecutiveRepeat())) {
            return "[AGENT_STOP] 已达到最大步数或重复次数限制，请立即停止并输出最终结论。";
        }

        String projectRoot = BASE_ROOT + "\\" + userName + "\\" + projectName;
        WorkflowContext context = WorkflowContextHolder.getContext();
        if (context != null && context.isCancelled()) {
            return "[AGENT_STOP] 任务已被用户取消，请立即停止并输出最终结论。";
        }
        if (context != null) {
            context.notifyAgentEvent(WorkflowState.BUILDING, "正在执行 npm install ...");
        }

        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
            Charset charset = isWindows ? Charset.forName("GBK") : Charset.defaultCharset();

            // 阶段 1: npm install
            ProcessBuilder installPb = isWindows
                    ? new ProcessBuilder("cmd.exe", "/c", "npm", "install")
                    : new ProcessBuilder("npm", "install");
            installPb.directory(new File(projectRoot));
            installPb.redirectErrorStream(true);
            Process installProcess = installPb.start();
            StringBuilder installOut = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(installProcess.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    installOut.append(line).append('\n');
                }
            }
            int installExit = installProcess.waitFor();
            if (installExit != 0) {
                return "BUILD_FAILED: npm install 失败(退出码 " + installExit + ")，请阅读以下报错并修复后重试。\n"
                        + tail(installOut.toString(), 50);
            }
            log.info("✅ [BuildTool] npm install 执行成功 | {}", projectRoot);

            // 阶段 2: npm run build
            if (context != null) {
                context.notifyAgentEvent(WorkflowState.BUILDING, "正在执行 npm run build ...");
            }
            ProcessBuilder buildPb = isWindows
                    ? new ProcessBuilder("cmd.exe", "/c", "npm", "run", "build")
                    : new ProcessBuilder("npm", "run", "build");
            buildPb.directory(new File(projectRoot));
            buildPb.redirectErrorStream(true);
            Process buildProcess = buildPb.start();
            StringBuilder buildOut = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(buildProcess.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buildOut.append(line).append('\n');
                }
            }
            boolean finished = buildProcess.waitFor(BUILD_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                buildProcess.destroyForcibly();
                return "BUILD_FAILED: 构建超时（超过 " + BUILD_TIMEOUT_MINUTES + " 分钟）";
            }
            int buildExit = buildProcess.exitValue();
            if (buildExit == 0) {
                log.info("✅ [BuildTool] npm run build 执行成功 | {}", projectRoot);
                return "BUILD_SUCCESS: 项目构建通过。\n" + tail(buildOut.toString(), 20);
            }
            return "BUILD_FAILED: 构建失败(退出码 " + buildExit + ")，请阅读以下报错并修复后重试。\n"
                    + tail(buildOut.toString(), 60);

        } catch (Exception e) {
            log.error("runBuild 执行异常", e);
            return "BUILD_FAILED: " + e.getMessage();
        }
    }

    private String tail(String text, int maxLines) {
        if (text == null || text.isBlank()) {
            return "(无输出)";
        }
        String[] lines = text.split("\n");
        if (lines.length <= maxLines) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("...(共 ").append(lines.length).append(" 行，仅显示最后 ").append(maxLines).append(" 行)\n");
        for (int i = lines.length - maxLines; i < lines.length; i++) {
            sb.append(lines[i]).append('\n');
        }
        return sb.toString();
    }
}
