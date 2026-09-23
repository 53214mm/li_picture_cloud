# R05 — 原创伙伴身份与实体化：第一阶段报告

日期：2026-09-23。状态：**R05 第一阶段已完成决策；第二阶段实施计划已编写，尚未开始正式产品实现。**

## 最新修订与跨任务交接

本节是最新人工决策基线，取代初轮候选建议和旧原型状态文案：

1. **第一 Species 正式选择「绫页 / 纸翼蛾族」**，当前名称统一使用绫页，不重新命名。Egg、Juvenile、Adult 三种视觉方向全部接受；先前对 Juvenile 的返工要求已撤销，不再阻塞第二阶段。未来可优化生产细节，但不重新发散角色方向。
2. **首版身体采用 Sprite + 静态 / 表情差分 Conversation Portrait**。Web Adult 保持正常比例；Shell 使用适合小尺寸的简化 Sprite，Home 使用全身 Sprite 与最小 Idle，Conversation 使用正常比例半身。首版不引入 Live2D、Spine 或 Pixi；未来可分别升级 Portrait / Home 的渲染方式，Instance 不变。
3. **Q 版仅保留为未来 Desktop Pet / Codex 式桌宠参考**，不作为 Juvenile、Web Adult 或当前 Home 主体。同一 Instance 的不同 Representation 不产生新伙伴。
4. **旧光团直接获得绫页身体**。原 companionId、owner、lifeExperience、level、lifeStage、Traits、Mood、Relationship、Memory、Feed 历史、Proposal / Contract 等已有数据全部保留；不重新创建、孵化、认领、重算 Feed 或要求身份确认。
5. **保留现有 LIGHT / SEEDLING / COMPANION**，不改数据库 Enum，不把 LIGHT 自动映射为 Egg。第二阶段只提供单实例视觉兼容层；真正新 Species 的 Egg → Hatch → Juvenile → Adult 属于后续领域设计。
6. **Owned Companion 与 Displayed Companion 分离**：未来每个已拥有 Instance 都保留独立成长、记忆、关系与生活状态；Displayed 只决定 Shell / 首页 / 全局 Presence 当前展示谁。未展示的伙伴仍存在，未来 Home 可体现多个 Instance 的存在状态。本轮只记录架构方向，不新增选择器、字段、接口或多实例迁移。
7. B 汐砚、C 陶眠继续冻结为历史探索档案，保留图像与文档，不迭代。现有 A 概念图也仍是 Exploratory Concept Art，不是已验收的 Production Asset。

用户授权本轮只修改 / 新增 R05 文档、检查后提交推送；完成后停止。请先看[第二阶段实施计划](R05-第二阶段实施计划.md)，不得自动执行资产生产或产品接入。

本报告以最新人工决策为准，初轮需求来源包括 `work-log/0923/R05 — Companion Identity & Embodiment.md`。9 月 21 日清单中的「全局 Avatar」「先用 Sprite、不做 Live2D」是早期范围；初轮已经扩展为身份与身体设计并完成技术调查，本次正式选定首版路线。旧清单中 R03/R04 的状态不代表今天的代码状态。

## 1. 阅读入口

| 产物 | 用途 |
| --- | --- |
| [世界观骨架](R05-世界观骨架.md) | 她从哪里来，如何与用户相识，世界的审美和边界 |
| [角色体系设计](R05-角色体系设计.md) | Species / Instance、身份连续性、既有模型兼容、资产扩展 |
| [第一位伙伴概念探索](R05-第一位伙伴概念探索.md) | 绫页三阶段已接受；B/C 历史 Art Brief 与冻结记录 |
| [身体技术路线评估](R05-身体技术路线评估.md) | 已选首版路线、易懂解释、18 维度历史调查和未来升级条件 |
| [第二阶段实施计划](R05-第二阶段实施计划.md) | 资产、轻量表示层、三个入口、Idle、兼容与验证；尚未执行 |
| [可打开的设计原型](design-exploration/r05-companion/index.html) | 前轮布局快照；旧“暂留 / 待修 / 路线待定”文案已由本报告取代 |
| [原型说明与验证](design-exploration/r05-companion/README.md) | 打开方法、全部文件、检查证据和限制 |
| [图像生成 Prompts](design-exploration/r05-companion/prompts.md) | 内置 imagegen 输入、参考图与正式资产的边界 |

