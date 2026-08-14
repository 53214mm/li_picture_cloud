package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.ModelUsageRecord;
import com.li.lipicturecloud.domain.airuntime.ModelUsageRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 连接探测全链路：真实加密器、真实仓储、stub 探测器；验证凭据永不以明文出现于记录中。
 *
 * <p>嵌套 @TestConfiguration 使本类拥有独立于其他伙伴测试的 Spring 上下文，
 * 因此必须使用独立的 H2 内存库名，避免与共享 li_picture_cloud 库的 Liquibase 初始化冲突。</p>
 */
@SpringBootTest(properties = "spring.datasource.url="
        + "jdbc:h2:mem:model_connectivity_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@Transactional
class ModelConnectivityIntegrationTest {

    static class StubTester implements ModelConnectivityTester {
        volatile ConnectivityResult next = ConnectivityResult.success();
        volatile String lastApiKey;

        @Override
        public ConnectivityResult test(URI endpointUri, String apiKey, ModelProvider provider) {
            this.lastApiKey = apiKey;
            return next;
        }
    }

    @TestConfiguration
    static class StubTesterConfig {
        @Bean
        @Primary
        StubTester stubModelConnectivityTester() {
            return new StubTester();
        }
    }

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private ModelConnectionService connectionService;

    @Autowired
    private ModelConnectivityService connectivityService;

    @Autowired
    private ModelUsageRecordRepository usageRepository;

    @Autowired
    private com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfileRepository profileRepository;

    @Autowired
    private StubTester tester;

    @Test
    void storesEncryptsProbesAndRecordsUsageWithoutPlaintext() {
        CredentialVault credential = credentialService.store(901L, ModelProvider.DEEPSEEK,
                "sk-secret-12345678");
        // 密文落库：保险库索引视图与密文都不包含明文。
        assertThat(credential.cipherText()).doesNotContain("sk-secret");

        ModelConnection connection = connectionService.create(901L, ModelProvider.DEEPSEEK,
                "主力", URI.create("https://api.deepseek.com/v1"), "deepseek-chat", credential.id());
        connectionService.enable(connection.id(), 901L);

        ConnectivityResult result = connectivityService.testConnection(connection.id(), 901L);

        assertThat(result.reachable()).isTrue();
        assertThat(tester.lastApiKey).isEqualTo("sk-secret-12345678");

        List<ModelUsageRecord> records = usageRepository.findRecent(901L, 10);
        assertThat(records).hasSize(1);
        ModelUsageRecord record = records.get(0);
        assertThat(record.task()).isEqualTo(ModelTask.CONNECTIVITY_CHECK);
        assertThat(record.success()).isTrue();
        assertThat(record.modelCode()).isEqualTo("deepseek-chat");
        // 使用记录只含安全字段。
        assertThat(record.correlationId()).hasSize(36);
        // 探测成功同时写能力画像快照。
        assertThat(profileRepository.findLatestByConnectionId(connection.id())).isPresent();
    }

    @Test
    void failedProbeRecordsFailureWithSafeCode() {
        CredentialVault credential = credentialService.store(902L, ModelProvider.DEEPSEEK,
                "sk-secret-abcdef");
        ModelConnection connection = connectionService.create(902L, ModelProvider.DEEPSEEK,
                "备用", URI.create("https://api.deepseek.com/v1"), "deepseek-chat", credential.id());
        connectionService.enable(connection.id(), 902L);

        tester.next = ConnectivityResult.failed(ConnectivityResult.CREDENTIAL_REJECTED);
        ConnectivityResult result = connectivityService.testConnection(connection.id(), 902L);

        assertThat(result.safeErrorCode()).isEqualTo(ConnectivityResult.CREDENTIAL_REJECTED);
        ModelUsageRecord record = usageRepository.findRecent(902L, 10).get(0);
        assertThat(record.success()).isFalse();
        assertThat(record.safeErrorCode()).isEqualTo(ConnectivityResult.CREDENTIAL_REJECTED);
    }
}
