# STATE · LiPictureCloud 2.0 主线

## 概览

- 仓库：https://github.com/53214mm/li_picture_cloud
- 唯一工作分支：`nexus/mainline-r11`（长期主线，不随Rxx改名）
- 当前Rxx：R14兼容回归；全量验收后新增修复图库二次进入 Invalid Date，MCP 实现暂缓
- 验收状态：R11/R13 V2、R12 V3共享视图与真实API、R16既有页面范围、R14现有自动回归矩阵通过；外部供应商/线上验收仍不在本轮证据范围
- 上一完整确认完成需求：R10（用户确认，main `d49fd06`）
- 共同基线：`d49fd06645995e6f2c0a67a6f230ae6704afb2e4`
- R14当前轮起点：已发布 `9cc9cf6ee314acd3359884faaa5d77d37fc0ea11`；前轮缓存起点165070e，创作链起点7953141，HTTP入口起点377ab3，空间首轮起点ff09c2f
- 本轮验收基线：本地 `f3b3ff5` 加工作区改动；当前改动未提交推送。前轮已核对代码对象 `003ae2b90b41b962b8ce3c2c7c180caca4673136` 的发布证据保留
- 当前产品改动：分类分析内部SQL别名统一为total_size；Vite支持测试缓存隔离；图片列表两级缓存恢复PictureVO泛型，保证日期及ID响应类型一致，四个图片视图共享日期兼容格式化。MCP、数据库迁移与Companion领域未改
- 空间首轮实现：远端对象 `68911f9a70fe01d5257d133cfd4513b192b7774a` / 源 `2bddf43`；产品代码e8d96ea追加review修复
- 历史已审代码：R16 `6f81e7286004d766169855ed953bf7422f895bcf`；R13 `2e05d6d`；R12 `7135e12`；R11 `6a96f92`
- 状态证据为随后docs-only提交，用 `git log -- STATE.md` 定位，避免自引用自己的hash
- 开工先读DECISIONS、根需求清单、最新STATE和远端历史；原附件进度不覆盖实际实现。源/发布SHA见 `docs/work/PUBLICATION-2026-10-02.md`

## Phase 看板

| Phase | 目标 | 状态 | 证据 |
|---|---|---|---|
| R11 P1/P2 | 情绪/关系/性格表现 | 代码review通过 | R11.md；6a96f92 |
| R11 P3 | V2验收 | Node/浏览器/键盘/窄屏及降级通过 | R14-WINDOWS-ACCEPTANCE |
| R12 P1/P2 | 共享轻量聊天、已观察提案 | 代码review通过 | R12.md；7135e12 |
| R12 P3 | V3验收 | 真实HTTP与浏览器共享聊天/持久历史通过 | R14-WINDOWS-ACCEPTANCE；外部模型为既有替身 |
| R13 P1/P2/P3 | 渲染预算、手机布局 | 原review/组件与浏览器、关键截图通过 | R13.md；R14-WINDOWS-ACCEPTANCE |
| R16 P1/P2/P3 | 文案与读取状态 | 既有范围的浏览器、键盘详情与窄屏通过 | R16.md；不扩大为全站逐页巡检 |
| R14 P1/P2 | 空间精度与读取恢复 | 首增量review通过 | R14.md；e8d96ea |
| R14 P3 | V3回归矩阵 | Node329、后端744项/1预期跳过、默认148项+生产42项、HTTP27阶段+4协议通过 | 全量首跑+修复复验逐项核对；R14-WINDOWS-ACCEPTANCE |

## 当前轮完成

- 全量验收之后，用户复现图库第二次打开Invalid Date：实际HTTP证明缓存命中将ISO日期变成毫秒字符串。已修复两级缓存泛型丢失并兼容旧前端载荷；新增红绿回归。当前Node332/332、后端定向11/11、Gallery双项目40/40、真实开发页面三次进入、lint/双build/bundle通过。详情见[R14日期修复](docs/work/R14-INVALID-DATE-FIX.md)；下列744项全后端及完整浏览器矩阵仍是此前验收证据，不冒充本次全量重跑。
- 已解决 Windows PATH、mock契约、时钟暂停时序、关闭构建断言和已取消流fixture问题；明确只读POST白名单仍对未知写入失败关闭。
- 分析页新真实浏览器回归定位分类SQL别名大小写缺陷，真实MyBatis/JDBC测试旧代码一红一绿、修复两绿；另有8项H2日期兼容红绿测试，仅影响测试库。
- 新R12真实故事线验证重复submit只发一次、跨页草稿/焦点交接、同页双入口共享历史、真实持久记录与刷新无重发。跨页重读遵守最后消费者退出即清历史的D-08，不改变生命周期。
- 全量执行与定向复验已覆盖最终默认目录全部148项有效测试；专用feature-off 1项另过，生产Shell/Gallery/Landing共41项全过。首跑失败及修复链均保留，不能宣称单次149项全绿。
- 后端clean verify为744项：743通过、0失败/错误、1项真实模型smoke按开关跳过，含Redis3项；原JaCoCo门、Node329/329、严格lint、双模式build/bundle通过。HTTP27业务阶段+4协议检查通过（TAP35另含4父容器）。
- Windows Docker Redis7使用独占6380，后台18124/前端15173均为自有测试进程；结束清理，不改日常服务或外部MCP。源文件对照、矩阵、截图、trace和报告位于独立output子目录；完整说明见[本轮验收](docs/work/R14-WINDOWS-ACCEPTANCE.md)。

