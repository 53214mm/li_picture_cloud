package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.application.airuntime.view.ModelDiscoveryView;
import com.li.lipicturecloud.application.airuntime.view.QuickConnectionView;
import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 模型快速连接：用户只选供应商、填 API Key、选模型，其余步骤由平台完成。
 *
 * <p>流程：预设端点 → 读取模型目录 → 用所选模型做一次最小对话验证 → 在一个事务里保存凭据、
 * 创建并启用连接、写入已验证文本能力的画像、把语言任务路由到该连接。验证在事务外完成，
 * 失败时不留下任何凭据或连接。</p>
 */
@Service
public class ModelQuickSetupService {

    private static final Logger log = LoggerFactory.getLogger(ModelQuickSetupService.class);

    private static final Map<String, String> FAILURE_MESSAGES = Map.of(
            ConnectivityResult.CREDENTIAL_REJECTED, "API Key 无效或没有权限，请检查后重试",
            ConnectivityResult.QUOTA_EXHAUSTED, "账户余额不足或请求过于频繁，请到厂商控制台确认",
            ConnectivityResult.UPSTREAM_TIMEOUT, "连接厂商超时，请稍后重试",
            ConnectivityResult.MODEL_UNAVAILABLE, "这个模型不能用于对话，请换一个模型",
            ConnectivityResult.UPSTREAM_ERROR, "厂商服务暂时不可用，请稍后重试");

    private final ModelCatalogClient catalogClient;
    private final EndpointAllowlist allowlist;
    private final CredentialService credentialService;
    private final ModelConnectionService connectionService;
    private final ModelConnectionRepository connectionRepository;
    private final ModelRoutingService routingService;
    private final TaskRoutingRuleRepository routingRepository;
    private final ModelCapabilityProfileService profileService;
    private final ModelUsageService usageService;
    private final TransactionTemplate transaction;

    public ModelQuickSetupService(ModelCatalogClient catalogClient,
                                  EndpointAllowlist allowlist,
                                  CredentialService credentialService,
                                  ModelConnectionService connectionService,
                                  ModelConnectionRepository connectionRepository,
                                  ModelRoutingService routingService,
                                  TaskRoutingRuleRepository routingRepository,
                                  ModelCapabilityProfileService profileService,
                                  ModelUsageService usageService,
                                  PlatformTransactionManager transactionManager) {
        this.catalogClient = catalogClient;
        this.allowlist = allowlist;
        this.credentialService = credentialService;
        this.connectionService = connectionService;
        this.connectionRepository = connectionRepository;
        this.routingService = routingService;
        this.routingRepository = routingRepository;
        this.profileService = profileService;
        this.usageService = usageService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** 第一步：用 API Key 读取可选模型。目录不可读时退回平台推荐模型，凭据留到最小对话时验证。 */
    public ModelDiscoveryView discover(long subjectId, ModelProvider provider, String apiKey) {
        checkIdentity(subjectId);
        ModelProviderPresets.Preset preset = allowedPreset(provider);
        ModelCatalogClient.Catalog catalog = catalogClient.listModels(preset.endpoint(),
                requireKey(apiKey), provider);
        failIfUnreachable(catalog.result());
        List<String> models = catalog.listed() ? catalog.modelCodes() : preset.suggestedModels();
        String recommended = preset.suggestedModels().stream()
                .filter(models::contains)
                .findFirst()
                .orElse(models.get(0));
        return new ModelDiscoveryView(provider, preset.label(), models, recommended, catalog.listed());
    }

    /** 第二步：验证所选模型能对话后，一次性建好连接并设为语言任务的模型。 */
    public QuickConnectionView connect(long subjectId, ModelProvider provider, String apiKey,
                                       String modelCode) {
        checkIdentity(subjectId);
        ModelProviderPresets.Preset preset = allowedPreset(provider);
        String key = requireKey(apiKey);
        if (modelCode == null || !ModelConnection.MODEL_CODE.matcher(modelCode.strip()).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择一个模型");
        }
        String model = modelCode.strip();
        failIfUnreachable(catalogClient.probeChat(preset.endpoint(), key, provider, model));

        ModelConnection connection = transaction.execute(status -> {
            CredentialVault credential = credentialService.store(subjectId, provider, key);
            ModelConnection created = connectionService.create(subjectId, provider, preset.label(),
                    preset.endpoint(), model, credential.id());
            ModelConnection enabled = connectionService.enable(created.id(), subjectId);
            profileService.snapshotWithVerifiedText(enabled, subjectId);
            routingService.upsert(subjectId, ModelTask.LANGUAGE_AGENT, enabled.id());
            return enabled;
        });
        recordVerification(Objects.requireNonNull(connection, "connection"), subjectId);
        return current(subjectId);
    }

    /** 当前语言任务使用的模型摘要。 */
    public QuickConnectionView current(long subjectId) {
        checkIdentity(subjectId);
        Optional<Long> connectionId = routingRepository
                .findBySubjectAndTask(subjectId, ModelTask.LANGUAGE_AGENT)
                .map(TaskRoutingRule::connectionId);
        if (connectionId.isEmpty()) {
            return QuickConnectionView.platform();
        }
        return connectionRepository.findById(connectionId.get())
                .filter(connection -> connection.subjectId() == subjectId)
                .map(connection -> new QuickConnectionView(
                        connection.enabled() && connection.credentialId() != null
                                ? QuickConnectionView.Status.CONNECTED : QuickConnectionView.Status.BROKEN,
                        connection.provider(), ModelProviderPresets.of(connection.provider()).label(),
                        connection.modelCode(), connection.id()))
                .orElseGet(() -> new QuickConnectionView(QuickConnectionView.Status.BROKEN,
                        null, null, null, connectionId.get()));
    }

    /** 不再使用自己的模型：清除语言任务路由，连接与凭据保留在高级设置里。 */
    public QuickConnectionView usePlatform(long subjectId) {
        checkIdentity(subjectId);
        routingService.delete(subjectId, ModelTask.LANGUAGE_AGENT);
        return QuickConnectionView.platform();
    }

    private ModelProviderPresets.Preset allowedPreset(ModelProvider provider) {
        ModelProviderPresets.Preset preset = ModelProviderPresets.of(
                Objects.requireNonNull(provider, "provider"));
        if (!allowlist.isAllowed(preset.endpoint())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "平台暂未开放该厂商");
        }
        return preset;
    }

    private void recordVerification(ModelConnection connection, long subjectId) {
        try {
            usageService.recordSuccess(subjectId, ModelTask.CONNECTIVITY_CHECK, connection.id(),
                    connection.provider(), connection.modelCode(), CostSource.BYOK);
        } catch (RuntimeException recordFailure) {
            // 使用记录失败不影响已经建好的连接；只记安全字段。
            log.warn("model_quick_setup_usage_record_failed subjectId={} connectionId={}",
                    subjectId, connection.id());
        }
    }

    private static void failIfUnreachable(ConnectivityResult result) {
        if (!result.reachable()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    FAILURE_MESSAGES.getOrDefault(result.safeErrorCode(), "暂时无法连接这个厂商，请稍后重试"));
        }
    }

    private static String requireKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请填写 API Key");
        }
        return apiKey.strip();
    }

    private static void checkIdentity(long subjectId) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
    }
}
