# R05 — 原创伙伴身份与实体化：第一阶段报告

日期：2026-09-23。状态：**A 绫页暂定保留，B / C 冻结；用户已要求提交推送当前探索稿，最终方向待讨论，未进入实现阶段。**

## 最新修订与跨任务交接

用户在第一阶段预览后明确要求：

1. Web 成熟体改用 A 设定页左侧、正常比例的全身立绘，不再使用右下角 Q 版。Home、Shell 及小尺寸检视同步使用该成熟体；Conversation 保留同角色的正常比例半身。
2. 原 Q 版原图保留，并在原型中单独列为「Codex 式桌宠参考」；本轮不制作、安装或运行桌宠，也不将它标为 Juvenile。
3. A 绫页 · 纸翼蛾族暂定保留；B 汐砚、C 陶眠冻结，文档与图像留档，暂停原型切换和进一步迭代。
4. 技术路线与最终方向由用户继续在另一个任务讨论；本次只修订既有设计原型和说明，不重画角色或改产品业务。
5. 未成熟体的对话头像已改为自身形象的头肩近景，与成熟体保持相近构图；Home / Shell 仍使用未成熟体全身。
6. 用户随后明确要求「提交推送」，授权归档当前设计文档、原型与图片。此授权不代表最终角色、技术路线或第二阶段实施已确认。

后续任务优先读取本节，再阅读技术评估与旧概念档案。A 暂留不等于 IP 终稿、Runtime 已选定、幼体已通过，或第二阶段已获授权。

本报告以本轮附加任务说明和 `work-log/0923/R05 — Companion Identity & Embodiment.md` 为需求依据。9 月 21 日清单中的「全局 Avatar」「先用 Sprite、不做 Live2D」是早期范围，本轮已经扩展为身份与身体设计，并要求重新评估技术。旧清单中 R03/R04 的状态不代表今天的代码状态。

## 1. 阅读入口

| 产物 | 用途 |
| --- | --- |
| [世界观骨架](R05-世界观骨架.md) | 她从哪里来，如何与用户相识，世界的审美和边界 |
| [角色体系设计](R05-角色体系设计.md) | Species / Instance、身份连续性、既有模型兼容、资产扩展 |
| [第一位伙伴概念探索](R05-第一位伙伴概念探索.md) | 三个 Art Brief、成长关系、选择依据、概念图偏差 |
| [身体技术路线评估](R05-身体技术路线评估.md) | 官方资料、授权、18 维度比较、条件化推荐 |
| [可打开的设计原型](design-exploration/r05-companion/index.html) | A 暂留、B/C 冻结；Shell、Home、Conversation；小尺寸与降级观察 |
| [原型说明与验证](design-exploration/r05-companion/README.md) | 打开方法、全部文件、检查证据和限制 |
| [图像生成 Prompts](design-exploration/r05-companion/prompts.md) | 内置 imagegen 输入、参考图与正式资产的边界 |

角色名与具体设定仍是工作稿；当前人工指示为暂留 A、冻结 B/C、修正成熟体比例。原型中的人物、台词、图片框均是示例，不读取用户图片或调用业务 API。

## 2. 仓库真实状态

核查目录：`G:\IDEA_NEW\product\picture-cloud\li-picture-cloud`。外部资料目录：`G:\IDEA_NEW\product\picture-cloud\work-log`。

已执行 `git status --short --branch`、`git rev-parse HEAD`、`git log --oneline -10`、`git branch -vv`，并检查以下提交都是 HEAD 的祖先：

| 项目 | 核查结果 |
| --- | --- |
| 当前分支 | `feat/r05-companion-identity`；未切分支、未创建 worktree |
| HEAD | `c1670d8459d88f891db9e73819d338ca3a21aebc` |
| 本地 main / 本地 origin/main 引用 | 均等于 HEAD；本轮没有 fetch，不能据此保证远端此刻没有新提交 |
| R01 | `18232d8 feat(design): add LiPictureCloud 2.0 visual foundation`；另有 `e3a7d5f` 标题色修复 |
| R02 | `0192c22 feat(shell): implement authenticated app shell`、`9ddb11c` 导航细化 |
| R03 | `c951ec6 feat(landing): implement R03 direction B` |
| R04 | `c1670d8 feat(gallery): refine original image picking experience` |
| 已存在的 dirty 内容 | 9 个旧 docs 路径已暂存删除，对应文件在 `docs/轮次/` 未跟踪；`docs/codex-review-learning/` 未跟踪；37 个 Companion Java 文件修改，diff 为补充注释；均非本轮产出 |

这些事实说明 R01～R04 的实现已在当前基线上；不把提交存在等同于人工验收或生产部署。设计检查时未 reset、stash、clean、stage、commit、push，也不推断已有修改的作者。后续获准提交仅包含 R05 产物，已有修改和会话期间观察到的额外 E2E 修改仍独立保留，详见验证记录。

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
| Species / 多实例 | [life-core changelog](../src/main/resources/db/changelog/changes/2026-08-11-companion-life-core.xml) 是 `UNIQUE(userId)`，没有 speciesId | `(userId, speciesId)` 是未来约束，非本轮迁移 |

当前源代码中的 `CompanionStage` 文案和既有世界观不同，需要后续单独处理兼容。这是实现前的真实依赖，不能靠一层图片替换掩盖。

## 3. 本轮设计工作单元

