package com.coding.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coding.agent.domain.po.ChatHistory;
import com.coding.agent.mapper.ChatHistoryMapper;
import com.coding.agent.service.IChatHistoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.coding.agent.tools.PromptAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 对话历史 服务实现类
 * </p>
 *
 * @author kagayatsuki
 * @since 2026-06-09
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements IChatHistoryService {
    private final ChatHistoryMapper chatHistoryMapper;
    private final PromptAiService promptAiService;
    @Override
    public void saveMessage(String userName, String projectName, String taskId, String message, String messageType) {
        ChatHistory history = new ChatHistory();
        history.setUserName(userName);
        history.setProjectName(projectName);
        history.setTaskId(taskId);
        history.setMessage(message);
        history.setMessageType(messageType);
        history.setCreateTime(LocalDateTime.now());
        chatHistoryMapper.insert(history);
        log.info(" 对话记录已入库 | 项目: {}/{} | 类型: {} | 长度: {} 字符",
                userName, projectName, messageType, message.length());
        if ("AI_MODIFY".equals(messageType) || "AI_CREATE".equals(messageType)) {
            final Long savedId = history.getId();
            final String msg = message;
            Thread.startVirtualThread(() -> {           // Java 21 虚拟线程，无需 @Async
                try {
                    String summary = promptAiService.summarizeModifyResult(msg);
                    updateSummary(savedId, summary);
                    log.info("📝 摘要已生成 | id: {} | 长度: {} → {}", savedId, msg.length(), summary.length());
                } catch (Exception e) {
                    log.warn("摘要生成失败 | id: {} {}", savedId, e.getMessage());
                }
            });
        }

    }

    @Override
    public List<ChatHistory> getRecentMessages(String userName, String projectName, int maxCount) {
        List<ChatHistory> list = chatHistoryMapper.selectList(
                new LambdaQueryWrapper<ChatHistory>()
                        .eq(ChatHistory::getUserName, userName)
                        .eq(ChatHistory::getProjectName, projectName)
                        .orderByDesc(ChatHistory::getCreateTime)
                        .last("LIMIT " + maxCount)
        );
        Collections.reverse(list);  // 老的在前，新的在后
        return list;
    }

    @Override
    public void updateSummary(Long id, String summary) {
        ChatHistory update = new ChatHistory();
        update.setId(id);
        update.setSummary(summary);
        chatHistoryMapper.updateById(update);
    }
}
