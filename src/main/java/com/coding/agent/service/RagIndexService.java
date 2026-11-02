package com.coding.agent.service;


import com.coding.agent.domain.dto.RagReference;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * RAG 混合检索服务（只读）。
 * <p>
 * 知识库为 pgvector 中的 rag_knowledge 表：
 * <ul>
 *     <li>embedding：向量召回（语义相似）</li>
 *     <li>bm25_text / content_tsv：BM25 关键词召回（精确 token，如组件名、API 名）</li>
 * </ul>
 * 两路召回后使用 RRF（Reciprocal Rank Fusion）融合，取 Top-K 返回。
 * 本服务只在提示词增强阶段做检索，绝不向知识库追加任何内容。
 */
@Service
@Slf4j
public class RagIndexService {

    /**
     * bge-small-zh-v1.5 检索侧专用前缀，与写入脚本保持一致。
     * 仅查询时添加，文档侧不加；LangChain4j 的 BgeSmallZhV15EmbeddingModel 不会自动添加该前缀。
     */
    private static final String QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章：";

    /** 单个样例 content 的最大字符数，防止上下文过大。 */
    private static final int MAX_CONTENT_CHARS = 8000;

    /** 分词后只保留中英文与数字，避免特殊字符破坏 to_tsquery 语法。 */
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** jieba 搜索模式，与入库脚本 Python 侧 lcut_for_search 语义一致。 */
    private static final JiebaSegmenter JIEBA = new JiebaSegmenter();

    private final EmbeddingModel embeddingModel;

    @Value("${spring.datasource.postgresql.url}")
    private String pgUrl;
    @Value("${spring.datasource.postgresql.username}")
    private String pgUsername;
    @Value("${spring.datasource.postgresql.password}")
    private String pgPassword;

    /** 相似度阈值 [0,1]，0.5 对应余弦相似度 0，低于该值视为不相关、不注入。 */
    @Value("${rag.min-score:0.5}")
    private double minScore;

    /** 是否启用 BM25 路召回；失败时自动降级为纯向量。 */
    @Value("${rag.bm25-enabled:true}")
    private boolean bm25Enabled;

    /** RRF 平滑常数，经验值 60，对结果不敏感。 */
    @Value("${rag.rrf-k:60}")
    private int rrfK;

    /** 每路召回数量 = topK * recallMultiplier，为融合提供足够候选。 */
    @Value("${rag.recall-multiplier:5}")
    private int recallMultiplier;

    /** 向量路在 RRF 中的权重。 */
    @Value("${rag.vector-weight:1.0}")
    private double vectorWeight;

    /** BM25 路在 RRF 中的权重，通常低于向量路（分词/关键词命中可信度略低）。 */
    @Value("${rag.bm25-weight:0.6}")
    private double bm25Weight;

    /**
     * ivfflat 索引的探查列表数。PG 默认 1，只扫一个聚类中心，
     * 在小表上会漏掉真实最近邻（实测 Vue3 需求漏掉了相似度 0.859 的样例，
     * 只召回 0.779 的错配样例）。取 100 等价全量探查，即精确检索。
     */
    @Value("${rag.ivfflat-probes:100}")
    private int ivfflatProbes;

    /** 仅用于检索查询，懒加载，避免在 Spring 里再注册一个 DataSource 与 MySQL 冲突。 */
    private volatile JdbcTemplate pgJdbcTemplate;