1. **事实与兼容边界**：核对基线、开关、状态所有权、当前唯一约束，保护既有修改。
2. **身份与世界**：明确 Species / Instance，建立同一伙伴跨生命周期和设备的连续性，以及现实图片进入幻想世界的边界。
3. **可选角色**：提出三套种族、剪影、性格、蛋与成熟形态；生成探索图，标记误差，比较小尺寸可读性。
4. **身体技术**：基于官方来源评估制作链、许可、运行成本及扩展，区分实测和推断。
5. **放回产品验证**：隔离 HTML 布局原型，检查三种表现、键盘、响应式、静态降级；留下人工决策入口。

这不是第二阶段实施计划。用户选择后，才为正式资产与最小 Vue 接入制定独立、可验收的实施单元。

## 4. 二十个核心问题的回答

| 问题 | 第一阶段回答 |
| --- | --- |
| 1. Companion 是什么？ | 官方创造身份模板、用户通过相处养成的唯一图像伙伴；长期经历属于实例。 |
| 2. Species 与 Instance？ | 前者管可复用身份与美术基因，后者管用户命名、现有成长、关系和经历。 |
| 3. 为什么同种仅一位？ | 让每次相遇具有不可替代性，避免复制角色和合成机制破坏长期关系。 |
| 4. 为什么图片不生成身体？ | 身份可读性、跨动作一致性和可控制作需要稳定美术资产；动态生图不能成为常规身体流水线。 |
| 5. 图片如何成长与记忆？ | 用户显式 Feed 后，成长结算与来源化记忆候选是不同结果；观察不自动成为用户事实。 |
| 6. 两个世界的关系？ | 用户通过自己打开的「窗」分享图片；伙伴生活在雾屿的一间住处，不窥视整个图库。 |
| 7. 第一位幻想种族？ | 纸翼蛾族 A 绫页暂定保留；潮壳螺与陶鳞蜥冻结留档。 |
| 8. 三个方向？ | A 绫页：整理与求证；B 汐砚：倾听与分寸；C 陶眠：动手与质疑。 |
| 9. 成长连续性？ | 固定 id；蛋、未成熟、成熟重复三项视觉基因；身体成熟不重置关系。 |
| 10. Sprite 与 Portrait？ | A 的 Web Adult 使用正常比例全身与正常比例半身；Q 版独立保留作未来桌宠参考，不作为当前 Web 成熟体。 |
| 11. Shell？ | 侧栏固定栖居位的小全身，桌面候选 64px；平板 48px；手机静态入口，避开业务浮层。 |
| 12. 三路线适用性？ | Sprite 适合有限离散动作；Live2D 擅长近景表演但制作与许可需解决；Spine 适合持续全身动作与附件复用。 |
| 13. 推荐？ | 在动作范围受控、个人项目产能有限的前提下，先静态 Portrait + 少帧 Sprite；不预设未来永远单一 runtime。 |
| 14. 最少资产？ | 身份母版、三阶段静态全身、成熟半身、Shell 简化版、单一阶段最小 Idle、锚点/裁切/许可清单；详见角色体系。 |
| 15. 第 2/5/10 个角色？ | 表现按 species + stage + form + artVersion 查资产；只加载活跃伙伴；业务多实例需独立迁移，不能声称完全只加图片。 |
| 16. 与 R06～09 边界？ | R05 定身份 / 资产规格；R06 映射状态；R07 调度动画；R08 交互；R09 房间布局与生活空间。 |
| 17. 本轮不做？ | 数据库/API/生产 Vue、孵化、多角色、EXP/Feed/Memory 重写、自主旅行、事件、商店、战斗、货币或任务。 |
| 18. 最大技术风险？ | 既有单实例及 LIGHT 阶段与新生命周期不兼容；其次是运行时许可、资产体积与多端资源释放。 |
| 19. 最大美术风险？ | 立绘好看但小尺寸无辨识度，未成熟体退化成缩小版，以及逐动作/逐角色产能爆炸。 |
| 20. 最大产品风险？ | 用户把她当通用助手或操作负担；用虚构记忆与情绪催促喂养，会破坏长期信任。 |

## 5. 本次修订后仍待确认的事项

1. **最终美术方向**：当前探索稿已获准提交归档；A 只是暂留，B/C 暂时冻结，不继续并行迭代。用户将在另一个任务讨论最终方向。
2. **制作优先级与路线**：先轻量全身存在（Sprite + 静态 Portrait），还是优先投入近景表演（Live2D），或全身动作 / 换装（Spine）。需要把美术预算和持续制作能力与路线一起决定。
3. **旧伙伴过渡**：建议保留所有 id 与经历，接受一次明确的视觉身份认领，不要求重新孵化；实际阶段映射及老用户是否可选 Species 需另行产品 / 数据设计，不在此自动拍板。

按用户最新指示提交和推送当前探索稿后停止。技术路线、最终方向和旧实例过渡继续讨论，不自动进入正式资产生产或第二阶段。

## 6. 验证与停止点

本轮检查设计原型、资源引用、事实出处及工作区保护；没有产品代码改动，因此没有运行会把原有 dirty 后端混入结果的全套业务测试。浏览器检查不代表真实设备性能、资产生产可用性或 R05 正式验收。详细结果见原型 README。

**当前停止在 R05 第一阶段。没有正式角色生产、Vue 接入、数据库修改，也没有进入 R06～R12。**
