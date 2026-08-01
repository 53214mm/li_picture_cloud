package com.li.lipicturecloud.collaboration.store;

import com.li.lipicturecloud.collaboration.CollaborationVersionConflictException;
import com.li.lipicturecloud.collaboration.model.CollaborationCommand;
import com.li.lipicturecloud.collaboration.model.CollaborationOperation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfSystemProperty(named = "redis.integration.enabled", matches = "true")
class RedisCollaborationStateStoreTest {

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static RedisCollaborationStateStore store;

    @BeforeAll
    static void setUpRedis() {
        String host = System.getProperty("redis.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("redis.port", "6379"));
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        String password = System.getProperty("redis.password", "");
        if (!password.isBlank()) {
            configuration.setPassword(password);
        }
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        store = new RedisCollaborationStateStore(redis, Duration.ofMinutes(5));
    }

    @BeforeEach
    void clearKeys() {
        redis.delete(store.stateKey(7001L));
        redis.delete(store.commandKey(7001L, "same"));
        redis.delete(store.commandKey(7001L, "next"));
    }

    @AfterAll
    static void tearDownRedis() {
        redis.delete(store.stateKey(7001L));
        redis.delete(store.commandKey(7001L, "same"));
        redis.delete(store.commandKey(7001L, "next"));
        connectionFactory.destroy();
    }

    @Test
    void atomicallyAppliesAndDeduplicatesCommandsWithTtl() {
        assertThat(store.current(7001L).version()).isZero();
        CollaborationCommand command = command("same", CollaborationOperation.ROTATE_RIGHT, 0);

        ApplyCollaborationResult first = store.apply(command);
        RedisCollaborationStateStore secondInstance = new RedisCollaborationStateStore(
                redis, Duration.ofMinutes(5));
        ApplyCollaborationResult duplicate = secondInstance.apply(command);

        assertThat(first.newlyApplied()).isTrue();
        assertThat(first.state().rotation()).isEqualTo(90);
        assertThat(duplicate.newlyApplied()).isFalse();
        assertThat(duplicate.state()).isEqualTo(first.state());
        assertThat(secondInstance.current(7001L)).isEqualTo(first.state());
        assertThat(redis.getExpire(store.stateKey(7001L))).isPositive();
        assertThat(redis.getExpire(store.commandKey(7001L, "same"))).isPositive();
    }

    @Test
    void rejectsAStaleVersionAcrossAtomicUpdates() {
        store.apply(command("same", CollaborationOperation.ZOOM_IN, 0));

        assertThatThrownBy(() -> store.apply(command("next", CollaborationOperation.ZOOM_OUT, 0)))
                .isInstanceOf(CollaborationVersionConflictException.class);
    }

    private static CollaborationCommand command(String id, CollaborationOperation operation, long version) {
        return new CollaborationCommand(id, 7001L, 99L, operation, version);
    }
}
