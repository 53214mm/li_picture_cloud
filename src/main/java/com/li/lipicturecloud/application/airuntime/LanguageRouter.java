package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 语言任务路由器：用户显式绑定连接则走 BYOK；未绑定或未配置路由则走平台。
 *
 * <p>关键约束：一旦存在指向用户连接的 BYOK 规则，连接不可用时必须大声失败，
 * 绝不静默回退到平台钱包扣费。</p>
 */
@Service
public class LanguageRouter {

    private final TaskRoutingRuleRepository routingRepository;
    private final ModelConnectionRepository connectionRepository;
    private final CredentialVaultRepository vaultRepository;
    private final CredentialCipher cipher;
    private final EndpointAllowlist allowlist;

    public LanguageRouter(TaskRoutingRuleRepository routingRepository,
                          ModelConnectionRepository connectionRepository,
                          CredentialVaultRepository vaultRepository,
                          CredentialCipher cipher,
                          EndpointAllowlist allowlist) {
        this.routingRepository = routingRepository;
        this.connectionRepository = connectionRepository;
        this.vaultRepository = vaultRepository;
        this.cipher = cipher;
        this.allowlist = allowlist;
    }

    public LanguageRouteDecision decide(long subjectId) {
        if (subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be positive");
        }
        Optional<TaskRoutingRule> rule = routingRepository.findBySubjectAndTask(subjectId,
                ModelTask.LANGUAGE_AGENT);
        if (rule.isEmpty() || rule.get().connectionId() == null) {
            // 未配置规则或用户显式选择平台：平台钱包路径。
            return LanguageRouteDecision.platform();
        }

        long connectionId = rule.get().connectionId();
        ModelConnection connection = connectionRepository.findById(connectionId)
                .filter(owned -> owned.subjectId() == subjectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                        "语言任务路由的连接不存在，请修复或清除路由规则"));
        if (!connection.enabled()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "语言任务路由的连接已停用，请启用或清除路由规则");
        }
        // 发送密钥前的最后一道防线：即使创建时通过白名单，配置收紧后也必须再验一次。
        if (!allowlist.isAllowed(connection.endpointUri())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "语言任务路由的连接端点不在允许清单内，请修复或清除路由规则");
        }
        if (connection.credentialId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "语言任务路由的连接未绑定凭据，请绑定或清除路由规则");
        }
        CredentialVault credential = vaultRepository.findById(connection.credentialId())
                .filter(owned -> owned.subjectId() == subjectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR,
                        "语言任务路由的凭据不存在，请修复或清除路由规则"));

        return LanguageRouteDecision.byok(connection, cipher.decrypt(credential));
    }
}