    public RagIndexService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 混合检索：向量 + BM25 双路召回，RRF 融合后返回 Top-K。
     *
     * @param query 用户需求描述（中文）
     * @param topK  期望返回的样例条数
     */
    public List<RagReference> search(String query, int topK) {
        int recallK = Math.max(topK * Math.max(1, recallMultiplier), topK);

        List<Hit> vectorHits = vectorRecall(query, recallK);
        if (!bm25Enabled) {
            log.info("[RAG] BM25 已关闭，仅向量召回 {} 条", vectorHits.size());
            return toReferences(vectorHits, topK);
        }

        List<Hit> bm25Hits;
        try {
            bm25Hits = bm25Recall(query, recallK);
        } catch (Exception e) {
            // BM25 只是增强路，失败降级为纯向量，不影响主流程
            log.warn("[RAG] BM25 召回失败，降级为纯向量检索: {}", e.getMessage());
            bm25Hits = List.of();
        }

        List<RagReference> refs = mergeByRrf(vectorHits, bm25Hits, topK);
        log.info("[RAG] 混合检索完成：vector={} bm25={} -> 融合后 {} 条（topK={}）",
                vectorHits.size(), bm25Hits.size(), refs.size(), topK);
        return refs;
    }

    /** 仅向量检索（排查/降级用）。 */
    public List<RagReference> searchVectorOnly(String query, int topK) {
        return toReferences(vectorRecall(query, topK), topK);
    }

    /** 仅 BM25 检索（排查/对比用）。 */
    public List<RagReference> searchBm25Only(String query, int topK) {
        return toReferences(bm25Recall(query, topK), topK);
    }

    // ==================== 单路召回 ====================

    private List<Hit> vectorRecall(String query, int limit) {
        Embedding embedding = embeddingModel.embed(QUERY_PREFIX + query).content();
        String vecLiteral = toVectorLiteral(embedding.vector());

        // pgvector 余弦距离 <=>；score = (2 - distance) / 2 归一化到 [0,1]，0.5 = 余弦相似度 0
        String sql = """
                SELECT id, content, scene, tags::text AS tags,
                       (2.0 - (embedding <=> CAST(? AS vector))) / 2.0 AS score
                FROM rag_knowledge
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> CAST(? AS vector) ASC
                LIMIT ?
                """;

        List<Hit> hits = new ArrayList<>();
        for (Map<String, Object> row : pgJdbcTemplate().queryForList(sql, vecLiteral, vecLiteral, limit)) {
            double score = number(row.get("score"));
            if (score < minScore) {
                continue;
            }
            Hit hit = toHit(row, score);
            if (hit != null) {
                hits.add(hit);
            }
        }
        return hits;
    }

    private List<Hit> bm25Recall(String query, int limit) {
        String tsQuery = buildTsQuery(query);
        if (tsQuery.isBlank()) {
            log.debug("[RAG] BM25 query 分词后为空，跳过 BM25 召回");
            return List.of();
        }

        // to_tsquery 用 OR 语义（token1 | token2 ...）提高召回；分词与入库侧 jieba 保持一致
        String sql = """
                SELECT id, content, scene, tags::text AS tags,
                       ts_rank(content_tsv, to_tsquery('simple', ?)) AS score
                FROM rag_knowledge
                WHERE content_tsv @@ to_tsquery('simple', ?)
                ORDER BY score DESC
                LIMIT ?
                """;

        List<Hit> hits = new ArrayList<>();
        for (Map<String, Object> row : pgJdbcTemplate().queryForList(sql, tsQuery, tsQuery, limit)) {
            Hit hit = toHit(row, number(row.get("score")));
            if (hit != null) {
                hits.add(hit);
            }
        }
        return hits;
    }

    // ==================== RRF 融合 ====================

