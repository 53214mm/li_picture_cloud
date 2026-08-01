package com.li.lipicturecloud.collaboration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.li.lipicturecloud.collaboration.model.CollaborationEvent;
import com.li.lipicturecloud.collaboration.model.CollaborationState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "redis.integration.enabled", matches = "true")
class RedisCollaborationEventBusTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                System.getProperty("redis.host", "127.0.0.1"),
                Integer.parseInt(System.getProperty("redis.port", "6379")));
        String password = System.getProperty("redis.password", "");
        if (!password.isBlank()) {
            configuration.setPassword(password);
        }
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @Test
    void onePublishedEventReachesSubscribersOnTwoBackendInstances() throws Exception {
        String channel = "picture-cloud:test:collaboration:" + System.nanoTime();
        CountDownLatch received = new CountDownLatch(2);
        RedisMessageListenerContainer first = listener(channel, received);
        RedisMessageListenerContainer second = listener(channel, received);
        try {
            first.start();
            second.start();
            RedisCollaborationEventPublisher publisher = new RedisCollaborationEventPublisher(
                    redis, new ObjectMapper(), channel, "instance-a");

            publisher.publish(7L, CollaborationEvent.state(new CollaborationState(7L, 90, 1.0, 1)));

            assertThat(received.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {
            first.stop();
            second.stop();
        }
    }

    private RedisMessageListenerContainer listener(String channel, CountDownLatch received) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            if (payload.contains("\"pictureId\":7")) {
                received.countDown();
            }
        }, new ChannelTopic(channel));
        container.afterPropertiesSet();
        return container;
    }
}
