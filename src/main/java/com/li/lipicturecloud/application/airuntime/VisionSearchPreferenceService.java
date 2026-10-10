package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.application.airuntime.view.VisionSearchView;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.VisionSearchPreferenceRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 识图强制联网：开启后，喂图时视觉模型必须先联网搜索作品、角色或出处再描述。
 *
 * <p>只有百炼千问支持带图联网（原生多模态接口）；识图走其他厂商的连接时开关保留但不生效，
 * 视图会如实标出 {@code supported=false}。</p>
 */
@Service
public class VisionSearchPreferenceService {

    private final VisionSearchPreferenceRepository repository;
    private final com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository routingRepository;
    private final ModelConnectionRepository connectionRepository;
    private final CompanionFeatureProperties properties;

    public VisionSearchPreferenceService(VisionSearchPreferenceRepository repository,
                                         com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository routingRepository,
                                         ModelConnectionRepository connectionRepository,
                                         CompanionFeatureProperties properties) {
        this.repository = repository;
        this.routingRepository = routingRepository;
        this.connectionRepository = connectionRepository;
        this.properties = properties;
    }

    public boolean forcedSearch(long subjectId) {
        return subjectId > 0 && repository.forcedSearch(subjectId);
    }

    public VisionSearchView view(long subjectId) {
        checkIdentity(subjectId);
        Optional<ModelConnection> own = routingRepository
                .findBySubjectAndTask(subjectId, ModelTask.VISION_UNDERSTANDING)
                .map(TaskRoutingRule::connectionId)
                .flatMap(connectionRepository::findById)
                .filter(connection -> connection.subjectId() == subjectId);
        boolean supported = own.map(connection -> connection.provider() == ModelProvider.DASHSCOPE)
                .orElseGet(() -> "dashscope".equalsIgnoreCase(properties.getVisionProvider()));
        String model = own.map(ModelConnection::modelCode).orElse(properties.getVisionModel());
        return new VisionSearchView(repository.forcedSearch(subjectId), supported, model);
    }

    public VisionSearchView choose(long subjectId, boolean forcedSearch) {
        checkIdentity(subjectId);
        repository.save(subjectId, forcedSearch);
        return view(subjectId);
    }

    private static void checkIdentity(long subjectId) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
    }
}
