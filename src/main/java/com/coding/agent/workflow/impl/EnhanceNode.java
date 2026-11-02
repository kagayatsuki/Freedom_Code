package com.coding.agent.workflow.impl;

import com.coding.agent.domain.dto.SearchResult;
import com.coding.agent.service.RagIndexService;
import com.coding.agent.tools.CodingAiService;
import com.coding.agent.tools.PromptAiService;
import com.coding.agent.tools.WebSearchTool;
import com.coding.agent.workflow.WorkflowContext;
import com.coding.agent.workflow.WorkflowNode;
import com.coding.agent.workflow.WorkflowState;


import com.coding.agent.domain.dto.RagReference;
import dev.langchain4j.service.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class EnhanceNode implements WorkflowNode {
  //  private final CodingAiService codingAiService;
    private final PromptAiService promptAiService;
    private final WebSearchTool webSearchTool;
    private final RagIndexService ragIndexService;
    private static final String BASE_ROOT = "D:\\code";

    /** RAG 样例注入的总字符预算，防止 prompt 膨胀导致 LLM 超时。 */
    private static final int RAG_TOTAL_BUDGET = 3000;
    /** 单条 RAG 样例的最大字符数。 */
    private static final int RAG_SINGLE_BUDGET = 1500;
    /** 首轮召回的候选条数。 */
    private static final int RAG_TOP_K = 5;
    /** 最终注入的样例条数上限。 */
    private static final int RAG_MAX_INJECT = 3;
    /**
     * 首轮过滤后为空时的扩大召回池。
     * 典型场景：Vue 需求因「球鞋/电商」等关键词召回一批 React + Three.js 篮球样例，
     * 过滤后全被丢弃，此时扩大候选池才能捞到技术栈无关的可用样例。
     */
    private static final int RAG_FALLBACK_POOL = 40;

    @Override
    public WorkflowState execute(WorkflowContext context) {
        try {
            log.info("开始进行提示词增强...");
            String memoryId = "project_" + context.getUserName() + "_" + context.getProjectName();
            String enPrompt=context.getOriginalPrompt();
            if (context.isUseWebSearch()) {
                List<SearchResult> results = webSearchTool.search(
                        extractKeywords(context.getOriginalPrompt()), 5);
                if (!results.isEmpty()) {
                    enPrompt= """
              【联网搜索结果（基于真实资料）】：
              %s

              【用户原始需求】：
              %s
              """.formatted(formatResults(results), context.getOriginalPrompt());
                }
                if (!results.isEmpty()) {
                    context.addWebSearchContent(formatResults(results));
                }
                log.info("联网搜索已激活，返回 {} 条结果", results.size());
            }
            // 转义双花括号，防止 LangChain4j 误认为 Prompt 模板变量
            String safePrompt = enPrompt.replace("{{", "{ {").replace("}}", "} }");
            StringBuilder enhancedPrompt ;
            if(needsGeneration(context.getUserName(),context.getProjectName())) {
                Result<String> enhanceResult = promptAiService.enhanceCreatePrompt(memoryId, safePrompt);
                context.addTokenUsed(enhanceResult);
                enhancedPrompt = new StringBuilder(enhanceResult.content());
                log.info("开始生成项目提示词增强...");
            }else {
                Result<String> enhanceResult = promptAiService.enhanceModifyPrompt(memoryId, safePrompt);
                context.addTokenUsed(enhanceResult);
                enhancedPrompt = new StringBuilder(enhanceResult.content());
                log.info("开始修改项目提示词增强...");
            }

            // 用户勾选了图片搜索增强 → 强制注入图片要求，引导 CodeGenNode 调用 searchImages 工具
            if (context.isUseImageSearch()) {
                enhancedPrompt.append("""
                        
                        【🚨 强制要求：高质量真实配图/插图】
                        本项目必须包含高质量的真实图片/插图。
                        请务必使用 searchImages 工具搜索与项目主题相关的真实图片 URL，并嵌入 HTML 的 <img> 标签中。
                        严禁使用占位图、纯色背景或编造 URL 来替代真实配图；
                          但是如果没有找到对应的官方游戏美术素材，严禁使用不相关的 Pexels 照片。
                          请改用 CSS 渐变 + 几何抽象图案作为该区域的视觉背景，保持整体科技感。
                        每张图片必须附带描述性的 alt 属性。
                        """);
                log.info("图片搜索增强已激活，已向增强提示词注入配图要求");
            }
            // RAG 样例检索注入（失败不影响主流程）
            // 防护：① 技术栈过滤，避免 Vue 需求注入 React/Three.js 样例；② 总长度预算，防止 prompt 膨胀
            try {
                List<RagReference> refs = ragIndexService.search(context.getOriginalPrompt(), RAG_TOP_K);
                String userStack = detectStack(context.getOriginalPrompt());
                List<RagReference> picked = pickByStack(refs, userStack);

                // 首轮候选被单一技术栈样例占满（如 Vue 需求捞到一堆 React 样例）时，扩大候选池重试
                if (picked.isEmpty()) {
                    List<RagReference> wider = ragIndexService.search(context.getOriginalPrompt(), RAG_FALLBACK_POOL);
                    picked = pickByStack(wider, userStack);
                    log.info("[RAG] 首轮 {} 条候选过滤后为空，扩大候选池至 {} 条重试，过滤后 {} 条",
                            refs.size(), wider.size(), picked.size());
                }

                if (!picked.isEmpty()) {
                    StringBuilder ragBlock = new StringBuilder();
                    ragBlock.append("\n\n【参考样例 —— 来自知识库 rag_knowledge】\n")
                            .append("以下是检索到的历史样例，仅参考其结构与实现思路；必须按当前需求自行生成，严禁原样照搬代码。\n");

                    int used = 0;
                    int injected = 0;
                    for (RagReference r : picked) {
                        if (used >= RAG_TOTAL_BUDGET) {
                            break;
                        }
                        String content = r.content() == null ? "" : r.content();
                        // 单条上限，避免一条样例挤占全部预算
                        if (content.length() > RAG_SINGLE_BUDGET) {
                            content = content.substring(0, RAG_SINGLE_BUDGET) + "\n...（样例已截断）";
                        }
                        // 总预算上限
                        if (used + content.length() > RAG_TOTAL_BUDGET) {
                            content = content.substring(0, Math.max(0, RAG_TOTAL_BUDGET - used)) + "\n...（样例已截断）";
                        }
                        // 转义双花括号，防止 LangChain4j 把 Vue 的 {{ }} 误当模板变量
                        content = content.replace("{{", "{ {").replace("}}", "} }");
                        ragBlock.append(String.format("\n### 样例 %d（场景：%s，相似度 %.2f）\n%s\n",
                                injected + 1, nvl(r.scene(), "未分类"), r.score(), content));
                        used += content.length();
                        injected++;
                        context.addRagResult(String.format("score=%.3f | scene=%s | tags=%s",
                                r.score(), nvl(r.scene(), "-"), nvl(r.tags(), "-")));
                    }
                    enhancedPrompt.append(ragBlock);
                    log.info("[RAG] 注入 {} 条样例（技术栈 {}），共 {} 字符（预算 {}）",
                            injected, userStack, used, RAG_TOTAL_BUDGET);
                } else {
                    log.info("[RAG] 未找到技术栈匹配的样例（技术栈 {}），跳过注入", userStack);
                }
            } catch (Exception e) {
                log.warn("[RAG] 检索样例失败，跳过增强: {}", e.getMessage());
            }
            context.setEnhancedPrompt(enhancedPrompt.toString());

            log.info("提示词增强完成，长度: {} 字符", enhancedPrompt.length());
            log.info("{}", enhancedPrompt.toString());

            return WorkflowState.ROUTED;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private String extractKeywords(String prompt) {
        // 简单实现：取 prompt 前 100 字作为搜索关键词
        // 可替换为 AI 提取
        return prompt.length() > 100 ? prompt.substring(0, 100) : prompt;
    }

    private String formatResults(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append(String.format("%d. %s\n   %s\n", i + 1, r.getTitle(), r.getSnippet()));
        }
        return sb.toString();
    }
    private String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 从需求文本里粗判目标技术栈，用于过滤不匹配的 RAG 样例。 */
    private String detectStack(String prompt) {
        String p = prompt == null ? "" : prompt.toLowerCase();
        if (p.contains("vue") || p.contains("nuxt")) {
            return "VUE";
        }
        if (p.contains("react") || p.contains("next.js")) {
            return "REACT";
        }
        if (p.contains("three.js") || p.contains("threejs") || p.contains("webgl")) {
            return "THREE";
        }
        return "UNKNOWN";
    }

    /** 按技术栈过滤候选样例，并截取注入条数上限。 */
    private List<RagReference> pickByStack(List<RagReference> refs, String userStack) {
        return refs.stream()
                .filter(r -> stackCompatible(userStack, r))
                .limit(RAG_MAX_INJECT)
                .toList();
    }

    /**
     * 技术栈兼容性判断：需求技术栈与样例标签（tags / content 开头）冲突时丢弃。
     * 只匹配 tags 与 content 前 300 字，避免「某 Vue 模板在正文里提到 React 对比」造成误伤。
     */
    private boolean stackCompatible(String userStack, RagReference ref) {
        if ("UNKNOWN".equals(userStack)) {
            return true;
        }
        String tags = ref.tags() == null ? "" : ref.tags().toLowerCase();
        String content = ref.content() == null ? "" : ref.content();
        String head = content.substring(0, Math.min(300, content.length())).toLowerCase();
        String hay = tags + " " + head;
        return switch (userStack) {
            case "VUE" -> !hay.contains("react") && !hay.contains("three");
            case "REACT" -> !hay.contains("vue");
            default -> true;
        };
    }
    @Override
    public WorkflowState getTargetState() {
        return WorkflowState.PROMPT_ENHANCED;
    }
    private boolean needsGeneration(String userName, String projectName) {
        Path projectDir = Path.of(BASE_ROOT, userName, projectName);

        if (!Files.exists(projectDir)) {
            return true;  // 目录都不存在，肯定是新建
        }

        try (var stream = Files.list(projectDir)) {
            return stream.findAny().isEmpty();  // 目录为空 → 新建；有文件 → 修改
        } catch (IOException e) {
            return true;  // 读取出错，保守走新建
        }
    }
}
