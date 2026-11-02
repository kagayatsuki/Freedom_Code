package com.coding.agent.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;

import java.time.Duration;

@Slf4j
@Configuration

public class LangChain4jConfig {
    // ---------- Qwen（阿里云百炼 compatible-mode）----------
    private final String baseUrl = "https://tokenrhythm.studio/v1";
    private final String apiKey = System.getenv("OPENAI_API_KEY");
    private final String modelName = "qwen3.8-27b";
    @Autowired
    private ChatMemoryStore redisChatMemoryStore;
    @Bean
    public EmbeddingModel embeddingModel() {
        return new BgeSmallZhV15EmbeddingModel();
    }

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }

    /**
     * 聊天记忆提供者（推荐在 AiServices 中配合 @MemoryId 使用）
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(redisChatMemoryStore)
                .build();
    }

//
//    @Bean("openAiStreamingChatModel")
//    public OpenAiStreamingChatModel openAiStreamingChatModel() {
//        log.info("[手动装载] 绕过 Starter 冲突，正在创建流式模型：{}", modelName);
//        return OpenAiStreamingChatModel.builder()
//                .apiKey(apiKey)
//                .baseUrl(baseUrl)
//                .modelName(modelName)
//                .temperature(0.7)
//                .maxTokens(8192)
//                .timeout(Duration.ofSeconds(60))
//                .logRequests(true)
//                .logResponses(true)
//                .build();
//    }

}
