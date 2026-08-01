# 第 18 轮：OpenCloudOS Docker 部署教程

> 目标服务器：`82.156.66.244`；未来域名：`lipicturecloud.com`。
>
> 本文按“第一次部署服务器”的节奏编写。除特别说明外，命令都在服务器的 SSH 终端执行。不要把密码、云密钥或 `.env` 内容发到聊天、截图或 GitHub。

## 1. 先理解这次会启动什么

本项目会启动四个容器：

| 容器 | 用途 | 内存上限 | 是否占用宿主机端口 |
| --- | --- | ---: | --- |
| `lipicturecloud-mysql` | 独立数据库 | 384 MB | 否 |
| `lipicturecloud-redis` | Session、协同状态等 | 96 MB | 否 |
| `lipicturecloud-backend` | Spring Boot 后端 | 640 MB | 否 |
| `lipicturecloud-web` | Vue 静态网页 | 32 MB | 否 |

它们只在 Docker 网络 `lipicturecloud` 内互通。已有的公共 `nginx` 容器仍负责服务器的 80/443 端口，再加入该网络并按域名或 IP 转发请求。因此不会抢占现有项目的 3306、6379、8080 等端口。

当前服务器只有约 1.3 GiB 可用内存、8.7 GiB 可用磁盘，Swap 已满。这套配置能限制本项目最大内存，但不能保证整台服务器永远不 OOM。不要执行 `docker system prune -a`，它可能删除其他项目依赖的镜像。

## 2. 部署前检查

### 2.1 登录服务器

在你自己的电脑终端执行：

```bash
ssh root@82.156.66.244
```

出现类似 `[root@VM-0-3-opencloudos ~]#` 表示已经进入服务器。后续命令在这个窗口执行。

### 2.2 检查 Docker 与 Compose

```bash
docker --version
docker compose version
```

两条命令都应输出版本号。若第二条提示不存在，OpenCloudOS 可先尝试：

```bash
dnf install -y docker-compose-plugin
docker compose version
```

不要安装旧的 Python `docker-compose` 来替换正在使用的 Compose 插件。

### 2.3 记录服务器现状

```bash
free -h
df -h /
docker system df
docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Ports}}"
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}"
```

判断标准：

- 根分区最好至少剩余 6 GiB；构建过程中空间会短时增长。
- `available` 内存若明显低于 1 GiB，构建更容易失败。
- 不能因为空间紧张就直接清理全部 Docker 数据；先用 `docker system df` 找原因。
- 容器退出码 137 或 `OOMKilled=true` 通常表示内存不足。

## 3. 下载项目

### 3.1 安装 Git 并创建固定目录

```bash
dnf install -y git
mkdir -p /opt/lipicturecloud
cd /opt/lipicturecloud
```

如果目录是空的，克隆项目：

```bash
git clone https://github.com/53214mm/li_picture_cloud.git .
git status
git log -1 --oneline
```

`git status` 应显示在 `main` 分支，且没有本地修改。

如果已经克隆过，使用：

```bash
cd /opt/lipicturecloud
git status
git pull --ff-only origin main
```

`--ff-only` 能避免服务器上意外产生合并提交。若提示本地文件冲突，先停止，不要使用 `git reset --hard`。

## 4. 创建生产环境变量

### 4.1 复制模板并收紧权限

```bash
cd /opt/lipicturecloud
cp .env.example .env
chmod 600 .env
ls -l .env
```

权限应类似 `-rw-------`，表示只有 root 可以读写。

### 4.2 生成三个不同的随机密码

逐条执行并把输出临时保存在安全的密码管理器中：

```bash
openssl rand -base64 36
openssl rand -base64 36
openssl rand -base64 36
```

三次结果分别用于：

1. `MYSQL_PASSWORD`：业务数据库用户密码。
2. `MYSQL_ROOT_PASSWORD`：数据库 root 密码，必须与上一项不同。
3. `REDIS_PASSWORD`：Redis 密码。