## 前轮登录注册修复（2026-10-02）

- 实际SFC/RouterView/Pinia回归复现旧页面迟到跳转、倒计时残留、重复提交与同实例路由污染；初始32项中17红，最终扩展42/42通过
- 当前路由/实例与导航意图共同保护UI回调；用户新导航仍在等待guard时也不会被登录完成或倒计时抢占。注册成功锁保留、原1.5秒正常跳转保留，取消导航可用已有手动登录链接
- 登录请求仍按既有store更新成功会话，不声称卸载取消服务端认证或注册；后端/API/R15身份规则未变
- 独立review无阻断性发现；10项额外探针、42/42专门套件和328/328全前端独立复跑通过
- 新增5项浏览器回归仅收集；本轮未运行浏览器、后端、Redis或HTTP故事线。范围及后续最小门槛见R14-AUTH-NAVIGATION-VALIDATION

## 前轮批量缓存修复

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

## 历史验证证据（2026-10-02；本轮结果见上文）

- Node **22.23.3** 全前端 **328/328**；本轮新增42项真实SFC/RouterView/Pinia生命周期回归；入口策略检查共5项（前轮新增2项），此前新增77项真实Vue SFC renderer测试（详情/PictureList/管理筛选47，空间列表/SpaceCard/AiAgentPanel30）
- lint、Companion开/关production build、两组bundle budget通过，最大chunk **390,035bytes**；CompanionView只在开启模式存在
- 先前空间review补充生产API/真实Axios载荷及分页/草稿探针4/4通过；前轮缓存修复review、独立BatchCache/TeamAccess9/9及真实HTTP复跑通过，无未解决阻断性代码发现
- 前轮HTTP真实27阶段 + 4项纯协议通过（Node TAP35包含4个父容器）；独立重启新H2/Redis复跑通过，全部自有子进程已关闭。端口占用拒绝、无开关拒绝、重定向不转发探针通过；前轮额外BYOK stub故事、当前分类重新校验、禁用路由不回退及精确终态重读探针通过
- Redis专项3/3通过并独立复跑；命令/报告独立保存，原718通过+4跳过统计保持原义
- 后端前轮 **clean verify通过：734项，730通过，0失败/错误，4预期跳过**；编译514个主源码/144个测试源码、打包和原JaCoCo门通过
- 前轮干净覆盖率：airuntime447/504（**88.69%**），companion541/626（**86.42%**），原门槛均85%；独立审核144份XML和覆盖率计数
- 后端早期722项、随后728项、前轮734项clean verify及缓存红绿证据分别保存；本轮仅改前端，未重跑或覆盖这些历史结果。命令沿用R14-BACKEND-VALIDATION，结果见R14-CACHE-VALIDATION
- 此前新增7项浏览器用例；本轮加入5项登录注册回归后组合Shell/Gallery/R16为 **116项 / 13文件**，仅收集；默认浏览器集另收集 **147项 / 23文件**。未执行浏览器断言/截图/键盘布局或真实浏览器API故事线
- 本轮diff-check通过；原需求附件第3行Markdown双空格硬换行的历史例外保留

## 前轮发布与恢复记录

