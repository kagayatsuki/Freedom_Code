package com.coding.agent.controller;

import com.coding.agent.domain.dto.ChatRequest;
import com.coding.agent.service.FileService;
import com.coding.agent.tools.ChatAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import sun.misc.Unsafe;

import java.util.Deque;
import java.util.LinkedList;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    private final ChatAiService chatAiService;
    private final FileService fileService;


    @PostMapping("/send")
    public String chat(@RequestBody ChatRequest request) {
        String userName = request.getUserName() != null ? request.getUserName() : "guest";
        String projectName = request.getProjectName();
        if (projectName == null || projectName.isBlank()) {
            return "请先选择一个项目，我才能基于项目代码回答你的问题。";
        }

        // 读取项目文件树作为初始上下文
        String fileTree;
        try {
            fileTree = fileService.getTree(userName, projectName).toString();
        } catch (Exception e) {
            fileTree = "（无法读取文件树）";
        }

        String memoryId = "chat_" + userName + "_" + projectName;

        // 把文件结构作为上下文注入用户消息
        String enrichedMessage = """
                  【当前项目：%s/%s】
                  【项目文件结构】：
                  %s

                  【用户问题】：
                  %s
                  """.formatted(userName, projectName, fileTree, request.getMessage());

        log.info("[Chat] user={}, project={}, msg={}", userName, projectName, request.getMessage());
        return chatAiService.chat(memoryId, enrichedMessage);
    }
    public int[] maxSlidingWindow(int[] nums, int k)throws InterruptedException {
        int[] result = new int[nums.length - k + 1];
        Deque<Integer> deque = new LinkedList<>();
        int index = 0;
        for (int i = 0; i < nums.length; i++) {
            //先删窗口以外的
           if(!deque.isEmpty()&&i-k+1>deque.peekFirst()){
               deque.pollFirst();
           }
           System.gc();
           //再删后面小的
           while (!deque.isEmpty()&&nums[deque.peekLast()]<nums[i]){
               deque.pollLast();
           }
           deque.offerLast(i);
           //记住何时开始第一次记录
           if(i>=k-1){
               result[index++] = nums[deque.peekFirst()];
           }

        }
        Unsafe unsafe = Unsafe.getUnsafe();
        int i = unsafe.pageSize();
        System.out.println(i);
        return result;

    }

}