### 4.3 编辑 `.env`

```bash
vi .env
```

按 `i` 进入编辑，替换全部 `replace_*` / `replace_on_server`。完成后按 `Esc`，输入 `:wq` 并回车。

需要填写：

- 腾讯 COS：Host、Bucket、Region 已在模板中填写；SecretId、SecretKey 使用上线前新轮换的密钥。
- 阿里云 DashScope：`DASHSCOPE_API_KEY`。
- 百度千帆：`QIANFAN_API_KEY` 和接口所需的完整 `QIANFAN_BEARER_TOKEN`。
- MXAI MCP：`MXAI_API_KEY`。

备案前暂时保持：

```dotenv
SESSION_COOKIE_SECURE=false
COLLABORATION_ALLOWED_ORIGINS=http://82.156.66.244
```

检查有没有忘记模板值（只显示行号，不打印实际密钥）：

```bash
if grep -nE 'replace_|replace_on_server' .env >/dev/null; then
  echo "错误：.env 仍有未替换值"
else
  echo "通过：模板值已经全部替换"
fi
```

检查 Compose 能否解析：

```bash
docker compose --env-file .env config --quiet
```

没有输出且退出码为 0 就是成功。不要执行 `docker compose config` 后把完整输出发给别人，因为其中会展开密钥。

## 5. 首次构建和启动

### 5.1 构建镜像

```bash
cd /opt/lipicturecloud
docker compose --env-file .env build
```

第一次会下载 Java、Node、Maven 和 npm 依赖，时间较长。另开一个 SSH 窗口观察：

```bash
watch -n 2 'free -h; echo; df -h /'
```

按 `Ctrl+C` 退出观察。如果构建被系统杀死，先执行第 11 节的 OOM 检查，不要不断重试。

### 5.2 启动四个服务

```bash
docker compose --env-file .env up -d
docker compose ps
```

MySQL 首次初始化可能需要几十秒。等待一分钟后再检查：

```bash
docker compose ps
docker compose logs --tail=100 mysql redis backend web
```

最终四个服务应是 `Up`，带健康检查的服务应显示 `healthy`。如果后端还在 `starting`，再等 30 秒；如果显示 `unhealthy`，看第 11 节。

### 5.3 确认没有新增宿主机端口

```bash
docker ps --filter name=lipicturecloud --format "table {{.Names}}\t{{.Ports}}"
```

`PORTS` 一列可以显示容器内部端口，但不应出现 `0.0.0.0:某端口->...`。

## 6. 接入服务器现有公共 Nginx

### 6.1 把公共 Nginx 加入项目网络

```bash
docker network connect lipicturecloud nginx 2>/dev/null || true
docker inspect nginx --format '{{json .NetworkSettings.Networks}}'
```

输出中应同时看到原来的 `bridge` 和新的 `lipicturecloud`。这条连接命令只需成功一次；重复执行不会影响现有网络。

在公共 Nginx 容器里验证能找到两个项目容器：

```bash
docker exec nginx getent hosts lipicturecloud-web
docker exec nginx getent hosts lipicturecloud-backend
```

两条命令都应输出容器 IP。

### 6.2 备份现有 Nginx 配置

```bash
mkdir -p /root/nginx-backup
cp -a /var/lib/docker/volumes/nginx_conf/_data/conf.d \
  /root/nginx-backup/conf.d-$(date +%Y%m%d-%H%M%S)
```

这里只备份，不覆盖其他站点。

### 6.3 安装备案前 IP 配置

```bash
cp /opt/lipicturecloud/deploy/nginx/lipicturecloud-ip-http.conf \
  /var/lib/docker/volumes/nginx_conf/_data/conf.d/lipicturecloud.conf
docker exec nginx nginx -t
```

只有看到 `syntax is ok` 和 `test is successful` 后，才执行：

```bash
docker exec nginx nginx -s reload
```

若检查失败，不要 reload。删除刚复制的独立文件即可回退：

