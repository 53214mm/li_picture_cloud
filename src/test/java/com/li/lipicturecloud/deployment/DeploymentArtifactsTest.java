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
                "allowed-origin-patterns: ${CORS_ALLOWED_ORIGINS}",
                "secretId: ${COS_SECRET_ID}",
                "secretKey: ${COS_SECRET_KEY}",
                "api-key: ${DASHSCOPE_API_KEY}",
                "api-key: ${QIANFAN_API_KEY}",
                "Bearer: ${QIANFAN_BEARER_TOKEN}",
                "api-key: ${MXAI_API_KEY}",
                "nutrition-policy: ${COMPANION_NUTRITION_POLICY}",
                "vision-endpoint: ${COMPANION_VISION_ENDPOINT}",
                "vision-provider: ${COMPANION_VISION_PROVIDER}",
                "vision-model: ${COMPANION_VISION_MODEL}",
                "vision-timeout: ${COMPANION_VISION_TIMEOUT}",
                "vision-max-bytes: ${COMPANION_VISION_MAX_BYTES}",
                "vision-daily-limit: ${COMPANION_VISION_DAILY_LIMIT}");
    }

    @Test
    void productionCorsRequiresAnExplicitAllowlistWithoutStarFallback() throws IOException {
        String prod = read("src/main/resources/application-prod.yaml");
        String local = read("src/main/resources/application.yaml");
        String compose = read("compose.yaml");
        String environment = read(".env.example");
        String cors = read("src/main/java/com/li/lipicturecloud/config/CorsProperties.java");

        // 生产必须显式提供白名单：yaml 无默认值、compose 必填、示例文件给出域名而不是 *。
        assertThat(prod).contains("allowed-origin-patterns: ${CORS_ALLOWED_ORIGINS}");
        assertThat(compose).contains(
                "CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:?CORS_ALLOWED_ORIGINS is required}");
        assertThat(environment).contains(
                "CORS_ALLOWED_ORIGINS=https://lipicturecloud.com,https://www.lipicturecloud.com");
        assertThat(local).contains("allowed-origin-patterns: ${CORS_ALLOWED_ORIGINS:*}");
        assertThat(cors).contains("allowedOriginPatterns", "List.of(\"*\")");
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

    @Test
    void gatewayTemplatesCoverIpAcmeHttpsWebSocketAndSse() throws IOException {
        String ip = read("deploy/nginx/lipicturecloud-ip-http.conf");
        String acme = read("deploy/nginx/lipicturecloud-acme-http.conf");
        String https = read("deploy/nginx/lipicturecloud-domain-https.conf");

        assertThat(ip).contains(
                "server_name 82.156.66.244",
                "client_max_body_size 55m",
                "location = /api/ws/collaboration",
                "proxy_set_header Upgrade $http_upgrade",
                "location = /api/ai/chat/stream",
                "proxy_buffering off",
                "proxy_pass http://lipicturecloud-backend:8124",
                "proxy_pass http://lipicturecloud-web:80");
        assertThat(acme).contains("server_name lipicturecloud.com", "location ^~ /.well-known/acme-challenge/");
        assertThat(https).contains("listen 443 ssl", "ssl_certificate", "return 301 https://$host$request_uri");
    }

    @Test
    void companionFeatureFlagsAreExplicitAndDisabledByDefaultAcrossDockerCompose() throws IOException {
        String frontendDockerfile = read("li-picture-cloud-frontend/Dockerfile");
        String compose = read("compose.yaml");
        String environment = read(".env.example");

        assertThat(frontendDockerfile).contains(
                "ARG VITE_COMPANION_ENABLED=false",
                "ENV VITE_COMPANION_ENABLED=$VITE_COMPANION_ENABLED");
        assertThat(compose).contains(
                "COMPANION_ENABLED: ${COMPANION_ENABLED:-false}",
                "COMPANION_FEEDING_ENABLED: ${COMPANION_FEEDING_ENABLED:-false}",
                "COMPANION_PROCESSING_TIMEOUT: ${COMPANION_PROCESSING_TIMEOUT:-5m}",
                "COMPANION_NUTRITION_POLICY: ${COMPANION_NUTRITION_POLICY:?COMPANION_NUTRITION_POLICY is required}",
                "COMPANION_VISION_ENDPOINT: ${COMPANION_VISION_ENDPOINT:?COMPANION_VISION_ENDPOINT is required}",
                "COMPANION_VISION_PROVIDER: ${COMPANION_VISION_PROVIDER:?COMPANION_VISION_PROVIDER is required}",
                "COMPANION_VISION_MODEL: ${COMPANION_VISION_MODEL:?COMPANION_VISION_MODEL is required}",
                "COMPANION_VISION_DAILY_LIMIT: ${COMPANION_VISION_DAILY_LIMIT:?COMPANION_VISION_DAILY_LIMIT is required}",
                "COMPANION_VISION_TIMEOUT: ${COMPANION_VISION_TIMEOUT:?COMPANION_VISION_TIMEOUT is required}",
                "COMPANION_VISION_MAX_BYTES: ${COMPANION_VISION_MAX_BYTES:?COMPANION_VISION_MAX_BYTES is required}",
                "VITE_COMPANION_ENABLED: ${VITE_COMPANION_ENABLED:-false}");
        assertThat(environment).contains(
                "COMPANION_ENABLED=false",
                "COMPANION_FEEDING_ENABLED=false",
                "COMPANION_PROCESSING_TIMEOUT=5m",
                "COMPANION_NUTRITION_POLICY=METADATA_ONLY",
                "COMPANION_VISION_ENDPOINT=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                "COMPANION_VISION_PROVIDER=dashscope",
                "COMPANION_VISION_MODEL=qwen3.6-flash",
                "COMPANION_VISION_DAILY_LIMIT=10",
                "COMPANION_VISION_TIMEOUT=20s",
                "COMPANION_VISION_MAX_BYTES=8MB",
                "VITE_COMPANION_ENABLED=false");
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
