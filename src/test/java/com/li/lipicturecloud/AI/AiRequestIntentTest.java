package com.li.lipicturecloud.AI;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRequestIntentTest {

    @Test
    void onlyExplicitImageGenerationRequestsShowGenerationProgress() {
        assertThat(AiRequestIntent.isImageGenerationRequest("你好，介绍一下你自己")).isFalse();
        assertThat(AiRequestIntent.isImageGenerationRequest("请帮我生成一张海边日落图片")).isTrue();
        assertThat(AiRequestIntent.isImageGenerationRequest("查询任务进度")).isFalse();
        assertThat(AiRequestIntent.isImageGenerationRequest("generate_image a cat")).isTrue();
    }
}