```bash
rm -f /var/lib/docker/volumes/nginx_conf/_data/conf.d/lipicturecloud.conf
docker exec nginx nginx -t
```

## 7. 验证备案前访问

浏览器打开：

```text
http://82.156.66.244
```

服务器本机也可以检查：

```bash
curl -I http://82.156.66.244/
curl -sS http://82.156.66.244/api/health || true
docker compose ps
```

功能验收建议按以下顺序：

1. 首页和登录页能打开，刷新子路由不会 404。
2. 注册或登录后刷新页面仍保持登录。
3. 上传一张小图，再测试接近业务上限的图片。
4. 测试 AI 对话，确认文字是逐步出现而非最后一次性出现。
5. 两个账号进入同一团队空间：编辑者旋转/缩放时，查看者能看到动作但不能修改。
6. 测试 AI 生图保存到个人空间，确认 COS 地址可访问。

IP HTTP 没有 TLS 加密，只适合备案等待期间自己的临时验证，不要让真实用户在公网传输敏感密码。

## 8. 创建生产管理员

生产脚本默认全部注释，不能直接产生弱密码账号。先在可信机器生成 BCrypt strength 12 密文，再编辑副本：

```bash
cd /opt/lipicturecloud
cp sql/prod_seed_users_template.sql /root/prod_seed_users.sql
chmod 600 /root/prod_seed_users.sql
vi /root/prod_seed_users.sql
```

在副本中替换账号名和 `REPLACE_WITH_BCRYPT_12_HASH`，仅取消需要的 INSERT 注释。确认文件中没有明文密码，然后执行：

```bash
docker exec -i lipicturecloud-mysql mysql \
  -ulipicturecloud -p"$(grep '^MYSQL_PASSWORD=' .env | cut -d= -f2-)" \
  li_picture_cloud_data < /root/prod_seed_users.sql
```

成功后立即登录、修改临时密码，并安全删除服务器上的临时 SQL：

```bash
rm -f /root/prod_seed_users.sql
```

更完整的种子账号说明见 `docs/round-17-user-seed-guide.md`。

## 9. 数据备份与恢复

### 9.1 创建 MySQL 备份

```bash
mkdir -p /opt/backups/lipicturecloud
chmod 700 /opt/backups/lipicturecloud
cd /opt/lipicturecloud
docker exec lipicturecloud-mysql sh -c \
  'exec mysqldump --single-transaction --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  | gzip > /opt/backups/lipicturecloud/mysql-$(date +%Y%m%d-%H%M%S).sql.gz
ls -lh /opt/backups/lipicturecloud
```

验证压缩包没有损坏：

```bash
gzip -t /opt/backups/lipicturecloud/mysql-*.sql.gz
```

备份要再复制到服务器以外的位置；只留在同一块系统盘不算可靠备份。

### 9.2 恢复前的原则

恢复会覆盖或重复写入业务数据，属于高风险操作。先停止后端写入并再次备份：

```bash
cd /opt/lipicturecloud
docker compose stop backend
```

确认要恢复的文件名，然后执行（把文件名替换成实际值）：

```bash
gunzip -c /opt/backups/lipicturecloud/mysql-YYYYMMDD-HHMMSS.sql.gz \
  | docker exec -i lipicturecloud-mysql sh -c \
    'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
docker compose start backend
docker compose ps
```

不要把 `docker compose down -v` 当作普通重启；`-v` 会删除 MySQL 和 Redis 数据卷。

## 10. 日常更新与回滚

### 10.1 更新前

```bash
cd /opt/lipicturecloud
git rev-parse HEAD
docker compose ps
```

记下旧提交号，并按第 9 节备份数据库。

### 10.2 拉取、构建、替换应用

```bash
git pull --ff-only origin main
docker compose --env-file .env build backend web
docker compose --env-file .env up -d --no-deps backend web
docker compose ps
docker compose logs --tail=100 backend web
```