当前角色名和种族方向已确定为绫页 / 纸翼蛾族；未来 IP / 商标 / 相似性检查可独立安排，不在本轮重命名。原型中的台词、图片框均是示例，不读取用户图片或调用业务 API。本轮不改 HTML / JS / CSS，也不生成图片；原型 README 已说明新旧状态的优先级。

## 2. 仓库真实状态

核查目录：`G:\IDEA_NEW\product\picture-cloud\li-picture-cloud`。外部资料目录：`G:\IDEA_NEW\product\picture-cloud\work-log`。

本次收口已执行 `git status --short --branch`、`git rev-parse HEAD`、`git log --oneline -8`、`git branch -vv`。以下 R01～R04 的祖先关系已在初轮核对；本轮补充 R05 归档基线：

| 项目 | 核查结果 |
| --- | --- |
| 当前分支 | `feat/r05-companion-identity`；未切分支、未创建 worktree |
| 本次文档修订开始时 HEAD | `3d164b1dbce731bae1d8a5088f82e5a868f70b58`，前轮 R05 探索归档 |
| 本地 main 基线 | `c1670d8`；当前 R05 分支在其后，不将本地引用视为远端实时状态 |
| R01 | `18232d8 feat(design): add LiPictureCloud 2.0 visual foundation`；另有 `e3a7d5f` 标题色修复 |
| R02 | `0192c22 feat(shell): implement authenticated app shell`、`9ddb11c` 导航细化 |
| R03 | `c951ec6 feat(landing): implement R03 direction B` |
| R04 | `c1670d8 feat(gallery): refine original image picking experience` |
| R05 初轮 | `3d164b1 docs(r05): archive companion identity design exploration`，14 个文档 / 原型 / 图像文件 |
| 已存在的 dirty 内容 | 9 个旧 docs 路径已暂存删除，对应文件在 `docs/轮次/` 未跟踪；`docs/codex-review-learning/` 未跟踪；37 个 Companion Java 文件修改，diff 为补充注释；均非本轮产出 |

另外存在 E2E 文件修改及未跟踪的 pnpm 文件，均不属于本轮。本轮保护无关文件与原有暂存状态，不 reset、stash、clean、覆盖、删除或 stage 无关改动；提交仅包含六份既有 R05 Markdown 和新增实施计划。提交存在不等于产品部署。

### 前端

- Vue 3、Vite 7、Pinia、Vue Router；`package.json` 声明 Node 22，当前机器 Node 24.19.0。尚无 Pixi、Live2D 或 Spine 依赖。原型不向正式包增加依赖。
- [CompanionView.vue](../li-picture-cloud-frontend/src/views/CompanionView.vue) 仍是光团 Hero + Stats / Feed / Mood / Relationship / Chat / Proposal / Memory / Story / Emoji / Fusion / Growth 面板。页面内部 `ref` 保存 home 与 Feed 状态；没有 Companion 全局 store/composable，当前 Pinia store 是 user。
- [CompanionPresenceSlot.vue](../li-picture-cloud-frontend/src/components/shell/CompanionPresenceSlot.vue) 是 `/companion` 静态入口，40×44 标记容器、28px 图标，不请求生命状态，不是已有 Avatar。
- [AppLayout.vue](../li-picture-cloud-frontend/src/layouts/AppLayout.vue) 桌面侧栏 224px，平板 88px，手机底部伙伴入口；沿用 feature flag 与旧弹层 inert 隔离。生产开关为 `VITE_COMPANION_ENABLED=true`，开发环境默认启用；本轮未检查部署环境配置。
- [router/index.js](../li-picture-cloud-frontend/src/router/index.js) 条件挂载 `/companion`，功能关闭不注册该业务路由；不是仅仅把可见角色藏起来。
- [api/companion.js](../li-picture-cloud-frontend/src/api/companion.js) 已有主页、唤醒、喂养、记忆操作、历史、契约、提案接口。聊天在 [CompanionChatPanel.vue](../li-picture-cloud-frontend/src/components/companion/CompanionChatPanel.vue) 中调用流式工具；尚无 Portrait。
- 当前 Feed UI 从用户自己的私有空间选一张图后显式提交。后端按图片可见权限再次校验，不能把前端的私有空间筛选误写为所有后端 Feed 都只允许私有空间。

