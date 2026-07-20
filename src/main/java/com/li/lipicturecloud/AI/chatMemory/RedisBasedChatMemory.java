package com.li.lipicturecloud.AI.chatMemory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis 的聊天记忆实现，使用 Jackson JSON 序列化存储对话历史
 * <p>
 * 相比 Kryo 更可靠：Spring AI Message 实现类支持 Jackson 注解，
 * 不受 Lambda/不可序列化字段影响，反序列化更安全。
 */
@Slf4j
@Component
public class RedisBasedChatMemory implements ChatMemory {

    /** 最大保留消息数，防止对话历史超出模型 context window */
    private static final int MAX_MESSAGES = 40;

    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // ★ 注册 Spring AI Message 子类型
        objectMapper.registerSubtypes(
                new NamedType(AssistantMessage.class, "ASSISTANT"),
                new NamedType(UserMessage.class, "USER"),
                new NamedType(SystemMessage.class, "SYSTEM"),
                new NamedType(ToolResponseMessage.class, "TOOL")
        );
        // ★ 序列化时写入类型信息，反序列化时 Jackson 才知道用哪个具体类
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Message.class)
                .allowIfBaseType(List.class)
                .allowIfBaseType(ArrayList.class)
                .build();
        objectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);
    }

    private final RedisTemplate<String, byte[]> redisTemplate;
    private final String keyPrefix;
    private final Duration ttl;

    public RedisBasedChatMemory(RedisTemplate<String, byte[]> redisTemplate,
                                @Value("${chat-memory.redis.key-prefix:chat:memory:}") String keyPrefix,
                                @Value("${chat-memory.redis.ttl-days:7}") long ttlDays) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.keyPrefix = keyPrefix == null || keyPrefix.isEmpty() ? "chat:memory:" : keyPrefix;
        this.ttl = Duration.ofDays(ttlDays);
    }

    private String keyFor(String conversationId) {
        return keyPrefix + conversationId;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<Message> current = getOrCreateConversation(conversationId);
        current.addAll(messages);

        // ★ 超长截断：限制保留最近 MAX_MESSAGES 条消息
        if (current.size() > MAX_MESSAGES) {
            log.info("对话 {} 消息数 {} 超过上限 {}，截断保留最近 {} 条",
                    conversationId, current.size(), MAX_MESSAGES, MAX_MESSAGES);
            current = current.subList(current.size() - MAX_MESSAGES, current.size());
        }

        saveConversation(conversationId, current);
    }

    @Override
    public List<Message> get(String conversationId) {
        return getOrCreateConversation(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        redisTemplate.delete(keyFor(conversationId));
    }

    private List<Message> getOrCreateConversation(String conversationId) {
        byte[] data = redisTemplate.opsForValue().get(keyFor(conversationId));
        if (data == null || data.length == 0) {
            return new ArrayList<>();
        }
        return deserializeMessages(data);
    }

    private void saveConversation(String conversationId, List<Message> messages) {
        byte[] data = serializeMessages(messages);
        redisTemplate.opsForValue().set(keyFor(conversationId), data, ttl);
    }

    private byte[] serializeMessages(List<Message> messages) {
        try {
            return objectMapper.writeValueAsBytes(messages);
        } catch (Exception e) {
            throw new RuntimeException("序列化消息到 Redis 失败", e);
        }
    }

    private List<Message> deserializeMessages(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes,
                    new TypeReference<List<Message>>() {});
        } catch (Exception e) {
            log.warn("从 Redis 反序列化消息失败（旧格式数据），将重新开始对话", e);
            return new ArrayList<>();
        }
    }
}