MySQL 和 Redis 不会被删除，数据卷保持不变。若 Compose、数据库参数也发生变化，再使用完整命令：

```bash
docker compose --env-file .env up -d
```

### 10.3 应用代码回滚

先确认旧提交号确实是你要回退的版本：

```bash
git log --oneline -10
git switch --detach 旧提交号
docker compose --env-file .env build backend web
docker compose --env-file .env up -d --no-deps backend web
```

恢复成功后仍应决定一个正式分支版本，不要长期让服务器停在 detached HEAD。数据库结构若已发生不兼容变化，不能只回滚镜像，必须按对应迁移/备份方案处理。

## 11. 常见故障排查

### 11.1 页面显示 502 Bad Gateway

```bash
docker compose ps
docker logs --tail=100 nginx
docker compose logs --tail=200 backend web
docker exec nginx getent hosts lipicturecloud-backend
docker exec nginx getent hosts lipicturecloud-web
```

如果域名解析失败，通常是公共 Nginx 没加入 `lipicturecloud` 网络，重新执行第 6.1 节。

### 11.2 后端连接 MySQL 或 Redis 失败

```bash
docker compose logs --tail=200 mysql redis backend
docker inspect lipicturecloud-mysql --format '{{json .State.Health}}'
docker inspect lipicturecloud-redis --format '{{json .State.Health}}'
```

不要在 Redis 无密码时保留 `.env` 中的假密码。本 Compose 明确启用了密码，因此后端和 Redis 必须使用同一个 `REDIS_PASSWORD`。

MySQL 初始化 SQL 只在数据卷第一次为空时执行。修改 SQL 后仅重启不会重新建表，也不要为“重跑 SQL”随意删除数据卷。

### 11.3 判断是否 OOM

```bash
docker inspect lipicturecloud-backend --format 'OOM={{.State.OOMKilled}} Exit={{.State.ExitCode}}'
docker inspect lipicturecloud-mysql --format 'OOM={{.State.OOMKilled}} Exit={{.State.ExitCode}}'
dmesg -T | grep -iE 'out of memory|killed process' | tail -30
docker stats --no-stream
```

`OOM=true` 或退出码 137 时：

1. 停止继续重启和重复构建。
2. 找出整机内存最高的容器。
3. 评估临时停止非关键容器，或将服务器升级到至少 8 GiB。
4. 不要盲目把 Java 堆调大；容器总内存不变时反而更易触发 OOM。

### 11.4 磁盘不足

```bash
df -h /
docker system df -v
du -sh /var/lib/docker/volumes/* 2>/dev/null | sort -h | tail
```

优先清理明确可删除的旧备份、旧日志或确认无用的单个镜像。不要执行未经核对的递归删除，也不要直接删除 `/var/lib/docker`。

### 11.5 WebSocket 连不上或 AI 一次性返回

确认公共 Nginx 使用的是本仓库模板：

```bash
docker exec nginx nginx -T | grep -nE 'ws/collaboration|chat/stream|proxy_buffering'
```

协同编辑还要求浏览器来源与 `.env` 的 `COLLABORATION_ALLOWED_ORIGINS` 完全一致，包括 `http/https` 和端口。修改 `.env` 后重建后端容器环境：

```bash
docker compose --env-file .env up -d --force-recreate --no-deps backend
```

## 12. 备案完成后切换域名 HTTPS

备案未完成时不要提前对外提供域名站点。完成后再做以下步骤。

### 12.1 DNS 与安全组

在 DNS 控制台添加：

- `@` 的 A 记录指向 `82.156.66.244`。
- 若需要 `www`，添加 `www` 的 A 记录指向同一 IP。

安全组只需对公网开放 22（最好限制来源 IP）、80、443。不要开放本项目 MySQL、Redis、8124。

等待解析后检查：

```bash
getent hosts lipicturecloud.com
getent hosts www.lipicturecloud.com
```

### 12.2 安装 ACME 临时配置

