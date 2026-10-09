package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelCapabilities;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfile;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfileRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;

/**
 * 能力画像快照服务：连接测试成功后，按平台认知表写一份追加式快照。
 */
@Service
public class ModelCapabilityProfileService {

    private final ModelCapabilityProfileRepository profileRepository;
    private final ModelCapabilityRegistry registry;
    private final Clock clock;

    public ModelCapabilityProfileService(ModelCapabilityProfileRepository profileRepository,
                                         ModelCapabilityRegistry registry,
                                         Clock clock) {
        this.profileRepository = profileRepository;
        this.registry = registry;
        this.clock = clock;
    }

    public ModelCapabilityProfile snapshot(ModelConnection connection, long subjectId) {
        return append(connection, subjectId, false);
    }

    /**
     * 平台已用该连接的模型完成一次最小对话：文本能力由这次验证确认，其余能力仍取认知表。
     * 认知表不认识的新模型因此也能用于语言任务，而不必按名称猜测。
     */
    public ModelCapabilityProfile snapshotWithVerifiedText(ModelConnection connection, long subjectId) {
        return append(connection, subjectId, true);
    }

    private ModelCapabilityProfile append(ModelConnection connection, long subjectId,
                                          boolean textVerified) {
        Objects.requireNonNull(connection, "connection");
        if (connection.id() == null || subjectId <= 0) {
            throw new IllegalArgumentException("invalid snapshot identity");
        }
        if (connection.subjectId() != subjectId) {
            throw new IllegalArgumentException("snapshot subject must own the connection");
        }
        ModelCapabilities known = registry.capabilitiesFor(connection.provider(),
                connection.modelCode());
        ModelCapabilities capabilities = textVerified ? known.withVerifiedText() : known;
        return profileRepository.append(ModelCapabilityProfile.snapshot(connection.id(),
                subjectId, connection.provider(), connection.modelCode(), capabilities,
                clock.instant()));
    }

    public ModelCapabilityProfile latest(long connectionId) {
        return findLatest(connectionId)
                .orElseThrow(() -> new com.li.lipicturecloud.exception.BusinessException(
                        com.li.lipicturecloud.exception.ErrorCode.NOT_FOUND_ERROR, "连接尚未生成能力画像"));
    }

    public java.util.Optional<ModelCapabilityProfile> findLatest(long connectionId) {
        return profileRepository.findLatestByConnectionId(connectionId);
    }
}
