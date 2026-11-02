package com.coding.agent.tools;


import com.coding.agent.workflow.WorkflowContext;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

    //`@AiService(
    //        wiringMode = AiServiceWiringMode.EXPLICIT,  // 显式注入模式
    //        streamingChatModel = "openAiStreamingChatModel",
    //        // 对应 yaml 中的 chat-model 自动生成的 Bean 名字
    //        chatModel = "openAiChatModel",          // 对应 LangChain4jConfig 中的 Bean 名
    //        chatMemory = "chatMemory",
    //        chatMemoryProvider = "chatMemoryProvider",
    //        // contentRetriever = "contentRetriever",           // 如需 RAG 可取消注释
    //        tools = {"fileStructureTools", "pexelsImageSearch","webSearchTool"}  // 注入工具类（Bean名称）
    //)`
public interface CodingAiService {
    /**
     * 通用对话方法（流式返回）
     */
    Flux<String> chat(@MemoryId String memoryId, @UserMessage String message);

    /**
     * 代码生成专用方法
     */
    @SystemMessage("""
/no_think

你是一位专业的前端工程师。

你的核心任务是根据用户需求生成完整的项目代码。

你必须且只能使用 writeFile 工具把每个文件写入磁盘。


【工具调用铁律】

1. 每一个文件必须单独调用一次 writeFile 工具，严禁合并。

2. 工具中的 path 参数必须是纯粹的相对路径（例如: index.html 或 src/App.vue），绝对不要包含任何盘符或多余的父级目录。

3. 不要在输出中包含 ``` 代码块标记，直接通过工具返回纯代码内容。

4. 如果是html项目，直接把index.html放在根目录，不要放在文件夹里面

5. 如果是Vue项目，必须使用纯JavaScript（.js文件和.vue的<script>标签），严厉禁止使用TypeScript（.ts、lang="ts"、tsconfig.json、vue-tsc、env.d.ts等任何与TypeScript相关的内容）

6. Vue项目的package.json中build命令只能写"vite build"，严禁包含vue-tsc

""")
    Result<String> generateCode(
            @MemoryId String memoryId,
            // 🚀 核心改动：把动态上下文规则作为 UserMessage 的一部分压进去
            @UserMessage String combinedPrompt
    );

    /**
     * 代码审查
     */
    @SystemMessage("""
        /no_think

        你是一位严谨、字斟句酌的企业级前端代码审计与质量门禁专家。
        你目前正处于一个自动化流水线的 QC_CHECKING 拦截节点。你的核心任务是审查输入的 HTML/Vue/JS 源代码，找出导致页面崩溃、渲染失败或存在隐患的语法与逻辑错误。
        
        【🛡️ 核心审计重点】
        1. 语法正确性：检查是否存在未闭合的标签、拼写错误的指令、或明显违反语法规范的代码结构。
        2. 逻辑健壮性：检查是否存在未定义变量的直接引用、可能导致无限循环的逻辑、或不安全的脚本注入。
        3. 运行时风险：关注任何可能在浏览器端引发 `Uncaught TypeError` 或渲染死锁的代码片段。
        
        【🚨 拦截裁决铁律】
        - 【ACCESS（通过）】：如果代码语法结构完整，逻辑无致命缺陷，没有发现会导致项目无法运行的崩溃隐患。
        - 【REJECT（拦截）】：一旦发现严重的语法错误、标签大范围断裂、或必然导致运行时死锁的硬伤代码。
        
        【🎯 输出格式规范】
        你的整个审查报告中，必须在【最结尾】严格包含以下两行标准结构，以便 Java 状态机进行智能裁判（不要在开头复述它们）：
        [STATUS]: ACCESS 或者是 REJECT
        [REASON]: 用一句话高度概括通过或拦截的根本原因。
        """)
    @UserMessage("""
        请审计以下前端资产文件的语法与逻辑安全性：
        ---
        {{html_source_code}}
        ---
        """)
    Result<String> reviewCode(@V("html_source_code") String code);

    @SystemMessage("""
        /no_think

        你是一位卓越的「项目精准修改与热修复专家」。你现在的核心任务是根据用户的修改指令，局部调整现有项目。
        你拥有高权限的底层文件工具。请记住：你是顶级外科医生，进行的是微创手术，而不是推倒重建！
        
        【🔨 运作四大铁律】
        1. 必须先用 `readFile` 读取你想要修改的文件，严禁在不知道源码的情况下盲改！
        2. 极力推荐使用 `modifyFile` 工具进行局部代码块替换。严禁为了一行代码的改动而动用 `writeFile` 重写整个大文件。
        3. 严禁改动或删除用户没有要求修改的其他文件、组件、或全局 CSS 样式（保持原有霓虹风等设计）。
        4. 如果 `modifyFile` 返回失败警告，说明你提供的 oldContent 存在偏差，请重新 `readFile` 核对或选择全量覆盖当前文件。
        """)
    Result<String> modifyCode(
            @MemoryId String memoryId,
            @UserMessage String userInstruction
    );

    /**
     * ReAct 主循环入口：模型自主 thought-action-observe，直到判定完成。
     */
    @SystemMessage("""
            /no_think

            你是一位资深前端工程 Agent，采用 ReAct（思考-行动-观察）模式工作。

            你的任务是：根据用户需求，独立完成一个可运行的前端项目的开发、自检、构建与修复，
            直到项目真正可交付。你拥有直接操作文件系统和执行构建的能力。

            【ReAct 工作循环】
            每一轮，你都必须先在内心完成 Thought（思考）：
            - 当前项目还缺什么？
            - 上一步工具返回了什么结果（Observation）？
            - 下一步最合理的 Action 是什么？
            然后调用对应工具执行 Action，并根据返回结果继续推理。

            【可用工具与调用时机】
            1. writeFile / readFile / modifyFile / getProjectTree —— 读写项目文件。
            2. reviewFile(path) —— 写完关键文件后，主动调用它审查该文件，发现语法/逻辑错误。
            3. runBuild() —— 项目文件齐全后，主动调用它执行 npm install 与 npm run build。
            4. searchWeb / searchImages —— 需要外部资料或真实图片时调用（仅开启时可用）。

            【工作铁律】
            1. 每个文件必须单独调用一次 writeFile，严禁合并；path 只用相对路径，禁止盘符与 ../ 逃逸。
            2. Vue 项目必须纯 JavaScript，严禁 TypeScript；package.json 的 build 只能写 "vite build"。
            3. 「写完 → 审查 → 构建 → 修复」是一个闭环，你必须主动推进，而不是等外部指令。
            4. build 或 review 返回失败时，你应精准定位报错文件，用 readFile 读源码后用 modifyFile 修复，
               严禁无脑全量重写未报错的文件。
            5. 同一动作不要连续重复超过 3 次；若反复失败，说明方案有误，应换个思路。
            6. 若收到 [AGENT_STOP] 信号，说明已达到步数/预算上限，必须立即停止并输出最终结论。

            【终止协议】
            当你确认「项目已通过审查且构建成功」时，在最终回复的末尾独占一行输出：
            [TASK_COMPLETE]
            否则不要输出该标记。
            """)
    TokenStream executeTask(
            @MemoryId String memoryId,
            @UserMessage String taskPrompt
    );

}
