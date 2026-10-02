# STATE · LiPictureCloud 2.0 主线

## 概览

- 仓库：https://github.com/53214mm/li_picture_cloud
- 唯一工作分支：`nexus/mainline-r11`（长期主线，不随Rxx改名）
- 当前Rxx：R14兼容回归；已复现并修复批量编辑成功后列表仍旧的缓存失效遗漏
- 验收状态：R14完整V3、R11浏览器、R12浏览器到API/共享视图验收、R13浏览器、R16页面/布局仍pending，不标DONE；R12后端HTTP故事线已有执行证据
- 上一完整确认完成需求：R10（用户确认，main `d49fd06`）
- 共同基线：`d49fd06645995e6f2c0a67a6f230ae6704afb2e4`
- R14当前轮起点：已发布 `165070ed9d84dfaa5b9451f4125df263dbbc063c`；前轮创作链起点7953141，HTTP入口起点377ab3，空间首轮起点ff09c2f
- 最近有效代码 / 已核对远端对象：`bf13623d1d72367dba1b9f915c34cea0f6bbe0b0`（tree `abf615cf36a0054fb4aa2169ddd868ab858375a3`；等价源 `a220a02d8429e2ca99364882f306917437a59ccf`）
- 当前唯一产品改动：PictureController批量编辑服务成功返回后调用已有clearListCache；API字段、数据库、权限与业务规则未改。前轮Recipe时间修复9f77c02、空间增量e8d96ea、HTTP入口5d0aef4
- 空间首轮实现：远端对象 `68911f9a70fe01d5257d133cfd4513b192b7774a` / 源 `2bddf43`；产品代码e8d96ea追加review修复
- 历史已审代码：R16 `6f81e7286004d766169855ed953bf7422f895bcf`；R13 `2e05d6d`；R12 `7135e12`；R11 `6a96f92`
- 状态证据为随后docs-only提交，用 `git log -- STATE.md` 定位，避免自引用自己的hash
- 开工先读DECISIONS、根需求清单、最新STATE和远端历史；原附件进度不覆盖实际实现。源/发布SHA见 `docs/work/PUBLICATION-2026-10-02.md`

## Phase 看板

| Phase | 目标 | 状态 | 证据 |
|---|---|---|---|
| R11 P1/P2 | 情绪/关系/性格表现 | 代码review通过 | R11.md；6a96f92 |
| R11 P3 | V2验收 | 自动证据通过 / 浏览器待执行 | 当前全回归包含；未DONE |
| R12 P1/P2 | 共享轻量聊天、已观察提案 | 代码review通过 | R12.md；7135e12 |
| R12 P3 | V3完整验收 | 前端及真实HTTP Home/history/SSE通过 / 浏览器集成待执行 | R14-API-VALIDATION；不替代共享视图验收 |
| R13 P1/P2/P3 | 渲染预算、手机布局 | 代码review与组件验证通过 / 浏览器待执行 | R13.md；2e05d6d |
| R16 P1/P2/P3 | 文案与读取状态 | 首增量review通过 / 页面手测待执行 | R16.md；6f81e72 |
| R14 P1/P2 | 空间精度与读取恢复 | 首增量review通过 | R14.md；e8d96ea |
| R14 P3 | V3完整回归 | 前端286/286、后端734项clean verify、Redis3项与HTTP27阶段通过 / 浏览器验收待执行 | 111重点/142默认浏览器仅收集 |

## 当前轮完成

- 实际HTTP复现：批量编辑成功且详情已更新，同一预热列表查询仍显示旧分类；不是额外发明测试场景
- 一行产品修复：事务服务成功返回后调用已有列表缓存失效方法。失败/权限拒绝不推进缓存；沿用本地失效及版本Redis key，不改缓存架构
- 新增6个控制器用例，旧实现2失败/4通过，修复6/6；加强原有HTTP批量阶段而非增加业务阶段计数，27阶段/TAP35保持
- 同查询重复读取、原/新分类成员、最终恢复通过；缓存命中仍授权，实际H2数据库拒绝后数据保持原样。未声称部分行已成功后的回滚或多实例缓存问题已解决
- 剩余验收矩阵、R15身份/API前置决策和精确验证边界见 `docs/work/R14-CACHE-VALIDATION.md`

## 前轮创作链与时间修复

- 新增Gateway7、Story5、Recipe7个真实HTTP阶段，连同既有Companion8阶段共27；4项纯协议与4个父容器另计，TAP总35，独立新实例复跑通过
- Gateway验证安全回显/归属/路由/使用记录与单连接轮换，Story验证幂等大纲→草稿→保存；Recipe验证最新试运行/固定版本确认、来源快照、条件、禁用/重复/跨用户拒绝及真实WHEN只产生待确认记录
- 实测并修复RecipeExecution完成/失败/拒绝时覆盖createdTime的问题；原数据库始终保留创建时间。6个新增domain回归旧实现全失败，修复后全绿；真实HTTP完成/拒绝响应与重读一致
- 入口在服务启动前要求5个套件文件完整且排序固定，run.json记录实际文件；缺套件的实际探针返回失败且未启动子进程
- 本轮范围/命令/证据见 `docs/work/R14-CREATION-API-VALIDATION.md`；没有UI、API形状、数据库迁移、权限、版本固定或执行规则变更

