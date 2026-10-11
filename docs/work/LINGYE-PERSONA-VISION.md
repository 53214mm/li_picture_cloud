# 绫页人格与客观视觉基础

决策：DECISIONS L-01–L-04、L-07、L-10；不替代普通聊天，不改成长领域数值或既有撤权守卫。

## 实现

- `LingyePersona` 与版本化资源 `lingye-persona-v1.txt` 提供可审阅、可调整的统一人格。坦诚 AI 身份，开朗、活泼、适度嘴欠而温暖；不强制安慰、追问或推销喂图，不虚构身体、线下活动与共同经历。普通用户指令不能重写身份边界。
- 普通聊天继续使用原入口、流式路径、历史、模式选择、账本和鉴权。演示文本去除喂图诱导；通用演示回复明确演示性质。
- 每轮仍先扫描原有最多 100 条记忆并进行来源权限检查（异常 fail-closed），然后才做保守相关筛选。问候或泛泛问记忆不会自动附带最新图片。支持明确图片 ID、限定对象词表、引号内原文、英文完整词匹配；仅匹配当前已确认内容，不读取被纠正的旧内容，不做语义猜测。未命中允许为空。召回还校验伙伴和用户归属。
- 上下文保留来源类型、记录时间、确认状态、是否用户修订，并提醒来源描述/旧模型独白并非用户事实。当前用户消息、历史角色与模型反应仍分开传输。
- 视觉请求升级到 `companion-vision-v3` / `visual-observation-v3`：`visibleObservations`（20–160 码点）与 `uncertainInterpretation`（0–60 码点）是不同字段。禁止第一人称扮演伙伴、猜测身份/关系/个人经历或把图片文字当指令。服务端限制长度、链接、控制字符及明显第一人称标记；这不是完美的语义真实性检测。
- 成长档案的兼容文字字段保留“可见观察 / 不确定解读”标签；新记忆候选仅使用观察，不把不确定解读晋升为记忆。数值评分与成长映射完全沿用原规则；视觉 tone 不声称是人的内心状态。

## 初版边界与兼容

- 不额外调用语言模型生成“绫页感想”，不增加收费调用次数。视觉本身不扮演绫页；后续用户主动对话可依据获准且相关的观察形成反应。
- 既有 8 参数 `VisualObservationCandidate` 构造方式保留，旧记录、旧供应商测试夹具可继续读；新客观证据为附加字段。无数据迁移、无历史改写。旧记录不会被重新标为客观观察。
- 明确支持未加引号的“你还记得那张机械女仆吗”“那张粉色双马尾的图呢”：仅加入完整实体短语，不拆成“机械”“粉色”等泛词，也不由“女仆”“双马尾”猜测更具体实体。相关测试继续覆盖问候/离题为空、跨用户/伙伴、未确认、撤权与纠正过滤。
- 词表式召回是有意保守的基础，可能漏掉同义词或没有明确对象的指代；不是已完成的来源化长记忆系统。没有新增持久索引或摘要，故无需新清理流程；现有纠正、删除、撤权守卫继续生效。
- 长期事实、共同经历与人格主观看法的独立持久化仍待后续增量；不能把本次 prompt 分隔说成已完成所有记忆治理。
- 真实模型人格自然度、多供应商 schema 支持与用户实聊仍需人工验收，本轮仅用本地替身。

## 验证

- 已新增人格契约、无关问候空召回、相关图片 ID 召回、授权前置、词法边界、纠正/失效/跨用户过滤、客观证据长度与第一人称拒绝、解释不进入记忆、成长数值不变测试。
- 初次红测入口受 Maven 默认缓存只读阻塞；不声称已取得红绿证据。
- Java 21 离线生产编译首轮通过 544 个源文件，随后由统一验证任务重跑完整最新源码。
- 10 项纯 Java 离线探针通过：问候空召回、明确对象/图片 ID、跨用户、纠正与失效过滤、不猜同义词、观察/解释标签、第一人称拒绝、预算、旧构造兼容。探针不是 JUnit 或集成测试的替代。
- 初期定向 Maven 测试受临时代理、工具取消和未缓存测试依赖阻塞；统一验证任务恢复依赖后，定向 **78/78 通过、0 跳过**，包括新增实体短语测试。证据：`../tool-cache/persona-tests.log`。
- 后端完整 `clean verify` **842 项：838 通过、0 失败、4 项预期跳过**，打包与 JaCoCo 门通过。首轮完整验证曾因发布清单遗漏新 presence 迁移失败，补齐后重跑通过；不是一次全绿。证据：`../tool-cache/full-verify.log`、`../tool-cache/full-success-reports/`、`../tool-cache/full-success-jacoco.xml`；失败证据保留于 `../tool-cache/full-first-failure/`。
- HTTP 首跑暴露 `e2e/api/companion.api.js` 仍断言旧演示文案；已改成精确的新台词，并同步修正 `e2e/companion.spec.js` 的相同陈旧断言。无 Java 或前端产品代码改动；统一验证任务正在 fresh H2 重跑，不能将修正断言本身当作 HTTP/浏览器验收。
- 定向套件：`CompanionChatContextAssemblerTest,CompanionChatServiceTest,RelevantCompanionMemoriesTest,VisualEvidenceTest,DashScopeVisionClientTest,VisualPictureNutritionAdapterTest,RoutedVisualObservationProviderTest,VisualFeedingStubIntegrationTest,CompanionMemoryServiceTest,VisualObservationCandidateTest`。