### 后端

| 概念 | 当前事实与证据 | R05 处理 |
| --- | --- | --- |
| 唯一身份 | [Companion.java](../src/main/java/com/li/lipicturecloud/domain/companion/Companion.java) 有 id / ownerId / revision | 未来 Instance 延续已有 id，不重建关系 |
| 成长 | `lifeExperience`、`level`、`lifeStage`、`balanceVersion`、`skillExperience` 已存在 | 不再新增平行的 exp/growth 账本 |
| 阶段 | [CompanionBalance.java](../src/main/java/com/li/lipicturecloud/domain/companion/CompanionBalance.java)：1–2 级 LIGHT，3–6 级 SEEDLING，7+ COMPANION | 不能直接声称已有 Egg / 孵化 |
| Traits | curiosity / enthusiasm / playfulness / empathy / creativity | 官方性格底色不覆盖已成长的五轴数值 |
| Mood | energy / joy / loneliness / inspiration / irritation，独立实体，有惰性衰减 | 展示消费现有事实，不新增情绪公式 |
| Relationship | familiarity / trust / closeness / tacit / recentFeedback，关联 companionId / subjectId | 不改为单一好感度 |
| Memory | [CompanionMemory.java](../src/main/java/com/li/lipicturecloud/domain/companion/CompanionMemory.java) 有来源图片、成长记录、置信度、原文、修正文、状态；VISUAL / DEMO 来源 | 三类未来语义不是现成 enum，不改 Memory Kernel |
| Feed | LifeService / FeedingCoordinator 已有权限、幂等、预占与结算、revision、视觉 / 元数据 / 显式降级 | Upload ≠ Feed；沿用现有入口与安全边界 |
| 对话 | ChatContextAssembler 消费 Traits / Mood / Relationship 与已确认可用记忆；DEMO / MODEL 路径存在 | 本轮不改 System Prompt，也不注入虚构经历 |
| Proposal | 自主契约、机会源、守门、接受 / 忽略 / 敲打已存在 | 表现能力不授予主动行为权限 |
| Story | [StoryDraftService.java](../src/main/java/com/li/lipicturecloud/application/airuntime/StoryDraftService.java) 是图片故事创作草稿、确认与保存 | 不等同于已经实现自主生活日记 |
| Species / 多实例 | [life-core changelog](../src/main/resources/db/changelog/changes/2026-08-11-companion-life-core.xml) 是 `UNIQUE(userId)`，没有 speciesId | 未来独立设计多实例约束与 Displayed 关系，第二阶段不迁移 |

当前 `CompanionStage` 与世界观视觉阶段分开处理：保留真实状态，首版只替换身体，不以图片反推业务年龄。完整生命周期迁移另立领域任务，不再作为建立身体表示层的前置阻塞。

## 3. 第一阶段完成状态

- [x] 世界观骨架与现实图片分享边界
- [x] Species / Instance 概念与身份连续性
- [x] 第一 Species 选定：绫页 / 纸翼蛾族
- [x] 绫页角色方向与当前名称
- [x] Egg 视觉方向接受
- [x] Juvenile 视觉方向接受
- [x] Adult 正常比例视觉方向接受
- [x] Representation 分层与 Q 版桌宠定位
- [x] 首版 Sprite + 静态 / 表情差分 Portrait
- [x] 旧光团直接获得身体的升级方式
- [x] 未来 Owned / Displayed 多伙伴展示关系
- [x] Runtime 调研与隔离原型
- [ ] 正式生产角色资产
- [ ] 正式 Vue 身体组件
- [ ] Sprite 动画与性能实测
- [ ] R06 状态协议
- [ ] 多 Species 数据迁移

“完成”指设计决策已收口，概念图并未因此成为生产资产。实施单元 A～H 见[第二阶段实施计划](R05-第二阶段实施计划.md)。

## 4. 二十个核心问题的回答

