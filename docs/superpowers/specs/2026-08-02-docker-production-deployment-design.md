# Docker Compose 生产部署设计

## 1. 目标与已知环境

本次为图片云项目增加 Docker Compose 部署能力和一份面向初学者的服务器部署教程。目标服务器信息如下：

- 操作系统：OpenCloudOS。
- 公网 IP：`82.156.66.244`。
- 正式域名：`lipicturecloud.com`，当前仍在备案。
- 配置：4 核 CPU、4 GB 内存、40 GB 系统盘。
- 当前可用内存约 1.3 GB，1 GB Swap 已用满。
- 系统盘剩余约 8.7 GB。
- 服务器已经运行多个 Docker 项目。
- 现有名为 `nginx` 的容器占用 80/443，并通过 `nginx_conf` 命名卷加载 `/etc/nginx/conf.d/*.conf`。

用户明确选择在当前资源条件下新增一套隔离的 MySQL、Redis、后端和前端容器，并接受资源不足可能导致 OOM 的风险。

## 2. 方案选择

### 2.1 采用方案：受限资源下的独立完整栈

图片云项目使用独立容器、独立网络和独立数据卷，不复用其他项目的 MySQL 或 Redis。这样可以避免数据库名、密码、Redis Key、升级计划和故障恢复互相影响。

为适应当前服务器，所有容器设置内存上限，MySQL 使用小内存参数，Java 堆设置明确上限，并对 Docker 日志启用轮转。这是一套“可以先运行，但应持续观察并准备扩容”的部署，而不是高可用方案。

### 2.2 没有采用的方案

- 升级到 8 GB 内存、80 GB 磁盘后再部署：稳定性最佳，但用户决定暂不升级。
- 复用现有 MySQL 和 Redis：资源占用更低，但跨项目共享基础设施会扩大故障影响范围。

## 3. 运行架构

项目 Compose 管理四个服务：

1. `lipicturecloud-mysql`：MySQL 8.0，仅在项目内部网络提供 3306。
2. `lipicturecloud-redis`：Redis 7 Alpine，仅在项目内部网络提供 6379。
3. `lipicturecloud-backend`：Java 21 Spring Boot，仅在项目内部网络提供 8124。
4. `lipicturecloud-web`：Nginx Alpine，提供前端构建产物，仅在项目内部网络提供 80。

Compose 创建固定名称的 `lipicturecloud` 网络。服务器已有的总入口 `nginx` 容器通过一次性、幂等的 `docker network connect` 命令加入此网络。

总入口 Nginx 按请求路径转发：

- `/` 转发到 `lipicturecloud-web:80`。
- `/api/` 转发到 `lipicturecloud-backend:8124`。
- `/api/ws/collaboration` 启用 WebSocket Upgrade 转发。
- `/api/ai/chat/stream` 使用长连接设置并关闭代理缓冲，保证 SSE 流式输出。

项目服务不发布任何宿主机端口，从而不会与现有的 80、443、3306、3307、6379、6380、8080 或 8123 冲突。MySQL、Redis和后端也不会新增公网攻击面。

## 4. 镜像构建

### 4.1 后端镜像

后端使用多阶段 Dockerfile：

- 构建阶段使用 Maven 与 Java 21，执行可复现的 Maven package。
- 运行阶段只包含 Java 21 JRE、时区数据和健康检查需要的轻量工具。
- 容器使用非 root 用户运行。
- JVM 通过环境变量限制初始堆、最大堆和元空间。
- 只复制最终 Spring Boot JAR，不复制源代码和 Maven 缓存。

### 4.2 前端镜像

前端使用多阶段 Dockerfile：

- Node 22 阶段通过 `npm ci` 安装锁定依赖并执行 Vite 构建。
- Nginx Alpine 阶段只复制 `dist` 静态文件和 SPA 配置。
- 前端 API 使用现有相对路径 `/api`，不把服务器 IP 或域名编译进 JavaScript。
- SPA 未命中静态文件时回退 `index.html`。

### 4.3 构建资源风险

Maven 和 Node 构建会产生短时内存、CPU 和磁盘峰值。部署教程必须先执行 `free -h`、`df -h /` 和 `docker system df`，并说明：

- 不自动执行 `docker system prune`，因为它可能影响其他项目。
- 如果构建出现 OOM，应先停止并检查，不重复无意义构建。
- 用户可以在确认业务允许后临时停止高内存容器，或升级服务器，再重新构建。
- Docker 构建完成后需要检查磁盘剩余空间。

## 5. 资源控制

初始资源预算如下：

