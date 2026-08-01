package com.li.lipicturecloud.config;

import com.li.lipicturecloud.collaboration.event.CollaborationEventSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "app.collaboration.store", havingValue = "redis", matchIfMissing = true)
public class CollaborationRedisConfig {

    @Bean
    RedisMessageListenerContainer collaborationRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            CollaborationEventSubscriber subscriber,
            @Value("${app.collaboration.channel:picture-cloud:collaboration:events}") String channel) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> subscriber.onMessage(
                new String(message.getBody(), StandardCharsets.UTF_8)), new ChannelTopic(channel));
        return container;
    }
}