    /**
     * 加权 RRF 融合：score(d) = Σ weight_i / (k + rank_i(d))。
     * 只依赖排名，不依赖各路分数量纲，因此无需归一化 BM25 分与余弦分；
     * 权重让可信度更高的向量路占据主导，避免弱 BM25 命中与强向量命中并列。
     */
    private List<RagReference> mergeByRrf(List<Hit> vectorHits, List<Hit> bm25Hits, int topK) {
        Map<Long, Hit> hitMap = new LinkedHashMap<>();
        Map<Long, Double> rrfScores = new HashMap<>();

        accumulate(vectorHits, hitMap, rrfScores, vectorWeight);
        accumulate(bm25Hits, hitMap, rrfScores, bm25Weight);

        if (rrfScores.isEmpty()) {
            return List.of();
        }

        // 两路都命中且排第一时的理论最高分，用于把融合分归一化到 [0,1]
        double maxRrf = (vectorHits.isEmpty() ? 0.0 : vectorWeight / (rrfK + 1))
                + (bm25Hits.isEmpty() ? 0.0 : bm25Weight / (rrfK + 1));

        return rrfScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(e -> {
                    Hit hit = hitMap.get(e.getKey());
                    double normalized = maxRrf > 0 ? Math.min(1.0, e.getValue() / maxRrf) : 0.0;
                    return new RagReference(truncate(hit.content()), hit.scene(), hit.tags(), normalized);
                })
                .toList();
    }

    private void accumulate(List<Hit> hits, Map<Long, Hit> hitMap, Map<Long, Double> rrfScores, double weight) {
        for (int rank = 0; rank < hits.size(); rank++) {
            Hit hit = hits.get(rank);
            hitMap.putIfAbsent(hit.id(), hit);
            rrfScores.merge(hit.id(), weight / (rrfK + rank + 1), Double::sum);
        }
    }

    private List<RagReference> toReferences(List<Hit> hits, int topK) {
        return hits.stream()
                .limit(topK)
                .map(h -> new RagReference(truncate(h.content()), h.scene(), h.tags(), h.score()))
                .toList();
    }

    // ==================== 分词与工具方法 ====================

    /** 用 jieba 搜索模式分词，拼成 to_tsquery 可用的 OR 表达式。 */
    private static String buildTsQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (SegToken token : JIEBA.process(query, JiebaSegmenter.SegMode.SEARCH)) {
            String word = NON_WORD.matcher(token.word).replaceAll("");
            // 过滤单字与纯标点，降低噪声（"的/了" 等）
            if (word.length() > 1) {
                tokens.add(word.toLowerCase());
            }
        }
        return String.join(" | ", tokens);
    }

    private static Hit toHit(Map<String, Object> row, double score) {
        String content = str(row.get("content"));
        if (content == null || content.isBlank()) {
            return null;
        }
        Object id = row.get("id");
        long idValue = id instanceof Number n ? n.longValue() : -1L;
        return new Hit(idValue, content, str(row.get("scene")), str(row.get("tags")), score);
    }

    private JdbcTemplate pgJdbcTemplate() {
        if (pgJdbcTemplate == null) {
            synchronized (this) {
                if (pgJdbcTemplate == null) {
                    HikariConfig cfg = new HikariConfig();
                    cfg.setJdbcUrl(pgUrl);
                    cfg.setUsername(pgUsername);
                    cfg.setPassword(pgPassword);
                    cfg.setMaximumPoolSize(2);
                    cfg.setPoolName("rag-search");
                    // 提高 ivfflat 探查数量，避免近似检索漏掉真实最近邻
                    cfg.setConnectionInitSql("SET ivfflat.probes = " + Math.max(1, ivfflatProbes));
                    pgJdbcTemplate = new JdbcTemplate(new HikariDataSource(cfg));
                }
            }
        }
        return pgJdbcTemplate;
    }

    @PreDestroy
    public void close() {
        if (pgJdbcTemplate != null && pgJdbcTemplate.getDataSource() instanceof HikariDataSource hikari) {
            hikari.close();
        }
    }

    /** 把 float[] 转成 pgvector 接受的字符串字面量，如 [0.1,0.2,...]。 */
    private static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        return sb.append("]").toString();
    }

    private static double number(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String truncate(String content) {
        if (content == null || content.length() <= MAX_CONTENT_CHARS) {
            return content;
        }
        return content.substring(0, MAX_CONTENT_CHARS) + "\n...（内容过长已截断）";
    }

    /** 单路召回的一条候选，用于 RRF 融合。 */
    private record Hit(long id, String content, String scene, String tags, double score) {
    }
}
