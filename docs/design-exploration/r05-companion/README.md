# R05 — Companion 设计探索原型

日期：2026-09-23。阶段：需求确认 / 设计。**这是独立静态原型，不是正式 Vue 页面、动画 Runtime 或业务实现。**

## 当前修订：A 暂留，成熟体恢复正常比例

- 用户指定设定页左侧的正常比例全身立绘作为 A 的 Web 成熟体；Home、Shell 与小尺寸观察均使用这一形象，Conversation 保留同角色半身。
- 原 Q 版放入独立「Codex 式桌宠参考」区；保留现有图像，不将它当幼体，也不在本轮制作桌宠。
- B/C 已冻结，原型按钮不可切换；图像与 Art Brief 继续留档。A 是暂定保留，技术路线与最终方向仍由用户在另一个任务讨论。
- 本轮仅更改既有原型和说明，没有新增/重生成图片；正常比例全身使用既有透明预览页的 CSS 裁切，去除相邻蛋图与字母，不修改 PNG。
- 后续头像修订：未成熟体的 Conversation 改用自身形象的独立头肩近景，与成熟体保持相近的半身构图；Home / Shell 仍使用未成熟体全身。沿用现有设定页裁切，未新增画像。
- 用户随后明确要求提交推送当前探索稿。本次归档不代表最终方向或技术路线已经选定，不自动进入第二阶段。

## 打开

直接用浏览器打开 [index.html](index.html)，不需要 npm、构建或后台服务。

