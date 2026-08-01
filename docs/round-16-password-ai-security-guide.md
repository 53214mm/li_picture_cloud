# 第 16 轮：密码与 AI 工具安全操作指南

这份文档面向第一次接触项目部署的同学。请先通读，再操作数据库。

## 一、本轮改变了什么

本轮包含两项安全整改：

1. 用户密码不再使用固定盐 MD5，改为 BCrypt（强度 12）。
2. AI 不再能调用服务器终端、通用文件读写、资源下载或 PDF 文件操作工具。

MCP 生图没有被删除。用户仍然可以让 AI 调用 MCP 生成图片；生成完成后，后端会把图片上传到 COS，并保存到该用户自己创建的个人空间。

## 二、为什么旧账号会登录失败

旧数据库中的密码通常是 32 位 MD5 字符串。新版本只接受 `$2a$12$`、`$2b$12$` 或 `$2y$12$` 开头的 BCrypt 密文，因此旧账号会统一收到“账号或密码错误”。

这是主动选择的安全策略，不是程序故障：

- 不再保留容易被批量破解的旧算法；
- 不在登录时偷偷兼容或升级 MD5；
- 不向外部用户透露数据库里保存了哪种密码格式。

### 上线时必须让旧 Session 失效

Spring Session 保存在 Redis 中，单纯重启后端不会自动删除旧登录态。如果不处理，切换 BCrypt 前已经登录的账号可能继续使用旧 Session。

生产启动时应更换 Session namespace，例如：

```bash
export SPRING_SESSION_REDIS_NAMESPACE=li-picture-cloud:sessions:bcrypt-v1
```

Windows PowerShell：

```powershell
$env:SPRING_SESSION_REDIS_NAMESPACE = "li-picture-cloud:sessions:bcrypt-v1"
```

然后重启所有后端实例。新版本只会读取新 namespace，旧 Session 会立即失效，同时不会误删协同编辑或其他 Redis 数据。以后再次进行需要全员重新登录的认证升级，可以把末尾版本改为 `v2`。

正式操作前必须备份数据库。例如在服务器终端使用 MySQL 官方工具，将文件保存到只有管理员可读的目录：

```bash
mysqldump -h 数据库地址 -u 管理账号 -p --single-transaction li_picture_cloud_data > li_picture_cloud_before_bcrypt.sql
```

命令会交互式询问密码。不要把数据库密码直接写在命令中，也不要把备份文件提交到 Git。

## 三、新环境创建第一个管理员

### 第 1 步：正常注册

启动新版后端和前端，通过注册页面创建账号。此时密码会由后端自动编码成 BCrypt，浏览器和数据库管理员都不需要生成散列。

### 第 2 步：在数据库客户端提升角色

先确认目标账号只有一条记录：

```sql
SELECT id, userAccount, userRole
FROM user
WHERE userAccount = '你的账号' AND isDelete = 0;
```

确认无误后执行：

```sql
UPDATE user
SET userRole = 'admin'
WHERE userAccount = '你的账号' AND isDelete = 0;
```

退出登录再重新登录，让 Session 重新加载管理员角色。

## 四、保留旧账号数据时怎样重置密码

项目目前没有经过验证的邮箱或手机号，因此不能安全实现“忘记密码”。开发环境可以使用下面的临时账号办法，让应用自己生成 BCrypt 密文。

1. 先做好数据库备份。
2. 通过注册页面创建一个临时账号，例如 `bcrypt_reset_temp`，密码填写旧账号的新密码。
3. 在数据库事务中把临时账号的 BCrypt 密文复制给旧账号，然后删除临时账号。

```sql
START TRANSACTION;

UPDATE user AS target
JOIN user AS source ON source.userAccount = 'bcrypt_reset_temp'
SET target.userPassword = source.userPassword
WHERE target.userAccount = '需要重置的旧账号'
  AND target.isDelete = 0
  AND source.isDelete = 0;

DELETE FROM user
WHERE userAccount = 'bcrypt_reset_temp';

COMMIT;
```

