package com.li.lipicturecloud.controller;

import com.li.lipicturecloud.application.airuntime.ConnectivityResult;
import com.li.lipicturecloud.application.airuntime.CredentialService;
import com.li.lipicturecloud.application.airuntime.ModelCapabilityProfileService;
import com.li.lipicturecloud.application.airuntime.ModelConnectionService;
import com.li.lipicturecloud.application.airuntime.ModelConnectivityService;
import com.li.lipicturecloud.application.airuntime.ModelRoutingService;
import com.li.lipicturecloud.application.airuntime.ModelUsageService;
import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilities;
import com.li.lipicturecloud.domain.airuntime.ModelCapabilityProfile;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.airuntime.ModelUsageRecord;
import com.li.lipicturecloud.domain.airuntime.TaskRoutingRule;
import com.li.lipicturecloud.exception.GlobalExceptionHandler;
import com.li.lipicturecloud.model.entity.User;
import com.li.lipicturecloud.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModelGatewayControllerTest {

    private MockMvc mockMvc;
    private CredentialService credentialService;
    private ModelConnectionService connectionService;
    private ModelConnectivityService connectivityService;
    private ModelRoutingService routingService;
    private ModelUsageService usageService;
    private ModelCapabilityProfileService profileService;

    @BeforeEach
    void setUp() {
        credentialService = mock(CredentialService.class);
        connectionService = mock(ModelConnectionService.class);
        connectivityService = mock(ModelConnectivityService.class);
        routingService = mock(ModelRoutingService.class);
        usageService = mock(ModelUsageService.class);
        profileService = mock(ModelCapabilityProfileService.class);
        UserService userService = mock(UserService.class);
        User loginUser = new User();
        loginUser.setId(7L);
        when(userService.getLoginUserEntity(any(HttpServletRequest.class))).thenReturn(loginUser);
        ModelGatewayController controller = new ModelGatewayController(credentialService,
                connectionService, connectivityService, routingService, usageService,
                profileService, userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createCredentialReturnsOnlyTheSafeViewWithoutCipherOrPlaintext() throws Exception {
        CredentialVault stored = CredentialVault.restore(5L, 7L, ModelProvider.DEEPSEEK,
                "1234", "cipher-blob", CredentialVault.ALGORITHM_AES_GCM_V1, 0L);
        when(credentialService.store(7L, ModelProvider.DEEPSEEK, "sk-secret-1234"))
                .thenReturn(stored);

        mockMvc.perform(post("/model/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"DEEPSEEK\",\"apiKey\":\"sk-secret-1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.tail4").value("1234"))
                .andExpect(jsonPath("$.data.cipherText").doesNotExist())
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());

        ArgumentCaptor<String> apiKey = ArgumentCaptor.forClass(String.class);
        verify(credentialService).store(eq(7L), eq(ModelProvider.DEEPSEEK), apiKey.capture());
        assertThat(apiKey.getValue()).isEqualTo("sk-secret-1234");
    }

    @Test
    void createConnectionBuildsCommandFromSessionSubject() throws Exception {
        ModelConnection created = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, false, 0L);
        when(connectionService.create(eq(7L), eq(ModelProvider.DEEPSEEK), eq("主力"),
                eq(URI.create("https://api.deepseek.com/v1")), eq("deepseek-chat"), eq(5L)))
                .thenReturn(created);

        mockMvc.perform(post("/model/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"DEEPSEEK","displayName":"主力",
                                 "endpoint":"https://api.deepseek.com/v1",
                                 "modelCode":"deepseek-chat","credentialId":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void testConnectionReturnsProbeResultWithOnlySafeCodes() throws Exception {
        when(connectivityService.testConnection(9L, 7L))
                .thenReturn(ConnectivityResult.success());

        mockMvc.perform(post("/model/connections/9/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reachable").value(true));

        when(connectivityService.testConnection(9L, 7L))
                .thenReturn(ConnectivityResult.failed(ConnectivityResult.UPSTREAM_TIMEOUT));
        mockMvc.perform(post("/model/connections/9/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reachable").value(false))
                .andExpect(jsonPath("$.data.safeErrorCode").value("UPSTREAM_TIMEOUT"));
    }

    @Test
    void upsertRoutingParsesTaskAndAllowsExplicitPlatform() throws Exception {
        TaskRoutingRule rule = TaskRoutingRule.restore(1L, 7L, ModelTask.LANGUAGE_AGENT, null, 0L);
        when(routingService.upsert(7L, ModelTask.LANGUAGE_AGENT, null)).thenReturn(rule);

        mockMvc.perform(put("/model/routing/LANGUAGE_AGENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task").value("LANGUAGE_AGENT"));

        mockMvc.perform(put("/model/routing/NOT_A_TASK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void usageListReturnsOnlySafeFields() throws Exception {
        ModelUsageRecord record = ModelUsageRecord.success(7L, ModelTask.CONNECTIVITY_CHECK, 9L,
                ModelProvider.DEEPSEEK, "deepseek-chat", CostSource.BYOK,
                "fef53056-2d9f-467d-9b1d-1afe9a6638fe", Instant.parse("2026-08-14T08:00:00Z"))
                .withId(3L);
        when(usageService.listRecent(7L, 50)).thenReturn(List.of(record));

        mockMvc.perform(get("/model/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(3))
                .andExpect(jsonPath("$.data[0].success").value(true))
                .andExpect(jsonPath("$.data[0].correlationId")
                        .value("fef53056-2d9f-467d-9b1d-1afe9a6638fe"));
    }

    @Test
    void capabilityEndpointRequiresOwnershipAndReturnsSafeSnapshot() throws Exception {
        ModelConnection owned = ModelConnection.restore(9L, 7L, ModelProvider.DEEPSEEK,
                "主力", URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 5L, true, 1L);
        when(connectionService.findOwned(9L, 7L)).thenReturn(owned);
        ModelCapabilityProfile profile = ModelCapabilityProfile.snapshot(9L, 7L,
                ModelProvider.DEEPSEEK, "deepseek-chat",
                ModelCapabilities.of(true, false, true, true, false, false, false, 64_000,
                        ModelCapabilities.SYNC, ModelCapabilities.COST_CHEAP),
                Instant.parse("2026-08-14T08:00:00Z")).withId(3L);
        when(profileService.findLatest(9L)).thenReturn(Optional.of(profile));

        mockMvc.perform(get("/model/connections/9/capability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.text").value(true))
                .andExpect(jsonPath("$.data.vision").value(false))
                .andExpect(jsonPath("$.data.maxContextTokens").value(64_000))
                .andExpect(jsonPath("$.data.costHint").value("CHEAP"));

        when(profileService.findLatest(9L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/model/connections/9/capability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void connectionAndCredentialDeletesFlowThroughToServices() throws Exception {
        when(connectionService.delete(9L, 7L)).thenReturn(true);
        mockMvc.perform(delete("/model/connections/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        when(credentialService.delete(5L, 7L)).thenReturn(true);
        mockMvc.perform(delete("/model/credentials/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        when(routingService.delete(7L, ModelTask.LANGUAGE_AGENT)).thenReturn(true);
        mockMvc.perform(delete("/model/routing/LANGUAGE_AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }
}
