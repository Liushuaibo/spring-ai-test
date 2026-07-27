package com.role.pojo.response;

public class ChatRequest {
    private String msg;           // 用户输入的问题
    private String conversationId; // 会话ID（比如用 UUID 生成）

    public ChatRequest() {
    }

    public ChatRequest(String msg) {
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getConversationId() {
        return conversationId;
    }
}