## 前轮隔离验证入口

- 新增 `npm run test:api:e2e`：在同一编排内启动自有隔离Redis和直接Java子进程，固定test,e2e、唯一H2、localhost、DEMO/stub；拒绝已有服务、环境覆盖和HTTP重定向，失败/中断清理自有进程
- 真实HTTP 8阶段通过：登录隔离、唤醒、同key喂养无双重成长、权威Home、明确SSE done与重新登录后持久历史、记忆、提案、空间小ID批量权限；4项纯协议检查另计
- 官方Redis7.2.11已校验准备；3项opt-in专项通过并独立复跑。原全量verify报告/覆盖率保留，不将额外3项拼成新的全量结果
- 当前前端默认Accept的SSE正常；严格Accept:text/event-stream会406的既有协商限制已记录，不擅自改API声明
- 运行命令、安全边界、失败探索与最终证据见 `docs/work/R14-API-VALIDATION.md`

## 上一轮空间增量

- 修复两个将Snowflake ID转Number的路径：空间批量编辑、管理员喂养日志图片筛选。准确字符串原样保留；不安全Number/无效表示拒绝，不舍入或变成无条件筛选。
- 空间管理、我的空间、空间详情、图片列表区分未知/失败/真正为空；重试只读取原有API，失败不误报空间已删除或诱导重复创建。
- 已有卡片、筛选控件、同账号编辑/AI草稿在刷新时保留；显示页与请求页分离，失败翻页不把旧图片标成新页。
- 路由/账号切换与卸载使旧读取、写入后的UI回调失效；清空旧账号上传URL/名称、批量、编辑和分享草稿。缺少具体用户ID不发无范围查询。
- 独立review发现的缓存端点mock错误、失败页码和草稿跨归属残留均修复，并有回归测试。后台API、领域权限、配额与业务规则未修改。

## 验证证据

- Node **22.23.3** 全前端 **286/286**；入口策略检查共5项（本轮新增2项），此前新增77项真实Vue SFC renderer测试（详情/PictureList/管理筛选47，空间列表/SpaceCard/AiAgentPanel30）
- lint、Companion开/关production build、两组bundle budget通过，最大chunk **390,035bytes**；CompanionView只在开启模式存在
- 先前空间review补充生产API/真实Axios载荷及分页/草稿探针4/4通过；当前缓存修复review、独立BatchCache/TeamAccess9/9及真实HTTP复跑通过，无未解决阻断性代码发现
- 当前HTTP真实27阶段 + 4项纯协议通过（Node TAP35包含4个父容器）；独立重启新H2/Redis复跑通过，全部自有子进程已关闭。端口占用拒绝、无开关拒绝、重定向不转发探针通过；本轮额外BYOK stub故事、当前分类重新校验、禁用路由不回退及精确终态重读探针通过
- Redis专项3/3通过并独立复跑；命令/报告独立保存，原718通过+4跳过统计保持原义
- 后端本轮 **clean verify通过：734项，730通过，0失败/错误，4预期跳过**；编译514个主源码/144个测试源码、打包和原JaCoCo门通过
- 本轮干净覆盖率：airuntime447/504（**88.69%**），companion541/626（**86.42%**），原门槛均85%；独立审核144份XML和覆盖率计数
- 后端早期722项、前轮728项、本轮734项clean verify及缓存红绿证据分别保存；历史结果未覆盖。本轮命令沿用R14-BACKEND-VALIDATION，结果见R14-CACHE-VALIDATION
- 新增7项浏览器用例；组合Shell/Gallery/R16为 **111项 / 12文件**，仅收集；默认浏览器集另收集 **142项 / 22文件**。未执行浏览器断言/截图/键盘布局或真实浏览器API故事线
- 本轮diff-check通过；原需求附件第3行Markdown双空格硬换行的历史例外保留

## 发布与恢复

