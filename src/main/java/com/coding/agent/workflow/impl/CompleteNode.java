package com.coding.agent.workflow.impl;

import com.coding.agent.service.RateLimitService;
import com.coding.agent.workflow.WorkflowContext;
import com.coding.agent.workflow.WorkflowNode;
import com.coding.agent.workflow.WorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class CompleteNode implements WorkflowNode {
    private static final String BASE_ROOT = "D:\\code";
    private final RateLimitService rateLimitService;

    @Override
    public WorkflowState execute(WorkflowContext context) {
        try {
            context.setCompleteTime(LocalDateTime.now());

            Duration duration = Duration.between(
                    context.getCreateTime(),
                    context.getCompleteTime()
            );

            // 生成预览地址（使用相对路径）
            String previewUrl = generatePreviewUrl(context);

            // 存入 metadata，供前端获取
            context.getMetadata().put("previewUrl", previewUrl);
            context.getMetadata().put("status", "SUCCESS");
            context.getMetadata().put("durationSeconds", duration.toSeconds());
            context.getMetadata().put("projectRootPath", context.getProjectRootPath());
            String path = Paths.get(BASE_ROOT).relativize(Paths.get(context.getProjectRootPath())).toString();
            context.getMetadata().put("relativeBasePath", path);

            log.info("🎉 任务 {} 全部完成！", context.getTaskId());
            log.info("项目路径: {}", context.getProjectRootPath());
            log.info("前端预览地址: {}", previewUrl);
            log.info("总耗时: {} 秒", duration.toSeconds());

            // 限流计数：只有成功完成的才计入用量
            Long userId = rateLimitService.getUserIdByUsername(context.getUserName());
            if (userId != null) {
                rateLimitService.incrementUsage(userId, context.isNewProject());
                rateLimitService.addTokenUsage(userId, context.getTokenUsed());
            }
            return WorkflowState.COMPLETED;
        } catch (Exception e) {
            log.error("CompleteNode 执行异常", e);
            context.addErrorLog("完成阶段处理异常: " + e.getMessage());
            // 即使收尾失败，也标记为完成
            return WorkflowState.COMPLETED;
        }
    }

    private String generatePreviewUrl(WorkflowContext context) {
        String projectRootPath = context.getProjectRootPath();

        if (projectRootPath != null) {
            String path = Paths.get(BASE_ROOT).relativize(Paths.get(context.getProjectRootPath())).toString();
            // 示例： /preview/hu/my-project/index.html
            return "/preview" + path + "/index.html";
        }
        // 兜底方案
        return "/preview/task_" + context.getTaskId() + "/index.html";
    }

    @Override
    public WorkflowState getTargetState() {
        return WorkflowState.COMPLETED;
    }
}
