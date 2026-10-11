package com.li.lipicturecloud.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CompanionChatDefaultsTest {
    @Test
    void shippedConfigurationAllowsBoundedPlatformChatForNewUsers() throws IOException {
        CompanionFeatureProperties properties = bind(new MockEnvironment());
        assertThat(properties.getChatPolicy()).isEqualTo(CompanionFeatureProperties.CompanionChatPolicy.MODEL);
        assertThat(properties.getChatDailyLimit()).isEqualTo(50);
        assertThat(properties.getChatContextBudget()).isEqualTo(12000);
        assertThat(new ModelCredentialProperties().getTrialDefaultBalance()).isEqualTo(100);
    }

    @Test
    void operatorCanExplicitlyDisablePlatformAndChangeItsDailyBudget() throws IOException {
        CompanionFeatureProperties properties = bind(new MockEnvironment()
                .withProperty("COMPANION_CHAT_POLICY", "DEMO_ONLY")
                .withProperty("COMPANION_CHAT_DAILY_LIMIT", "7"));
        assertThat(properties.getChatPolicy()).isEqualTo(CompanionFeatureProperties.CompanionChatPolicy.DEMO_ONLY);
        assertThat(properties.getChatDailyLimit()).isEqualTo(7);
    }

    private CompanionFeatureProperties bind(MockEnvironment environment) throws IOException {
        for (var source : new YamlPropertySourceLoader().load("shipped", new ClassPathResource("application.yaml"))) {
            environment.getPropertySources().addLast(source);
        }
        return Binder.get(environment).bind("app.companion", CompanionFeatureProperties.class).get();
    }
}
