# STATE · LiPictureCloud 2.0 主线

## 概览

- 仓库：https://github.com/53214mm/li_picture_cloud
- 唯一工作分支：`nexus/mainline-r11`（长期主线，不随Rxx改名）
- 当前Rxx：R14空间/管理筛选兼容回归增量，代码已独立审核；后端clean verify通过
- 验收状态：R14完整V3、R11浏览器、R12真实API故事线、R13浏览器、R16页面/布局仍pending，不标DONE
- 上一完整确认完成需求：R10（用户确认，main `d49fd06`）
- 共同基线：`d49fd06645995e6f2c0a67a6f230ae6704afb2e4`
- R14本轮起点：已发布 `ff09c2fed69393e69e764ddb817e95ba95ea6188`
- 最近有效代码 / 已核对远端对象：`e8d96eafd896aa4182fcf4157702a6fe7f06dec8`（tree `f6862c6bda3e7903d0bc70432d6c75ba2a8f56f6`；等价于已审源 `f734708fed3b4746828f969b2ac0f4836e648d3c`）
- 本轮首个实现：远端对象 `68911f9a70fe01d5257d133cfd4513b192b7774a` / 源 `2bddf43`；上一条追加review修复
- 历史已审代码：R16 `6f81e7286004d766169855ed953bf7422f895bcf`；R13 `2e05d6d`；R12 `7135e12`；R11 `6a96f92`
- 状态证据为随后docs-only提交，用 `git log -- STATE.md` 定位，避免自引用自己的hash
- 开工先读DECISIONS、根需求清单、最新STATE和远端历史；原附件进度不覆盖实际实现。源/发布SHA见 `docs/work/PUBLICATION-2026-10-02.md`

## Phase 看板

| Phase | 目标 | 状态 | 证据 |
|---|---|---|---|
| R11 P1/P2 | 情绪/关系/性格表现 | 代码review通过 | R11.md；6a96f92 |
| R11 P3 | V2验收 | 自动证据通过 / 浏览器待执行 | 当前全回归包含；未DONE |
| R12 P1/P2 | 共享轻量聊天、已观察提案 | 代码review通过 | R12.md；7135e12 |
| R12 P3 | V3完整验收 | 前端证据通过 / 真实Home/history/SSE故事线待执行 | 后端verify不替代故事线 |
| R13 P1/P2/P3 | 渲染预算、手机布局 | 代码review与组件验证通过 / 浏览器待执行 | R13.md；2e05d6d |
| R16 P1/P2/P3 | 文案与读取状态 | 首增量review通过 / 页面手测待执行 | R16.md；6f81e72 |
| R14 P1/P2 | 空间精度与读取恢复 | 首增量review通过 | R14.md；e8d96ea |
| R14 P3 | V3完整回归 | 前端281/281、后端clean verify通过 / Redis与浏览器API验收待执行 | 111项浏览器仅收集 |

## 本轮完成

- 修复两个将Snowflake ID转Number的路径：空间批量编辑、管理员喂养日志图片筛选。准确字符串原样保留；不安全Number/无效表示拒绝，不舍入或变成无条件筛选。
- 空间管理、我的空间、空间详情、图片列表区分未知/失败/真正为空；重试只读取原有API，失败不误报空间已删除或诱导重复创建。
- 已有卡片、筛选控件、同账号编辑/AI草稿在刷新时保留；显示页与请求页分离，失败翻页不把旧图片标成新页。
- 路由/账号切换与卸载使旧读取、写入后的UI回调失效；清空旧账号上传URL/名称、批量、编辑和分享草稿。缺少具体用户ID不发无范围查询。
- 独立review发现的缓存端点mock错误、失败页码和草稿跨归属残留均修复，并有回归测试。后台API、领域权限、配额与业务规则未修改。

## 验证证据

