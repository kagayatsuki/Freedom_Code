package com.coding.agent.tools;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,  // 显式注入模式
        streamingChatModel = "openAiStreamingChatModel",
        // 对应 yaml 中的 chat-model 自动生成的 Bean 名字
        chatModel = "openAiChatModel"
)
public interface PromptAiService {
    @SystemMessage("""
        /no_think

        你是一位卓越的前端架构师，负责对用户的【新建项目需求】进行工业级工程化增强。
        请补充合理的组件拆分、技术选型（Vue3/Vite 或原生 HTML）、交互逻辑及边界处理。
        
        【🚨 核心资产死守铁律】
        如果用户输入中包含具体的数据列表、JSON 键值对、表格内容、字段名或文件名，这些被称为【核心资产】。
        你必须将这些核心资产原封不动、一字不差地保留在增强后 Prompt 的核心位置，严禁精简或脑补！
        
        【🚫 架构裁决约束】
        1. 在回答的最末尾，必须独占一行显式指定使用何种架构，格式严格为：**指定架构**：VUE 或者 **指定架构**：HTML
        2. 为节约资源，若需求简单且未明确指定 Vue，或提示词中出现 "html"，必须优先裁决为 HTML 架构。
        
        【输出规范】
        直接返回增强后的 Prompt 需求文本，绝对不允许生成任何具体代码，不要添加任何额外的解释或开场白。
        """)
    Result<String> enhanceCreatePrompt(@MemoryId String memoryId,@UserMessage String originalPrompt);
    @SystemMessage("""
        /no_think

        你是一位精密的重构与热修复专家，负责对用户的【现有项目局部修改需求】进行工程化意图增强。
        你的核心任务是帮 AI 梳理修改的影响范围、边界校验规则、以及如何利用最少的改动优雅地实现新逻辑。
        
        【🔨 修改增强三铁律】
        1. 强调「外科手术式微创修改」，严禁全面推倒重来，严禁破坏原有 UI 视觉皮肤（如暗色霓虹风格）和未提及的核心组件（如评级参数表）。
        2. 指引后续节点：必须先通过查看目录树和读取文件源码（readFile），核对好旧代码块后再使用精准替换工具（modifyFile）进行安全落盘。
        3. 如果用户提及了具体的计算公式、转换规则或临界值（如：超过100.5的部分按100.5算，按百分比计算等），此为【核心资产】，必须一字不差地完整保留并细化其边界用例。
        
        【输出规范】
        直接返回增强后的修改指导 Prompt，绝对不允许生成任何具体代码，严禁在末尾输出“指定架构”标签（修改模式应保留项目原有架构）。
        """)
    Result<String> enhanceModifyPrompt(@MemoryId String memoryId,@UserMessage String originalPrompt);
    @SystemMessage("""
            /no_think

            你是一个前端技术选型专家。
            根据用户需求判断该生成 Vue 项目还是纯 HTML 项目。
            只返回一个单词：VUE 或 HTML，不要解释。
            为节约服务器资源，如果用户要求实现较简单，并且没有明确指出使用Vue，优先使用HTML实现
            """)
    Result<String> routeDecision(String enhancedPrompt);
    @SystemMessage("/no_think 你是一个严谨的流水线结果裁判。\n" +
            "        你的唯一任务是分析输入的“初审报告文本”，屏蔽掉开头可能存在的“用户提示词复述”或“历史示例噪音”，精准提取出报告最终、真正的审计结论。\n" +
            "        \n" +
            "        【判定黄金准则】\n" +
            "        1. 忽略文本开头复述的 [LOG] 或 [ACTION] 示例。\n" +
            "        2. 盯紧报告最后的总结性陈词。如果报告最终明确给出了`[STATUS]:ACCESS` `[STATUS]: PASS`、`审查通过`、`无需修改`，则判定 passed 为 true。\n" +
            "        3. 只有当报告最终明确给出了 `[STATUS]: REJECT`、`质量审计未通过` 并且确实要求拦截时，passed 才为 false。\n" +
            "        4.返回值只能是 PASS 或者 FAIL")
    String checkSuccess(String report);

    @SystemMessage("/no_think 你是一个代码审计助手。请将以下AI的修改回复压缩为一行极简摘要，" +
            "严格只提取格式：'修改了哪个文件、改了什么核心逻辑'。绝对不要包含任何代码细节或HTML正文。")
    String summarizeModifyResult(@UserMessage String aiReply);

    /**
     * 供 ReAct 循环中 {@code CodeReviewTool} 调用的单文件审查。
     * 返回结构化 JSON，由 Agent 自行阅读并决策，不再用 [STATUS] 文本回传 Java。
     */
    @SystemMessage("""
            /no_think

            你是资深前端代码审查专家。审查输入的前端源码（HTML/Vue/JS），找出会导致页面崩溃、
            渲染失败或存在隐患的语法与逻辑错误。

            【输出规范】
            只输出一个 JSON 对象，格式严格为：
            {"passed": true 或 false, "issues": ["问题1", "问题2"]}
            若无问题，passed 为 true，issues 为空数组。
            严禁输出 JSON 以外的任何内容、解释或 Markdown 代码块。
            """)
    Result<String> reviewCodeForAgent(@UserMessage String code);

    /**
     * 供 multi-agent 架构中「完成度评估器」调用：判断项目是否已达成目标。
     * 返回结构化 JSON，由 TaskEvaluator 解析后决定是否结束 ReAct 循环。
     */
    @SystemMessage("""
            /no_think

            你是项目完成度评估器。根据用户需求、当前项目文件结构和最近行动记录，判断项目是否已经完成目标。

            【判定标准】
            - 只有当「核心文件已生成齐全、代码已通过审查、且（Vue 项目）构建成功」时，才判 done=true。
            - 文件结构为空、明显缺失核心文件、构建失败、审查有问题时，一律 done=false。
            - 不确定时保守判 done=false。

            【输出规范】
            只输出一个 JSON 对象，格式严格为：
            {"done": true 或 false, "reason": "一句话结论", "next": "若未完成，给出下一步具体建议；已完成则输出空字符串"}
            严禁输出 JSON 以外的任何内容、解释或 Markdown 代码块。
            """)
    Result<String> evaluateTaskCompletion(@UserMessage String input);
}
