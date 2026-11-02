package com.coding.agent.workflow;

import lombok.Data;

@Data
public class WorkflowStartRequest {
    private String prompt;          // 用户需求（必传）
    private String projectName;     // 项目名称（必传）
    private String userName;        // 用户名（可选）
    private boolean useImageSearch;
    private boolean useWebSearch;
}