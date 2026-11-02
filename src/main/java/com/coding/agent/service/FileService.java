package com.coding.agent.service;

import com.coding.agent.domain.dto.FileNodeDTO;
import com.coding.agent.exception.BadRequestException;
import com.coding.agent.utils.WorkflowContextHolder;
import com.coding.agent.workflow.WorkflowContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class FileService {
    private static final String BASE_ROOT = "D:\\code";

    private Path getProjectBaseDir(String userName, String projectName) {
        return Paths.get(BASE_ROOT, userName, projectName);
    }

    public List<String> getUserProjects(String userName) {
        Path userRoot = Paths.get(BASE_ROOT, userName);

        // 如果该用户的 code 根目录还不存在，说明是新用户，直接返回空列表
        if (!Files.exists(userRoot) || !Files.isDirectory(userRoot)) {
            return new ArrayList<>();
        }

        try (Stream<Path> stream = Files.list(userRoot)) {
            return stream
                    .filter(Files::isDirectory) // 只保留文件夹（一个文件夹代表一个项目）
                    .map(path -> path.getFileName().toString()) // 获取文件夹名字
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("扫描用户项目列表失败, userName: {}", userName, e);
            return new ArrayList<>();
        }
    }

    public List<FileNodeDTO> getTree(String userName, String projectName) {
        Path rootPath = getProjectBaseDir(userName, projectName);
        // 如果目录不存在，可以先创建一个，或者抛出异常
        if (!Files.exists(rootPath)) {
            return new ArrayList<>();
        }
        return scanDirectory(rootPath, rootPath);
    }

    public String readFile(String userName, String projectName, String relativePath) throws IOException {
        if (Paths.get(relativePath).isAbsolute()) {
            throw new SecurityException("危险：拒绝接受绝对路径访问！");
        }
        Path root = getProjectBaseDir(userName, projectName).toAbsolutePath();
        Path target = root.resolve(relativePath).toAbsolutePath();

        // 安全检查：防止路径遍历 (Path Traversal)
        if (!target.startsWith(root)) {
            throw new SecurityException("非法的文件路径访问");
        }
        if (Files.isDirectory(target)) {
            return "/* 这是一个文件夹，无法直接预览内容 */";
        }

        if (!Files.exists(target)) {
            return "/* 文件不存在 */";
        }

        return Files.readString(target);
    }

    private List<FileNodeDTO> scanDirectory(Path root, Path current) {
        List<FileNodeDTO> nodes = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path entry : stream) {
                FileNodeDTO node = new FileNodeDTO();
                node.setLabel(entry.getFileName().toString());
                node.setFolder(Files.isDirectory(entry));
                // 计算相对于根目录的路径
                node.setPath(root.relativize(entry).toString());
                node.setId(UUID.nameUUIDFromBytes(node.getPath().getBytes()).toString());

                if (node.isFolder()) {
                    node.setChildren(scanDirectory(root, entry));
                }
                nodes.add(node);
            }
        } catch (IOException e) {
            log.error("扫描目录失败", e);
        }
        return nodes;
    }

    public void addProject(String projectName, String userName)  {
        if (projectName.contains("/") || projectName.contains("\\") || projectName.contains("..")) {
            throw new BadRequestException("项目名称包含非法字符！");
        }
        Path newProject = getProjectBaseDir(userName, projectName);
        if (Files.exists(newProject)) {
            throw new BadRequestException("项目已存在！");
        }
        try {
            Files.createDirectories(newProject);
        } catch (IOException e) {
            log.error("创建文件夹失败：", e);
            throw new RuntimeException(e);
        }
    }
    //path是相对路径
    public void editFile(String userName, String projectName, String path, String fileContent) {
        Path absolutePath = Paths.get(BASE_ROOT, userName, projectName, path);
        //防止../../..非法路径
        log.info("向该路径写入文本: {}", absolutePath);
        if(!absolutePath.startsWith(Paths.get(BASE_ROOT,userName,projectName).normalize())){
            throw new BadRequestException("非法文件路径，禁止访问！！！");
        }
        if(!Files.exists(absolutePath)|| Files.isDirectory(absolutePath)){
            throw new BadRequestException("文件不存在或者是文件夹");
        }
        try {
            Files.writeString(
                    absolutePath,
                    fileContent,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            String successMsg = String.format("📄 文件已生成: %s", path);
            WorkflowContext context = WorkflowContextHolder.getContext();
            if(context!=null){
                context.notifyFileWritten(successMsg);
            }
            log.info("文件写入成功: {}", path);
        } catch (IOException e) {
            log.error("读取文件失败", e);
            throw new RuntimeException(e);
        }
    }

    public List<String> getTree4Check(String userName, String projectName) {
        // 1. 获取项目在沙箱中的物理绝对根路径（如 /sandbox/user/project）
        Path rootPath = getProjectBaseDir(userName, projectName).toAbsolutePath().normalize();

        if (!Files.exists(rootPath)) {
            log.warn("⚠️ 项目根目录不存在: {}", rootPath);
            return Collections.emptyList();
        }

        // 2. 使用带有自动资源释放的 try-with-resources 流式扫描
        try (Stream<Path> pathStream = Files.walk(rootPath)) {
            return pathStream
                    // 过滤掉文件夹本身，只留下文件
                    .filter(Files::isRegularFile)
                    .map(Path::toAbsolutePath)

                    .filter(path -> !isNodeModules(path, rootPath))

                    .filter(this::isCoreFrontendAsset)
                    // 将绝对路径裁剪转换为相对项目根目录的相对路径（如 src/App.vue）
                    .map(path -> rootPath.relativize(path).toString().replace("\\", "/"))
                    .collect(Collectors.toList());

        } catch (IOException e) {
            log.error("💥 扫描质检文件树失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 判定是否属于 node_modules 依赖库内容
     */
    private boolean isNodeModules(Path path, Path rootPath) {
        String relativeStr = rootPath.relativize(path).toString().toLowerCase();
        return relativeStr.contains("node_modules/") || relativeStr.contains("node_modules\\")
                ||relativeStr.startsWith("dist/") || relativeStr.startsWith("dist\\");
    }

    /**
     * 判定是否是你发现的黄金核心资产（index.html, .vue, .js, .ts）
     */
    private boolean isCoreFrontendAsset(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith("index.html")
                || fileName.endsWith(".vue")
                || fileName.endsWith(".js")
                || fileName.endsWith(".ts")
                || fileName.endsWith(".jsx")
                || fileName.endsWith(".tsx");
    }

    public void downloadProject(String userName, String projectName, HttpServletResponse response) throws IOException {
        Path projectDir = Paths.get("D:/code", userName, projectName);
        if (!Files.exists(projectDir)) {
            response.setStatus(404);
            return;
        }
        if(!projectDir.startsWith(Paths.get(BASE_ROOT,userName,projectName).normalize())){
            response.setStatus(404);
            return;
        }
        String zipName = URLEncoder.encode(projectName + ".zip", StandardCharsets.UTF_8);
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + zipName);
        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            Files.walk(projectDir)
                    .filter(Files::isRegularFile)
                    .filter(p->!p.toString().contains("node_modules"))
                    .forEach(p->{
                        try {
                            String entryName = projectDir.relativize(p).toString().replace("\\", "/");
                            zos.putNextEntry(new ZipEntry(entryName));
                            Files.copy(p, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }catch (IOException ignored){}





    }
}
