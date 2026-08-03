package com.role.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    // 将 Spring AI 自动配置好的 EmbeddingModel 注入进来
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // 创建一个基于内存的向量数据库
        return new SimpleVectorStore(embeddingModel);
    }
}