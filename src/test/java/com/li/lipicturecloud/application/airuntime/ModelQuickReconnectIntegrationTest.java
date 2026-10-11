package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real encrypted persistence and routing, with a case-insensitive name index and no vendor IO. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:quick_reconnect;MODE=MySQL;"
        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;IGNORECASE=TRUE")
@ActiveProfiles("test")
class ModelQuickReconnectIntegrationTest {
    private static final URI ENDPOINT = URI.create("https://api.deepseek.com/v1");
    @Autowired ModelQuickSetupService quick;
    @Autowired ModelConnectionService connections;
    @Autowired ModelConnectionRepository connectionRepository;
    @Autowired CredentialService credentials;
    @Autowired CredentialVaultRepository vault;
    @Autowired ModelCapabilityProfileRepository profiles;
    @Autowired ModelRoutingService routing;
    @Autowired TaskRoutingRuleRepository rules;
    @Autowired CatalogStub catalog;

    static class CatalogStub implements ModelCatalogClient {
        ConnectivityResult result = ConnectivityResult.success();
        String lastKey;
        String lastModel;
        Runnable duringProbe = () -> {};
        int calls;
        public Catalog listModels(URI endpoint, String key, ModelProvider provider) {
            return Catalog.of(List.of("deepseek-chat", "deepseek-reasoner"));
        }
        public ConnectivityResult probeChat(URI endpoint, String key, ModelProvider provider, String model) {
            calls++;
            lastKey = key;
            lastModel = model;
            duringProbe.run();
            return result;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary CatalogStub reconnectCatalog() { return new CatalogStub(); }
    }

    @BeforeEach
    void resetProbe() {
        catalog.result = ConnectivityResult.success();
        catalog.lastKey = null;
        catalog.lastModel = null;
        catalog.duringProbe = () -> {};
        catalog.calls = 0;
    }

    @ParameterizedTest
    @CsvSource({"9501,deepseek,true", "9502,DeepSeek,true", "9503,DeepSeek,false"})
    void quickConnectUsesTheVerifiedConfigurationWithoutChangingExistingConnections(
            long user, String name, boolean enabled) {
        ModelConnection old = saved(user, name, enabled);
        routing.upsert(user, ModelTask.LANGUAGE_AGENT, old.id());
        var result = quick.connect(user, ModelProvider.DEEPSEEK, "synthetic-new-key", "deepseek-reasoner");
        ModelConnection selected = connectionRepository.findById(result.connectionId()).orElseThrow();
        assertThat(selected.id()).isNotEqualTo(old.id());
        assertThat(selected.modelCode()).isEqualTo("deepseek-reasoner");
        assertThat(credentials.reveal(selected.credentialId(), user)).isEqualTo("synthetic-new-key");
        assertThat(connectionRepository.findById(old.id())).contains(old);
        assertThat(profiles.findLatestByConnectionId(selected.id()).orElseThrow().text()).isTrue();
        assertThat(profiles.findLatestByConnectionId(old.id())).isEmpty();
        assertThat(rules.findBySubjectAndTask(user, ModelTask.LANGUAGE_AGENT).orElseThrow().connectionId())
                .isEqualTo(selected.id());
    }

    @Test
    void repeatedQuickConnectActuallyChangesModelAndKey() {
        long user = 9504L;
        var first = quick.connect(user, ModelProvider.DEEPSEEK, "synthetic-first-key", "deepseek-chat");
        var second = quick.connect(user, ModelProvider.DEEPSEEK, "synthetic-second-key", "deepseek-reasoner");
        assertThat(second.connectionId()).isNotEqualTo(first.connectionId());
        assertThat(second.modelCode()).isEqualTo("deepseek-reasoner");
        var selected = connectionRepository.findById(second.connectionId()).orElseThrow();
        assertThat(credentials.reveal(selected.credentialId(), user)).isEqualTo("synthetic-second-key");
    }

    @Test
    void failedVerificationKeepsThePreviousRouteAndCreatesNothing() {
        long user = 9505L;
        ModelConnection old = saved(user, "old", true);
        routing.upsert(user, ModelTask.LANGUAGE_AGENT, old.id());
        catalog.result = ConnectivityResult.failed(ConnectivityResult.CREDENTIAL_REJECTED);
        assertThatThrownBy(() -> quick.connect(user, ModelProvider.DEEPSEEK, "synthetic-bad-key", "new-model"))
                .hasMessageContaining("API Key");
        assertThat(connectionRepository.findByOwnerId(user)).containsExactly(old);
        assertThat(vault.findByOwnerId(user)).hasSize(1);
        assertThat(quick.current(user).connectionId()).isEqualTo(old.id());
    }

    private ModelConnection saved(long user, String name, boolean enabled) {
        var key = credentials.store(user, ModelProvider.DEEPSEEK, "synthetic-saved-key");
        var created = connections.create(user, ModelProvider.DEEPSEEK, name, ENDPOINT,
                "deepseek-flash", key.id());
        return enabled ? connections.enable(created.id(), user) : created;
    }

    @Test
    void savedConnectionCanBeVerifiedAndSelectedWithoutAnotherKeyOrConnection() {
        long user = 9506L;
        var existing = saved(user, "deepseek", true);
        assertThat(quick.current(user).connectionId()).isNull();
        var selected = quick.useExisting(user, existing.id());
        assertThat(selected.connectionId()).isEqualTo(existing.id());
        assertThat(selected.modelCode()).isEqualTo("deepseek-flash");
        assertThat(catalog.lastKey).isEqualTo("synthetic-saved-key");
        assertThat(catalog.lastModel).isEqualTo("deepseek-flash");
        assertThat(connectionRepository.findByOwnerId(user)).containsExactly(existing);
        assertThat(vault.findByOwnerId(user)).hasSize(1);
        assertThat(profiles.findLatestByConnectionId(existing.id()).orElseThrow().text()).isTrue();
    }

    @Test
    void savedConnectionRejectsOtherOwnersAndDisabledConnectionsBeforeVendorIO() {
        var existing = saved(9507L, "deepseek", true);
        assertThatThrownBy(() -> quick.useExisting(9508L, existing.id())).hasMessageContaining("不存在");
        var disabled = saved(9508L, "disabled", false);
        assertThatThrownBy(() -> quick.useExisting(9508L, disabled.id())).hasMessageContaining("停用");
        assertThat(catalog.calls).isZero();
    }

    @Test
    void failedSavedVerificationDoesNotChangeRouteOrMarkModelAsVerified() {
        long user = 9509L;
        var previous = saved(user, "previous", true);
        var candidate = saved(user, "candidate", true);
        routing.upsert(user, ModelTask.LANGUAGE_AGENT, previous.id());
        catalog.result = ConnectivityResult.failed(ConnectivityResult.CREDENTIAL_REJECTED);
        assertThatThrownBy(() -> quick.useExisting(user, candidate.id())).hasMessageContaining("API Key");
        assertThat(quick.current(user).connectionId()).isEqualTo(previous.id());
        assertThat(profiles.findLatestByConnectionId(candidate.id())).isEmpty();
    }

    @Test
    void changingSavedCredentialDuringProbeRequiresAnotherVerification() {
        long user = 9510L;
        var existing = saved(user, "deepseek", true);
        catalog.duringProbe = () -> credentials.rotate(existing.credentialId(), user, "synthetic-rotated-key");
        assertThatThrownBy(() -> quick.useExisting(user, existing.id())).hasMessageContaining("验证期间发生变化");
        assertThat(quick.current(user).connectionId()).isNull();
        assertThat(profiles.findLatestByConnectionId(existing.id())).isEmpty();
    }
}
