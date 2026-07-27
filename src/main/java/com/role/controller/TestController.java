package com.role.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    // 注入 Spring AI 的核心客户端
    private final ChatClient chatClient;

    // 通过构造器注入
    public TestController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/test")
    public String test() {
        // 发送一个最简单的同步请求
        return chatClient.prompt()
                .user("Integer.parseInt(\"2.0\") 会报错吗？用一句话回答。")
                .call()
                .content(); // 直接获取回复的文本内容
    }
}