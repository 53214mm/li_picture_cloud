package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.airuntime.ChatTurn;
import com.li.lipicturecloud.application.airuntime.LanguageModelInvoker;
import com.li.lipicturecloud.application.airuntime.ModelRouteDecision;
import com.li.lipicturecloud.application.airuntime.ModelUsageService;
import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.domain.companion.CompanionChatMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelChatEpisodeSummarizerTest {

    private static final Instant AT = Instant.parse("2026-10-10T01:00:00Z");
    private final List<CompanionChatMessage> conversation = List.of(
            CompanionChatMessage.user(11L, 7L, "秋招投了三家，二面在下周", AT).withId(1L),
            CompanionChatMessage.companion(11L, 7L, "二面加油！\n我给你讲个面试官的笑话", "dashscope", "qwen3.8-max", AT)
                    .withId(2L));

    @Test
    void cleansTheModelOutput() {
        assertThat(ModelChatEpisodeSummarizer.clean("无")).isEmpty();
        assertThat(ModelChatEpisodeSummarizer.clean("“无。”")).isEmpty();
        assertThat(ModelChatEpisodeSummarizer.clean(null)).isEmpty();
        assertThat(ModelChatEpisodeSummarizer.clean("\n经历：对方秋招二面在下周。\n第二行不要")).isEqualTo("对方秋招二面在下周。");
        assertThat(ModelChatEpisodeSummarizer.clean("长".repeat(300))).hasSize(120);
    }

    @Test
    void transcriptNamesBothSidesOnOneLineEach() {
        assertThat(ModelChatEpisodeSummarizer.transcript(conversation))
                .isEqualTo("对方：秋招投了三家，二面在下周\n绫页：二面加油！ 我给你讲个面试官的笑话\n");
    }

    @Test
    void platformRouteUsesThePlatformChatModelAndRecordsUsage() {
        ChatModel chatModel = mock(ChatModel.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(new AssistantMessage("对方秋招投了三家，二面在下周。"));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));
        ModelUsageService usage = mock(ModelUsageService.class);
        ModelChatEpisodeSummarizer summarizer = new ModelChatEpisodeSummarizer(provider,
                mock(LanguageModelInvoker.class), usage);

        ChatEpisodeSummarizer.Summary summary = summarizer.summarize(ModelRouteDecision.platform(), conversation);

        assertThat(summary.text()).isEqualTo("对方秋招投了三家，二面在下周。");
        assertThat(summary.modelProvider()).isEqualTo("dashscope");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(prompt.capture());
        // 对方讲的笑话也算值得记的事，只有纯打招呼才是"无"。
        assertThat(prompt.getValue().getInstructions().get(0).getText())
                .contains("只输出一个字：无", "对方讲的笑话、梗或段子也要记");
        verify(usage).recordSuccess(eq(7L), eq(ModelTask.LANGUAGE_AGENT), isNull(), eq(ModelProvider.DASHSCOPE),
                anyString(), eq(CostSource.PLATFORM));
    }

    @Test
    void byokRouteUsesTheUsersOwnModelWithoutATokenCapThatThinkingModelsWouldExhaust() {
        LanguageModelInvoker invoker = mock(LanguageModelInvoker.class);
        ModelRouteDecision route = mock(ModelRouteDecision.class);
        com.li.lipicturecloud.domain.airuntime.ModelConnection connection =
                mock(com.li.lipicturecloud.domain.airuntime.ModelConnection.class);
        when(route.isByok()).thenReturn(true);
        when(route.connection()).thenReturn(connection);
        when(route.costSource()).thenReturn(CostSource.BYOK);
        when(connection.provider()).thenReturn(ModelProvider.DEEPSEEK);
        when(connection.modelCode()).thenReturn("deepseek-chat");
        when(connection.id()).thenReturn(5L);
        when(invoker.stream(eq(route), any())).thenReturn(Flux.just("对方二面", "在下周。"));
        ModelChatEpisodeSummarizer summarizer = new ModelChatEpisodeSummarizer(mock(ObjectProvider.class),
                invoker, mock(ModelUsageService.class));

        ChatEpisodeSummarizer.Summary summary = summarizer.summarize(route, conversation);

        assertThat(summary.text()).isEqualTo("对方二面在下周。");
        assertThat(summary.modelCode()).isEqualTo("deepseek-chat");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatTurn>> turns = ArgumentCaptor.forClass(List.class);
        verify(invoker).stream(eq(route), turns.capture());
        assertThat(turns.getValue().get(1).content()).startsWith("对方：秋招投了三家");
    }

    @Test
    void anEmptyAnswerIsAFailureNotAnEmptyMemory() {
        LanguageModelInvoker invoker = mock(LanguageModelInvoker.class);
        ModelRouteDecision route = mock(ModelRouteDecision.class);
        com.li.lipicturecloud.domain.airuntime.ModelConnection connection =
                mock(com.li.lipicturecloud.domain.airuntime.ModelConnection.class);
        when(route.isByok()).thenReturn(true);
        when(route.connection()).thenReturn(connection);
        when(route.costSource()).thenReturn(CostSource.BYOK);
        when(connection.provider()).thenReturn(ModelProvider.DEEPSEEK);
        when(connection.modelCode()).thenReturn("deepseek-flash");
        // 思考型模型只返回了思考、没有正文：不能记成"这段没什么可记"。
        when(invoker.stream(eq(route), any())).thenReturn(Flux.empty());
        ModelUsageService usage = mock(ModelUsageService.class);
        ModelChatEpisodeSummarizer summarizer = new ModelChatEpisodeSummarizer(mock(ObjectProvider.class),
                invoker, usage);

        assertThatThrownBy(() -> summarizer.summarize(route, conversation)).isInstanceOf(IllegalStateException.class);
        verify(usage).recordFailure(eq(7L), eq(ModelTask.LANGUAGE_AGENT), any(), eq(ModelProvider.DEEPSEEK),
                eq("deepseek-flash"), eq(CostSource.BYOK), eq("EPISODE_SUMMARY_FAILED"));
    }

    @Test
    void failuresAreRecordedAndRethrown() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        ModelUsageService usage = mock(ModelUsageService.class);
        ModelChatEpisodeSummarizer summarizer = new ModelChatEpisodeSummarizer(provider,
                mock(LanguageModelInvoker.class), usage);

        assertThatThrownBy(() -> summarizer.summarize(ModelRouteDecision.platform(), conversation))
                .isInstanceOf(IllegalStateException.class);
        verify(usage).recordFailure(eq(7L), eq(ModelTask.LANGUAGE_AGENT), isNull(), eq(ModelProvider.DASHSCOPE),
                anyString(), eq(CostSource.PLATFORM), eq("EPISODE_SUMMARY_FAILED"));
    }
}
