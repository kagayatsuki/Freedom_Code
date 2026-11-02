package com.coding.agent.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 搜索结果标题
     */
    private String title;

    /**
     * 搜索结果摘要
     */
    private String snippet;

    /**
     * 来源 URL
     */
    private String url;

    /**
     * 相关性分数（0~1，部分 API 支持）
     */
    private double score;
}

