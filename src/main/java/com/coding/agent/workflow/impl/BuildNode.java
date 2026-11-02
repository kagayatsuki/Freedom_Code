package com.coding.agent.workflow.impl;

import com.coding.agent.workflow.WorkflowContext;
import com.coding.agent.workflow.WorkflowNode;
import com.coding.agent.workflow.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "agent.react.enabled", havingValue = "false", matchIfMissing = true)
public class BuildNode implements WorkflowNode {

    private static final String BASE_ROOT = "D:\\code";
    private static final int BUILD_TIMEOUT_MINUTES = 5;

    @Override
    public WorkflowState execute(WorkflowContext context) {
        String projectRoot = context.getProjectRootPath();
        if (projectRoot == null) {
            context.addErrorLog("项目根目录为空，无法执行构建");
            return WorkflowState.GENERATING;
        }
        if (!"VUE".equalsIgnoreCase(context.getAppType())) {
            log.info("HTML 项目，跳过构建阶段");
            return WorkflowState.QC_CHECKING;
        }

        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
            Charset charset = isWindows ? Charset.forName("GBK") : Charset.defaultCharset();

            // ==========================================
            // 阶段 1: 执行 npm install
            // ==========================================
            log.info("开始执行 npm install | 项目路径: {}", projectRoot);
            ProcessBuilder installPb;
            if (isWindows) {
                installPb = new ProcessBuilder("cmd.exe", "/c", "npm", "install");
            } else {
                installPb = new ProcessBuilder("npm", "install");
            }
            installPb.directory(new File(projectRoot));
            installPb.redirectErrorStream(true); // 合并错误流和输出流

            Process installProcess = installPb.start();

            // 【重要修复】：必须消耗掉 install 过程的输出流，否则会导致进程挂起（死锁）
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(installProcess.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Install] {}", line);
                }
            }

            int installExitCode = installProcess.waitFor();
            if (installExitCode != 0) {
                context.addErrorLog("npm install 失败，退出码: " + installExitCode);
                return WorkflowState.GENERATING;
            }
            log.info("✅ npm install 执行成功！");

            // ==========================================
            // 阶段 2: 执行 npm run build
            // ==========================================
            log.info("开始执行 npm run build | 项目路径: {}", projectRoot);
            ProcessBuilder buildPb;
            if (isWindows) {
                buildPb = new ProcessBuilder("cmd.exe", "/c", "npm", "run", "build");
            } else {
                buildPb = new ProcessBuilder("npm", "run", "build");
            }
            // 【重要修复】：使用动态上下文路径，千万不能写死
            buildPb.directory(new File(projectRoot));
            buildPb.redirectErrorStream(true);

            // 【重要修复】：只 start 一次
            Process buildProcess = buildPb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(buildProcess.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Build] {}", line);
                    // TODO: 可注入 NotificationService 实时推送构建日志给前端用户
                }
            }

            boolean finished = buildProcess.waitFor(BUILD_TIMEOUT_MINUTES, TimeUnit.MINUTES);

            if (!finished) {
                buildProcess.destroyForcibly();
                context.addErrorLog("构建超时（超过" + BUILD_TIMEOUT_MINUTES + "分钟）");
                return WorkflowState.GENERATING; // 回滚重试
            }

            int exitCode = buildProcess.exitValue();

            if (exitCode == 0) {
                log.info("✅ npm run build 执行成功！");
                return WorkflowState.QC_CHECKING;
            } else {
                context.addErrorLog("构建失败，退出码: " + exitCode);
                return WorkflowState.GENERATING; // 失败回滚到代码生成
            }

        } catch (Exception e) {
            log.error("BuildNode 执行异常", e);
            context.addErrorLog("构建过程发生异常: " + e.getMessage());
            return WorkflowState.GENERATING;
        }
    }

    @Override
    public WorkflowState getTargetState() {
        return WorkflowState.BUILDING;
    }
}