# 生产 CORS 白名单、凭据/日志审计与发布检查

> 日期：2026-08-14
>
> 对应三年规划「未来 90 天执行清单」第 5 条。
>
> 本文是**人工签字清单**：自动化测试能验证配置存在，但生产发布前仍须由部署者逐项确认。

## 1. CORS 白名单

### 机制

- `CorsConfig` 只从 `app.cors.allowed-origin-patterns` 读取白名单，不再硬编码 `*`。
- 本地 `application.yaml` 默认 `${CORS_ALLOWED_ORIGINS:*}`（开发放行）。
- 生产 `application-prod.yaml` 为 `${CORS_ALLOWED_ORIGINS}`（**无默认值，缺失时启动失败**）。
- `compose.yaml` 使用 `:?` 必填语法，未配置时 `docker compose config` 即报错。

### 人工检查

1. `.env` 中 `CORS_ALLOWED_ORIGINS` 只包含真实业务域名（逗号分隔），例如
   `https://lipicturecloud.com,https://www.lipicturecloud.com`。
2. 不包含 `*`、内网调试地址或已下线的域名。
3. 浏览器抓包确认跨域预检（`OPTIONS`）只对白名单域名返回
   `Access-Control-Allow-Origin`，来源不在白名单时响应头不含该字段。
4. `SESSION_COOKIE_SECURE=true`（HTTPS 环境）与 `same-site=lax` 保持开启。

## 2. 凭据与日志审计

### 禁止出现在数据库、日志、指标标签、异常消息中的内容

- 用户简介原文、图片 Data URL、原始模型响应正文；
- `DASHSCOPE_API_KEY`、COS Secret、千帆/MXAI 密钥及任何 Token；
- 完整图片对象地址（COS URL）。

### 允许记录的安全字段

- subjectId、pictureId、companionId、correlationId、memoryId；
- 安全错误码（如 `VISION_TIMEOUT`、`PICTURE_UNAVAILABLE`）、Provider/模型标识、置信度。

### 人工检查

1. `grep -R "API_KEY\|SecretKey\|password" 日志目录` 无真实值。
2. 伙伴相关日志只包含上面白名单字段（`companion_*` 结构化日志的 key=value 均可展示）。
3. 数据库抽查：`companion_memory` 的 `content/originalContent` 只来自视觉独白或 Demo
   固定文案；`companion_growth_record.reason` 无用户原文。
4. 轮换 `DASHSCOPE_API_KEY` 后旧 key 立即撤销，不在任何提交或环境文件中留存。

## 3. 真实环境发布检查（每项打勾后才放行）

### 数据与迁移

- [ ] 冷库已备份并确认恢复点。
- [ ] 只在物理 MySQL 运行 Liquibase（绝不指向 `jdbc:shardingsphere:`）。
- [ ] `DATABASECHANGELOG` 存在 `20260811-01`…`20260811-07`、`20260813-01`…、`20260814-01`…`20260814-04`。
- [ ] 若启用分片：先 `scripts/migrate-companion-physical.ps1` 再启动分片 profile；
      用专用主体对 `companion`、`companion_mood`、`companion_relationship`、
      `companion_memory` 做 CRUD smoke test。

### 功能开关与配置

- [ ] `COMPANION_ENABLED`、`COMPANION_FEEDING_ENABLED` 与 `VITE_COMPANION_ENABLED` 三者一致。
- [ ] 视觉七项（policy/provider/model/daily-limit/timeout/max-bytes/endpoint）全部显式配置。
- [ ] `CORS_ALLOWED_ORIGINS` 已按第 1 节配置。

### 行为验收

- [ ] 唤醒 → 喂养 → 情绪/关系/记忆候选生成 → 记忆确认/纠正/忽略/删除 全链路可用。
- [ ] 同一幂等键回放不重复生成情绪、关系变化或记忆候选。
- [ ] 撤销图片权限后刷新记忆列表，对应记忆变为"来源不可用"且内容不再展示。
- [ ] 主页情绪在跨小时后展示回落，不会突破 `[0,100]`。

### 隐私与钱包（沿用 round-19 第 7 条语义）

- [ ] 抓包中只出现发往已配置 Provider endpoint 的视觉请求。
- [ ] 请求不含 COS Secret、永久签名参数或他人图片。
- [ ] 第 11 次视觉调用在出站前被阻断；失败与降级不退还已预占次数。
- [ ] 401/403 不降级；超时、429、5xx 只按白名单降级并记录原因。

## 4. 结论模板

| 项目 | 填写内容 |
| --- | --- |
| 审核范围 | CORS、凭据/日志、迁移与行为验收 |
| 基线与目标 | `3a24fbf`（或更新后的 HEAD）/ 目标环境 |
| CORS 白名单 | 实际域名列表 |
| 密钥轮换 | 新 key 生效时间、旧 key 撤销时间 |
| 自动化证据 | `verify`、前端 `npm test/lint/build`、E2E 通过记录 |
| 人工签字 | 部署者、领域审核人、日期 |
| 最终结论 | 放行 / 有条件放行 / 不放行 |
