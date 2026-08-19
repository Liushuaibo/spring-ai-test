package com.role.controller;

import com.role.pojo.response.ChatRequest;
import com.role.pojo.response.Result;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;

    private final ChatMemory chatMemory; // 会话记忆

    private final VectorStore vectorStore;



    // 1. 从 application.yml 中读取模板和默认语气
    @Value("${my-ai.system-template}")
    private String systemTemplateStr;

    @Value("${my-ai.default-voice}")
    private String defaultVoice;


    // 构造器注入 ChatClient
    public ChatController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
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


    // 测试 RAG 环境的接口
    @GetMapping("/test-rag")
    public Result<String> testRag() {
        try {
            // 1. 准备一条测试数据（模拟公司的开发规范）
            Document doc = new Document("公司规定：Java代码中的变量命名必须使用小驼峰命名法。");

            // 2. 存入向量数据库（Spring AI 会自动调用 Embedding 模型把这句话变成数字存起来）
            vectorStore.add(List.of(doc));

            // 3. 从向量数据库中检索（模拟用户提问）
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.query("Java变量怎么命名？").withTopK(1)
            );

            // 4. 返回检索到的内容
            if (!results.isEmpty()) {
                return Result.success("检索成功，最相关的规范是：" + results.get(0).getContent());
            } else {
                return Result.success("未检索到相关内容");
            }
        } catch (Exception e) {
            return Result.error("RAG环境测试失败: " + e.getMessage());
        }
    }


    // 测试文读取和分割接口
    @GetMapping("/test-split")
    public Result<List<String>> testDoucementSplit(){
        try {
            //读取文件
            TextReader textReader = new TextReader(new ClassPathResource("java-spec.txt"));
            //设置读取时的编码格式,防止中文乱码
            textReader.getCustomMetadata().put("charset","utf-8");
            //读取文件
            List<Document> rowDocs = textReader.get();
            // 2. 使用 TokenTextSplitter 对文档进行切分
            // 参数说明：
            // 1000: 每个文本块的最大 Token 数量
            // 400: 相邻文本块之间的重叠 Token 数量（保留上下文）
            // 5: 最小文本块大小
            // 10000: 最大文本块大小
            // true: 是否保持段落结构
            TokenTextSplitter tokenTextSplitter = new TokenTextSplitter(1000, 400, 5, 10000, true);
            List<Document> splitDocs = tokenTextSplitter.apply(rowDocs);

            //3.提取切分后的内容,返回给前端
            List<String> chunkContents = splitDocs.stream().map(Document::getContent).toList();
            return Result.success(chunkContents);

        }catch (Exception e){
            return Result.error("文档切分失败: " + e.getMessage());
        }

    }


    // 新增：初始化企业私有知识库接口
    @GetMapping("/init-knowledge")
    public Result<String> initKnowledgeBase() {
        try {
            // 1. 读取并切分文档（复用昨天的逻辑）
            TextReader textReader = new TextReader(new ClassPathResource("java-spec.txt"));
            textReader.getCustomMetadata().put("charset", "UTF-8");
            List<Document> rawDocs = textReader.get();

//            TokenTextSplitter splitter = new TokenTextSplitter(1000, 400, 5, 10000, true);
            TokenTextSplitter splitter = new TokenTextSplitter(1000, 100, 5, 10000, true);

            List<Document> splitDocs = splitter.apply(rawDocs);

            // 2. 为每个文本块添加元数据（Metadata），方便后续溯源
            for (Document doc : splitDocs) {
                doc.getMetadata().put("source", "java-spec.txt");
                doc.getMetadata().put("type", "开发规范");
            }

            // 3. 存入向量数据库
            // 注意：当调用 add 方法时，Spring AI 会自动调用 Embedding 模型，
            // 把 splitDocs 里的文本转换成向量，并连同原文本一起存入 VectorStore
            vectorStore.add(splitDocs);

            return Result.success("知识库初始化成功！共存入 " + splitDocs.size() + " 个知识片段。");
        } catch (Exception e) {
            return Result.error("知识库初始化失败: " + e.getMessage());
        }
    }

    // 新增：测试语义检索接口
    @GetMapping("/test-search")
    public Result<List<Map<String, Object>>> testSearch(String question) {
        try {
            // 1. 构建检索请求
            // withTopK(3) 表示最多返回 3 个最相关的片段
            // withSimilarityThreshold(0.6) 表示相似度必须大于 0.6 才算命中
            SearchRequest searchRequest = SearchRequest.query(question)
                    .withTopK(3)
                    .withSimilarityThreshold(0.33);

            // 2. 执行相似度搜索
            List<Document> results = vectorStore.similaritySearch(searchRequest);

            // 3. 组装返回结果，把文本内容和相似度得分展示出来
            List<Map<String, Object>> responseList = results.stream().map(doc -> {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("content", doc.getContent());
                // 注意：部分版本的 Spring AI 获取相似度得分的方法可能是 doc.getMetadata().get("distance")
                // 或者直接通过 SearchRequest 的返回结果获取，这里为了直观展示，先返回文本内容
                map.put("source", doc.getMetadata().get("source"));
                return map;
            }).toList();

            return Result.success(responseList);
        } catch (Exception e) {
            return Result.error("语义检索失败: " + e.getMessage());
        }
    }


    // 终极 RAG 问答接口
    @PostMapping("/chat/rag")
    public Result<String> chatWithRag(@RequestBody ChatRequest request) {
        try {
            // 1. 根据用户的问题，去向量数据库里检索相关知识
            List<Document> relevantDocs = vectorStore.similaritySearch(
                    SearchRequest.query(request.getMsg()).withTopK(2).withSimilarityThreshold(0.3)
            );

            // 2. 把检索到的文档内容拼接成一个长字符串
            String context = relevantDocs.stream()
                    .map(Document::getContent)
                    .collect(Collectors.joining("\n---\n"));

            // 3. 组装终极 Prompt（给大模型下达指令）
            String finalPrompt = String.format("""
                    你是一个严谨的Java架构师。请严格根据下面提供的【背景知识】来回答用户的【问题】。
                    如果背景知识中没有相关信息，请直接回答“抱歉，知识库中未找到相关规定”，严禁自己瞎编。
                    
                    【背景知识】：
                    %s
                    
                    【用户问题】：
                    %s
                    """, context, request.getMsg());

            // 4. 调用大模型并返回结果
            String aiResponse = chatClient.prompt()
                    .user(finalPrompt)
                    .call()
                    .content();

            return Result.success(aiResponse);
        } catch (Exception e) {
            return Result.error("RAG问答失败: " + e.getMessage());
        }
    }

    // 终极 RAG 问答接口
    @PostMapping("/chat/rag1")
    public Result<String> chatWithRag1(@RequestBody ChatRequest request) {
        try {
            // 1. 根据用户的问题，去向量数据库里检索相关知识
            List<Document> relevantDocs = vectorStore.similaritySearch(
                    SearchRequest.query(request.getMsg()).withTopK(2).withSimilarityThreshold(0.3)
            );

            // 2. 把检索到的文档内容拼接成一个长字符串
            String context = relevantDocs.stream()
                    .map(Document::getContent)
                    .collect(Collectors.joining("\n---\n"));

            // 3. 组装终极 Prompt（给大模型下达指令）
            String finalPrompt = String.format("""
                    你是一个严谨的Java架构师。请严格根据下面提供的【背景知识】来回答用户的【问题】。
                    如果背景知识中没有相关信息，请直接回答“抱歉，知识库中未找到相关规定”，严禁自己瞎编。
                    
                    【背景知识】：
                    %s
                    
                    【用户问题】：
                    %s
                    """, context, request.getMsg());

            // 4. 调用大模型并返回结果
            String aiResponse = chatClient.prompt()
                    .user(finalPrompt)
                    .call()
                    .content();

            return Result.success(aiResponse);
        } catch (Exception e) {
            return Result.error("RAG问答失败: " + e.getMessage());
        }
    }

}