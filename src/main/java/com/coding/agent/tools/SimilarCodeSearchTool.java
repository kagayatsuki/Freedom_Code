package com.coding.agent.tools;

import com.coding.agent.domain.dto.RagReference;
import com.coding.agent.service.RagIndexService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("similarCodeSearchTool")
@RequiredArgsConstructor
public class SimilarCodeSearchTool {

     private final RagIndexService ragIndexService;

     @Tool("检索知识库中与需求最相关的历史样例（完整 prompt 内容）。传入需求描述，返回参考样例")
     public String searchSimilarCode(String requirementDescription) {
         List<RagReference> refs = ragIndexService.search(requirementDescription, 3);

         if (refs.isEmpty()) return "未在知识库中找到与当前需求相关的样例";

         StringBuilder sb = new StringBuilder("从知识库检索到以下参考样例：\n");
         for (int i = 0; i < refs.size(); i++) {
             RagReference r = refs.get(i);
             String scene = (r.scene() == null || r.scene().isBlank()) ? "未分类" : r.scene();
             String tags = r.tags() == null ? "-" : r.tags();
             sb.append(String.format("\n【样例%d】场景：%s | 标签：%s | 相似度 %.2f\n%s\n",
                     i + 1, scene, tags, r.score(), r.content()));
         }
         return sb.toString();
     }

}
