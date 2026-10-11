# 四项 review 修复发布记录（2026-10-10）

唯一目标：`53214mm/li_picture_cloud` 的 `nexus/mainline-r11`。未开 PR、合并 main、部署、重启日常服务或修改原工作区。

| 身份 | SHA |
|---|---|
| 发布前核实的父提交 | `d8a6e1a57afd0f8b995bdc700807099f22512b19` |
| 本地稳定源提交 | `da300236845873b5117176f91e5b4d3dc7a15ed0` |
| 已发布有效代码提交 | `6f72e456f9ff0fc1d34ba36e0b77308f63cb9016` |
| 两者相同的完整 tree | `e1ad5a4bae9074bd4da99196f5285936f03d6efa` |

19 文件，+640/−53；四项修复、回归和真实状态文档。通过现有 GitHub connector 上传已提交 blob，API tree 与本地完整 tree 精确比较通过；提交以基线为单一父节点，更新 ref 前重读仍为基线，`expected_sha` 加 `force=false` 快进。更新后回读 HEAD、tree、父节点，全部匹配。API 提交身份不同造成 commit SHA 不同，不改变源码。原工作区及其未跟踪文件保留，本地隔离 clone 的 origin 指向原工作区，禁止直接 push origin。

09:51 UTC 最终 offline verify：915 项，911 通过、0 失败/错误、4 跳过，package/JaCoCo 通过。受影响 8 套件 64/64；隔离 H2 清空/经历持久回归各 4/4。Node22 前端 431/431、严格 lint、Companion 开关两组 build/budget 通过，Chromium daily fixture 26/26。独立 Standards review 无阻断；Spec review 1 项首批候选召回缺口已复现、修复并复核，无剩余阻断。reviewer 未独立执行测试。

真实供应商/收费模型、MySQL/pgvector、生产与人工人格验收未执行。4 跳过为 3 Redis opt-in 与 1 真实视觉 smoke。现有独立 HTTP runner 明确为 POSIX-only，Windows 本轮未重跑；不把浏览器网络 fixture 当本轮真实 HTTP 验收。原版立绘、普通单次聊天、主动 DEMO 保持。

恢复以远端此分支最新 HEAD 为准；本记录与 STATE 后续仅文档提交，不再修改已验源码。隔离目录：`C:\Users\MECHREVO\Documents\Codex\2026-10-10\task\li-picture-cloud-nexus`。复跑命令与失败探索见 `LINGYE-REVIEW-FIXES-2026-10-10.md`；证据不入库：`evidence-nexus/backend-verify-final.log`、`affected-final.log` 和 `nexus-review/`，Surefire/JaCoCo 产物在 target。
