# 2026-10-02 · R11 / R12 首次远端发布

## 结果与边界

- 长期分支：[nexus/mainline-r11](https://github.com/53214mm/li_picture_cloud/tree/nexus/mainline-r11)
- 首次远端检查点：`7c5c975b19b686dd4dfdd01998fe1edc5b8139bf`；tree `18320f4ec95c1e68ed15792607b8b76d6fb253b4`，与原本地 `dda1650f6c5b77d5cdbed70be408ae039d2205f6` 完全一致。
- 共同基线：`d49fd06645995e6f2c0a67a6f230ae6704afb2e4`。发布前 `nexus/*` 远端分支为空；仅创建独立长期分支，未强推、修改 main、创建 PR、merge 或部署。
- 用户安装 GitHub App 后，先前 integration 403 的权限条件已改变；同一 GitHub connector 的实际 tree/commit/branch 写入及回读均成功，未换凭据绕行。
- Connector 未提供原始 author/committer/date 参数，因此 GitHub 使用连接账号及发布时间创建新的 commit SHA；未伪造原始身份或时间。提交顺序、父链基线、message 与每个完整 tree 保持一致。

## 本地源提交 → 已发布提交

| 原本地源 SHA | 已发布 SHA | 完全相同的 tree SHA |
|---|---|---|
| `c9d3584e6ab6998861fcfab5b564b5f35e504823` | `030bbde4827d972497c647294a9b538dd35cebc5` | `d82a5b52e1767f223dcde3b4c007d237cd55f883` |
| `36cb536dd7517c684200f277c73959ab1c47eb2d` | `6a96f92a6ce299a3eabeecf48f68ff0b4de82bb1` | `d7ca0b4f99e777851ec1e5f6262c387507bbce83` |
| `5177cf49f6349b9f861980bbbcb9d9dac3290fc1` | `7a68d4204a7c166c6545f59b14429a915fe931a5` | `9d31b41b78646a25cf628b22aa143b54b12a3306` |
| `0648c559c8195fbebd865c58f8d57e97e53dacd1` | `7135e120b8b46e0cc95bb81ddabb49f772446ef7` | `0056df8ed51bdb98a6d913e13c2fce305f00ed27` |
| `dda1650f6c5b77d5cdbed70be408ae039d2205f6` | `7c5c975b19b686dd4dfdd01998fe1edc5b8139bf` | `18320f4ec95c1e68ed15792607b8b76d6fb253b4` |

## 恢复与后续工作

- 活动本地 `nexus/mainline-r11` 已对齐已发布父链，上游为 `origin/nexus/mainline-r11`；原本地检查点保留在 `nexus/checkpoint-r12-local-dda1650`，不得将这条备份父链混入后续主线或强推远端。
- 原本地完整历史另存为 `pre-publication-dda1650.bundle`，`git bundle verify` 通过；SHA-256：`3cac2cca5c42a2b84641460a35fa3c1eee1d22f388f77dac6c3d8887fa474acf`。原 R12 Library 恢复包仍可恢复本地历史。
- 从新环境续接时优先读取远端长期分支及根目录 `DECISIONS.md` / `STATE.md`；只有恢复原本地包时才使用左列 SHA。
- 本发布状态文档是随后追加的 docs-only 提交；其 SHA 使用 `git log -- STATE.md docs/work/PUBLICATION-2026-10-02.md` 定位，避免在文件内自引用。

## 验证边界

- 五个远端 commit 均回读确认 tree、父提交及顺序，完整 Git tree SHA 与本地验证对象一一相同。
- 既有前端 157/157、lint、功能开/关 build + bundle budget 和独立 review 证据因 tree 相同适用于已发布代码；本次仅做 Git 发布及文档更新，不冒称重新运行代码测试。
- `.github/workflows/ci.yml` 当前仅监听 main push 和 pull_request；独立分支发布不触发该 CI，无运行不等于通过。
- R11 浏览器验收以及 R12 V3 真实 Home/history/SSE / 后端故事线验收仍未完成；原 Chromium 安装限制和 50 项仅收集的事实保持不变。


## R13 后续检查点（2026-10-02）

沿已发布 `bb4e6f501635230e8b515fde18e718026f91d37e` 开始，不恢复旧本地 R12 父链。仍使用相同 connector 的 tree/commit/ref 接口；同一内容因 author/date 差异产生新 SHA，提交顺序与每个 tree 不变。

| 原本地源 SHA | 对应远端 commit 对象 | 完全相同的 tree SHA |
|---|---|---|
| `b10070303b4847c1f06fb3c94e66d533c2e4b2a4` | `5d707515202d96d81f410abe4f1a1cf24253775f` | `7502604ff8d559ad5d9e557c3482bd651f85b226` |
| `ca743a9df55a791864084e10c23bcfe7ae9ec5b9` | `2e05d6dd328da59060f0cd01ba373527d96d32f2` | `d31e856b1728f7d5217a446c8e135d7284d7f3de` |

源提交已独立审核：Node22全前端173/173、lint、功能开/关build/budget通过；64项浏览器仅收集，R11/R12/R13未验收项保留。GitHub创建结果与只读fetch再次确认上述tree/父链，之后只快进 `nexus/mainline-r11`；不得推其他分支、创建PR、merge/deploy。

状态证据为随后追加的 docs-only 提交（含本节）；发布后源状态提交保存在本地专用 `nexus/checkpoint-r13-local-state`，活动分支按完整tree相同条件对齐远端。状态提交不自引用哈希；通过 `git log -- STATE.md docs/work/PUBLICATION-2026-10-02.md` 可查到其已发布版本。新的恢复应优先clone远端长期分支，源备份只用于核查，不能混入后续主线父链。


## R16 后续检查点（2026-10-02）

从 R13 已发布 `7da6afbeaa05059f6c9c41ffcfea7f2e2b079000` 继续，仅发布原专用分支。保留相同 tree、原提交 message 和顺序；connector 的 author/date 差异继续产生不同 SHA。

| 原本地源 SHA | 对应远端 commit 对象 | 完全相同的 tree SHA |
|---|---|---|
| `ff634e4addc2e44e14a7ae9bf9516608f90b1e1a` | `6f81e7286004d766169855ed953bf7422f895bcf` | `3343d313f4c269d761f3b7cf5a64860007b010d9` |

源代码独立review通过：204/204、lint、功能开/关build/budget及分块检查通过；70项前端模拟和11项真实后端故事线仅收集。上述远端对象已由只读fetch核对完整tree与父链。随后创建包含本节的docs-only状态提交，再以force=false快进长期分支；不会创建PR、merge/deploy或触碰其他分支。

本轮源状态提交保存在本地专用 `nexus/checkpoint-r16-local-state`，并保留增量bundle作作者/时间/SHA核查。增量bundle需要已发布基线 `7da6afbeaa05059f6c9c41ffcfea7f2e2b079000`，不是独立完整备份。状态提交通过 `git log -- STATE.md docs/work/PUBLICATION-2026-10-02.md` 定位，不在自己的内容中自引用。活动分支在工作区干净、完整tree相同后对齐远端；新的恢复首选远端主线，不从源检查点继续开发。


## R14 后续检查点（2026-10-02）

沿 R16 已发布 `ff09c2fed69393e69e764ddb817e95ba95ea6188` 继续，没有从原始源备份父链开发。本轮仍仅快进长期专用分支；原顺序、message、每个tree完整保留。

| 原本地源 SHA | 对应远端 commit 对象 | 完全相同的 tree SHA |
|---|---|---|
| `2bddf43280cbfac0349b510892a3cba8a07bf54e` | `68911f9a70fe01d5257d133cfd4513b192b7774a` | `d4250330a382d534b86b02d6befde77a3bc46fd4` |
| `f734708fed3b4746828f969b2ac0f4836e648d3c` | `e8d96eafd896aa4182fcf4157702a6fe7f06dec8` | `f6862c6bda3e7903d0bc70432d6c75ba2a8f56f6` |

最终源代码独立审核与281/281、lint、双模式build/budget通过；111项浏览器仅收集。额外完成后端clean verify（718通过、4预期跳过、0失败/错误），打包与原覆盖率门通过；后端源码和POM未改，命令及边界见R14-BACKEND-VALIDATION。当前浏览器IPC实际权限拒绝，R14 V3及此前pending验收仍未完成。

远端对象经只读fetch核对tree与父链后，随后创建包含本节的docs-only状态提交，再以force=false发布。源状态提交保留在 `nexus/checkpoint-r14-local-state`；源增量bundle必须已有基线 `ff09c2fed69393e69e764ddb817e95ba95ea6188`，不作为独立全量恢复包。活动分支仅在干净且完整tree一致时对齐发布父链。状态提交仍通过git历史定位，不自引用自己的SHA；恢复和继续开发首选远端长期分支，不使用源备份父链。


## R14 Redis / HTTP 验证检查点（2026-10-02）

从已发布 `377ab3deecb05fb8779bf49b7fba643de36c5bbe` 继续；本轮只增加测试入口、测试及文档，产品和后端源码/POM未改。

| 原本地源 SHA | 对应远端 commit 对象 | 完全相同的 tree SHA |
|---|---|---|
| `a0a4922cb122bce016a424c1bc42ba2871df411f` | `5d0aef4039be57d816637394a33e9bea3404c284` | `376dff18c26989216a75b50227455a7c3531d10e` |

源代码review与独立真实HTTP复跑通过：8个业务阶段、4项纯协议（TAP13包含父容器）；Redis3/3另有独立复跑，前端284/284、lint、双模式build/budget通过。111/142浏览器仅收集，完整验收pending。只读fetch已核对上述远端对象的tree与父链，随后创建docs-only提交，并仅force=false快进专用分支。

源状态检查点使用独立 `nexus/checkpoint-r14-api-local-state`，不覆盖上一轮空间增量源检查点；增量bundle需已发布基线377ab3，不是完整备份。活动工作区仅在干净且完整tree相同后对齐远端，恢复首选已发布长期分支。docs-only状态提交仍由git历史定位，避免内容自引用SHA。


## R14 创作链回归检查点（2026-10-02）

从已发布 `7953141217980e7bf496ee7ed4dfbfcfafbc4ebc` 继续；新增模型网关/Story/Recipe HTTP链及入口完整性检查，并最小修复Recipe终态createdTime与数据库重读不一致。

| 原本地源 SHA | 对应远端 commit 对象 | 完全相同的 tree SHA |
|---|---|---|
| `117c8d4ae2aaabc5511d00f1c936a431a3167f9e` | `9f77c02fd665e6ca0e6c240e4d545e596b844b8c` | `9c97360395a60afd2dfc084e4146ed4fe20993f8` |

27个真实HTTP阶段与独立复跑通过（4协议+4父容器另计，TAP35）；前端286/286、lint、双build/budget通过。后端新clean verify728项（724通过、4跳过）及原覆盖门通过，旧722项证据单独保留。111/142浏览器仍仅收集，验收pending。只读fetch核对远端对象父链/tree后，再追加docs-only状态提交并force=false快进唯一专用分支。

原源状态保留在新检查点 `nexus/checkpoint-r14-creation-local-state`，不覆盖此前源分支；增量bundle需要已发布7953141基线。活动本地只在干净且完整tree相同后对齐发布父链；恢复优先远端，不使用源备份继续开发。状态提交从git历史定位，不自引用自身SHA。


## R14 批量编辑缓存检查点（2026-10-02）

从已发布165070ed9d84dfaa5b9451f4125df263dbbc063c继续；仅补一行成功批量编辑后的既有缓存失效，并增加针对性回归。

| 原本地源 SHA | 对应远端 commit 对象 | 完全相同的 tree SHA |
|---|---|---|
| `a220a02d8429e2ca99364882f306917437a59ccf` | `bf13623d1d72367dba1b9f915c34cea0f6bbe0b0` | `abf615cf36a0054fb4aa2169ddd868ab858375a3` |

HTTP同查询红绿实证、6项控制器红绿及完整新clean verify734项（730通过、4跳过）、前端286/286和双模式build/budget通过。原27业务阶段计数不增加，浏览器111/142仅收集。只读fetch核对tree和父链后，追加docs-only状态提交并只force=false快进专用分支。

源状态用新检查点nexus/checkpoint-r14-cache-local-state，不覆盖既有源备份。增量bundle需要已发布165070e基线；活动分支仅在干净且tree完全相同后对齐远端。状态提交从git历史定位，不自引用，恢复仍优先远端长期分支。


## R14 登录注册生命周期检查点（2026-10-02）

从已发布9cc9cf6ee314acd3359884faaa5d77d37fc0ea11继续；只修LoginView/RegisterView的异步导航/重复提交并增加针对性回归。

| 原本地源 SHA | 对应远端 commit 对象 | 完全相同的 tree SHA |
|---|---|---|
| `6e29ca6a99cc2fe29eb07e4d17232770516ddc4a` | `003ae2b90b41b962b8ce3c2c7c180caca4673136` | `ec34e46d3d624412ac8aff598d62e03f5b48a0c9` |

实际SFC/RouterView/Pinia红绿、最终专门42/42、全前端328/328、lint和双模式build/budget通过；独立review及10项额外探针通过。后端/API未改，本轮不重跑历史后端数据；重点116/默认147浏览器仅收集。只读fetch核对上述tree/父链后，追加docs-only状态提交并以force=false快进专用分支。

源状态用新检查点nexus/checkpoint-r14-auth-local-state，增量bundle需要已发布9cc9cf6基线。活动分支只在干净且完整tree一致后对齐远端，不改其他用户分支。状态提交仍由git历史定位，不自引用；恢复优先远端长期分支，不从源备份开发。