| 服务 | 容器内存上限 | 关键限制 |
| --- | ---: | --- |
| 后端 | 640 MB | JVM 最大堆约 384 MB，元空间约 128 MB |
| MySQL | 384 MB | InnoDB Buffer Pool 128 MB、最多约 50 个连接、关闭 Performance Schema |
| Redis | 96 MB | 数据最大内存约 48 MB，启用淘汰策略 |
| 前端 Nginx | 32 MB | 仅提供静态文件 |

总容器上限约 1.15 GB。该上限接近服务器当前可用内存，因此文档必须明确以下判断方法：

- `docker stats` 观察持续内存。
- `docker inspect` 查看 OOMKilled。
- 容器退出码 137 通常表示内存不足。
- Redis 达到上限可能淘汰 Session、AI 记忆或协同临时状态。
- MySQL 或 Java 在高并发下仍可能因为上限过低退出。

## 6. 数据库初始化与持久化

MySQL 使用独立命名卷。首次创建空数据卷时，Docker MySQL 初始化机制按以下顺序执行现有 SQL：

1. `sql/user.sql`
2. `sql/picture.sql`
3. `sql/space.sql`

顺序不可改变，因为 `space.sql` 会修改 `picture` 表。Compose 通过将源文件分别挂载为 `/docker-entrypoint-initdb.d/01-user.sql`、`02-picture.sql` 和 `03-space.sql` 固定执行顺序。

初始化只建表，不执行 `sql/dev_seed_users.sql`。生产管理员必须按 `sql/prod_seed_users_template.sql` 和第 17 轮指南手工创建。

Redis 和 MySQL 都使用命名卷。部署文档提供：

- MySQL 使用 `mysqldump` 的备份命令。
- 备份文件的权限和保存位置。
- 从备份恢复到空库的命令。
- 更新应用时保留数据卷的方法。
- 删除数据卷属于破坏性操作，不出现在普通更新步骤中。

## 7. 配置与密钥

仓库提交 `.env.example`，真实服务器创建 `.env`。`.env` 已被 Git 忽略，服务器上权限设置为 `600`。

环境变量覆盖：

- MySQL 数据库、用户和强随机密码。
- Redis 强随机密码。
- COS Host、Bucket、Region、SecretId 和 SecretKey。
- DashScope API Key。
- 百度千帆 API Key 和 Bearer Token。
- MXAI MCP API Key。
- 协同编辑允许来源。
- Spring Profile、Cookie Secure 开关和 Session 命名空间版本。

真实密码或 Key 不写入 Compose、Dockerfile、Nginx 配置、部署文档或 Git。当前本地 `application-local.yaml` 虽然被 Git 忽略，但包含真实密钥；上线前应轮换这些密钥，并只把新值放入服务器 `.env`。

生产配置补齐 COS 和 AI 的环境变量映射，并将 Session Cookie 的 `secure` 属性改为环境变量控制，默认值仍为 `true`。

## 8. 备案前后的访问方式

### 8.1 备案前：IP HTTP

- 访问地址：`http://82.156.66.244`。
- 总入口 Nginx 的临时站点同时匹配 IP。
- `SESSION_COOKIE_SECURE=false`，否则浏览器不会通过 HTTP 回传登录 Cookie。
- `COLLABORATION_ALLOWED_ORIGINS=http://82.156.66.244`。
- 腾讯云安全组只需要允许 22、80；443 可以保留为后续 HTTPS 使用。

IP HTTP 只作为备案等待期间的临时方案。密码和 Session 在公网传输时缺少 TLS 保护，不适合真实用户或敏感数据。

### 8.2 备案后：域名 HTTPS

- 将 `lipicturecloud.com` 和可选的 `www.lipicturecloud.com` 解析到 `82.156.66.244`。
- 通过 Certbot Webroot 或等价方式申请 Let’s Encrypt 证书。
- HTTP 仅保留 ACME 验证并跳转 HTTPS。
- `SESSION_COOKIE_SECURE=true`。
- `COLLABORATION_ALLOWED_ORIGINS=https://lipicturecloud.com`，如果启用 `www`，再显式追加对应来源。
- 修改 `.env` 后只重建或重启后端，不重建数据库卷。

文档同时提供证书续期检查和 Nginx 配置测试命令。证书私钥不得进入仓库。

## 9. Nginx 接入方式

仓库提供两个模板：

- 备案前 IP HTTP 站点配置。
- 备案后域名 HTTPS 站点配置。

用户将选中的配置复制到 Docker 命名卷实际目录 `/var/lib/docker/volumes/nginx_conf/_data/conf.d/`。每次变更都遵循：

