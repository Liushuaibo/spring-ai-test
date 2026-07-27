package com.role.controller;

import com.role.pojo.response.ChatRequest;
import com.role.pojo.response.Result;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;

    private final ChatMemory chatMemory; // 会话记忆

    // 1. 从 application.yml 中读取模板和默认语气
    @Value("${my-ai.system-template}")
    private String systemTemplateStr;

    @Value("${my-ai.default-voice}")
    private String defaultVoice;


    // 构造器注入 ChatClient
    public ChatController(ChatClient.Builder chatClientBuilder) {
        // 使用内存存储对话历史（企业级通常会换成 Redis 或数据库存储）
        this.chatMemory = new InMemoryChatMemory();

        this.chatClient = chatClientBuilder
                // 3. 将记忆组件作为 Advisor 挂载到 ChatClient 上
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
    }

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody ChatRequest request) {
        try {
            // 1. 定义一个带占位符的模板，{voice} 就是变量
            String systemTemplateStr = this.systemTemplateStr;

            // 2. 使用 Spring AI 的 SystemPromptTemplate 进行渲染
            SystemPromptTemplate systemTemplate = new SystemPromptTemplate(systemTemplateStr);

            // 3. 填充变量，生成最终的 Message 对象
            Message systemMessage = systemTemplate.createMessage(Map.of(
                    "voice", defaultVoice // 这里可以改成 "严厉"、"温柔" 等
            ));

            // 调用大模型并获取纯文本回复
            String aiResponse = chatClient.prompt()
                    .messages(systemMessage)
                    .user(request.getMsg())
                    .call()
                    .content();

            // 包装成统一格式返回
            return Result.success(aiResponse);
        } catch (Exception e) {
            // 简单处理异常，实际项目中建议用全局异常处理器
            return Result.error("AI调用失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        try {
            SystemPromptTemplate systemTemplate = new SystemPromptTemplate(systemTemplateStr);
            Message systenMessage = systemTemplate.createMessage(Map.of("voice", defaultVoice));

            return chatClient.prompt()
                    .messages(systenMessage)
                    .user(request.getMsg())
                    .stream() //关键: 使用流式处理
                    .content(); //只返回文本内容流
        } catch (Exception e) {
            // 流式接口中如果出错，返回一个包含错误信息的 Flux
            return Flux.just("AI调用失败: " + e.getMessage());
        }
    }


    @PostMapping(value = "/chat/memory", produces = "text/event-stream")
    public Flux<String> chatWithMemory(@RequestBody ChatRequest request) {
        try {
            SystemPromptTemplate systemTemplate = new SystemPromptTemplate(systemTemplateStr);
            Message systenMessage = systemTemplate.createMessage(Map.of("voice", defaultVoice));

            return chatClient.prompt()
                    .messages(systenMessage)
                    .user(request.getMsg())
                    .advisors(spec -> spec.param("chat_memory_conversation_id", request.getConversationId()))
                    .stream() //关键: 使用流式处理
                    .content(); //只返回文本内容流
        } catch (Exception e) {
            // 流式接口中如果出错，返回一个包含错误信息的 Flux
            return Flux.just("AI调用失败: " + e.getMessage());
        }
    }

    @PostMapping("/chat/memory-sync")
    public Result<String> chatWithMemorySync(@RequestBody ChatRequest request) {
        try {
            SystemPromptTemplate systemTemplate = new SystemPromptTemplate(systemTemplateStr);
            Message systemMessage = systemTemplate.createMessage(Map.of("voice", defaultVoice));

            // 注意这里改成了 .call() 和 .content()
            String response = chatClient.prompt()
                    .messages(systemMessage)
                    .user(request.getMsg())
                    .advisors(spec -> spec.param("chat_memory_conversation_id", request.getConversationId()))
                    .call()
                    .content();

            return Result.success(response);
        } catch (Exception e) {
            return Result.error("AI调用失败: " + e.getMessage());
        }
    }
}