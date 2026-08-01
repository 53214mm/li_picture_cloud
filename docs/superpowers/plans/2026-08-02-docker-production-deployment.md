# Docker Production Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a resource-limited, isolated Docker Compose stack and a beginner-safe OpenCloudOS deployment guide for `82.156.66.244` and the future `lipicturecloud.com` HTTPS site.

**Architecture:** Compose owns four internal-only services (`mysql`, `redis`, `backend`, `web`) on a dedicated `lipicturecloud` network; the server's existing `nginx` container joins that network and remains the sole 80/443 gateway. Multi-stage images build Java 21 and Node 22 artifacts, production settings come only from `.env`, and ordered MySQL init mounts create a fresh schema without public seed users.

**Tech Stack:** Docker Engine with Compose v2, MySQL 8.0, Redis 7 Alpine, Eclipse Temurin Java 21, Maven Wrapper, Node 22, Nginx Alpine, Spring Boot 3.5, Vue 3/Vite 7, OpenCloudOS.

## Global Constraints

- Target server is OpenCloudOS at `82.156.66.244`, 4 CPU / 4 GB RAM / 40 GB disk, with roughly 1.3 GB currently available and full Swap.
- Existing container `nginx` remains the sole owner of host ports 80 and 443 and must not be recreated by this project.
- Project MySQL, Redis, backend, and web publish no host ports.
- Memory ceilings are backend 640 MB, MySQL 384 MB, Redis 96 MB, and web 32 MB.
- MySQL initialization order is `user.sql`, `picture.sql`, then `space.sql`; development seed accounts never run automatically.
- BCrypt remains strength 12; the production seed template is the only documented initial-admin path.
- Real COS, DashScope, Qianfan, MXAI, MySQL, and Redis secrets exist only in server `.env`, mode `600`.
- Before filing, use `http://82.156.66.244` with `SESSION_COOKIE_SECURE=false`; after filing, use `https://lipicturecloud.com` with `SESSION_COOKIE_SECURE=true`.
- Do not run Docker prune, delete volumes, stop other projects, or edit the server automatically.
- Never stage or commit the user's local `src/main/resources/application.yaml` change.
- Every artifact change follows RED → GREEN, verified commits are pushed to `origin/main` when the network permits.

---

## File Structure

- `src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java`: real-artifact contract checks for production properties, Compose, Dockerfiles, and Nginx templates.
- `src/main/resources/application-prod.yaml`: environment-only production COS/AI/session configuration.
- `Dockerfile`: backend multi-stage build and non-root Java runtime.
- `.dockerignore`: excludes secrets, local build output, Git metadata, frontend dependencies, and docs from backend context.
- `li-picture-cloud-frontend/Dockerfile`: Node 22 build and static Nginx runtime.
- `li-picture-cloud-frontend/.dockerignore`: excludes local dependencies/build output.
- `li-picture-cloud-frontend/nginx.conf`: SPA/static runtime configuration inside the project web container.
- `compose.yaml`: four internal services, health checks, limits, volumes, network, and log rotation.
- `.env.example`: exhaustive non-secret deployment variable template.
- `deploy/nginx/lipicturecloud-ip-http.conf`: temporary IP HTTP gateway.
- `deploy/nginx/lipicturecloud-domain-https.conf`: post-filing domain HTTPS gateway.
- `deploy/nginx/lipicturecloud-acme-http.conf`: HTTP/ACME bootstrap before a certificate exists.
- `docs/round-18-docker-deployment-guide.md`: beginner deployment, operations, backup, upgrade, rollback, and troubleshooting guide.

### Task 1: Production environment contract

**Files:**
- Create: `src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java`
- Modify: `src/main/resources/application-prod.yaml`

**Interfaces:**
- Produces Spring properties for `COS_*`, `DASHSCOPE_API_KEY`, `QIANFAN_API_KEY`, `QIANFAN_BEARER_TOKEN`, `MXAI_API_KEY`, `SESSION_COOKIE_SECURE`, and `SESSION_REDIS_NAMESPACE`.
- Preserves `prod` defaults: secure cookies enabled, Swagger disabled, Redis collaboration enabled.

- [ ] **Step 1: Write the first failing artifact test**

Create a JUnit test that reads real repository files. The first test asserts production configuration contains these exact environment mappings and no literal secret:

