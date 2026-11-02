package com.coding.agent.controller;

import com.coding.agent.domain.R;
import com.coding.agent.exception.RateLimitException;
import com.coding.agent.exception.UnauthorizedException;
import com.coding.agent.service.RateLimitService;
import com.coding.agent.service.TokenBucketRateLimiter;
import com.coding.agent.workflow.*;
import com.coding.agent.workflow.agent.AgentResult;
import com.coding.agent.workflow.agent.ReActSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEngine workflowEngine;
    private final RateLimitService rateLimitService;
    private final TokenBucketRateLimiter tokenBucketRateLimiter;
    private final ReActSyncService reActSyncService;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, WorkflowContext> contexts = new ConcurrentHashMap<>();

    private static final String BASE_ROOT = "D:\\code";

    @PostMapping("/create")
    public Flux<ServerSentEvent<NotificationEvent>> startWorkflow(
            @RequestBody WorkflowStartRequest request) {

        // 1. 后端自动生成 taskId
        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);

        // 2. 获取 userName（前端传入，默认为 guest）
        String userName = request.getUserName() != null && !request.getUserName().trim().isEmpty()
                ? request.getUserName().trim()
                : "guest";

        // 3. 登录校验：guest 匿名用户禁止生成文件和创建项目
        if ("guest".equals(userName)) {
            log.warn("[登录校验] 匿名用户禁止创建项目");
            return Flux.error(new UnauthorizedException("请先登录"));
        }

        // 4. 项目名称（前端必传，无则自动生成）
        String projectName = request.getProjectName();
        if (projectName == null || projectName.trim().isEmpty()) {
            projectName = "project-" + System.currentTimeMillis();
        }

        // 5. 令牌桶限流：每个用户每分钟最多 10 次提交，进入队列前拦截
        if (!tokenBucketRateLimiter.tryAcquire(userName)) {
            log.warn("[限流拒绝] user={} 提交过于频繁", userName);
            return Flux.error(new RateLimitException("提交太频繁"));
        }

        // 6. 每日配额检查（仅检查不递增，成功后 CompleteNode 才递增）
        boolean isNewProject = isNewProject(userName, projectName);
        if (!"guest".equals(userName)) {
            Long userId = rateLimitService.getUserIdByUsername(userName);
            if (userId != null) {
                String error = rateLimitService.checkQuota(userId, isNewProject);
                if (error == null) {
                    error = rateLimitService.checkTokenQuota(userId);
                }
                if (error != null) {
                    log.warn("[限流拒绝] user={}, project={}, reason={}", userName, projectName, error);
                    return Flux.error(new RateLimitException(error));
                }
            }
        }

        log.info("启动工作流 | taskId: {} | user: {} | project: {} | isNew: {}",
                taskId, userName, projectName, isNewProject);

        // 调用引擎启动工作流
        return workflowEngine.startWorkflow(taskId, request.getPrompt(), userName, projectName,
                        isNewProject, request.isUseImageSearch(), request.isUseWebSearch())
                .map(event -> ServerSentEvent.<NotificationEvent>builder()
                        .id(taskId)
                        .event("workflow-progress")
                        .data(event)
                        .retry(Duration.ofSeconds(5))
                        .build());
    }

    /**
     * 同步执行 ReAct 工作流（普通方法返回，不走 SSE）。
     * 用于调试：直接在当前请求线程内同步跑完整个 ReAct 循环并返回终态结果，
     * 便于排查「是否进入 loop、工具是否被调用」等问题。
     */
    @PostMapping("/run-sync")
    public R<AgentResult> runSync(@RequestBody WorkflowStartRequest request) {
        String userName = request.getUserName() != null && !request.getUserName().trim().isEmpty()
                ? request.getUserName().trim()
                : "guest";

        if ("guest".equals(userName)) {
            log.warn("[登录校验] 匿名用户禁止创建项目");
            return R.error("请先登录");
        }

        String projectName = request.getProjectName();
        if (projectName == null || projectName.trim().isEmpty()) {
            projectName = "project-" + System.currentTimeMillis();
        }

        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);
        boolean isNewProject = isNewProject(userName, projectName);

        WorkflowContext context = WorkflowContext.builder()
                .userName(userName)
                .projectName(projectName)
                .taskId(taskId)
                .originalPrompt(request.getPrompt())
                .currentState(WorkflowState.INIT)
                .createTime(java.time.LocalDateTime.now())
                .isNewProject(isNewProject)
                .useImageSearch(request.isUseImageSearch())
                .useWebSearch(request.isUseWebSearch())
                .retryCount(0)
                .build();

        AgentResult result = reActSyncService.run(context);
        return R.ok(result);
    }

    /**
     * 查询用户今日用量（供前端展示）
     */
    @GetMapping("/quota")
    public R<RateLimitService.QuotaInfo> getQuota(@RequestParam String userName) {
        if ("guest".equals(userName)) {
            return R.ok(new RateLimitService.QuotaInfo(1, "访客", 999, 0, 999, 999, 0, 999, -1, 0, -1));
        }
        Long userId = rateLimitService.getUserIdByUsername(userName);
        if (userId == null) {
            return R.ok(new RateLimitService.QuotaInfo(1, "普通用户", 3, 0, 3, 6, 0, 6, 500_000L, 0, 500_000L));
        }
        return R.ok(rateLimitService.getQuotaInfo(userId));
    }

    private boolean isNewProject(String userName, String projectName) {
        Path projectDir = Path.of(BASE_ROOT, userName, projectName);
        if (!Files.exists(projectDir)) return true;
        try (var stream = Files.list(projectDir)) {
            return stream.findAny().isEmpty();
        } catch (IOException e) {
            return true;
        }
    }


    @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<NotificationEvent>> streamWorkflow(@PathVariable String taskId) {

        log.info("客户端订阅工作流 SSE 流: {}", taskId);

        return workflowEngine.getWorkflowStream(taskId)
                .map(event -> ServerSentEvent.<NotificationEvent>builder()
                        .id(taskId)
                        .event("workflow-progress")
                        .data(event)
                        .build())
                // 心跳包，防止前端连接超时
                .mergeWith(Flux.interval(Duration.ofSeconds(15))
                        .map(i -> ServerSentEvent.<NotificationEvent>builder()
                                .event("heartbeat")
                                .data(NotificationEvent.of(taskId, null, "♥"))
                                .build()));
    }

    /**
     * 查询任务状态（供前端刷新后判断任务是否仍在运行）
     */
    @GetMapping("/{taskId}/status")
    public R<TaskStatusInfo> getTaskStatus(@PathVariable String taskId) {
        return R.ok(workflowEngine.getTaskStatus(taskId));
    }

    /**
     * 取消任务：设置取消标志，节点/工具会在下一个检查点退出
     */
    @PostMapping("/{taskId}/cancel")
    public R<Boolean> cancelTask(@PathVariable String taskId) {
        return R.ok(workflowEngine.cancelTask(taskId));
    }
}
