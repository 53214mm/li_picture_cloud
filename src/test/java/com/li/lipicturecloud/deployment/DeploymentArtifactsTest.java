package com.li.lipicturecloud.deployment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentArtifactsTest {

    @Test
    void backendImageUsesJava21MultiStageAndNonRootRuntime() throws IOException {
        String dockerfile = read("Dockerfile");

        assertThat(dockerfile).contains("AS build", "./mvnw", "21-jre", "USER app", "EXPOSE 8124");
        assertThat(dockerfile).doesNotContain("application-local.yaml");
    }

    @Test
    void frontendImageBuildsWithNode22AndServesSpaWithNginx() throws IOException {
        String dockerfile = read("li-picture-cloud-frontend/Dockerfile");
        String nginx = read("li-picture-cloud-frontend/nginx.conf");

        assertThat(dockerfile).contains("node:22", "npm ci", "npm run build", "nginx:1.27-alpine");
        assertThat(nginx).contains("try_files $uri $uri/ /index.html", "location /assets/");
    }

    @Test
    void productionProfileMapsEveryExternalSecretFromEnvironment() throws IOException {
        String yaml = read("src/main/resources/application-prod.yaml");

        assertThat(yaml).contains(
                "secure: ${SESSION_COOKIE_SECURE:true}",
                "namespace: ${SESSION_REDIS_NAMESPACE:lipicturecloud:session:v1}",
                "secretId: ${COS_SECRET_ID}",
                "secretKey: ${COS_SECRET_KEY}",
                "api-key: ${DASHSCOPE_API_KEY}",
                "api-key: ${QIANFAN_API_KEY}",
                "Bearer: ${QIANFAN_BEARER_TOKEN}",
                "api-key: ${MXAI_API_KEY}");
    }

    @Test
    void composeKeepsEveryProjectServiceInternalAndResourceLimited() throws IOException {
        String compose = read("compose.yaml");

        assertThat(compose).contains(
                "container_name: lipicturecloud-mysql",
                "container_name: lipicturecloud-redis",
                "container_name: lipicturecloud-backend",
                "container_name: lipicturecloud-web",
                "mem_limit: 640m",
                "mem_limit: 384m",
                "mem_limit: 96m",
                "mem_limit: 32m",
                "/docker-entrypoint-initdb.d/01-user.sql:ro",
                "/docker-entrypoint-initdb.d/02-picture.sql:ro",
                "/docker-entrypoint-initdb.d/03-space.sql:ro",
                "max-size: 10m",
                "max-file: 3");
        assertThat(compose).doesNotContain("ports:");
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