```java
package com.li.lipicturecloud.deployment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentArtifactsTest {

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

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run RED**

```powershell
$env:JAVA_HOME='G:\JDK\Java\jdk-21'
.\mvnw.cmd -Dtest=DeploymentArtifactsTest test
```

Expected: FAIL because `application-prod.yaml` still hardcodes `secure: true` and lacks COS/AI mappings.

- [ ] **Step 3: Add minimal production mappings**

Extend `application-prod.yaml` without changing local defaults:

```yaml
cos:
  client:
    host: ${COS_HOST}
    secretId: ${COS_SECRET_ID}
    secretKey: ${COS_SECRET_KEY}
    region: ${COS_REGION}
    bucket: ${COS_BUCKET}

spring:
  config:
    activate:
      on-profile: prod
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
    qianfan:
      api-key: ${QIANFAN_API_KEY}
      Bearer: ${QIANFAN_BEARER_TOKEN}
  session:
    redis:
      namespace: ${SESSION_REDIS_NAMESPACE:lipicturecloud:session:v1}
    cookie:
      secure: ${SESSION_COOKIE_SECURE:true}
      http-only: true
      same-site: lax

mxai:
  api-key: ${MXAI_API_KEY}
```

Retain the existing MyBatis, SpringDoc, and collaboration blocks exactly once.

- [ ] **Step 4: Run GREEN**

```powershell
.\mvnw.cmd -Dtest=DeploymentArtifactsTest test
git diff --check
```

Expected: one test passes and no whitespace errors.

- [ ] **Step 5: Commit and push**

```powershell
git add -- src/main/resources/application-prod.yaml src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java
git diff --cached --name-only
git commit -m "config: externalize production deployment secrets"
git push origin main
```

Expected staged paths are only the production YAML and test.

---

### Task 2: Backend and frontend container images

**Files:**
- Modify: `src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java`
- Create: `Dockerfile`
- Create: `.dockerignore`
- Create: `li-picture-cloud-frontend/Dockerfile`
- Create: `li-picture-cloud-frontend/.dockerignore`
- Create: `li-picture-cloud-frontend/nginx.conf`

**Interfaces:**
- Produces image `lipicturecloud-backend` listening internally on 8124 with a non-root UID.
- Produces image `lipicturecloud-web` listening internally on 80 and serving Vue SPA output.

- [ ] **Step 1: Add failing image artifact tests**

Add two tests to `DeploymentArtifactsTest`:

```java
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
```

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -Dtest=DeploymentArtifactsTest test
```

Expected: both new tests error with `NoSuchFileException` for missing Dockerfiles.

- [ ] **Step 3: Implement the backend image**

Create root `Dockerfile` with:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache tzdata busybox-extras \
    && addgroup -S app && adduser -S -G app app
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/li-picture-cloud-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8124
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Create root `.dockerignore` excluding `.git`, `.idea`, `.env*` except `.env.example`, `target`, all `node_modules`, frontend `dist`, logs, local YAML, credential/key formats, and `docs`.

- [ ] **Step 4: Implement the frontend image and SPA config**

Create the frontend Dockerfile:

