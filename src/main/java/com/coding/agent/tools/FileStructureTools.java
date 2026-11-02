package com.coding.agent.tools;

import cn.hutool.json.JSONUtil;
import com.coding.agent.domain.dto.FileNodeDTO;
import com.coding.agent.exception.BadRequestException;
import com.coding.agent.service.FileService;
import com.coding.agent.workflow.WorkflowContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;

@Slf4j
@Component("fileStructureTools")
@RequiredArgsConstructor
public class FileStructureTools {
    private final FileService fileService;
   // private final ObjectMapper objectMapper;
   @Tool("在指定项目中创建或覆盖文件内容。path 为相对路径（如 src/App.vue 或 index.html）")
   public String writeFile(String userName, String projectName, String path, String content) {
       try {
           // 1. 关键防护：绝不返回 null 或空字符串
           if (content == null || content.trim().isEmpty()) {
               log.warn("AI 尝试写入空内容: {}/{}/{}", userName, projectName, path);
               return "错误：文件内容不能为空";
           }

           log.info("AI 即将写入文件: 用户名：{}/项目名：{}/路径：{}", userName, projectName, path);
           if (path.endsWith("index.html")) {
               log.info("ℹ️ 检测到 HTML 文件写入，启动路径脱钩器，强卡绝对路径为相对路径...");
               content = content.replace("src=\"/assets/", "src=\"assets/")
                       .replace("href=\"/assets/", "href=\"assets/")
                       // 顺便兼容可能出现的单引号情况
                       .replace("src='/assets/", "src='assets/")
                       .replace("href='/assets/", "href='assets/");
           }

           // 2. 构造项目根目录沙箱（请根据你实际的基准路径调整，这里以 D:\code 为例）
           String baseDir = "D:\\code";
           java.io.File projectRootDir = new java.io.File(baseDir + java.io.File.separator + userName
                   + java.io.File.separator + projectName);

           // 获取标准的项目根目录绝对路径（这一步会消化掉路径中潜在的相对符号）
           String canonicalProjectRoot = projectRootDir.getCanonicalPath();

           // 3. 基于沙箱根目录，组装 AI 传入的目标文件对象
           java.io.File targetFile = new java.io.File(projectRootDir, path);
           // 获取目标文件的标准绝对路径
           String canonicalTargetFilePath = targetFile.getCanonicalPath();

           // 🛡️ 【核心安全防护】严格校验：目标文件的实际物理路径必须以项目根目录为前缀！
           // 如果不满足，说明 AI 传入了形如 ../../../ 企图逃逸沙箱的路径
           if (!canonicalTargetFilePath.startsWith(canonicalProjectRoot)) {
               log.error("🚨 安全警报：检测到恶意路径回溯！用户: {}, 项目: {}, 试图写入非法路径: {}",
                       userName, projectName, path);
               return "错误：拒绝写入！路径不合法，不允许回溯到项目根目录之外。";
           }

           // 📁 4. 自动开荒：物理创建可能不存在的父级目录（如 src/components）
           java.io.File parentDir = targetFile.getParentFile();
           if (parentDir != null && !parentDir.exists()) {
               boolean mkdirsResult = parentDir.mkdirs();
               log.info("📁 检测到上级目录不存在，系统已自动创建文件夹: {} | 结果: {}",
                       parentDir.getCanonicalPath(), mkdirsResult);
           }

           // 📄 5. 自动开荒：如果文件不存在，强行在磁盘上创建出这个空白文件
           if (!targetFile.exists()) {
               boolean createNewFileResult = targetFile.createNewFile();
               log.info("📄 检测到目标文件不存在，系统已自动创建物理文件: {} | 结果: {}",
                       targetFile.getCanonicalPath(), createNewFileResult);
           }

           // 6. 规范化文本内容
           content = content.replace("\\\"", "\"")
                   .replace("\r\n", "\n")
                   .replace("\r", "\n");

           // 7. 此时文件在磁盘上已绝对存在，可以放心地交给底层 service 去编辑了
           fileService.editFile(userName, projectName, path, content);

           log.info("✅ AI 写入文件成功: {}/{}/{} | 长度: {} 字符",
                   userName, projectName, path, content.length());

           return "文件写入成功: " + path;

       } catch (BadRequestException e) {
           log.warn("写入失败: {}, 目标路径:{}", e.getMessage(), path);
           return "写入失败: " + e.getMessage();
       } catch (Exception e) {
           log.error("写入文件异常", e);
           return "写入文件异常: " + e.getMessage();
       }
   }
    @Tool("""
        精准修改或替换指定文件中的某一段局部代码。
        适用于修复Bug、微调单行逻辑或局部组件更新。
        要求提供被替换的旧代码块(oldContent)和准备替换的新代码块(newContent)。
        """)
    public String modifyFile(String userName, String projectName, String path, String oldContent, String newContent) {
        try {
            if (oldContent == null || oldContent.isEmpty()) {
                return "错误：被替换的旧内容(oldContent)不能为空";
            }
            if (newContent == null) {
                newContent = "";
            }

            log.info("🛠️ AI 发起精准局部替换. 用户: {}, 项目: {}, 文件: {}", userName, projectName, path);



            // 2. 读取当前文件现有的源码
            String originalContent = fileService.readFile(userName, projectName, path);
            if (originalContent == null || originalContent.trim().isEmpty()) {
                return " 修改失败：该文件内容为空或不存在，无法进行局部替换。请先使用 writeFile 工具全量初始化文件。";
            }

            // 3. 规范化处理旧内容与新内容，抹平特殊字符影响
            oldContent = normalizeContent(oldContent);
            newContent = normalizeContent(newContent);

            // 4. 【第一层门禁】：精准匹配替换
            if (originalContent.contains(oldContent)) {
                String updatedContent = originalContent.replace(oldContent, newContent);
                fileService.editFile(userName, projectName, path, updatedContent);
                log.info("✅ 精准匹配替换成功！文件已热更新: {}", path);
                return "✅ 物理替换成功！已成功更新文件区域: " + path;
            }

            // 5. 【第二层门禁】：🚨 弹性模糊匹配机制
            // 剥离掉所有缩进、空格、换行符，比对核心逻辑是否存在
            String normalizedOriginal = originalContent.replaceAll("\\s+", "");
            String normalizedOld = oldContent.replaceAll("\\s+", "");

            if (!normalizedOriginal.contains(normalizedOld)) {
                log.warn("❌ 精准替换失败：AI 提供的 oldContent 与实际源码核心逻辑不匹配。File: {}", path);
                return "❌ 修改失败：在目标文件中未找到匹配的旧代码片段。请先调用 readFile 查看最新源码，核对好缩进和字符后再试；或者使用 writeFile 工具进行全量覆盖。";
            }

            // 走到这里，说明逻辑是一样的，但由于换行符(CRLF/LF)或Tab/空格混用导致 replace 失败
            log.warn("⚠️ 检测到核心逻辑一致，但因为空白符、换行符差异导致精准替换失败。提示 AI 采用全量覆盖。");
            return "⚠️ 修改失败：原文件中包含这段逻辑，但由于缩进、换行符（LF与CRLF）或空格不一致导致无法精准替换。请直接调用 writeFile 工具对文件进行全量覆盖修改。";

        } catch (Exception e) {
            log.error("精准修改文件异常", e);
            return "❌ 局部修改发生致命异常: " + e.getMessage();
        }
    }
    @Tool("读取指定项目中的文件内容,path是相对路径")
    public String readFile(String userName, String projectName, String path) {
        try {
            String content = fileService.readFile(userName, projectName, path);
            return content != null ? content : "文件内容为空";
        } catch (Exception e) {
            log.warn("读取文件失败", e);
            return "读取失败: " + e.getMessage();
        }
    }

    @Tool("获取项目文件树")
    public String getProjectTree(String userName, String projectName) {
        try {
            var tree = fileService.getTree(userName, projectName);
            return convertTreeToJson(tree);
        } catch (Exception e) {
            log.error("获取文件树失败", e);
            return "[]";
        }
    }

    private String convertTreeToJson(List<FileNodeDTO> tree) {
        if (tree == null || tree.isEmpty()) {
            return "[]";
        }
        try {
            return JSONUtil.toJsonStr(tree);
        } catch (Exception e) {
            log.warn("JSON序列化失败", e);
            return "[]";
        }
    }

    private String normalizeContent(String content) {
        return content.replace("\\\"", "\"")
                .replace("\r\n", "\n")
                .replace("\r", "\n");
    }




}
