package com.li.lipicturecloud.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorsConfigTest {

    @Test
    void rejectsWildcardAllowlistOutsideDevelopmentProfiles() {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(Profiles.of("local", "test", "e2e"))).thenReturn(false);

        assertThatThrownBy(() -> new CorsConfig(new CorsProperties(), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void acceptsWildcardAllowlistInsideDevelopmentProfiles() {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(Profiles.of("local", "test", "e2e"))).thenReturn(true);

        assertThat(new CorsConfig(new CorsProperties(), environment)).isNotNull();
    }

    @Test
    void acceptsExplicitAllowlistOutsideDevelopmentProfiles() {
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(Profiles.of("local", "test", "e2e"))).thenReturn(false);
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOriginPatterns(java.util.List.of("https://lipicturecloud.com"));

        assertThat(new CorsConfig(properties, environment)).isNotNull();
    }
}
