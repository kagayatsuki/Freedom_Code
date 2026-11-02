package com.coding.agent.config;

import com.coding.agent.domain.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
@RequiredArgsConstructor

public class WrapperResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper=new ObjectMapper();

    @Override
    public boolean supports(MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        // 1. 如果返回值已经是你的结果类 R（天机学堂是 R，你项目里如果是 Result 就改成 Result），就不再包装
        // 2. 这里的第二个条件移除了天机学堂的网关判断，确保在单体或本地测试时也能生效
        return returnType.getParameterType() != R.class;
    }

    @Override
    public Object beforeBodyWrite(
            Object body, @NonNull MethodParameter returnType, @NonNull MediaType selectedContentType,
            @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
            @NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response) {

        // 过滤掉 Swagger 等 API 文档的路径，防止文档页面被包装导致无法显示
        String path = request.getURI().getPath();
        if (path.contains("/v2/api-docs") || path.contains("/v3/api-docs") || path.contains("/doc.html")){
            return body;
        }

        // 情况 A：你的方法返回 void（或者返回了 null）
        if (body == null) {
            return R.ok();
        }
        if (body instanceof String) {
            try {
                // 将响应头修改为 application/json，防止前端解析成纯文本
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                // 手动把 R 对象转成 JSON 字符串返回，切断 StringHttpMessageConverter 的强转报错
                return objectMapper.writeValueAsString(R.ok(body));
            } catch (Exception e) {
                return R.error("String包装序列化失败");
            }
        }
        // 情况 B：你的方法已经手动返回了 R 对象
        if (body instanceof R) {
            return body;
        }

        // 情况 C：方法返回了其他对象（如 User, List 等），自动包装
        return R.ok(body);
    }
}