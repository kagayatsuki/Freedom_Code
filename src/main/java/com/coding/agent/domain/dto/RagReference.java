package com.coding.agent.domain.dto;

/**
 * RAG 检索到的一条历史样例（rag_knowledge 表）。
 *
 * @param content 完整原始 prompt 内容（命中后返回给 LLM，注入提示词）
 * @param scene   场景分类（如 "前端-品牌落地页"、"代码-图表生成"）
 * @param tags    标签（JSONB 数组的文本表示，如 ["React","Three.js","GSAP"]）
 * @param score   相似度得分 [0,1]，0.5 对应余弦相似度 0
 */
public record RagReference(
        String content,
        String scene,
        String tags,
        double score
) {
}