- GitHub权限已恢复（D-10）；只经已有connector快进长期分支，保留每个tree/顺序/message；不强推，不PR/merge/deploy
- 代码远端对象已回读；本STATE对应docs-only检查点随后发布。活动本地仅在干净、完整tree相同后对齐发布父链
- 源 `nexus/checkpoint-r12-local-dda1650`、`nexus/checkpoint-r13-local-state`、`nexus/checkpoint-r16-local-state`、`nexus/checkpoint-r14-local-state`、`nexus/checkpoint-r14-api-local-state`、`nexus/checkpoint-r14-creation-local-state`、`nexus/checkpoint-r14-cache-local-state`、`nexus/checkpoint-r14-auth-local-state` 只供审计，不从旧源父链续开发
- 首选恢复远端 `nexus/mainline-r11`；根需求/DECISIONS/STATE、R11/R12/R13/R16/R14、R14-BACKEND-VALIDATION、R14-API-VALIDATION、R14-CREATION-API-VALIDATION、R14-CACHE-VALIDATION、R14-AUTH-NAVIGATION-VALIDATION和PUBLICATION足以续接
- CI只监听main push/pull_request；本分支无CI触发不等于CI通过，本轮报告为已执行的本地验证

## 历史环境阻塞（2026-10-02；当前Windows浏览器已恢复）

- 当前系统Chromium154存在，前一轮保留sandbox的标准启动在about:blank前因 `socket() failed: Operation not permitted` 失败；Unix IPC权限拒绝已实际复核。停止该路径，不通过安全参数或其他通道绕过
- Playwright自有运行时先前无效ZIP下载，本轮未重复；它与已确认IPC拒绝是不同证据，不混同原因
- 本地TCP bind/listen可用；后端并非整体不可测试。完整官方JDK/Maven已准备，现有可信配置下载依赖后离线verify通过；未关闭TLS/更改CA/系统代理或attach安全策略
- Redis依赖已通过官方源码准备，3项专项和隔离HTTP故事线已通过。初次跨命令服务不可达，改为同一次编排中的自有进程后通过；未推断底层命名空间实现。apt和netlink已拒绝路径不重试
- 上述Linux IPC与安装失败是历史事实，2026-10-09的正常Windows浏览器环境已完成相关浏览器/API矩阵，不再作为当前阻塞。普通COS上传/下载、真实模型未被stub覆盖；R03初访理解/样片授权仍需发布前核实
- R15未启动功能实现：缺首个渠道与归属证明/冲突、同形标识符、无密码账号/最后身份解绑等业务/API决策；不猜自动合并或找回规则。已有BCrypt/拒绝MD5决策仍有效

## 下一步

1. 本轮先停在验收完成与文档更新；未提交推送、不merge/deploy，不自动进入R15或恢复MCP工作。
2. 后续代码/环境改变时按R14-WINDOWS-ACCEPTANCE复跑相关检查；保留独立Vite缓存及测试Redis，不复用日常6379/8124实例。
3. 生产AI/COS/外部MCP、其他浏览器/真机及R03发布前事项继续单独验收；现有DEMO/stub证据不覆盖这些边界。
4. R15先收敛身份/API决策再实现；严格Accept的SSE协商缺口也需单独边界确认。

## 最近更新

| 时间（UTC） | 更新者 | 内容 |
|---|---|---|
| 2026-10-09 | Codex | 修复图库缓存命中日期类型丢失；Node332、后端定向11、Gallery双项目40和真实页面三次进入通过；未重启用户后端、未提交 |
| 2026-10-09 | Codex | Windows全量浏览器+复验覆盖148项、生产42项、HTTP27阶段+4协议、Node329、后端744/743/1通过；修整测试并修复分类统计内部别名，当前工作区未提交 |
| 2026-10-02 13:49 | Nexus | R11review通过，浏览器安装失败 |
| 2026-10-02 14:47 | Nexus | R12前端157/157通过，V3保留pending |
| 2026-10-02 15:20 | Nexus | 写权限恢复，R11/R12逐tree发布 |
| 2026-10-02 15:59 | Nexus | R13review与173/173通过；64项仅收集 |
| 2026-10-02 17:07 | Nexus | R16review与204/204通过，70+11仅收集并发布 |
| 2026-10-02 18:12 | Nexus | R14空间增量review通过，前端281/281、后端干净verify通过；IPC拒绝已复核，111项仅收集 |
| 2026-10-02 18:51 | Nexus | R14隔离Redis3/3及真实HTTP8阶段独立复跑通过；前端284/284，浏览器111/142仅收集，完整验收仍pending |
| 2026-10-02 19:49 | Nexus | R14模型网关/Story/Recipe扩展27阶段通过；修复Recipe创建时间一致性，后端728/724/4、前端286/286；浏览器仍pending |
| 2026-10-02 20:49 | Nexus | R14批量编辑缓存缺陷先红后绿，734/730/4后端及27阶段HTTP通过；R15前置身份决策明确为待定，浏览器pending |
| 2026-10-02 21:46 | Nexus | R14登录注册迟到跳转红绿通过；专门42/42、全前端328/328、独立review通过；浏览器116/147仅收集，剩余依赖明确暂停 |
