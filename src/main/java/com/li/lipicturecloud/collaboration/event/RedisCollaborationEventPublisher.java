package com.li.lipicturecloud.collaboration.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.collaboration.model.CollaborationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.collaboration.store", havingValue = "redis", matchIfMissing = true)
public class RedisCollaborationEventPublisher implements CollaborationEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String channel;
    private final String instanceId;

    public RedisCollaborationEventPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.collaboration.channel:picture-cloud:collaboration:events}") String channel,
            @Value("${app.collaboration.instance-id:${random.uuid}}") String instanceId) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.channel = channel;
        this.instanceId = instanceId;
    }

    @Override
    public void publish(Long pictureId, CollaborationEvent event) {
        try {
            CollaborationEventEnvelope envelope = new CollaborationEventEnvelope(
                    UUID.randomUUID().toString(), instanceId, pictureId, event);
            redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化协同事件", exception);
        }
    }
}
