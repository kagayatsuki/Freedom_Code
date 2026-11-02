package com.coding.agent.controller;

import com.coding.agent.domain.dto.FileNodeDTO;
import com.coding.agent.domain.dto.ProjectAddDTO;
import com.coding.agent.service.FileService;
import com.coding.agent.utils.UserContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class FileController {
    private final FileService fileService;
    // 在 FileController.java 中追加以下方法：
    @GetMapping("/{userName}")
    public List<String> getProjects(@PathVariable String userName) {
        // 自动通过你之前的 WrapperResponseBodyAdvice 包装成 R<List<String>>
        return fileService.getUserProjects(userName);
    }
    @GetMapping("/{userName}/{projectName}/tree")
    public List<FileNodeDTO> getProjectTree(
            @PathVariable String userName,
            @PathVariable String projectName) {
        return fileService.getTree(userName, projectName);
    }
    @GetMapping("/{userName}/{projectName}/file/content")
    public String getFileContent(
            @PathVariable String userName,
            @PathVariable String projectName,
            @RequestParam String path) throws IOException {
        return fileService.readFile(userName, projectName, path);
    }

    @PostMapping("/add")
    public void addProject(@RequestBody ProjectAddDTO dto) {
        // 自动包装 R 对象的 ResponseBodyAdvice 依然会生效
        fileService.addProject(dto.getProjectName(), dto.getUserName());
    }
    @PostMapping("/{userName}/{projectName}/edit")
    public void editFile(
            @PathVariable("userName") String userName,
            @PathVariable("projectName") String projectName,
            @RequestParam("path") String path, // 文件的相对路径，通过 Query 拼接
            @RequestBody String fileContent    // 接收前端 Monaco 传过来的纯文本代码内容
    ) {
        // 调用你的 Service 层去写入物理磁盘
        fileService.editFile(userName, projectName, path, fileContent);
    }

    /**
     * 检测项目预览入口路径：有 dist/index.html 返回 dist，否则根目录 index.html；
     * 都不存在时 exists=false，前端据此置灰「预览」按钮
     */
    @GetMapping("/{userName}/{projectName}/preview-path")
    public Map<String, Object> getPreviewPath(
            @PathVariable String userName,
            @PathVariable String projectName) {
        Path projectRoot = Paths.get("D:/code", userName, projectName);
        Path distIndex = projectRoot.resolve("dist/index.html");
        if (Files.isRegularFile(distIndex)) {
            return Map.of("exists", true, "path", "dist/index.html");
        }
        Path rootIndex = projectRoot.resolve("index.html");
        if (Files.isRegularFile(rootIndex)) {
            return Map.of("exists", true, "path", "index.html");
        }
        return Map.of("exists", false, "path", "");
    }
    @GetMapping("/{userName}/{projectName}/download")
    public void downloadProject(
            @PathVariable String userName,
            @PathVariable String projectName,
            HttpServletResponse response) throws IOException {
        fileService.downloadProject(userName,projectName,response);
    }
}
