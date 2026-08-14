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
        Objects.requireNonNull(connection, "connection");
        if (connection.id() == null || subjectId <= 0) {
            throw new IllegalArgumentException("invalid snapshot identity");
        }
        ModelCapabilities capabilities = registry.capabilitiesFor(connection.provider(),
                connection.modelCode());
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
