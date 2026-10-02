# STATE · LiPictureCloud 2.0 主线

## 概览

- 仓库：https://github.com/53214mm/li_picture_cloud
- 唯一工作分支：`nexus/mainline-r11`
- 当前 Rxx：R12 提案 / 聊天实体化；R11 独立保留待浏览器验收
- 当前阶段：R12 首个实现增量等待独立 review；R11 已审代码不退回，浏览器验收受阻
- 上一已完成需求：R10（用户确认，main `d49fd06`）
- 基线：`d49fd06645995e6f2c0a67a6f230ae6704afb2e4`
- 最近有效代码提交 / 最终验证对象：`36cb536dd7517c684200f277c73959ab1c47eb2d`
- 首版代码提交：`c9d3584e6ab6998861fcfab5b564b5f35e504823`
- 状态证据提交：在上述代码提交之后，使用 `git log -- STATE.md` 定位；本文件不自引用自己的 hash。
- 全部长期边界：先读 `DECISIONS.md`；根需求清单历史进度过时，不得退回 R03。

## Phase 看板

| Phase | 目标 | 状态 | 证据 |
|---|---|---|---|
| R11 P1 | 核实语义与设计边界 | 已通过 | DECISIONS D-04–D-06；docs/work/R11.md |
| R11 P2 | Mapper、表现和面板实现 | 已通过代码 review | docs/work/R11.md；36cb536 |
| R11 P3 | 自动测试、独立 review、浏览器验收 | 部分通过 / 浏览器受阻 | Node 22 全前端 125/125；lint；on/off build/budget；独立复核通过；未宣称 DONE |
| R12 P1 | 核实轻量聊天与提案边界 | 已通过 | DECISIONS D-07–D-09；docs/work/R12.md |
| R12 P2 | 共享会话、原位聊天与既有提案预览 | 待 review | 当前增量 |
| R12 P3 | 会话/传输/接线测试与验收 | 进行中 | 全前端157/157；真实浏览器未运行 |

## 阻塞 / 限制

- GitHub connector 写入已返回 `403 Resource not accessible by integration`；权限未变化，不尝试其他写入路径。本地工作继续，远端未发布。
- 本轮 Playwright Chromium 可执行文件缺失：首项在 launch 前失败，3 项未运行。官方安装器五次正常下载都返回无效 ZIP，最终失败；未修改安全/代理/沙箱。真实浏览器交互/布局和截图尚未验收，未执行全量后端 E2E。
- 历史 socket policy / ERR_BLOCKED_BY_CLIENT 仍是背景信息，本轮未再次证明它们，不能把下载失败直接归因于权限。
- 输入需求原文第 3 行保留 Markdown 两个空格的硬换行，完整基线 diff-check 因此报告1项；产品源码范围通过。

## 下一步

1. 首个稳定增量已结束，不因当前浏览器阻塞把 R11 标为 DONE。
2. 环境支持正常官方浏览器运行后，复跑 `e2e/config/companion-disposition.config.js`（29 项），R10 feeding 与 feature-off，检查桌面与 320px 的实际截图、键盘披露和暂停。若运行环境未变化，不反复安装或换路径规避已知失败。
3. 本轮代码 review 已通过；可继续无外部副作用的下一需求准备，但 R12 实现前先复核 R11 未验收项与依赖，不能混称完成。主线后续顺序仍为 R12 → R03 核实 → R13 → R16 → R14；R15 独立高风险支线。
4. 远端未推送、未 PR、未 merge、未部署。权限有真实变化后才重新检查远端发布，必须验证确切 SHA。
5. 恢复只需本仓库的需求、DECISIONS、STATE 和 docs/work/R11.md。首个稳定分支将包含在完整 Git bundle 及恢复 manifest 中，另外保存到用户 Library，避免仅依赖临时工作区。

## 最近更新

| 时间（UTC） | 更新者 | 内容 |
|---|---|---|
| 2026-10-02 | Nexus | 核实附件与 main，建立独立长期分支；开始 R11，保留其他分支。 |
| 2026-10-02 13:49 UTC | Nexus | 125/125 与双模式静态验证通过、独立 review 通过，修复 caption 语义；浏览器官方下载失败，保留验收阻塞，准备持久化恢复包。 |
| 2026-10-02 14:43 UTC | Nexus | 推进独立 R12 增量，保留 R11 待验收；共享轻量聊天、晚到隔离与被动提案预览，待独立 review 与恢复包。 |
