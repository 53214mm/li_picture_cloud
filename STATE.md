# STATE · LiPictureCloud 2.0 主线

## 概览

- 仓库：https://github.com/53214mm/li_picture_cloud
- 唯一工作分支：`nexus/mainline-r11`（长期主线，继续 R12 后不改名）
- 当前 Rxx：R12 提案 / 聊天实体化；R11 独立保留待浏览器验收
- 当前阶段：R12 首个稳定实现已通过独立代码 review；V3 完整验收未完成
- 上一完整确认完成需求：R10（用户确认，main `d49fd06`）
- 整条 Nexus 主线基线：`d49fd06645995e6f2c0a67a6f230ae6704afb2e4`
- R12 增量基线：`7a68d4204a7c166c6545f59b14429a915fe931a5`（已发布）
- 最近有效代码提交 / 最终验证对象：`7135e120b8b46e0cc95bb81ddabb49f772446ef7`（已发布；与本地已验证源提交 `0648c559` tree 完全一致）
- R11 已审代码：`6a96f92a6ce299a3eabeecf48f68ff0b4de82bb1`；其首版 `030bbde4`，状态证据 `7a68d420`（均已发布）
- 首次远端发布检查点：`7c5c975b19b686dd4dfdd01998fe1edc5b8139bf`（等价于原本地 `dda1650`）；本文件后续记录发布状态，不自引用自己的 hash
- 本轮状态证据提交：在 R12 代码提交之后，使用 `git log -- STATE.md` 定位；本文件不自引用自己的 hash
- 开工先读 `DECISIONS.md`；根需求清单历史进度过时，不得据旧表退回 R03 或覆盖用户已确认进度

## Phase 看板

| Phase | 目标 | 状态 | 证据 |
|---|---|---|---|
| R11 P1 | 核实语义与设计边界 | 已通过 | DECISIONS D-04–D-06；docs/work/R11.md |
| R11 P2 | Mapper、表现和面板实现 | 已通过代码 review | docs/work/R11.md；已发布 6a96f92 |
| R11 P3 | 前端验证与浏览器验收 | 部分通过 / 浏览器受阻 | 前轮125/125；lint；on/off build/budget；独立复核；未 DONE |
| R12 P1 | 核实轻量聊天与提案边界 | 已通过 | DECISIONS D-07–D-09；docs/work/R12.md |
| R12 P2 | 共享会话、原位聊天与既有提案预览 | 已通过独立代码 review | 已发布 7135e12；原本地源 0648c55 |
| R12 P3 | V3 完整验收 | V2 层级证据通过 / V3 待完成 | Node22全前端157/157；lint；开/关构建与包体；50项浏览器仅收集；真实 API/后端路径未运行 |

## 本轮完成

- 点击伙伴可在原有互动抽屉原位聊天；无需路由到完整聊天页。图片检查/拖放不额外读取聊天。
- 主页与轻量抽屉共用账号内存会话，单次历史读取和单发送；不可变快照；无轮询、持久化或自动重发。
- 关闭最后一个消费者、换账号后 abort 客户端传输并隔离晚到响应；未发送草稿同账号保留，换账号清空。
- 不确定发送要求显式刷新历史后再由用户决定发送；错误不伪装成伙伴台词，停止接收不声称服务端取消。
- 提案只预览 Home 当前观察租约已取得的 PENDING 内容；轻量入口不调用 proposal/contract 接口，接受/忽略/敲打保留原入口与契约。
- 修复 review 发现的平板 CSS 选择器和延迟历史焦点问题；R12 验收目标修正为需求指定的 V3。

## 验证证据

- Node **22.23.3**：全前端 **157/157**（本轮新增27会话/传输 + 5预览/接线测试）
- lint 通过；Companion 开启与关闭各一组 production build + bundle budget 通过；最大 chunk **390,035 bytes**（限制512,000）
- 独立 reviewer 对同一代码 SHA 重跑上述测试/静态验证通过，无未解决阻断性代码问题
- 新增 **11** 个 R12 浏览器场景；组合配置 **50** 项已收集，浏览器断言、截图和真实后端 API 回归 **未运行**
- 原本地验证增量 `5177cf4..0648c55`（已发布等价增量 `7a68d420..7135e120`） 的 diff whitespace 检查通过；整条主线原样需求附件第3行 Markdown 双空格硬换行的已知例外继续保留

## 阻塞 / 限制

- GitHub 写入阻塞已解除：用户安装 GitHub App 后，2026-10-02 同一 connector 的真实 tree/commit/branch 写入成功。五个 R11/R12 提交已发布至独立长期分支，逐一核对 tree 与父链；未创建 PR、merge 或部署。映射与恢复见 `docs/work/PUBLICATION-2026-10-02.md`。
- GitHub Actions 当前仅响应 main push / pull_request；本分支发布不触发该 CI，不将无运行记为通过。
- R11 尝试中缺失 Playwright Chromium；官方安装器正常重试均返回无效 ZIP、Download failure code=1。本轮没有新环境证据，未重复下载或修改安全/代理/沙箱。
- 当前证据只证明官方运行时安装失败，不将历史 socket policy / ERR_BLOCKED_BY_CLIENT 直接套成本轮原因。
- R11 浏览器验收和 R12 **V3** 真实 Home/history/SSE / 后端故事线验收仍待完成，不得标 DONE。

## 下一步

1. 浏览器环境确有变化且官方运行时可用后，运行 `e2e/config/companion-quick-chat.config.js`（50项，包含R11相关场景），检查桌面/320px截图、键盘/触摸、关闭/重开、账号切换、请求计数与焦点。
2. 继续 R10 feeding、feature-off、真实后端 `e2e/companion.spec.js` 关键回归，完成 R12 V3 的实际 API 路径证据。环境没变时不反复安装或绕过失败。
3. 若仍只有验证环境阻塞，可按用户授权推进独立的下一主线增量，但先读相应需求与实际代码；保留 R11/R12 待验收项，不把未运行测试写成通过。后续为 R03 当前实现核实 → R13 → R16 → R14；R15 独立高风险支线。
4. 从已发布 `nexus/mainline-r11` 继续工作；活动本地分支已对齐远端父链。后续发布只快进该分支，核对远端 HEAD / tree / 可用 CI；不得自行 PR、merge/deploy。
5. 恢复只需仓库 `DECISIONS.md`、`STATE.md`、根需求清单、`docs/work/R11.md`、`docs/work/R12.md` 与 `docs/work/PUBLICATION-2026-10-02.md`。每个稳定检查点配完整 Git bundle、恢复 manifest 与证据日志，保存到用户 Library，避免依赖临时磁盘。

## 最近更新

| 时间（UTC） | 更新者 | 内容 |
|---|---|---|
| 2026-10-02 | Nexus | 核实附件与 main，建立独立长期分支，保留其他分支 |
| 2026-10-02 13:49 | Nexus | R11：125/125 及独立 review 通过；浏览器官方安装失败，保存稳定恢复包 |
| 2026-10-02 14:47 | Nexus | R12原本地代码0648c55独立review通过；157/157与双模式静态验证通过；明确V3验收未完成，准备持久检查点 |
| 2026-10-02 15:20 | Nexus | GitHub App 写入实测恢复；R11/R12 五提交逐 tree 校验发布；保留原本地备份并对齐远端，V3 状态不变 |
