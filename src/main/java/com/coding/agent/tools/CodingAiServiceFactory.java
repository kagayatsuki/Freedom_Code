package com.coding.agent.tools;


import com.coding.agent.config.RedisChatMemoryStore;
import com.coding.agent.workflow.WorkflowContext;
import com.coding.agent.workflow.agent.AgentLoopConfig;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

//针对工具隔离，设置工厂类
@Configuration
@EnableConfigurationProperties(AgentLoopConfig.class)
public class CodingAiServiceFactory {
    @Resource
    private ChatLanguageModel chatModel;
    @Resource
    private StreamingChatLanguageModel streamingChatModel;
    @Resource
    private ChatMemoryProvider chatMemoryProvider;
    @Resource
    private ChatMemory chatMemory;
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    // 文件工具（永远启用）
    @Resource
    private FileStructureTools fileStructureTools;

    // RAG 相似案例检索工具
    @Resource
    private SimilarCodeSearchTool similarCodeSearchTool;

    @Resource
    private PexelsImageSearch pexelsImageSearch;

    @Resource
    private WebSearchTool webSearchTool;

    // ===== ReAct 化新增工具 =====
    @Resource
    private BuildTool buildTool;

    @Resource
    private CodeReviewTool codeReviewTool;

    @Resource
    private AgentLoopConfig agentLoopConfig;

    private final Cache<String, CodingAiService> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    /**
     * 解析本次任务可见的工具集：写文件 + 构建 + 审查 + 可选搜索/RAG。
     */
    public List<Object> resolveTools(WorkflowContext ctx) {
        List<Object> tools = new ArrayList<>();
        tools.add(fileStructureTools);
        // build/review 工具仅在 ReAct 模式生效时注入，避免污染旧节点（CodeGenNode/CheckNode）的工具集
        if (agentLoopConfig.enabled() && agentLoopConfig.enableBuild()) {
            tools.add(buildTool);
        }
        if (agentLoopConfig.enabled() && agentLoopConfig.enableReview()) {
            tools.add(codeReviewTool);
        }
        if (ctx.isUseImageSearch()) {
            tools.add(pexelsImageSearch);
        }
        if (ctx.isUseWebSearch()) {
            tools.add(webSearchTool);
        }
        // RAG 相似样例检索工具：Agent 可在编码过程中主动检索相似案例
        tools.add(similarCodeSearchTool);
        return tools;
    }

    public CodingAiService createService(WorkflowContext ctx) {
        return createService(ctx, resolveTools(ctx));
    }

    /**
     * 用显式工具集现场构建 AiService（多任务并发下按任务隔离）。
     */
    public CodingAiService createService(WorkflowContext ctx, List<Object> tools) {
        return AiServices.builder(CodingAiService.class)
                .chatLanguageModel(chatModel)
                .streamingChatLanguageModel(streamingChatModel)
                .chatMemoryProvider(id -> MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(20)
                        .chatMemoryStore(redisChatMemoryStore)
                        .build())
                .tools(tools.toArray())
                .build();

    }

}
