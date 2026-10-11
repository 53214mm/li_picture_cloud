package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.airuntime.*;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.companion.*;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real H2 quota/ledger/message transactions; all external generation is stubbed. */
@SpringBootTest(properties = {"app.companion.chat-daily-limit=1",
        "spring.datasource.url=jdbc:h2:mem:chat_quota;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ActiveProfiles("test")
class CompanionChatQuotaIntegrationTest {
    @Autowired private CompanionChatService chat;
    @Autowired private ChatQuotaGuard quota;
    @Autowired private PlatformTrialLedgerService ledger;
    @Autowired private CompanionRepository companions;
    @Autowired private CompanionChatMessageRepository messages;
    @Autowired private Clock clock;
    @MockitoBean private CompanionChatModeService modes;
    @MockitoBean private LanguageRouter router;
    @MockitoBean private LanguageModelInvoker invoker;
    @MockitoBean private CompanionEpisodeService episodes;
    @MockitoBean private ChatModel platform;

    @Test
    void exhaustedPlatformQuotaRollsBackTrialReservationAndRejectsBeforeWriting() {
        long user = 9761L;
        Companion companion = prepare(user, CompanionChatMode.MODEL);
        quota.reserve(user, today(), 1);
        var before = ledger.getOrCreate(user);
        when(router.decide(user)).thenReturn(ModelRouteDecision.platform());

        assertThatThrownBy(() -> chat.chat(AuthorizationSubject.user(user), "平台仍受限制"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("次数已用完");
        assertThat(ledger.getOrCreate(user)).isEqualTo(before);
        assertThat(messages.findRecent(companion.id(), 10)).isEmpty();
        verifyNoInteractions(platform, invoker);
    }

    @Test
    void exhaustedExistingBucketAllowsByokAndDoesNotChangeTrialOrQuota() {
        long user = 9762L;
        Companion companion = prepare(user, CompanionChatMode.MODEL);
        quota.reserve(user, today(), 1);
        var before = ledger.getOrCreate(user);
        ModelConnection connection = ModelConnection.restore(9762L, user, ModelProvider.DEEPSEEK,
                "Test model", URI.create("https://api.deepseek.com/v1"), "deepseek-chat", 9762L, true, 0);
        when(router.decide(user)).thenReturn(ModelRouteDecision.byok(connection, "synthetic-test-only"));
        when(invoker.stream(any(), anyList())).thenReturn(Flux.just("自费成功"));

        assertThat(chat.chat(AuthorizationSubject.user(user), "继续聊").blockLast()).isEqualTo("自费成功");
        assertThat(ledger.getOrCreate(user)).isEqualTo(before);
        assertThat(messages.findRecent(companion.id(), 10)).hasSize(2);
        assertThatThrownBy(() -> quota.reserve(user, today(), 1)).hasMessageContaining("次数已用完");
        verifyNoInteractions(platform);
    }

    @Test
    void exhaustedExistingBucketAllowsFreeDemoWithoutModel() {
        long user = 9763L;
        Companion companion = prepare(user, CompanionChatMode.DEMO);
        quota.reserve(user, today(), 1);
        assertThat(chat.chat(AuthorizationSubject.user(user), "你好").blockLast()).contains("演示回复");
        assertThat(messages.findRecent(companion.id(), 10)).hasSize(2);
        verifyNoInteractions(platform, router, invoker);
    }

    @Test
    void platformSuccessSettlesAndPlatformFailureOrCancellationReleasesCredit() {
        for (int outcome = 0; outcome < 3; outcome++) {
            long user = 9764L + outcome;
            prepare(user, CompanionChatMode.MODEL);
            long initial = ledger.available(user);
            when(router.decide(user)).thenReturn(ModelRouteDecision.platform());
            when(platform.stream(any(Prompt.class))).thenReturn(outcome == 0
                    ? Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("平台回复")))))
                    : outcome == 1 ? Flux.error(new IllegalStateException("synthetic failure")) : Flux.never());
            Flux<String> response = chat.chat(AuthorizationSubject.user(user), "你好");
            assertThat(ledger.getOrCreate(user).reserved()).isEqualTo(1);
            if (outcome == 0) assertThat(response.blockLast()).isEqualTo("平台回复");
            else if (outcome == 1) assertThatThrownBy(response::blockLast).isInstanceOf(IllegalStateException.class);
            else response.subscribe().dispose();
            assertThat(ledger.getOrCreate(user).reserved()).isZero();
            assertThat(ledger.available(user)).isEqualTo(initial - (outcome == 0 ? 1 : 0));
            assertThatThrownBy(() -> quota.reserve(user, today(), 1)).hasMessageContaining("次数已用完");
        }
    }

    private Companion prepare(long user, CompanionChatMode mode) {
        when(modes.effectiveMode(user)).thenReturn(mode);
        return companions.createIfAbsent(user, CompanionBalance.v1());
    }

    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))); }
}