- GitHub权限已恢复（D-10）；只经已有connector快进长期分支，保留每个tree/顺序/message；不强推，不PR/merge/deploy
- 代码远端对象已回读；本STATE对应docs-only检查点随后发布。活动本地仅在干净、完整tree相同后对齐发布父链
- 源 `nexus/checkpoint-r12-local-dda1650`、`nexus/checkpoint-r13-local-state`、`nexus/checkpoint-r16-local-state`、`nexus/checkpoint-r14-local-state`、`nexus/checkpoint-r14-api-local-state`、`nexus/checkpoint-r14-creation-local-state`、`nexus/checkpoint-r14-cache-local-state` 只供审计，不从旧源父链续开发
- 首选恢复远端 `nexus/mainline-r11`；根需求/DECISIONS/STATE、R11/R12/R13/R16/R14、R14-BACKEND-VALIDATION、R14-API-VALIDATION、R14-CREATION-API-VALIDATION、R14-CACHE-VALIDATION和PUBLICATION足以续接
- CI只监听main push/pull_request；本分支无CI触发不等于CI通过，本轮报告为已执行的本地验证

## 当前阻塞 / 限制

- 当前系统Chromium154存在，前一轮保留sandbox的标准启动在about:blank前因 `socket() failed: Operation not permitted` 失败；Unix IPC权限拒绝已实际复核。停止该路径，不通过安全参数或其他通道绕过
- Playwright自有运行时先前无效ZIP下载，本轮未重复；它与已确认IPC拒绝是不同证据，不混同原因
- 本地TCP bind/listen可用；后端并非整体不可测试。完整官方JDK/Maven已准备，现有可信配置下载依赖后离线verify通过；未关闭TLS/更改CA/系统代理或attach安全策略
- Redis依赖已通过官方源码准备，3项专项和隔离HTTP故事线已通过。初次跨命令服务不可达，改为同一次编排中的自有进程后通过；未推断底层命名空间实现。apt和netlink已拒绝路径不重试
- R11/R12/R13/R16和R14浏览器及浏览器到API验收仍pending；HTTP检查不覆盖共享抽屉/页面、前端实际请求、布局或真实模型。普通COS上传/下载未被creation stub覆盖；R03初访理解/样片授权仍需发布前核实
- R15未启动功能实现：缺首个渠道与归属证明/冲突、同形标识符、无密码账号/最后身份解绑等业务/API决策；不猜自动合并或找回规则。已有BCrypt/拒绝MD5决策仍有效

## 下一步

1. 后端可复跑既有clean verify，复用R14-BACKEND-VALIDATION记录的工具链；需要新下载时只用既有可信路由和证书验证，不能复用失效的临时网络配置
2. 用R14-API-VALIDATION中的入口可重复运行当前27阶段HTTP故事线（扩展细节见R14-CREATION-API-VALIDATION）；Redis及后端应在同一编排启动。仅因新代码或环境变化需要时复跑，不重复已取得证据制造进度
3. 浏览器需要允许正常IPC的环境。该条件未变时不重复已拒绝启动；可用后执行 `e2e/config/space-recovery.config.js`（111项）、feature-off、feeding和默认完整E2E，再判断V3/DONE
4. 默认E2E用H2和已有外部服务stub，无需生产MySQL/COS/真实模型密钥；需Redis127.0.0.1:6380无密码DB15，Node22，后端18124和前端15173
5. 可先复现源码已发现的Login/Register迟到跳转候选：离开页面后旧请求/注册计时器可能抢回路由；尚未运行复现，不当作已确认缺陷。若成立，只修页面生命周期，不改登录会话或服务端注册语义。没有新缺陷或环境变化时，不重跑同类证据制造进度
6. R15先收敛上述身份/API决策，再选一条V4纵向切片；不搭无消费者的验证框架。严格Accept的SSE协商缺口需要单独API边界确认。浏览器相关工作在正常IPC环境可用前暂停，不绕行
7. 每轮从远端最新专用分支开始，稳定后review/STATE/验证tree发布；不改其他用户分支，不PR/merge/deploy

## 最近更新

| 时间（UTC） | 更新者 | 内容 |
|---|---|---|
| 2026-10-02 13:49 | Nexus | R11review通过，浏览器安装失败 |
| 2026-10-02 14:47 | Nexus | R12前端157/157通过，V3保留pending |
| 2026-10-02 15:20 | Nexus | 写权限恢复，R11/R12逐tree发布 |
| 2026-10-02 15:59 | Nexus | R13review与173/173通过；64项仅收集 |
| 2026-10-02 17:07 | Nexus | R16review与204/204通过，70+11仅收集并发布 |
| 2026-10-02 18:12 | Nexus | R14空间增量review通过，前端281/281、后端干净verify通过；IPC拒绝已复核，111项仅收集 |
| 2026-10-02 18:51 | Nexus | R14隔离Redis3/3及真实HTTP8阶段独立复跑通过；前端284/284，浏览器111/142仅收集，完整验收仍pending |
| 2026-10-02 19:49 | Nexus | R14模型网关/Story/Recipe扩展27阶段通过；修复Recipe创建时间一致性，后端728/724/4、前端286/286；浏览器仍pending |
| 2026-10-02 20:49 | Nexus | R14批量编辑缓存缺陷先红后绿，734/730/4后端及27阶段HTTP通过；R15前置身份决策明确为待定，浏览器pending |