- Node **22.23.3** 全前端 **281/281**；新增77项真实Vue SFC renderer测试（详情/PictureList/管理筛选47，空间列表/SpaceCard/AiAgentPanel30）
- lint、Companion开/关production build、两组bundle budget通过，最大chunk **390,035bytes**；CompanionView只在开启模式存在
- 独立复跑以上检查、111项收集、diff-check通过；补充生产API/真实Axios载荷及分页/草稿探针4/4通过，无未解决阻断性代码发现
- 后端 **clean verify通过：722项，718通过，0失败/错误，4预期跳过**；编译514个主源码/143个测试源码、打包和原JaCoCo门通过
- 干净覆盖率：airuntime447/504（**88.69%**），companion541/626（**86.42%**），原门槛均85%；独立审核143份XML和覆盖率计数
- 后端原自附加失败、首次成功、干净成功分别保留；最终结果使用独立新target执行数据。命令/工具链见 `docs/work/R14-BACKEND-VALIDATION.md`
- 新增7项浏览器用例；组合Shell/Gallery/R16为 **111项 / 12文件**，仅收集，未执行断言/截图/键盘布局或真实浏览器API故事线
- 本轮diff-check通过；原需求附件第3行Markdown双空格硬换行的历史例外保留

## 发布与恢复

- GitHub权限已恢复（D-10）；只经已有connector快进长期分支，保留每个tree/顺序/message；不强推，不PR/merge/deploy
- 代码远端对象已回读；本STATE对应docs-only检查点随后发布。活动本地仅在干净、完整tree相同后对齐发布父链
- 源 `nexus/checkpoint-r12-local-dda1650`、`nexus/checkpoint-r13-local-state`、`nexus/checkpoint-r16-local-state`、`nexus/checkpoint-r14-local-state` 只供审计，不从旧源父链续开发
- 首选恢复远端 `nexus/mainline-r11`；根需求/DECISIONS/STATE、R11/R12/R13/R16/R14、R14-BACKEND-VALIDATION和PUBLICATION足以续接
- CI只监听main push/pull_request；本分支无CI触发不等于CI通过，本轮报告为已执行的本地验证

## 当前阻塞 / 限制

- 当前系统Chromium154存在，但本轮保留sandbox的标准启动在about:blank前因 `socket() failed: Operation not permitted` 失败；Unix IPC权限拒绝已实际复核。停止该路径，不通过安全参数或其他通道绕过
- Playwright自有运行时先前无效ZIP下载，本轮未重复；它与本轮IPC拒绝是不同证据，不混同原因
- 本地TCP bind/listen可用；后端并非整体不可测试。完整官方JDK/Maven已准备，现有可信配置下载依赖后离线verify通过；未关闭TLS/更改CA/系统代理或attach安全策略
- Redis运行时仍未准备；3项opt-in Redis测试及真实API/E2E未执行。apt配置读取被拒绝，未继续该apt路径；不能把普通缺少依赖误写成所有安装方式均无权限
- R11/R12/R13/R16和R14浏览器/API验收仍pending；R03初访理解/样片授权仍需发布前核实

## 下一步

1. 后端可复跑既有clean verify，复用R14-BACKEND-VALIDATION记录的工具链；需要新下载时只用既有可信路由和证书验证，不能复用失效的临时网络配置
2. 可单独评估授权范围内的官方隔离Redis运行时，完成3项opt-in Redis专项及test,e2e真实API前置条件。不要绕过被拒绝的apt配置读取
3. 浏览器需要允许正常IPC的环境。该条件未变时不重复已拒绝启动；可用后执行 `e2e/config/space-recovery.config.js`（111项）、feature-off、feeding和默认完整E2E，再判断V3/DONE
4. 默认E2E用H2和已有外部服务stub，无需生产MySQL/COS/真实模型密钥；需Redis127.0.0.1:6380无密码DB15，Node22，后端18124和前端15173
5. 后续只处理实际发现、对应需求及未完成验证，不制造新产品范围或重复报告。R15仍为独立高风险支线；没有可执行独立工作时说明最小缺失条件并暂停相关验收
6. 每轮从远端最新专用分支开始，稳定后review/STATE/验证tree发布；不改其他用户分支，不PR/merge/deploy

## 最近更新

| 时间（UTC） | 更新者 | 内容 |
|---|---|---|
| 2026-10-02 13:49 | Nexus | R11review通过，浏览器安装失败 |
| 2026-10-02 14:47 | Nexus | R12前端157/157通过，V3保留pending |
| 2026-10-02 15:20 | Nexus | 写权限恢复，R11/R12逐tree发布 |
| 2026-10-02 15:59 | Nexus | R13review与173/173通过；64项仅收集 |
| 2026-10-02 17:07 | Nexus | R16review与204/204通过，70+11仅收集并发布 |
| 2026-10-02 18:12 | Nexus | R14空间增量review通过，前端281/281、后端干净verify通过；IPC拒绝已复核，111项仅收集 |
