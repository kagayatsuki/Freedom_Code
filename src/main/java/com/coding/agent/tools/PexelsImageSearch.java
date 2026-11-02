package com.coding.agent.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * Pexels 图片搜索工具
 * AI 通过 Tool 调用获取真实图片 URL，直接嵌入生成的 HTML
 */
@Slf4j
@Component("pexelsImageSearch")
public class PexelsImageSearch {

    private static final String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    @Value("${pexels.api-key}")
    private String apiKey;

    @Tool("搜索真实图片URL，用于在网页中展示。keyword建议使用英文，返回的URL可直接用于 <img src=\"...\">"+
     "注意：整个项目最多调用2次此工具，不要更换关键词重复搜索。" +
             "如果搜索结果不理想，请接受并选择最接近的图片使用。")
    public String searchImages(
            @P("英文搜索关键词，如 'ecommerce product' 'nature landscape'")
            String keyword,
            @P("需要的图片数量，建议 3~6 张")
            int count) {

        if (keyword == null || keyword.isBlank()) {
            return "错误：搜索关键词不能为空";
        }
        if (count <= 0 || count > 20) {
            count = 4;
        }

        StringBuilder result = new StringBuilder();

        try (HttpResponse response = HttpRequest.get(PEXELS_API_URL)
                .header("Authorization", apiKey)
                .form("query", keyword)
                .form("per_page", count)
                .form("page", 1)
                .timeout(10_000)
                .execute()) {

            if (!response.isOk()) {
                log.error("Pexels API 返回非 200: {}", response.getStatus());
                return "图片搜索失败：Pexels API 返回状态码 " + response.getStatus();
            }

            JSONObject body = JSONUtil.parseObj(response.body());
            JSONArray photos = body.getJSONArray("photos");

            if (photos == null || photos.isEmpty()) {
                return "未找到与 '" + keyword + "' 相关的图片，请尝试更换关键词";
            }
            log.info("photos:{}",photos);

            for (int i = 0; i < photos.size(); i++) {
                JSONObject photo = photos.getJSONObject(i);
                JSONObject src = photo.getJSONObject("src");
                String url = src.getStr("medium");
                String alt = photo.getStr("alt", keyword);
                String photographer = photo.getStr("photographer", "Pexels");

                result.append(String.format("[图片%d] %s (by %s): %s\n",
                        i + 1, alt, photographer, url));
            }

            log.info("Pexels 搜索成功 | 关键词: {} | 返回 {} 张图片", keyword, photos.size());
            return result.toString();

        } catch (Exception e) {
            log.error("Pexels API 调用失败: {}", e.getMessage(), e);
            return "图片搜索失败: " + e.getMessage() + "。请在 HTML 中使用纯色背景替代图片。";
        }
    }



}