| 问题 | 第一阶段回答 |
| --- | --- |
| 1. Companion 是什么？ | 官方创造身份模板、用户通过相处养成的唯一图像伙伴；长期经历属于实例。 |
| 2. Species 与 Instance？ | 前者管可复用身份与美术基因，后者管用户命名、现有成长、关系和经历。 |
| 3. 为什么同种仅一位？ | 让每次相遇具有不可替代性，避免复制角色和合成机制破坏长期关系。 |
| 4. 为什么图片不生成身体？ | 身份可读性、跨动作一致性和可控制作需要稳定美术资产；动态生图不能成为常规身体流水线。 |
| 5. 图片如何成长与记忆？ | 用户显式 Feed 后，成长结算与来源化记忆候选是不同结果；观察不自动成为用户事实。 |
| 6. 两个世界的关系？ | 用户通过自己打开的「窗」分享图片；伙伴生活在雾屿的一间住处，不窥视整个图库。 |
| 7. 第一位幻想种族？ | 正式选择绫页 / 纸翼蛾族；潮壳螺与陶鳞蜥冻结留档。 |
| 8. 三个方向？ | A 绫页：整理与求证；B 汐砚：倾听与分寸；C 陶眠：动手与质疑。 |
| 9. 成长连续性？ | 固定 id；蛋、未成熟、成熟重复三项视觉基因；身体成熟不重置关系。 |
| 10. Sprite 与 Portrait？ | A 的 Web Adult 使用正常比例全身与正常比例半身；Q 版独立保留作未来桌宠参考，不作为当前 Web 成熟体。 |
| 11. Shell？ | 现有 40×44 标记容器内使用简化静态 Sprite，保留路由与文字；224px / 88px 侧栏和手机入口行为不变。 |
| 12. 三路线适用性？ | Sprite 适合有限离散动作；Live2D 擅长近景表演但制作与许可需解决；Spine 适合持续全身动作与附件复用。 |
| 13. 首版路线？ | 已选择 Sprite + 静态 / 表情差分 Portrait；未来 Portrait 可升级 Live2D，Home 可按需求升级 Spine。 |
| 14. 最少资产？ | 计划以 Adult 全身静态、Shell 简化静态、中性半身和最小 Idle 为首发；差分可选，三阶段量产不全量前置。 |
| 15. 第 2/5/10 个角色？ | Owned 与 Displayed 分离，未展示实例继续存在；资产按身份 / 版本 / 表现组织，多实例业务需独立迁移。 |
| 16. 与 R06～09 边界？ | R05 定身份 / 资产规格；R06 映射状态；R07 调度动画；R08 交互；R09 房间布局与生活空间。 |
| 17. 本轮不做？ | 数据库/API/生产 Vue、孵化、多角色、EXP/Feed/Memory 重写、自主旅行、事件、商店、战斗、货币或任务。 |
| 18. 最大技术风险？ | 把视觉替换误写成身份 / 生命周期迁移，以及资产加载、暂停和资源释放不正确。首版不接商业 runtime。 |
| 19. 最大美术风险？ | 概念方向已接受，但生产资产仍需保证小尺寸辨识、透明边缘、锚点和帧间一致性；不能无限增加动作。 |
| 20. 最大产品风险？ | 用户把她当通用助手或操作负担；用虚构记忆与情绪催促喂养，会破坏长期信任。 |

## 5. 已撤销的旧结论与实施前准备

“A 暂留 / 技术待选”已改为正式选择；A 的“Juvenile 必须重画 / 未通过”结论已被人工决策撤销；“旧伙伴认领 Species / 身份确认”已删除，直接更换表现且保留全部数据。

剩余工作是生产准备与计划审阅：确认 Phase A 资产画幅、锚点、预算和首发视觉兼容方案，实际制作后验收透明边缘及帧间一致性。帧数由最小 Idle PoC 验证；表情状态集留给 R06 / R11。它们不重新打开角色或技术选型，也不要求先完成数据库生命周期迁移。

## 6. 验证与停止点

初轮原型的浏览器验证记录保留在 README。本轮只做文档一致性、Markdown 结构、本地链接、diff 范围和工作区保护检查，不重复运行原型 UI 或产品 Build / Lint / 测试，不把计划中的验证写成已经通过。

**第一阶段决策已收口。提交推送文档后停止，等待用户审阅第二阶段计划；没有正式角色生产、Vue 接入、数据库修改，也没有自动执行第二阶段或 R06～R12。**
