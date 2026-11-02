package com.coding.agent.workflow.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.agent.domain.po.User;
import com.coding.agent.mapper.TUserMapper;
import com.coding.agent.utils.UserContext;
import com.coding.agent.workflow.WorkflowContext;
import com.coding.agent.workflow.WorkflowNode;
import com.coding.agent.workflow.WorkflowState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class InitNode implements WorkflowNode {
    private static final String BASE_ROOT = "D:\\code";
    @Override
    public WorkflowState execute(WorkflowContext context) {
        try {
            if (context.getUserName() == null || context.getProjectName() == null) {
                context.addErrorLog("缺少 userName 或 projectName，无法创建项目目录");
                return WorkflowState.FAILED;
            }
            Path projPath=getProjectPath(context.getUserName(), context.getProjectName());
            if (Files.notExists(projPath)) {
                Files.createDirectories(projPath);
                Path basePath=Paths.get(BASE_ROOT);
                Path userPath=basePath.relativize(projPath);
                log.info("新建项目目录成功: {}",userPath);
            } else {
                Path basePath=Paths.get(BASE_ROOT);
                Path userPath=basePath.relativize(projPath);
                log.info("使用已存在的项目目录: {}",userPath);
            }
            //前端使用的时候，使用relativize切割
            context.setProjectRootPath(projPath.toAbsolutePath().toString());
            context.setCreateTime(LocalDateTime.now());

            log.info("InitNode 执行完成 | 任务ID: {} | 用户: {} | 项目: {} | 路径: {}",
                    context.getTaskId(), context.getUserName(), context.getProjectName(), projPath);
            if(context.getOriginalPrompt()!=null){
                log.info("初始提示词: {}",context.getOriginalPrompt());
            }
            return WorkflowState.PROMPT_ENHANCED;
            //return WorkflowState.PROMPT_ENHANCED;

        }catch (SecurityException e) {
            log.error("路径安全检查失败", e);
            context.addErrorLog("项目名称包含非法字符: " + e.getMessage());
            return WorkflowState.FAILED;
        } catch (Exception e) {
            log.error("InitNode 执行异常", e);
            context.addErrorLog("初始化项目目录失败: " + e.getMessage());
            return WorkflowState.FAILED;
        }

    }
    private Path getProjectPath(String userName, String projectName) {
        // 基础安全过滤
        if (userName.contains("..") || projectName.contains("..") ||
                userName.contains("/") || userName.contains("\\") ||
                projectName.contains("/") || projectName.contains("\\")) {
            throw new SecurityException("用户名或项目名包含非法路径字符");
        }

        return Paths.get(BASE_ROOT, userName, projectName);
    }
    @Override
    public WorkflowState getTargetState() {
        return WorkflowState.INIT;
    }
}
