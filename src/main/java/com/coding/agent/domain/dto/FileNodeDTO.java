package com.coding.agent.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class FileNodeDTO {
    private String id;       // 唯一标识 (可用相对路径的 hash 值)
    private String label;    // 文件名
    private boolean isFolder;  //文件夹还是文件
    private String path;     // 相对路径，作为请求内容的凭证
    private List<FileNodeDTO> children;
}
