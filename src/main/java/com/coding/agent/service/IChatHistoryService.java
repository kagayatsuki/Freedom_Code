package com.coding.agent.service;

import com.coding.agent.domain.po.ChatHistory;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 对话历史 服务类
 * </p>
 *
 * @author kagayatsuki
 * @since 2026-06-09
 */
public interface IChatHistoryService extends IService<ChatHistory> {
    /**
     * 保存一条对话消息到数据库
     */
    void saveMessage(String userName, String projectName, String taskId,
                     String message, String messageType);

    /**
     * 加载某个项目最近 N 条对话（时间正序，老的在前）
     * 用于注入给 modifyCode 做上下文
     */
    List<ChatHistory> getRecentMessages(String userName, String projectName, int maxCount);
    void updateSummary(Long id, String summary);
}
