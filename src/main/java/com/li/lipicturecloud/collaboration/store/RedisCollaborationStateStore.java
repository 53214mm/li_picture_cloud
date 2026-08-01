package com.li.lipicturecloud.collaboration.store;

import com.li.lipicturecloud.collaboration.CollaborationVersionConflictException;
import com.li.lipicturecloud.collaboration.model.CollaborationCommand;
import com.li.lipicturecloud.collaboration.model.CollaborationState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.collaboration.store", havingValue = "redis", matchIfMissing = true)
public class RedisCollaborationStateStore implements CollaborationStateStore {

    private static final DefaultRedisScript<List> CURRENT_SCRIPT = script(
            "redis/collaboration-current.lua");
    private static final DefaultRedisScript<List> APPLY_SCRIPT = script(
            "redis/collaboration-apply.lua");

    private final StringRedisTemplate redisTemplate;
    private final long ttlMillis;

    public RedisCollaborationStateStore(StringRedisTemplate redisTemplate,
                                        @Value("${app.collaboration.state-ttl:24h}") Duration stateTtl) {
        this.redisTemplate = redisTemplate;
        if (stateTtl == null || stateTtl.isZero() || stateTtl.isNegative()) {
            throw new IllegalArgumentException("协同状态过期时间必须大于 0");
        }
        this.ttlMillis = stateTtl.toMillis();
    }

    @Override
    public CollaborationState current(Long pictureId) {
        List<?> result = redisTemplate.execute(CURRENT_SCRIPT, List.of(stateKey(pictureId)),
                Long.toString(ttlMillis), pictureId.toString());
        return state(result, 0);
    }

    @Override
    public ApplyCollaborationResult apply(CollaborationCommand command) {
        List<?> result = redisTemplate.execute(APPLY_SCRIPT,
                List.of(stateKey(command.pictureId()), commandKey(command.pictureId(), command.commandId())),
                Long.toString(ttlMillis), command.pictureId().toString(), command.operation().name(),
                Long.toString(command.baseVersion()));
        if (result == null || result.size() != 5) {
            throw new IllegalStateException("Redis 返回了无效的协同结果");
        }
        String status = text(result.get(0));
        CollaborationState state = state(result, 1);
        if ("CONFLICT".equals(status)) {
            throw new CollaborationVersionConflictException(command.baseVersion(), state.version());
        }
        return new ApplyCollaborationResult(state, "APPLIED".equals(status));
    }

    @Override
    public int activeSessionCount() {
        return 0;
    }

    String stateKey(Long pictureId) {
        return "picture-cloud:collaboration:state:{" + pictureId + "}";
    }

    String commandKey(Long pictureId, String commandId) {
        return "picture-cloud:collaboration:command:{" + pictureId + "}:" + commandId;
    }

    private CollaborationState state(List<?> values, int offset) {
        if (values == null || values.size() < offset + 4) {
            throw new IllegalStateException("Redis 返回了不完整的协同状态");
        }
        return new CollaborationState(
                Long.parseLong(text(values.get(offset))),
                Integer.parseInt(text(values.get(offset + 1))),
                Double.parseDouble(text(values.get(offset + 2))),
                Long.parseLong(text(values.get(offset + 3))));
    }

    private String text(Object value) {
        return value instanceof byte[] bytes ? new String(bytes) : String.valueOf(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DefaultRedisScript<List> script(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(List.class);
        return script;
    }
}
