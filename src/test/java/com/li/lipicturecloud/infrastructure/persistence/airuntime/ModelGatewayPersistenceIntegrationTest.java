package com.li.lipicturecloud.infrastructure.persistence.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.CredentialVaultRepository;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilities;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfile;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfileRepository;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelConnectionRepository;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.ModelUsageRecord;
import com.li.lipicturecloud.domain.airuntime.ModelUsageRecordRepository;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ModelGatewayPersistenceIntegrationTest {

    private static final URI HTTPS = URI.create("https://api.deepseek.example/v1");
    private static final Instant NOW = Instant.parse("2026-08-14T08:00:00Z");
    private static final String CORRELATION = "fef53056-2d9f-467d-9b1d-1afe9a6638fe";

    @Autowired
    private ModelConnectionRepository connectionRepository;

    @Autowired
    private CredentialVaultRepository vaultRepository;

    @Autowired
    private ModelUsageRecordRepository usageRepository;

    @Autowired
    private TaskRoutingRuleRepository routingRepository;

    @Autowired
    private ModelCapabilityProfileRepository profileRepository;

    @Test
    void connectionLifecycleHonorsRevisionCasAndDeletesOnlyOnMatch() {
        ModelConnection created = connectionRepository.insert(ModelConnection.create(
                801L, ModelProvider.DEEPSEEK, "DeepSeek 主力", HTTPS, "deepseek-chat", null));
        assertThat(created.id()).isPositive();
        assertThat(connectionRepository.findById(created.id())).contains(created);

        ModelConnection enabled = created.enable();
        assertThat(connectionRepository.save(enabled, 0L)).isTrue();
        assertThat(connectionRepository.findById(created.id()).orElseThrow().enabled()).isTrue();

        // CAS：数据库 revision 已推进后，用同一旧版本再次写入失败。
        assertThat(connectionRepository.save(enabled, 0L)).isFalse();

        // 唯一键冲突：并发创建输的一方读回赢家行。
        ModelConnection duplicate = connectionRepository.insert(ModelConnection.create(
                801L, ModelProvider.DEEPSEEK, "DeepSeek 主力", HTTPS, "deepseek-chat", null));
        assertThat(duplicate.id()).isEqualTo(created.id());

        assertThat(connectionRepository.delete(created.id(), 99L)).isFalse();
        assertThat(connectionRepository.delete(created.id(), 1L)).isTrue();
        assertThat(connectionRepository.findById(created.id())).isEmpty();
    }

    @Test
    void credentialLifecycleOverwritesCipherTextOnlyWithMatchingRevision() {
        CredentialVault created = vaultRepository.insert(CredentialVault.create(
                801L, ModelProvider.DEEPSEEK, "aB12", "cipher-v1"));
        assertThat(created.id()).isPositive();
        assertThat(vaultRepository.findByOwnerId(801L)).hasSize(1);

        CredentialVault rotated = new CredentialVault(created.id(), 801L, ModelProvider.DEEPSEEK,
                "cD34", "cipher-v2", CredentialVault.ALGORITHM_AES_GCM_V1, 1L);
        assertThat(vaultRepository.save(rotated, 0L)).isTrue();
        CredentialVault stored = vaultRepository.findById(created.id()).orElseThrow();
        assertThat(stored.tail4()).isEqualTo("cD34");
        assertThat(stored.cipherText()).isEqualTo("cipher-v2");

        assertThat(vaultRepository.save(rotated, 0L)).isFalse();

        assertThat(vaultRepository.delete(created.id(), 1L)).isTrue();
        assertThat(vaultRepository.findByOwnerId(801L)).isEmpty();
    }

    @Test
    void usageRecordsAppendAndListInReverseChronologicalOrder() {
        ModelUsageRecord first = usageRepository.append(ModelUsageRecord.success(
                801L, ModelTask.LANGUAGE_AGENT, 9L, ModelProvider.DEEPSEEK, "deepseek-chat",
                CostSource.BYOK, CORRELATION, NOW));
        ModelUsageRecord second = usageRepository.append(ModelUsageRecord.failure(
                801L, ModelTask.VISION_UNDERSTANDING, null, ModelProvider.DASHSCOPE,
                "qwen-vl-max", CostSource.PLATFORM, "UPSTREAM_TIMEOUT",
                "fef53056-2d9f-467d-9b1d-1afe9a6638ff", NOW.plusSeconds(1)));

        assertThat(first.id()).isPositive();
        assertThat(second.id()).isPositive();

        List<ModelUsageRecord> recent = usageRepository.findRecent(801L, 10);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).id()).isEqualTo(second.id());
        assertThat(recent.get(0).success()).isFalse();
        assertThat(recent.get(0).safeErrorCode()).isEqualTo("UPSTREAM_TIMEOUT");
        assertThat(recent.get(1).costSource()).isEqualTo(CostSource.BYOK);
    }

    @Test
    void routingRuleUpsertsResolveToWinnerRowAndRouteToAdvancesWithCas() {
        TaskRoutingRule created = routingRepository.insert(TaskRoutingRule.create(
                801L, ModelTask.LANGUAGE_AGENT, 9L));
        assertThat(created.id()).isPositive();
        assertThat(routingRepository.findBySubjectAndTask(801L, ModelTask.LANGUAGE_AGENT))
                .contains(created);

        // (subjectId, task) 唯一键：并发创建输的一方读回赢家行。
        TaskRoutingRule duplicate = routingRepository.insert(TaskRoutingRule.create(
                801L, ModelTask.LANGUAGE_AGENT, null));
        assertThat(duplicate.id()).isEqualTo(created.id());
        assertThat(duplicate.connectionId()).isEqualTo(9L);

        TaskRoutingRule routed = created.routeTo(12L);
        assertThat(routingRepository.save(routed, 0L)).isTrue();
        Optional<TaskRoutingRule> stored = routingRepository.findBySubjectAndTask(
                801L, ModelTask.LANGUAGE_AGENT);
        assertThat(stored.orElseThrow().connectionId()).isEqualTo(12L);

        assertThat(routingRepository.save(routed, 0L)).isFalse();

        assertThat(routingRepository.delete(created.id(), 1L)).isTrue();
        assertThat(routingRepository.findByOwnerId(801L)).isEmpty();
    }

    @Test
    void capabilityProfilesAppendAndKeepTheLatestSnapshot() {
        ModelCapabilityProfile first = profileRepository.append(ModelCapabilityProfile.snapshot(
                9L, 801L, ModelProvider.DEEPSEEK, "deepseek-chat", ModelCapabilities.unknown(), NOW));
        ModelCapabilityProfile second = profileRepository.append(ModelCapabilityProfile.snapshot(
                9L, 801L, ModelProvider.DEEPSEEK, "deepseek-chat",
                ModelCapabilities.of(true, false, true, true, false, false, false, 64_000,
                        ModelCapabilities.SYNC, ModelCapabilities.COST_CHEAP),
                NOW.plusSeconds(1)));

        assertThat(first.id()).isPositive();
        assertThat(second.id()).isPositive();
        assertThat(second.id()).isNotEqualTo(first.id());

        ModelCapabilityProfile latest = profileRepository.findLatestByConnectionId(9L).orElseThrow();
        assertThat(latest.id()).isEqualTo(second.id());
        assertThat(latest.text()).isTrue();
        assertThat(latest.maxContextTokens()).isEqualTo(64_000);
    }
}