执行后检查受影响行数，再用旧账号和新密码登录。生产环境应由数据库管理员执行，并使用参数化的数据库客户端输入账号名，避免拼接不可信内容。

严禁以下做法：

- 把明文密码写进 SQL 文件、源码、测试、Git 提交或启动参数；
- 使用在线“BCrypt 生成网站”，因为网站可能记录密码；
- 把旧 MD5 密文恢复回来；
- 把其他真实用户的 BCrypt 密文当作临时散列复制。

如果不需要保留开发数据，最简单的方法是清理开发数据库，通过新版注册接口重新创建账号。

## 五、哪些 AI 能力被删除了

模型不再获得这些服务器能力：

- 执行 `dir`、`ping`、`systeminfo` 等终端命令；
- 读取或写入服务器任意业务目录下的文件；
- 把任意网络资源下载到服务器目录；
- 按模型给出的路径创建、读取、合并或拆分 PDF。

这些类已经从源码和工具注册表中删除，不是只在页面上隐藏按钮。因此提示词注入也无法让模型重新调用它们。

## 六、哪些 AI 与 MCP 能力仍然存在

以下业务能力继续保留：

- MCP `generate_image`、`generate_video` 和 `get_task_status`；
- AI 图片生成结果自动保存；
- 将合法的 HTTP(S) 图片地址保存到当前用户的个人空间；
- 查询公开且审核通过的图片；
- 图片格式、尺寸和质量分析；
- 受 SSRF 校验保护的网页抓取与配置了密钥的联网搜索。

MCP 工具由 `RefreshableMcpToolProvider` 单独注入，删除本地服务器工具不会关闭 MCP。部署环境仍需正确提供 MCP API Key、COS 配置、MySQL 和 Redis 配置。

## 七、怎样验证 AI 生图和自动保存

### 准备

1. 使用普通账号登录。
2. 创建一个“私有空间”，记住空间名称。
3. 可以另外创建一个团队空间，用来验证不会保存错位置。
4. 确认 MCP 和 COS 环境变量已经配置。

### 操作

在 AI 页面输入类似内容：

> 生成一张夜晚雪山和星空的图片，并保存到我的空间。

生成任务可能需要一到数分钟。完成后页面应展示 MCP 返回的图片，并显示保存成功信息，其中包含个人空间名称和图片 ID。

### 核对

1. 打开刚才创建的个人空间，确认能看到新图片。
2. 打开团队空间，确认新图片没有出现在这里。
3. 查询数据库时，新图片的 `spaceId` 应等于个人空间 ID。
4. 如果用户没有个人空间，AI 应明确显示“未保存：请先创建个人空间”，而不是自动放入团队空间。

## 八、常见问题

### MCP 能生成图片，但提示没有保存

先检查用户是否创建了私有空间，再检查 COS 配置和后端日志。不要因为保存失败就重新开放文件下载工具；图片保存必须经过 `PictureService` 的校验、上传和入库流程。

### 重启后仍然是旧工具清单

重启后端进程。工具注册发生在 Spring 容器启动阶段，旧进程不会自动卸载已经注册的工具。

### 是否可以回滚到 MD5

不可以。应用版本需要回滚时，可以回退相应 Git 提交，但密码应继续保持 BCrypt。恢复 MD5 会重新引入已知安全风险。

## 九、上线前人工验收清单

- [ ] 已备份数据库，并验证备份文件可读取。
- [ ] 新注册账号的 `userPassword` 以 BCrypt 前缀开头，但没有打印明文。
- [ ] 旧 MD5 账号无法直接登录。
- [ ] 已切换 Spring Session Redis namespace，旧登录态全部失效。
- [ ] 第一个管理员重新登录后能进入管理页面。
- [ ] AI 工具清单中没有终端、文件、下载和 PDF 路径工具。
- [ ] MCP 生图仍然成功。
- [ ] 生成图片进入当前用户的个人空间。
- [ ] 生成图片没有进入当前用户创建的团队空间。
- [ ] `src/main/resources/application.yaml` 的本地配置没有进入 Git 提交。