```dockerfile
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

Create `nginx.conf` with a port-80 server, `try_files` SPA fallback, no-cache headers for `/index.html`, one-year immutable cache for `/assets/`, and `/healthz` returning text `ok`.

Create frontend `.dockerignore` excluding `node_modules`, `dist`, `.vite`, logs, `.env*`, and editor metadata.

- [ ] **Step 5: Run GREEN and local frontend gates**

```powershell
.\mvnw.cmd -Dtest=DeploymentArtifactsTest test
Set-Location li-picture-cloud-frontend
npm test
npm run lint
npm run build
npm run check:bundle
Set-Location ..
```

Expected: artifact tests and all frontend gates pass.

- [ ] **Step 6: Build both images**

```powershell
docker build -t lipicturecloud-backend:test .
docker build -t lipicturecloud-web:test li-picture-cloud-frontend
```

Expected: both builds finish successfully. If local Docker is unavailable, record that exact environmental blocker and defer image execution verification to a Docker-capable environment; do not claim images were built.

- [ ] **Step 7: Commit and push**

```powershell
git add -- Dockerfile .dockerignore li-picture-cloud-frontend/Dockerfile li-picture-cloud-frontend/.dockerignore li-picture-cloud-frontend/nginx.conf src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java
git commit -m "build: add production container images"
git push origin main
```

---

### Task 3: Resource-limited Compose stack

**Files:**
- Modify: `src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java`
- Create: `compose.yaml`
- Create: `.env.example`

**Interfaces:**
- Produces Compose services named `lipicturecloud-mysql`, `lipicturecloud-redis`, `lipicturecloud-backend`, and `lipicturecloud-web`.
- Produces network named `lipicturecloud` for later attachment by the existing gateway Nginx.
- Produces volumes `lipicturecloud_mysql_data` and `lipicturecloud_redis_data`.

- [ ] **Step 1: Add failing Compose safety test**

Add a test asserting the real Compose file contains the four fixed container names, ordered init targets, memory limits, log rotation, and no `ports:` key:

```java
@Test
void composeKeepsEveryProjectServiceInternalAndResourceLimited() throws IOException {
    String compose = read("compose.yaml");
    assertThat(compose).contains(
            "container_name: lipicturecloud-mysql",
            "container_name: lipicturecloud-redis",
            "container_name: lipicturecloud-backend",
            "container_name: lipicturecloud-web",
            "mem_limit: 640m", "mem_limit: 384m", "mem_limit: 96m", "mem_limit: 32m",
            "/docker-entrypoint-initdb.d/01-user.sql:ro",
            "/docker-entrypoint-initdb.d/02-picture.sql:ro",
            "/docker-entrypoint-initdb.d/03-space.sql:ro",
            "max-size: 10m", "max-file: 3");
    assertThat(compose).doesNotContain("ports:");
}
```

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -Dtest=DeploymentArtifactsTest test
```

Expected: `NoSuchFileException: compose.yaml`.

- [ ] **Step 3: Create `.env.example`**

Declare non-secret defaults and empty markers for:

```dotenv
COMPOSE_PROJECT_NAME=lipicturecloud
MYSQL_DATABASE=li_picture_cloud_data
MYSQL_USERNAME=lipicturecloud
MYSQL_PASSWORD=replace_with_random_mysql_password
MYSQL_ROOT_PASSWORD=replace_with_different_random_root_password
REDIS_PASSWORD=replace_with_random_redis_password
SPRING_PROFILES_ACTIVE=prod
SESSION_COOKIE_SECURE=false
SESSION_REDIS_NAMESPACE=lipicturecloud:session:v1
COLLABORATION_ALLOWED_ORIGINS=http://82.156.66.244
COS_HOST=https://xx-li-picture-1421056219.cos.ap-beijing.myqcloud.com
COS_BUCKET=xx-li-picture-1421056219
COS_REGION=ap-beijing
COS_SECRET_ID=replace_on_server
COS_SECRET_KEY=replace_on_server
DASHSCOPE_API_KEY=replace_on_server
QIANFAN_API_KEY=replace_on_server
QIANFAN_BEARER_TOKEN=replace_on_server
MXAI_API_KEY=replace_on_server
```

The file header states it is a template and every `replace_*` value must change before startup.

- [ ] **Step 4: Create Compose services**

Implement `compose.yaml` with:

- MySQL 8.0, UTF-8 defaults, 128 MB buffer pool, max 50 connections, disabled Performance Schema, 384 MB limit, ordered read-only init mounts, and password-authenticated `mysqladmin ping`.
- Redis 7 Alpine using `--requirepass`, append-only persistence, 48 MB maxmemory, `allkeys-lru`, 96 MB limit, and authenticated `redis-cli ping`.
- Backend build from root, `JAVA_TOOL_OPTIONS=-Xms128m -Xmx384m -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError`, all required environment variables, healthy dependencies, `nc -z 127.0.0.1 8124`, and 640 MB limit.
- Web build from frontend directory, backend healthy dependency, `wget -qO- http://127.0.0.1/healthz`, and 32 MB limit.
- `restart: unless-stopped`, `json-file` 10 MB × 3 logs for every service.
- Named network with `name: lipicturecloud` and named data volumes with explicit names.

- [ ] **Step 5: Run GREEN and parse Compose**

```powershell
.\mvnw.cmd -Dtest=DeploymentArtifactsTest test
Copy-Item .env.example .env.compose-test
docker compose --env-file .env.compose-test config --quiet
Remove-Item -LiteralPath .env.compose-test
```

Expected: tests pass and Compose exits 0. The temporary file is ignored by `.gitignore` because it starts with `.env.`.

