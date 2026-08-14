package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * BYOK 规则解析的共享尾部：语言/视觉/图像创作三个路由器复用同一套
 * 归属、启停、凭据与端点白名单校验，保证"坏路由大声失败"的语义一致。
 */
@Component
public class ByokConnectionResolver {

    private final ModelConnectionRepository connectionRepository;
    private final CredentialVaultRepository vaultRepository;
    private final CredentialCipher cipher;
    private final EndpointAllowlist allowlist;

    public ByokConnectionResolver(ModelConnectionRepository connectionRepository,
                                  CredentialVaultRepository vaultRepository,
                                  CredentialCipher cipher,
                                  EndpointAllowlist allowlist) {
        this.connectionRepository = connectionRepository;
        this.vaultRepository = vaultRepository;
        this.cipher = cipher;
        this.allowlist = allowlist;
    }

    /**
     * 校验 BYOK 连接并解密凭据。所有失败都必须让用户修复或清除路由规则，
     * 绝不静默回退平台钱包。
     */
    public ModelRouteDecision resolveByok(long subjectId, long connectionId, String taskLabel) {
        ModelConnection connection = connectionRepository.findById(connectionId)
                .filter(owned -> owned.subjectId() == subjectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                        taskLabel + "任务路由的连接不存在，请修复或清除路由规则"));
        if (!connection.enabled()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    taskLabel + "任务路由的连接已停用，请启用或清除路由规则");
        }
        if (connection.credentialId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    taskLabel + "任务路由的连接未绑定凭据，请绑定或清除路由规则");
        }
        // 发送密钥前的最后一道防线：即使创建时通过白名单，配置收紧后也必须再验一次。
        if (!allowlist.isAllowed(connection.endpointUri())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    taskLabel + "任务路由的连接端点不在允许清单内，请修复或清除路由规则");
        }
        CredentialVault credential = vaultRepository.findById(connection.credentialId())
                .filter(owned -> owned.subjectId() == subjectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                        taskLabel + "任务路由的凭据不存在，请修复或清除路由规则"));

        return ModelRouteDecision.byok(connection, cipher.decrypt(credential));
    }
}