本次检查使用仅绑定本机的预览：[本地 R05 原型](http://127.0.0.1:8765/design-exploration/r05-companion/index.html)。服务关闭后可直接打开 HTML，或在仓库根目录运行：

```powershell
python -m http.server 8765 --bind 127.0.0.1 --directory docs
```

正式设计入口是 [R05 第一阶段报告](../../R05-Companion-Identity-计划.md)。本轮没有修改外部 work-log，避免把探索建议写成已经确认的产品决策。

## 可以检查什么

- A 绫页的正常比例成熟体；B 汐砚 / C 陶眠以冻结档案保留。
- Shell / Home / Conversation 三个位置；Adult / Juvenile / Egg 三阶段。两组切换相互独立。
- 正常比例成年形态在 48 / 64 / 80px 的缩略观察，以及单独保留的 Q 版桌宠参考；保留文字入口。
- 伙伴入口关闭、手机底部入口、窄屏 Portrait 缩略、可关闭呼吸示意、弹层 inert 与动效暂停。
- Home 的角色落点、照片架和未来纪念品留位；不包含真正房间、旅行、收藏或背包业务。

所有图像是设定页在浏览器中的展示裁切。**不是逐帧 Sprite Sheet，也不是已经完成的 Live2D / Spine 模型。** 原型为了比较会预留/加载概念资源，不能据此声称生产开关关闭后的代码拆包或零网络请求已验证。

「呼吸示意」默认关闭，只是整个缩略图上下移动 2px，不代表骨骼呼吸或 R07 动画状态机。系统要求减少动态效果时有 CSS 与 `matchMedia` 两层静态分支；页面不可见时停止主动示意。Hover / Focus 改变边界与焦点环，没有随机打扰或假通知。

## 全部新增文件

相对仓库根目录；本轮全部工作均位于这些新文件，没有修改原有产品源码。

| 文件 | 内容 |
| --- | --- |
| `docs/R05-Companion-Identity-计划.md` | 仓库事实、20 问回答、边界与待选择事项 |
| `docs/R05-世界观骨架.md` | 雾屿 / 小屋、主动分享、记忆来源与未来生活边界 |
| `docs/R05-角色体系设计.md` | Species / Instance、兼容风险、三层表现与资产清单 |
| `docs/R05-身体技术路线评估.md` | Sprite / Live2D / Spine 官方来源和 18 维度比较 |
| `docs/R05-第一位伙伴概念探索.md` | 三套 Art Brief、成长关系、优点与未通过项 |
| `docs/design-exploration/r05-companion/index.html` | 原型入口 |
| `docs/design-exploration/r05-companion/prototype.css` | 隔离样式 |
| `docs/design-exploration/r05-companion/prototype.js` | 仅本页的展示切换，无 API / 存储 / 业务状态 |
| `docs/design-exploration/r05-companion/assets/concept-a.png` | A 原始探索页，1536×1024 RGB |
| `docs/design-exploration/r05-companion/assets/concept-a-ui.png` | A 经 imagegen 去背景的 UI 预览副本，RGBA |
| `docs/design-exploration/r05-companion/assets/concept-b.png` | B 探索页，1536×1024 RGBA |
| `docs/design-exploration/r05-companion/assets/concept-c.png` | C 探索页，1536×1024 RGBA |
| `docs/design-exploration/r05-companion/prompts.md` | 三次生成及一次去背景编辑的完整 prompt |
| `docs/design-exploration/r05-companion/README.md` | 本说明、实际验证、文件清单 |

图片使用内置 `image_gen.imagegen`；没有 CLI API 调用、第三方角色输入或生产资源覆盖。保留生成原图，UI 去背景版本单独命名；生成式编辑仍可能有细节漂移。四张 PNG 合计约 8.27 MiB，是探索资料体积，绝非建议的生产常驻包体。

## 初轮探索已执行的验证（冻结前）

### 仓库与只读事实

- 核对 status、HEAD、10 条日志、branch -vv、main / origin/main 本地引用及 R01～R04 祖先关系。
- 读取 Companion 页面、聊天、Feed、Shell/路由/开关/样式/API，以及后端聚合、成长规则、情绪/关系/记忆、Feed 编排、聊天上下文、Story 草稿和数据库 changelog。
- 在新建产物前对已有 53 个未提交/未跟踪文件做 SHA-256 快照，并记录 HEAD 和 index tree。完成时 53 个内容哈希全部一致，HEAD 与暂存区 tree 均一致。
- 会话期间另观察到 `li-picture-cloud-frontend/e2e/landing.spec.js`、`e2e/shell.spec.js` 的外部修改；它们不在本轮写入集合内，没有修改、暂存、回退或推断其作者。保护核验不冒称锁住其他并行会话。

### 原型浏览器检查

实际工具：Codex 内置浏览器 / Chromium UI 控制；本地静态 HTTP 服务。没有真实账号、图片、后端或外部 API。

| 项目 | 实际结果 |
| --- | --- |
| 3 角色 × 3 阶段 × 3 位置 | 27 组切换，角色/阶段/位置与可见 panel 一致；1440px 下均无页面横向溢出 |
| 响应式 | 320 / 390 / 768 / 1024 / 1440px 各核对三个位置，共 15 组；无页面横向溢出，手机侧栏隐藏、底部入口出现 |
| 图片加载 | 已观察的三套方案及 A 去背景预览均可加载；原图可单独打开 |
| 入口关闭 / 恢复 | Presence 与手机入口隐藏，静态关闭说明出现；恢复按钮正常 |
| 弹层 | 原生 dialog 打开时背景带 inert、呼吸 animationPlayState 为 paused；关闭后解除 inert |
| 键盘 | Esc 关闭 dialog 后焦点回到打开按钮；Tab / Enter 检查原型切换与可见焦点环 |
| 动效 | 默认静态；打开/关闭呼吸示意有效；系统 reduced-motion 的 CSS / JS 路径已源码核对，当前系统偏好为 false，未伪称已完成系统设置切换实测 |
| 控制台 | 最终检查未见 error / warn 日志 |
| 可视检查 | 检查设定页、Home、窄屏 Conversation、48/64/80px；发现并修正 C 裁切邻图碎片与场景文字靠近角色的问题 |

浏览器第一次按完整标签定位 select 时遇到定位不匹配，改用已知控件 id 后完成全部检查；不是业务或图片加载失败。完整长截图在内置浏览器的 viewport override 下曾出现拼接重复，视觉复核使用单视口截图和 DOM 几何结果；没有把异常长截图当成验收产物。

### 文件与设计复核

- `node --check docs/design-exploration/r05-companion/prototype.js` 通过（本机 Node 24.19.0；不代表项目要求的 Node 22 生产验证）。
- 新增 Markdown / HTML 本地引用和 UTF-8 编码核验通过；只检查本轮文件，不改历史文档。
- 独立只读设计复核检查旧阶段、多实例迁移、记忆与世界观边界、成长/表示正交性。修正了「打开图片」可能被理解为 Feed 的措辞，明确上传/打开/预览不等于分享。
- 技术评估只采用官方文档、官方仓库与许可证；未下载商业模型、未购买许可、未宣称本产品已获许可豁免。

## 本次比例修订的验证（A 暂留、B/C 冻结后）

- A 的 3 阶段 × 3 位置共 9 组切换通过：场景与阶段一致，成熟体全身、对话半身和独立桌宠 Q 版的裁切互不混用；B/C 始终禁用。
- 在 320 / 390 / 768 / 1440px 下核对三个位置，共 12 组，无页面横向溢出。Home 正常比例立绘保持 760:1000 比例，完整落在场景范围内；手机底部入口和桌面侧栏按断点显示。
- 单视口截图复核 320px 手机 Home、768px Home、小尺寸三档及独立 Q 版参考区；正常比例立绘没有夹带设定页字母或相邻蛋图，头顶与脚底完整。
- 伙伴入口关闭与恢复通过；图片加载正常；最终浏览器检查未见 error / warn 日志。JS 语法检查通过。
- 复核本次开始时记录的 59 个保护文件（既有修改及四张 PNG）：内容哈希全部一致，HEAD 和暂存区 tree 未变。分支仍为 `feat/r05-companion-identity`；没有提交或推送。

后续头像修订已通过 JS 语法检查；在 320 / 1440px 核对三阶段资源选择与页面宽度，未成熟体头像完整位于头像框内，Home / Shell 仍使用对应全身资源。另以 640px 手机布局、1024px 桌面布局截图复核头肩构图；图片加载正常，浏览器未见 error / warn 日志。

## 当前不能据此验收的部分

1. **美术**：A 的 Juvenile 结构差异不足、B 的 Juvenile 偏低龄、C 的幼体陶板差异与小尺寸碎鳞仍需修订。三者都不是量产母版。
2. **识别度**：48px 能看到大轮廓，不保证陌生用户能认出品牌。A 翼缘、B 壳、C 尾部仍需专门小尺寸重画；尚未做用户盲测。
3. **技术**：没有真实帧图集、骨骼或 Live2D 样本；CPU/GPU/内存/功耗没有 Runtime benchmark。文档中的数字仅为有假设的面积计算。
4. **无障碍 / 多端**：完成的是隔离原型的键盘与布局检查；未进行完整屏幕阅读器、系统减少动态效果、Safari、Firefox 或实体移动设备验收。
5. **业务**：没有改生产代码，故未运行后端、数据库或正式前端全套测试；不把这次原型验证描述为 R05 第二/三阶段完成。

## 停止条件

按用户最新指示提交推送当前第一阶段探索稿后停止。A 暂留、B/C 冻结；技术路线和最终方向继续讨论。没有正式资产生产、Vue 集成或 schema / API 修改。