- [ ] **Step 6: Commit and push**

```powershell
git add -- compose.yaml .env.example src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java
git commit -m "build: add resource-limited Compose stack"
git push origin main
```

---

### Task 4: Existing-gateway Nginx templates

**Files:**
- Modify: `src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java`
- Create: `deploy/nginx/lipicturecloud-ip-http.conf`
- Create: `deploy/nginx/lipicturecloud-acme-http.conf`
- Create: `deploy/nginx/lipicturecloud-domain-https.conf`

**Interfaces:**
- IP template serves `http://82.156.66.244`.
- ACME template permits certificate issuance for `lipicturecloud.com` before HTTPS exists.
- HTTPS template redirects HTTP and serves `https://lipicturecloud.com` with REST, WebSocket, and SSE forwarding.

- [ ] **Step 1: Add failing gateway tests**

Add tests verifying:

```java
@Test
void gatewayTemplatesCoverIpHttpsWebsocketAndSse() throws IOException {
    String ip = read("deploy/nginx/lipicturecloud-ip-http.conf");
    String https = read("deploy/nginx/lipicturecloud-domain-https.conf");
    assertThat(ip).contains("server_name 82.156.66.244", "proxy_pass http://lipicturecloud-web:80", "proxy_pass http://lipicturecloud-backend:8124");
    assertThat(https).contains("server_name lipicturecloud.com", "ssl_certificate", "proxy_buffering off", "proxy_set_header Upgrade $http_upgrade", "client_max_body_size 55m");
}
```

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd -Dtest=DeploymentArtifactsTest test
```

Expected: missing Nginx template errors.

- [ ] **Step 3: Implement three templates**

Each template declares an upstream-free server using Docker DNS names directly. Both serving templates route exact WebSocket and SSE paths before the general `/api/` location, forward Host/real IP/protocol headers, use 3600-second streaming timeouts, and set `client_max_body_size 55m`.

The HTTPS template reads certificates from `/etc/letsencrypt/live/lipicturecloud.com/fullchain.pem` and `privkey.pem`; the deployment guide mounts or copies these into the existing `nginx_conf` volume without committing certificate material.

- [ ] **Step 4: Run GREEN and syntax-check templates**

```powershell
.\mvnw.cmd -Dtest=DeploymentArtifactsTest test
docker run --rm --add-host=lipicturecloud-web:127.0.0.1 --add-host=lipicturecloud-backend:127.0.0.1 -v "${PWD}/deploy/nginx/lipicturecloud-ip-http.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.27-alpine nginx -t
```

Expected: tests pass and the IP config reports syntax successful. HTTPS syntax is checked after certificate paths are available on the server; static tests still require both certificate directives.

- [ ] **Step 5: Commit and push**

```powershell
git add -- deploy/nginx/lipicturecloud-ip-http.conf deploy/nginx/lipicturecloud-acme-http.conf deploy/nginx/lipicturecloud-domain-https.conf src/test/java/com/li/lipicturecloud/deployment/DeploymentArtifactsTest.java
git commit -m "ops: add gateway Nginx templates"
git push origin main
```

---

### Task 5: Beginner OpenCloudOS deployment and operations guide

**Files:**
- Create: `docs/round-18-docker-deployment-guide.md`

**Interfaces:**
- Documents operator actions only; it never stores a real secret or performs remote changes.

- [ ] **Step 1: Write the guide from preflight through IP launch**

Include copyable commands and expected outcomes for: `free -h`, `df -h /`, `docker system df`, `docker compose version`, cloning to `/opt/lipicturecloud`, copying `.env.example` to `.env`, `chmod 600 .env`, generating independent random passwords with `openssl rand -base64 36`, rotating cloud keys, `docker compose config`, building, starting, `docker compose ps`, logs, and attaching gateway via:

```bash
docker network inspect lipicturecloud >/dev/null
docker network connect lipicturecloud nginx 2>/dev/null || true
```

Explain why `|| true` is acceptable only for the already-connected network case, and require `docker inspect nginx` verification afterward.

Give safe commands to back up existing Nginx configuration, copy the IP template into `/var/lib/docker/volumes/nginx_conf/_data/conf.d/lipicturecloud.conf`, run `docker exec nginx nginx -t`, then reload only after success.

- [ ] **Step 2: Add administrator, validation, and troubleshooting**

Explain how to copy `sql/prod_seed_users_template.sql`, locally generate a BCrypt-12 hash using the round-17 guide, run only the enabled statement inside the new MySQL container, and verify without selecting `userPassword`.

Provide tests for homepage, `/api` response, login cookie, COS upload, DashScope, Qianfan, MXAI generation, SSE streaming, and two-browser WebSocket collaboration.

Document 502/504, unhealthy MySQL/Redis, bad `.env`, CORS/Origin, secure-cookie mismatch, exit code 137/OOMKilled, full disk, and how to inspect logs without printing environment variables.

- [ ] **Step 3: Add backup, update, rollback, and HTTPS migration**

Provide `mysqldump --single-transaction`, backup permissions, test restore into a disposable database, `git pull --ff-only`, selective image rebuild, safe rollback to a named Git commit, and explicit warnings never to use `docker compose down -v` in normal operations.

For HTTPS, cover DNS, Tencent security-group 80/443, ACME bootstrap config, Certbot webroot, certificate placement visible inside existing Nginx, `nginx -t`, HTTPS template switch, `.env` changes to secure cookie/domain Origin, backend recreation, HTTP redirect test, WebSocket `wss`, and renewal dry-run.

Include a separate security section for closing public 3306/6379 only after identifying which existing projects depend on them; do not provide a blind command that could interrupt unrelated projects.

- [ ] **Step 4: Verify guide coverage and secrets**

```powershell
rg -n "82\.156\.66\.244|lipicturecloud\.com|OpenCloudOS|docker compose|nginx -t|WebSocket|SSE|mysqldump|OOMKilled|down -v|SESSION_COOKIE_SECURE|COLLABORATION_ALLOWED_ORIGINS" docs/round-18-docker-deployment-guide.md
rg -n "AKID|sk-[A-Za-z0-9]|nb_[A-Za-z0-9]|bce-v3/" Dockerfile compose.yaml .env.example deploy docs/round-18-docker-deployment-guide.md
git diff --check
```

Expected: coverage search finds every topic; secret-pattern search is empty; whitespace check passes.

- [ ] **Step 5: Commit and push**

```powershell
git add -- docs/round-18-docker-deployment-guide.md
git commit -m "docs: teach Docker production deployment"
git push origin main
```

---

### Task 6: Full delivery verification

**Files:**
- Verify only. Any defect correction starts with a failing `DeploymentArtifactsTest` or the narrowest existing behavior test.

**Interfaces:**
- Proves the committed stack is buildable, internally isolated, documented, and aligned with remote `main`.

- [ ] **Step 1: Run complete backend verification**

```powershell
$env:JAVA_HOME='G:\JDK\Java\jdk-21'
.\mvnw.cmd -B "-Dspring.profiles.active=test" verify
```

Expected: `BUILD SUCCESS`, zero failures and errors; existing Redis integration assumptions may skip when Redis is unavailable.

- [ ] **Step 2: Run complete frontend gates**

```powershell
Set-Location li-picture-cloud-frontend
npm test
npm run lint
npm run build
npm run check:bundle
npm audit --omit=dev --audit-level=high --registry=https://registry.npmjs.org
Set-Location ..
```

Expected: tests, lint, build, bundle budget, and production audit pass.

- [ ] **Step 3: Validate Compose and images**

```powershell
Copy-Item .env.example .env.compose-test
docker compose --env-file .env.compose-test config --quiet
docker compose --env-file .env.compose-test build backend web
Remove-Item -LiteralPath .env.compose-test
```

Expected: Compose parses and both images build. Do not start the full stack locally if host resources or ports/data are not appropriate.

- [ ] **Step 4: Verify security and repository scope**

```powershell
rg -n "^\s*ports:" compose.yaml
rg -n "AKID|sk-[A-Za-z0-9]|nb_[A-Za-z0-9]|bce-v3/" Dockerfile compose.yaml .env.example deploy docs/round-18-docker-deployment-guide.md
git diff --check
git status --short
git fetch origin main
git rev-parse HEAD
git rev-parse origin/main
```

Expected: both security searches are empty; only the user's `application.yaml` remains modified; local and remote hashes match.

- [ ] **Step 5: Report the manual server boundary**

Tell the user which files were delivered, that no server command was executed remotely, the exact first section of the guide to follow, the current 4 GB/40 GB risk, and that production Keys must be rotated and entered only on the server.
