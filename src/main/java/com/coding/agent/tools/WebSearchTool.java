package com.coding.agent.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.coding.agent.domain.dto.SearchResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import java.util.*;
@Component("webSearchTool")
@Slf4j
public class WebSearchTool {
    @Value("${websearch.provider:tavily}")
    private String provider;

    @Value("${websearch.api-key}")
    private String apiKey;

    private static final String TAVILY_URL = "https://api.tavily.com/search";

    // ==================== Brave Search API ====================
    private static final String BRAVE_URL = "https://api.search.brave.com/res/v1/web/search";

    @Tool("联网搜索真实资料，用于获取专业知识、最新数据或官方文档。" +
            "返回每条结果的标题、摘要和来源URL。query 建议使用中文关键词")
    public String searchWeb(
            @P("搜索关键词，如 'maimai DX 评分规则 达成率计算公式'")
            String query,
            @P("返回结果数量，建议 3~5 条")
            int count) {
        if(query==null||query.isBlank()){
            return "搜索关键词不能为空！";
        }
        if(count<=0||count>10){
            count=5;
        }
        
        List<SearchResult> results = search(query, count);

        if (results.isEmpty()) {
            return "未找到与 '" + query + "' 相关的搜索结果";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 联网搜索结果（基于真实资料，请据此生成代码）===\n\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append(String.format("[结果%d] %s\n", i + 1, r.getTitle()));
            sb.append(String.format("  摘要: %s\n", r.getSnippet()));
            sb.append(String.format("  来源: %s\n\n", r.getUrl()));
        }
        return sb.toString();
    }

    public List<SearchResult> search(String query, int count) {
        return switch (provider.toLowerCase()){
            case "tavily" -> searchTavily(query, count);
            case "brave" -> searchBrave(query, count);
            default -> searchTavily(query,count);
        };
    }

    private List<SearchResult> searchBrave(String query, int count) {
        try (HttpResponse response = HttpRequest.get(BRAVE_URL)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .header("X-Subscription-Token", apiKey)
                .form("q", query)
                .form("count", count)
                .timeout(15_000)
                .execute()) {

            if (!response.isOk()) {
                log.error("Brave 搜索失败: HTTP {}", response.getStatus());
                return Collections.emptyList();
            }

            JSONObject body = JSONUtil.parseObj(response.body());
            JSONObject web = body.getJSONObject("web");
            if (web == null) return Collections.emptyList();

            JSONArray results = web.getJSONArray("results");
            if (results == null || results.isEmpty()) return Collections.emptyList();

            List<SearchResult> list = new ArrayList<>();
            for (int i = 0; i < Math.min(results.size(), count); i++) {
                JSONObject r = results.getJSONObject(i);
                list.add(SearchResult.builder()
                        .title(r.getStr("title", ""))
                        .snippet(r.getStr("description", ""))
                        .url(r.getStr("url", ""))
                        .score(r.getDouble("score",0.0))
                        .build());
            }
            log.info("Brave 搜索成功 | 关键词: {} | 返回 {} 条", query, list.size());
            return list;
        } catch (Exception e) {
            log.error("Brave 搜索异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<SearchResult> searchTavily(String query, int count) {
        JSONObject tavilyBody = new JSONObject();
        tavilyBody.set("api_key", apiKey);
        tavilyBody.set("query", query);
        tavilyBody.set("max_results", count);

        try (HttpResponse response = HttpRequest.post(TAVILY_URL)
                .header("Content-Type", "application/json")
                .body(tavilyBody.toString())
                .timeout(15_000)
                .execute()) {
            if (!response.isOk()) {
                log.error("Tavily 搜索失败: HTTP {}", response.getStatus());
                return Collections.emptyList();
            }
            JSONObject body = JSONUtil.parseObj(response.body());
            JSONArray result = body.getJSONArray("results");
            if (result == null || result.isEmpty()) {
                return Collections.emptyList();
            }
            List<SearchResult> list = new ArrayList<>();
            for (int i = 0; i < Math.min(result.size(), count); i++) {
                JSONObject r = result.getJSONObject(i);
                list.add(SearchResult.builder()
                        .title(r.getStr("title", ""))
                        .snippet(r.getStr("content", ""))
                        .url(r.getStr("url", ""))
                        .score(r.getDouble("score", 0.0))
                        .build());
            }

            log.info("Tavily 搜索成功 | 关键词: {} | 返回 {} 条", query, list.size());
            return list;

        } catch (Exception e) {
            log.error("Tavily 搜索异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

}