```bash
cp /opt/lipicturecloud/deploy/nginx/lipicturecloud-acme-http.conf \
  /var/lib/docker/volumes/nginx_conf/_data/conf.d/lipicturecloud.conf
docker exec nginx nginx -t
docker exec nginx nginx -s reload
```

### 12.3 申请证书

下面让 Certbot 的验证文件写入现有 `nginx_html` 卷，证书写入现有 `nginx_conf` 卷。这样公共 Nginx 分别能从 `/usr/share/nginx/html` 和 `/etc/nginx/live` 看到它们。

```bash
docker run --rm \
  -v nginx_html:/var/www/certbot \
  -v nginx_conf:/etc/letsencrypt \
  certbot/certbot certonly --webroot \
  -w /var/www/certbot \
  -d lipicturecloud.com -d www.lipicturecloud.com \
  --email 你的真实邮箱 --agree-tos --no-eff-email
```

如果暂时不使用 `www`，同时从 DNS、命令的 `-d` 参数和 Nginx `server_name` 中移除它，三处保持一致。

### 12.4 启用 HTTPS

```bash
cp /opt/lipicturecloud/deploy/nginx/lipicturecloud-domain-https.conf \
  /var/lib/docker/volumes/nginx_conf/_data/conf.d/lipicturecloud.conf
docker exec nginx nginx -t
docker exec nginx nginx -s reload
```

然后编辑 `.env`：

```dotenv
SESSION_COOKIE_SECURE=true
COLLABORATION_ALLOWED_ORIGINS=https://lipicturecloud.com
```

让后端读取新环境变量：

```bash
cd /opt/lipicturecloud
docker compose --env-file .env up -d --force-recreate --no-deps backend
curl -I https://lipicturecloud.com
```

浏览器重新登录；旧的 HTTP Cookie 不应继续作为生产会话使用。

### 12.5 证书续期

先手工演练：

```bash
docker run --rm \
  -v nginx_html:/var/www/certbot \
  -v nginx_conf:/etc/letsencrypt \
  certbot/certbot renew --dry-run
```

正式续期命令去掉 `--dry-run`，成功后执行 `docker exec nginx nginx -s reload`。可以再配置 systemd timer 或 cron，但第一次先确保手工续期流程成功。

## 13. 上线检查清单

- [ ] `.env` 权限为 600，Git 中没有 `.env`。
- [ ] 所有 `replace_*` 均已替换，旧云密钥已轮换。
- [ ] `docker compose config --quiet` 通过。
- [ ] 四个项目容器健康，且没有宿主机端口映射。
- [ ] 公共 `nginx` 同时连接原网络和 `lipicturecloud` 网络。
- [ ] `nginx -t` 通过后才 reload。
- [ ] 登录、上传、COS、AI、SSE、WebSocket 协同均已人工验收。
- [ ] 管理员使用 BCrypt 12 密文创建，没有生产明文密码。
- [ ] MySQL 备份已生成、校验并复制到服务器之外。
- [ ] 已观察 `docker stats`、OOM 和磁盘空间。
- [ ] 备案前仅临时 IP HTTP；备案后启用 HTTPS 和 Secure Cookie。
- [ ] 腾讯云安全组没有向公网开放 MySQL、Redis、8124。

## 14. 明确禁止的操作

- 不要执行 `docker compose down -v`，除非明确决定永久删除本项目数据。
- 不要执行 `docker system prune -a --volumes`，它可能影响所有项目。
- 不要覆盖 `/etc/nginx/nginx.conf` 或删除其他站点配置。
- 不要把 `.env`、证书私钥、数据库备份提交到 Git。
- 不要把开发种子账号用于生产环境。
- 不要因为服务异常就先删除 MySQL/Redis 卷；日志和备份永远优先。

本轮只提供仓库部署资产和操作手册，没有自动登录或修改你的服务器。这样可以保证服务器上其他项目不会被未经核对的命令影响。