1. 先备份现有配置文件。
2. 写入图片云独立配置文件，不覆盖 `nginx.conf` 和其他站点。
3. 执行 `docker exec nginx nginx -t`。
4. 只有语法检查成功后才执行 `docker exec nginx nginx -s reload`。

代理配置包含：

- 上传体积至少 50 MB，并留出协议开销。
- WebSocket Upgrade/Connection 头。
- SSE 的长超时与 `proxy_buffering off`。
- `X-Forwarded-For`、`X-Forwarded-Proto`、`Host` 等转发头。
- 静态资源缓存，而 `index.html` 不使用长期不可变缓存。

## 10. 健康检查与启动顺序

- MySQL 使用 `mysqladmin ping` 并携带容器内部密码。
- Redis 使用带密码的 `redis-cli ping`。
- 后端等待 MySQL、Redis 健康后启动，并通过一个无需登录、能证明 HTTP 服务已就绪的路径检查。
- 前端 Nginx 使用本地 HTTP 检查。
- 总入口 Nginx 不由本项目 Compose 管理，避免误重建其他项目的公共入口。

所有容器使用 `restart: unless-stopped`，日志使用 `json-file` 的 `max-size` 和 `max-file` 控制磁盘增长。

## 11. 部署文档范围

小白部署文档逐条解释并给出预期输出：

1. OpenCloudOS 环境和 Docker Compose 检查。
2. 当前内存、Swap、磁盘和端口的上线前检查。
3. 克隆或更新 GitHub 仓库。
4. 创建部署目录和 `.env`。
5. 生成强随机 MySQL、Redis 密码。
6. 轮换并填写 COS 与三套 AI Key。
7. 构建镜像、启动基础设施和查看健康状态。
8. 将总入口 Nginx 加入项目网络。
9. 安装备份后的站点配置并验证。
10. 通过 IP 检查首页、登录、上传、AI、SSE 和协同 WebSocket。
11. 使用生产模板创建首个管理员。
12. 查看日志、判断 OOM、排查 502/504、数据库和 Redis 连接错误。
13. 执行 MySQL 备份与恢复演练。
14. 更新代码、重建应用、回滚到旧 Git 提交。
15. 备案完成后的 DNS、证书和 HTTPS 切换。
16. 收紧腾讯云安全组和现有公网数据库端口。

文档不会要求用户粘贴 SSH 私钥、数据库密码或云平台密钥到聊天中。

## 12. 自动化验证

实现完成前至少执行：

- 后端完整 Maven `verify`。
- 前端测试、lint、build 和 bundle budget。
- `docker compose config`，使用测试环境变量证明 Compose 能完整解析。
- 后端镜像构建。
- 前端镜像构建。
- 静态检查 Compose 不发布 MySQL、Redis和后端端口。
- 静态检查 `.env.example` 不包含真实密钥。
- Nginx 配置语法检查；使用临时 Nginx 容器和模板依赖解析方式验证配置。
- Git 范围检查，继续排除用户本地的 `src/main/resources/application.yaml` 修改。

由于当前任务不会直接登录用户服务器，真实公网连通性、腾讯云安全组、DNS、备案和真实密钥可用性必须由用户按文档在服务器上验证。

## 13. 验收标准

1. `docker compose up -d` 能创建四个带项目前缀的服务，且不绑定宿主机端口。
2. MySQL 首次启动能按正确顺序创建全部业务表。
3. 后端在 `prod` Profile 下通过容器名连接 MySQL 和 Redis。
4. 前端、REST API、SSE 和 WebSocket 都能通过总入口 Nginx 工作。
5. 备案前可使用 IP HTTP，备案后有明确且安全的 HTTPS 切换步骤。
6. 所有真实密钥只存在于服务器 `.env`，仓库无新增秘密。
7. 日志轮转、内存限制、健康检查、备份、更新和回滚都有可执行说明。
8. 文档明确当前 4 GB / 40 GB 服务器的 OOM 与磁盘风险。
9. 不修改、不停止、不删除服务器上的其他项目容器。
10. 用户本地 `src/main/resources/application.yaml` 改动不进入任何提交。

## 14. 范围外事项

- Kubernetes、高可用 MySQL、Redis Cluster 或多机部署。
- 自动登录服务器并执行部署。
- 自动修改腾讯云安全组、DNS 或域名备案。
- 自动停止、迁移或删除服务器上的其他项目。
- 自动清理 Docker 镜像、构建缓存或数据卷。
- 自动创建公开开发账号。
