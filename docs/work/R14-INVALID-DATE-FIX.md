# R14 · 图库第二次打开显示 Invalid Date

日期：2026-10-09。分支：nexus/mainline-r11，延续此前未提交的验收修整。

## 复现与根因

用户反馈首次打开可能正常，切换页面再返回必定出现 Invalid Date。对同一实际查询连续读取得到：

| 路径 | createTime |
|---|---|
| 不经过缓存的列表 | `2026-09-21T06:17:32.000+00:00` |
| 缓存列表首次未命中 | `2026-09-21T06:17:32.000+00:00` |
| 缓存列表第二次命中 | `1789971452000`（字符串） |

缓存写入用 Hutool 保存 Date 的毫秒数，但读取时使用原始 Page.class，丢失 records 中 PictureVO 的泛型类型。时间字段退化为数字，再被保护雪花 ID 精度的全局 Long → String 序列化器变成字符串。Gallery 直接 new Date(数字字符串)，得到 Invalid Date。全局 Long 序列化规则本身不能删除，否则会破坏 ID 精度。

直接将真实响应传给原 Gallery 格式化函数已稳定复现；新增浏览器测试也先红：首次 ISO 显示正常，展开侧栏空间 → 我的空间 → 图库后，卡片显示 Invalid Date。

## 修复

- PictureController 的 Caffeine 和 Redis 读取均使用 TypeReference<Page<PictureVO>>，恢复 Date 和 Long 字段类型；缓存命中/未命中的对外 JSON 一致。读取旧缓存内容也走这个转换，无需清空共享 Redis。
- 图片视图统一使用 utils/date.js：兼容 ISO、毫秒数字及旧毫秒字符串，保留浏览器本地时区和 zh-CN 日期展示；缺失/不可解析/越界值显示 `-`，零毫秒不误判为空。
- Gallery、PictureList、PictureDetail、PictureManage 接入同一方法。没有猜秒/毫秒单位，没有更改数据库、权限、缓存失效规则或全局 Long 序列化器。
- 原缓存测试曾通过再序列化成 PictureVO 来检查值，会掩盖响应类型丢失；现在直接消费实际类型，并使用生产 Jackson Long 配置比对完整响应。

## 验证

- 新增两级缓存回归旧实现 2/2 失败；修复后缓存与团队权限相关套件 **11/11**。比较创建/编辑/更新/审核日期、完整 JSON、超长 ID、缓存命中无重复数据库读取；原缓存权限检查仍通过。
- 前端 Node **332/332**，新增 3 项日期格式与边界测试。
- Gallery 浏览器 **40/40**：开发与生产默认关闭构建各 20 项，含切页返回及刷新回归、权限、筛选、触摸与窄屏。
- 当前运行的 localhost:5173 / 8124 真实服务：连续三次进入图库，每次 12 张卡片均显示有效日期、0 pageerror。该验证未登录新账号、未写业务数据、未重启用户开发服务；前端兼容已覆盖仍返回旧时间格式的现有后端进程。
- 严格 lint、Companion 开/关两次 build 与 bundle budget 通过，最大 chunk 396,005 bytes；diff-check 通过。
- 本轮采用局部后端回归，没有把此前 744 项 clean verify 改写成新版本全量结果。

命令：frontend 下 Node 22 运行 `node --test tests/*.test.mjs`、`node node_modules/eslint/bin/eslint.js . --max-warnings 0`，两种 VITE_COMPANION_ENABLED 分别 build/check:bundle，再运行 `playwright.gallery.config.js` 两个项目。后端沿用 Windows 验收记录工具链，执行 `-Dtest=PictureControllerBatchCacheTest,PictureControllerTeamAccessTest test`。

红绿日志、两个 Surefire XML、浏览器 trace/截图/JSON、真实页面三次读取摘要位于 `output/invalid-date-20261009/`，不覆盖上一轮验收证据。

## 生效与范围

前端源码已由现有 Vite 加载，刷新页面即可使用；当前后端进程没有被重启，后端缓存根因修复在其重新编译启动后生效。前端已兼容这段过渡期。MCP 未改，工作区变更尚未提交推送。
