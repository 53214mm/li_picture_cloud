package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CostSource;
import com.li.lipicturecloud.domain.airuntime.ModelConnection;
import com.li.lipicturecloud.domain.airuntime.ModelProvider;
import com.li.lipicturecloud.domain.airuntime.ModelTask;
import com.li.lipicturecloud.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageCreationServiceTest {

    private ImageRouter imageRouter;
    private ImageModelInvoker imageInvoker;
    private ModelUsageService usageService;
    private ImageCreationService service;

    @BeforeEach
    void setUp() {
        imageRouter = mock(ImageRouter.class);
        imageInvoker = mock(ImageModelInvoker.class);
        usageService = mock(ModelUsageService.class);
        service = new ImageCreationService(imageRouter, imageInvoker, usageService);
    }

    private ModelRouteDecision byokRoute() {
        return ModelRouteDecision.byok(ModelConnection.restore(9L, 7L, ModelProvider.OPENAI,
                "出图主力", URI.create("https://api.openai.com/v1"), "gpt-image-2", 5L, true, 1L),
                "sk-openai");
    }

    @Test
    void byokGenerationReturnsResultAndRecordsUsage() {
        when(imageRouter.decide(7L)).thenReturn(byokRoute());
        ImageGenerationResult result = new ImageGenerationResult(
                URI.create("https://cdn.example.test/result.png"), null);
        when(imageInvoker.invoke(any(ModelRouteDecision.class), anyString(), anyString()))
                .thenReturn(result);

        assertThat(service.generate(7L, "一只安静的猫", "1024x1024")).isEqualTo(result);
        verify(usageService).recordSuccess(7L, ModelTask.IMAGE_CREATION, 9L,
                ModelProvider.OPENAI, "gpt-image-2", CostSource.BYOK);
    }

    @Test
    void byokFailureIsRecordedWithSafeCodeAndNeverFallsBack() {
        when(imageRouter.decide(7L)).thenReturn(byokRoute());
        when(imageInvoker.invoke(any(ModelRouteDecision.class), anyString(), anyString()))
                .thenThrow(new ModelInvocationException(ConnectivityResult.CREDENTIAL_REJECTED,
                        "rejected"));

        assertThatThrownBy(() -> service.generate(7L, "一只安静的猫", "1024x1024"))
                .isInstanceOf(ModelInvocationException.class);
        verify(usageService).recordFailure(7L, ModelTask.IMAGE_CREATION, 9L,
                ModelProvider.OPENAI, "gpt-image-2", CostSource.BYOK,
                ConnectivityResult.CREDENTIAL_REJECTED);
    }

    @Test
    void platformRouteFailsLoudlyUntilThePlatformLedgerExists() {
        when(imageRouter.decide(7L)).thenReturn(ModelRouteDecision.platform());

        assertThatThrownBy(() -> service.generate(7L, "一只安静的猫", "1024x1024"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("平台图片创作尚未开放");
        verify(imageInvoker, never()).invoke(any(), anyString(), anyString());
        verify(usageService, never()).recordSuccess(anyLong(), any(), any(), any(), anyString(), any());
    }

    @Test
    void validatesPromptAndSizeBeforeRouting() {
        assertThatThrownBy(() -> service.generate(7L, "   ", "1024x1024"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.generate(7L, "x".repeat(2001), "1024x1024"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.generate(7L, "带控制字符\u0007", "1024x1024"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.generate(7L, "一只安静的猫", "512x512"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的图片尺寸");
        assertThatThrownBy(() -> service.generate(0L, "一只安静的猫", "1024x1024"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(imageRouter, never()).decide(anyLong());
    }
}
