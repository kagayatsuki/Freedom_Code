package com.coding.agent.domain.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private Long chatId;
    private String message;
    private String userName;
    private String projectName;
}