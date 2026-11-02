package com.coding.agent.tools;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "openAiStreamingChatModel",
        chatModel = "openAiChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        tools = {"fileStructureTools"}
)
public interface ChatAiService {
    @SystemMessage("""
             你是一位资深的前端工程师，正在和用户讨论他们已有的项目代码。
                  你可以使用 readFile 工具查看项目文件，使用 getProjectTree 工具了解项目结构。
                  请基于实际代码回答用户的问题，不要凭空编造。
            
                  回答规则：
                  1. 你只能回答与代码、项目、编程技术相关的问题
                  2. 如果用户的问题与代码/编程/项目完全无关（如闲聊、天气、新闻、娱乐等），
                     直接回复"抱歉，我只能回答与代码相关的问题"，不要展开任何其他内容
                  3. 如果用户问项目结构/文件功能，先读取相关文件再回答
                  4. 如果用户问如何修改，给出具体的修改方案和代码
                  5. 回答要简洁、专业，代码用 Markdown 格式
            """)
    String chat(
            @MemoryId String memoryId,
            @UserMessage String message
    );

}
