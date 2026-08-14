package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 连接探测应用服务：加载连接与凭据、解密、发起最小化探测并把结果写成使用记录。
 * 探测失败只暴露安全错误码，日志不出现端点路径、凭据或响应正文。
 */
@Service
public class ModelConnectivityService {

    private static final Logger log = LoggerFactory.getLogger(ModelConnectivityService.class);

    private final ModelConnectionRepository connectionRepository;
    private final CredentialVaultRepository vaultRepository;
    private final CredentialCipher cipher;
    private final ModelConnectivityTester tester;
    private final ModelUsageService usageService;
    private final ModelCapabilityProfileService profileService;
    private final EndpointAllowlist allowlist;

    public ModelConnectivityService(ModelConnectionRepository connectionRepository,
                                    CredentialVaultRepository vaultRepository,
                                    CredentialCipher cipher,
                                    ModelConnectivityTester tester,
                                    ModelUsageService usageService,
                                    ModelCapabilityProfileService profileService,
                                    EndpointAllowlist allowlist) {
        this.connectionRepository = connectionRepository;
        this.vaultRepository = vaultRepository;
        this.cipher = cipher;
        this.tester = tester;
        this.usageService = usageService;
        this.profileService = profileService;
        this.allowlist = allowlist;
    }

    public ConnectivityResult testConnection(long connectionId, long subjectId) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
        ModelConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "模型连接不存在"));
        if (connection.subjectId() != subjectId) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权操作该模型连接");
        }
        if (connection.credentialId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "连接未绑定凭据，无法探测");
        }
        if (!connection.enabled()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "连接已停用，无法探测");
        }
        // 发送密钥前的最后一道防线：创建时通过的白名单在配置收紧后必须重新校验。
        if (!allowlist.isAllowed(connection.endpointUri())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "连接端点不在允许清单内，无法探测");
        }
        CredentialVault credential = vaultRepository.findById(connection.credentialId())
                .filter(owned -> owned.subjectId() == subjectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "连接绑定的凭据不存在"));

        String apiKey = cipher.decrypt(credential);
        ConnectivityResult result = tester.test(connection.endpointUri(), apiKey,
                connection.provider());
        recordOutcome(connection, subjectId, result);
        if (result.reachable()) {
            snapshotCapabilities(connection, subjectId);
        }
        return result;
    }

    /** 探测成功时写能力画像快照；快照失败不得掩盖探测结果（只记安全字段）。 */
    private void snapshotCapabilities(ModelConnection connection, long subjectId) {
        try {
            profileService.snapshot(connection, subjectId);
        } catch (RuntimeException snapshotFailure) {
            log.warn("model_capability_snapshot_failed subjectId={} connectionId={}",
                    subjectId, connection.id());
        }
    }

    private void recordOutcome(ModelConnection connection, long subjectId, ConnectivityResult result) {
        try {
            if (result.reachable()) {
                usageService.recordSuccess(subjectId, ModelTask.CONNECTIVITY_CHECK,
                        connection.id(), connection.provider(), connection.modelCode(),
                        CostSource.BYOK);
            } else {
                usageService.recordFailure(subjectId, ModelTask.CONNECTIVITY_CHECK,
                        connection.id(), connection.provider(), connection.modelCode(),
                        CostSource.BYOK, result.safeErrorCode());
            }
        } catch (RuntimeException recordFailure) {
            // 使用记录失败不得掩盖探测结果；只记录安全字段。
            log.warn("model_connectivity_usage_record_failed subjectId={} connectionId={} code={}",
                    subjectId, connection.id(), result.safeErrorCode());
        }
    }
}
