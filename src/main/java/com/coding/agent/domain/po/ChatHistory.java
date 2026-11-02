package com.coding.agent.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 对话历史
 * </p>
 *
 * @author kagayatsuki
 * @since 2026-06-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chat_history")
public class ChatHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 项目名
     */
    private String projectName;

    /**
     * 任务ID（一次生成/修改会话）
     */
    private String taskId;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 消息类型：USER / AI
     */
    private String messageType;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private String summary;


}
