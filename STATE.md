# STATE · LiPictureCloud 2.0 主线

## 概览

- 仓库：https://github.com/53214mm/li_picture_cloud
- 唯一工作分支：`nexus/mainline-r11`（长期主线，不随 Rxx 改名）
- 当前 Rxx：R13 响应式与表现降级；首个实现增量已独立审核
- 验收状态：R11 浏览器、R12 V3真实API、R13浏览器仍待完成；不标 DONE
- 上一完整确认完成需求：R10（用户确认，main `d49fd06`）
- Nexus主线共同基线：`d49fd06645995e6f2c0a67a6f230ae6704afb2e4`
- R13 本轮起点：已发布 `bb4e6f501635230e8b515fde18e718026f91d37e`
- 最近有效代码 / 已核对远端对象：`2e05d6dd328da59060f0cd01ba373527d96d32f2`（tree `d31e856b1728f7d5217a446c8e135d7284d7f3de`；等价于已审源 `ca743a9`）
- R13实现提交：`5d707515202d96d81f410abe4f1a1cf24253775f`（等价于已审源 `b100703`）；上一条只补入既有触摸/导航验收集
- R12已审发布代码：`7135e120b8b46e0cc95bb81ddabb49f772446ef7`；R11为 `6a96f92a6ce299a3eabeecf48f68ff0b4de82bb1`
- 状态证据提交在上述代码之后；用 `git log -- STATE.md` 定位，不自引用自己的hash
- 开工先读 DECISIONS；原附件进度表过时，不得退回已实现需求。发布与源SHA对应见 `docs/work/PUBLICATION-2026-10-02.md`

## Phase 看板

| Phase | 目标 | 状态 | 证据 |
|---|---|---|---|
| R11 P1/P2 | 情绪/关系/性格投影与实现 | 已通过代码review | docs/work/R11.md；6a96f92 |
| R11 P3 | 前端与浏览器验收 | 自动证据通过 / 浏览器待完成 | 前轮125/125；本轮全回归包含；未DONE |
| R12 P1/P2 | 共享轻量聊天、既有提案预览 | 已通过代码review | docs/work/R12.md；7135e12 |
| R12 P3 | V3完整验收 | 前端证据通过 / 实际API路径待完成 | 前轮157/157；浏览器/后端故事线未运行 |
| R13 P1/P2 | 渲染预算、手机紧凑布局 | 已通过独立代码review | docs/work/R13.md；5d70751 / 2e05d6d |
| R13 P3 | V2验收 | 自动/组件证据通过 / 浏览器待执行 | 173/173；lint；双模式build/budget；64项仅收集 |

## 本轮完成

- 已核实R03方案B、身份/开关CTA与响应式代码存在，没有阻挡R13的缺失实现；未把历史smoke冒充新的最终验收。
- 小于768px、明确Save-Data、2G/slow-2g时采用静态预算；未知宽度保守静态，不推断领域状态。
- 通过已有paused暂停同一个SpritePlayer，保留实例、提案一次提示额度、用户暂停及原可见性门；没有新请求/计时器。
- 手机小屋不再固定680px/绝对布局；自然流Grid与96px可点击伙伴入口，保留聊天、喂图、照片留位。Shell已有44px入口/上传/底部导航未改。
- 根据review统一分数宽度断点，并把既有8项小屋触摸/键盘/空态/布局回归纳入R13验收集。

## 验证证据

- Node **22.23.3**：全前端 **173/173**（新增12策略/监听/真实Vue挂卸载 + 4接线/动画额度测试）
- lint通过；Companion开启/关闭production build及两组bundle budget通过；最大chunk **390,035bytes**（限制512,000）
- 独立review复跑以上检查、64项收集与diff-check通过，无未解决阻断性代码发现
- 补充实际编译Body/SpritePlayer的Vue自定义renderer验证：冷启动图集元素/时钟门控、重复断点同实例/提示额度、用户暂停与其他门组合、SSR与卸载清理均通过；不等于真实浏览器或图片网络测试
- 新增6项R13浏览器测试，组合 **64项 / 8文件** 已收集；浏览器断言、截图、真实触摸/键盘/后端API仍未运行
- 本轮增量diff-check通过；原附件第3行Markdown双空格硬换行的历史例外保留

## 发布与恢复

- GitHub写入权限已实际恢复（DECISIONS D-10）；只使用已有connector发布专用分支，保留每个tree和提交顺序，绝不强推/merge/deploy或创建PR。
- 先创建并回读核对远端code对象，再发布本STATE对应的docs-only检查点；只快进原长期分支。发布后活动本地分支只在完整tree相同且工作区干净时对齐已发布父链。
- 原R12本地历史保留 `nexus/checkpoint-r12-local-dda1650`；本轮源状态提交保留 `nexus/checkpoint-r13-local-state`，不要从这些源备份继续开发或推回远端。
- 恢复首选远端 `nexus/mainline-r11`；读取本文件、DECISIONS、根需求清单、R11/R12/R13工作记录、PUBLICATION映射即可续接。
- CI只监听main push/pull_request，本分支无CI运行不等于CI通过。

## 阻塞 / 限制

- 官方Playwright运行时安装先前返回无效ZIP且失败；没有新环境证据，本轮未重复安装，也未修改安全/代理/沙箱或绕换通道。
- 历史socket policy并未在本轮重新验证，不把它直接当作安装失败的原因。
- R11/R13浏览器验收与R12 V3真实Home/history/SSE/后端故事线仍未完成。
- R03最终初访理解/已有样片授权属于发布前核实事项，本轮不宣称通过；不阻塞独立R13代码。

## 下一步

1. 运行环境确有变化、官方浏览器可用后，跑 `e2e/config/companion-render-budget.config.js`（64项），检查冷启动图集请求、断点往返、320px/平板截图、键盘/触摸/上传和照片入口。
2. 继续R10 feeding、feature-off及真实后端 `e2e/companion.spec.js`，补齐R12 V3证据。环境没变不反复安装。
3. 若仍只是验收环境受阻，可按授权推进独立R16文案一致性收口或R14可执行回归准备；先读实际代码与对应需求，保留全部pending项。R15继续独立高风险支线。
4. 每轮从远端最新专用分支开始；不从源备份恢复旧父链，不改其他用户分支。发版前核对远端HEAD/tree与可用CI，不自行PR/merge/deploy。

## 最近更新

| 时间（UTC） | 更新者 | 内容 |
|---|---|---|
| 2026-10-02 13:49 | Nexus | R11代码review通过，浏览器安装失败，保存恢复包 |
| 2026-10-02 14:47 | Nexus | R12前端157/157与review通过，V3保留pending |
| 2026-10-02 15:20 | Nexus | GitHub写入恢复，R11/R12逐tree发布并对齐父链 |
| 2026-10-02 15:50 | Nexus | R13 173/173与独立组件/静态验证通过；64项仅收集，准备已验证tree的专用分支检查点 |
