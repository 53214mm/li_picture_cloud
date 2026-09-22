# R04 — Image-first Gallery 需求确认报告

日期：2026-09-22

阶段：实现 / 迭代 · **用户已对定向修订下达“实施”指令；本轮实现完成，等待人工验收。**

## 最新修订：保留原版挑图体验（优先于全部历史提案）

此前 Astra / Kimi 方案均未获整体认可。当前仅实施定向修订：以正式 GalleryView 为基线，保留原版规则等大 4:3/cover 网格、每页 12 张、名称/作者/完整日期/标签图内常驻、原黑色硬投影/轻位移/缩放、列表直接进详情及完整分页；吸收 Kimi 搜索框与局部检索控件的优点。手机补回跳页能力，不强塞两列。

明确撤销“图片零遮挡、作者时间必须移到图下、必须移除黑边硬投影、点击先开 Viewer、每页改 16、默认隐藏标签”作为本轮硬标准。先前的 original-refinement 亦未获认可，不作为本轮已接受基线。

实施起点 `kimi@5809dd2bf948971cac8ae98eba3dedf852fdc142`。起点的正式 Gallery / Detail / style.css / tokens.css 与 c951ec6 无差异。定向 [前后对照原型](design-exploration/r04-gallery-targeted/index.html) 和 [设计记录](design-exploration/r04-gallery-targeted/README.md) 保留；正式改动只落在 GalleryView，另加前端隔离浏览器测试。详见 [定向优化实现记录](R04-图库定向优化-实现记录.md)。未提交或推送。

字段、搜索语义、标签 AND、分页接口限制、Gallery/Space/Admin 边界等调查事实继续保留。本轮修正列表 error/empty 混淆、请求竞态及手机跳页；完整 URL 筛选同步、同页 query 监听和返回滚动位置仍延期。旧本地 Kimi 原型受浏览器安全策略限制，长图根因仍未证实；本轮通过正式前端的 1:12 图片 fixture 验证新画框，不倒推为旧原型已复现。新的状态以本节为准，下文冻结与推荐均为历史记录。

## 历史冻结状态（已由上方定向修订恢复，仅恢复设计）

- 用户最新反馈：“依旧不好看，R04需求冻结，将你现在的成果推送上去，我让kimi去看看”。
- 冻结意味着停止当前设计迭代与实现，**不是需求通过、方向收口或验收完成**。
- A 行式相册、B 瀑布流、C 展墙、D 整齐网格，以及最后的“原版 / 微调版”均未被采纳。不得将某个历史推荐当成最终决策。
- 用户曾明确偏好基于原版优化，但最后一次基于原版的微调也被否定；可把原版作为后续审视的参照，不能把该微调当已接受的起点。
- 当前成果以调查依据、失败的设计探索和反馈轨迹交接，不作为生产实现规格。正式 Gallery / Picture 页面保持原样。
- 本次仅提交并推送本报告及离线原型目录；原有后端修改、文档迁移和暂存内容全部排除。
- 交接入口：[R04 冻结交接说明](design-exploration/r04-gallery/README.md)、[原型索引](design-exploration/r04-gallery/index.html)。后续是否解冻、如何推进，由用户与 Kimi 决定。
- 下文“待选择”“推荐”“未提交推送”等属于对应时点的历史记录；以本节冻结与交接状态为准。

## 历史方向：基于原版优化（微调结果亦未采纳）

用户看过 D 后仍认为不如原版，明确要求“基于原版优化”。**A–D 均未采纳，下面的初版推荐及 D 试看记录只作历史，不再作为实施方向。**

新入口：[原版 / 微调版对照](design-exploration/r04-gallery/original-refinement.html)。直接提取当前 GalleryView.vue 的 scoped CSS，并加载实际 legacy style.css；保留原版标题、控件分组、常驻筛选和标签、内容宽度、网格密度 / 间距、4:3、每页 12 张、手机单列，以及名称 / 作者 / 日期 / 标签常驻显示。对照两态用同一组本地图片与相同查询、页码，不通过更换素材制造差异。

目前只提出小幅优化：轻边框 / 6px 圆角、移除硬偏移阴影和悬停位移 / 放大、柔和选中态、增加清除筛选和搜索无结果文案。未折叠原有控件，未隐藏图片信息，未引入新 Viewer 方案；点击弹出全图仅为离线素材查看，正式点击仍以现有详情路由为基准。Shell 是简化示意、业务是样本模拟，不能声称像素级复刻整个应用。

沿用 prototype 技能，只做原版的最小差异验证，不开展第五套独立设计。仍在设计阶段，等待用户试看后收口；未修改正式 Gallery / 其它业务、未提交推送。脚本 / 路径可静态检查，浏览器实测仍受前述安全策略限制。

## 历史反馈与 D 方向试看（已被上述方向替代）

用户否定了 A / B / C 的观看体验，明确认为图片大小参差不齐、不如原版舒服。下文第 22 节的“推荐 A”是初版判断，**已不作为当前建议**；原调查事实保留。

按“先做套看看”的要求，新增 [D 整齐网格原型](design-exploration/r04-gallery/direction-d.html)：沿用原版等大 4:3 预览和每页 12 张，统一行列 / 间距；桌面按可用宽度排等宽列，手机两列；去硬边框 / 硬阴影 / hover 位移，标题仅 hover/focus 出现，筛选折叠。点击打开完整图查看器，信息可展开；列表才使用 cover 裁切，查看器仍 contain。Loading 也统一 4:3。

这次仅验证“统一尺寸是否更舒服”，**不代表用户已最终接受裁切政策或选定 D**。初版保留比例的验收项、每页 16 建议及 A 推荐应在用户看过 D 后重新收口，不能直接用于实现。A/B/C 保留历史对照；比较首页与底部切换已加入 D。没有正式页面变更，没有提交推送。沿用 prototype 技能的隔离演示方式，不新增另一轮多方向发散。此前浏览器安全限制仍按未完成视觉实测记录，不绕过。

## 1. 当前 Gallery 真实实现与基线

- 分支：`kimi`；HEAD：`c951ec6f2545ac87cd52721538bec35330595643`，开始时与本地已知 `origin/kimi` 一致。本轮未 fetch，不把远端跟踪引用等同实时远端查询。
- 已只读执行 `git status --short --branch`、`git rev-parse HEAD`、`git log --oneline -10`。
- R01：`b7daf4d` 需求收口、`18232d8` 视觉基础设施、`e3a7d5f` 标题继承兼容修复。
- R02：`17d9647` 需求确认、`0192c22` App Shell、`9ddb11c` 导航 / Toolbar 细化。
- R03：`99a6e83` 方向 B 收口、`c951ec6` 方向 B 实现。仓库实现记录仍写等待人工反馈；本次用户任务明确声明 R03 已人工验收，按该声明作为前置。Git 提交本身不能证明人工验收，未额外虚构验收记录。
- 原有 dirty：37 个 Companion Java 文件修改；9 份旧文档删除（6 份已暂存、3 份未暂存）及其 `docs/轮次/` 新位置未跟踪文件；`docs/codex-review-learning/` 未跟踪目录。全部绕开，不 stage、不覆盖、不提交。
- 本轮只新增本报告与 `docs/design-exploration/r04-gallery/` 六个离线原型文件。

真实结构，见 `li-picture-cloud-frontend/src/views/GalleryView.vue`：

```text
Public Layout / App Layout（由原有 adaptive 决策）
└─ GalleryView → legacy .container
   ├─ 页头：探索图库；仅管理员有本地“上传图片”按钮
   ├─ 搜索：名称 / 简介关键词
   ├─ 筛选：分类、格式、时间排序
   ├─ 标签按钮：元数据返回后全部显示，可多选
   ├─ loading → 一行“加载中…”
   ├─ 无记录 → “暂无图片，上传第一张吧！”
   ├─ 列表：页面内自写 grid，每项 4:3
   │  └─ 图片 + 常驻渐变 / 名称 / 作者时间 / 最多 3 个标签
   └─ 上一页、页数 / 总数、下一页、页码跳转
      点击项 → /picture/:id
```

没有独立的 Gallery error 区，也没有浏览 / 选择模式切换。

## 2. 当前图片组件关系

| 使用方 | 实际展示方式 | R04 影响控制 |
| --- | --- | --- |
| GalleryView | 自己渲染卡片，未引用 PictureList | 可以独立迁移，无需先改共享组件 |
| SpaceDetailView | 引用 PictureList，传 selectable / selectedIds、动作 slot、权限开关 | 保留旧组件及调用方式 |
| MySpaceView | SpaceCard / SpaceSection，展示空间不是图片列表 | 不迁移 |
| PictureManageView | 独立管理表格、审核标签、上传者、批量抓取等 | 不迁移 |
| PictureDetailView | 独立路由、全图、信息与业务操作 | 保留管理能力，谨慎设计返回上下文 |
| ShareModal / ImageEditModal | 分享链接 / 外部二维码；图片编辑及协同连接 | 不直接搬进新 Viewer |

在前端 Vue 范围检索 PictureList 调用，当前消费者为 SpaceDetailView；不存在一个已被 Gallery / Space / Admin 全部共用的通用 ImageGrid。`PictureList.vue:52` 已有 role / tabindex / Enter / Space，而 Gallery 自写的 clickable div 没有，这两者不能混为一谈。

## 3. 当前 Gallery 的具体问题

| 检查项 | 代码事实与判断 |
| --- | --- |
| 图片占屏 | App 1440×900、224px 侧栏、68px Toolbar、标签一行的假设下：内容宽 1152px，三列各约 368×276；首张图大约从 y=390–400 开始。扣掉列 / 行间距后，首屏图片框约占整个 viewport 的 40% 左右。**这是 CSS 几何估算，不是浏览器测量**；未扣常驻遮罩，实际无遮挡画面更少。访客 / 标签换行 / 窗口尺寸会改变结果。 |
| 控件重量 | 页顶 padding 48px，标题约 40px + 24px 间隔；搜索、三个 select、标签三层常驻。叠加 App Toolbar，首屏约 320px 高消耗在 Gallery 自身顶部结构上。 |
| 边框阴影 | 每图 2px 边框；hover 位移 (-2,-2)、6px 硬偏移阴影，与 R01 轻层级冲突。 |
| 元信息 | 名称、作者 / 日期、最多三个标签常驻，底部黑渐变长期覆盖作品；不是按需显露。 |
| Hover | 图放大 1.05、整项抬起；元信息本来就可见。没有逐层信息机制。 |
| 手机 | Gallery 元信息不依赖 hover 才能看到，但 hover 效果没有专门降级；点击图进入详情。Space 的 PictureList 快捷操作另有 hover:none 规则，不能归到 Gallery 名下。 |
| 裁切 | 全部 `aspect-ratio:4/3` + `object-fit:cover`；竖图、全景都损失构图，hover 缩放进一步裁切。 |
| 分页 | 每页 12；上一页 / 下一页 + 页码跳转；翻页主动 smooth 滚到顶部。手机隐藏跳转器。 |
| 搜索状态 | 输入框内保留词，但缺少结果条件摘要；输入草稿与已应用条件没有区分。 |
| 复位 | 分类、格式可各选“全部”，tag 逐个取消，没有统一清除入口。 |
| 点击 | 直接 router.push(`/picture/${id}`)，没有 Viewer / Drawer。 |
| 返回状态 | q / page 可由 URL 重建；category / tags / format / order 不在 URL，组件重建后丢失。Router 无 scrollBehavior，App 无 KeepAlive，不能保证图片位置恢复。不能据此断言每次必然回到顶部。 |
| 加载跳动 | 图片项自身 4:3 已能占位，不应声称图片 decode 必然导致 CLS。但 loading 分支把整个 grid 换成短文本，完成后恢复长列表，会改变文档高度；详情图也缺少显式尺寸占位。 |
| 空状态 | 无数据、搜索无结果、请求错误都容易落到同一句上传提示；错误 catch 清空图片但未同步清零旧 total，可能残留分页。 |
| 登录差异 | adaptive 外壳不同；公共接口统一只给公开且审核通过图片。管理员本地上传入口与 App 全局上传入口是两个层级；普通登录者虽无 Gallery 本地按钮，仍有 Shell 上传入口。 |

来源重点：`GalleryView.vue:57–98,121–178,238–272`；`router/index.js:119`；`App.vue:3`。本轮不把这些代码观察包装成已经完成的线上性能 / 回归结论。

## 4. 图片 API 与字段事实

前端 `src/api/picture.js`：

| 能力 | 真实接口 / 行为 |
| --- | --- |
| 公共列表 | POST `/picture/list/page/vo/cache`，current / pageSize → records / total |
| 非缓存 VO 列表 | POST `/picture/list/page/vo`，不是新的搜索引擎 |
| 详情 | GET `/picture/get/vo?id=...` |
| 元数据 | GET `/picture/tag_category` |
| 编辑 / 删除 / 上传 | `/picture/edit`、`/picture/delete`、`/picture/upload`，有原有业务与授权边界 |
| 下载 | Detail 通过 `/api/picture/download/:id` 下载，不是简单取 thumbnailUrl |
| 收藏 | Gallery、Detail、picture API 中未发现收藏功能，不设计收藏按钮 |
| 以图搜图 | 另有 Bing 搜图接口与管理入口，不是当前 Gallery 的语义搜索 |

后端只读证据：`model/vo/PictureVO.java`、`model/dto/picture/PictureQueryRequest.java`、`controller/PictureController.java:220–312`、`service/impl/PictureServiceImpl.java:185–247`。

| VO 字段 | 事实 / 设计使用 |
| --- | --- |
| `url` | 展示图片地址；压缩成功路径下可为 WebP，不等同原始上传字节 |
| `thumbnailUrl` | 字段存在；列表现在 `thumbnailUrl || url` |
| `originalUrl` | 原始格式地址，主要下载语义；不用于一次性加载整页原图 |
| `picWidth`, `picHeight` | 真正尺寸字段，**不是 width / height**；可用于提前占位 |
| `picScale` | 存在，但上传代码只保留两位小数；优先由宽 / 高计算准确比例 |
| `name`, `introduction` | 当前关键词检索目标；空标题已有 fallback |
| `category`, `tags` | 分类字符串 / 标签数组，可映射筛选 |
| `picFormat`, `picSize` | 格式 / 体积；详情展示，不默认铺满卡片 |
| `createTime`, `editTime`, `updateTime` | 时间字段；列表默认按 createTime 排序 |
| `userId`, `user` | 所有者及用户 VO；不是作者身份认证事实 |
| `spaceId` | 可知空间标识，VO 没有 spaceName；不能虚构空间名称 |
| `reviewStatus`, `reviewMessage`, `reviewerId`, `reviewTime` | 管理 / 详情事实；不能因为字段存在就铺到公共图库 |
| `id` | 内部关联及路由 key，不出现在公共 tile 上 |

尺寸可靠性边界：上传路径会填写尺寸，但没有读生产数据，不能保证历史记录零值 / 缺失 / EXIF 转向全部正确。实施需校验有限正数，并设计异常 fallback。

缩略图：`manager/CosManager.java:61–79` 生成压缩 WebP；文件大于 2MB 才另生成 `thumbnail/256x256>`。`PictureUploadTemplate.java:60–70,103–140` 中无独立缩略图时可复用压缩对象；基础 fallback 路径可能只有原图 url。图片地址来自 COS host 配置；**代码未证明当前环境已启用独立 CDN，也未证明缩略图实际命中率**。不读取密钥配置，不改图片处理后端。

缓存不是授权替代：cache 接口在命中缓存前仍检查空间权限；带 spaceId 的调用也经过该缓存逻辑，不能描述成空间必然绕过缓存。

## 5. 当前搜索 / 筛选 / 分页行为

- `query.searchText` 只在 setup 由 `route.query.q` 初始化；首次打开 `/gallery?q=山` 会带词请求，但不是防御性 query parser。
- 已在 Gallery 时，外部导航只改 q、复用同一组件，不触发重新搜索：没有 route-query watch。页面内按钮 / Enter 则直接调用 handleSearch，可以请求。
- syncUrl 每次重新构造只有 q / page 的对象并 router.replace；不是完整筛选快照，还会抹掉其它 query。
- 分类、格式、多个标签、排序只在内存；标签在后端循环追加 LIKE 条件，为 **AND / 同时满足**，不是 OR。
- 后端 searchText 是 `name LIKE ... OR introduction LIKE ...`；分类等附加条件再与之相交。不是 embedding / AI 语义检索。
- `sortField=createTime`；当前 UI 只有 ascend / descend；格式后端用 LIKE，不能把它表述为已经严格枚举等值过滤。
- 元数据请求 await 后才请求图片；元数据失败被吞掉，成功 / 失败最终仍继续列表。列表请求缺乏取消或“仅最后请求可落地”的序号保护。
- public VO 两个分页接口都限制 `pageSize <= 20`；公开请求强制 `reviewStatus=PASS`、`nullSpaceId=true`，包括管理员在这个公共入口看到的结果。
- 值得做轻量 URL 同步，但必须限定为 Gallery 的展示状态，不改变搜索 API、Pinia 或路由授权。

## 6. 当前 Detail 行为

`PictureDetailView.vue`：独立路由；图片与信息两列，主图宽度 100%、高度自然，点击打开全屏；全屏图 `max-width:95vw / max-height:88vh / object-fit:contain`，支持 Esc。现有全屏没有上一张 / 下一张，未看到焦点陷阱、背景 inert 或完整位置恢复机制。图片触发区还是 div，无键盘动作。

信息：名称、用户、简介、分类、格式、宽高、体积、上传 / 编辑 / 审核时间、标签；有 reviewStatus 即显示审核标签，待审管理员有审核动作。获取 spaceId 对应权限与空间信息用于能力判断，不等于当前页面展示了空间名。

业务：分享调用 ShareModal；下载使用受控下载路径；编辑信息、图片编辑 / 协同、删除仍在 Detail。ShareModal 当前二维码来自外站，离线原型不调用；ImageEditModal 会连协同服务并保存替换图片，不能当纯展示组件直接复用。

权限风险记录而非修改：`canEdit` 允许管理员、所有者或 picture:edit 权限；现有删除按钮也复用 canEdit，没有像 Space 那样单独用 picture:delete gate。后端仍裁决授权；R04 不趁机改这套业务逻辑，也不复制该判断到 Viewer。

返回按钮无条件 `$router.back()` 却写“返回图库”，实际来源可能为 Space/Admin/外部。删除成功 replace `/gallery`，丢失来源条件。详情取数只在 mounted，未来同组件切换 id 需要另做参数响应，当前不假装已支持。

## 7. Public / App 边界

`constants/shell.js` 保留 Gallery / Picture Detail 的 adaptive 元信息。访客 Public；登录用户 App；不绕开 auth-ready 判定，不更改登录或导航。

设计：两种 Shell 内共享同一 Gallery 内容。Public 只留小号“图库”标题，与单行搜索 / 筛选并列；App 已有位置标题，内容保留语义 H1 但可 visually-hidden，不再增加巨大页头。App 全局“图库搜索”继续是进入图库的入口，不把它改成第二个搜索框。图库搜索始终在内容区易找，手机不依赖 Shell 的桌面搜索链接。

Gallery 自身使用流式宽度与 gutter，不要求全站改 max-width，也不改 App Shell。原型用 224 / 88px 侧栏与约 68 / 60px 顶栏作尺寸参照，并能切 Public；这些是静态简化参照，不是精确复刻已验收 Shell，更不是 R02 重新设计。导航与伙伴位不承担真实动作。

## 8. Gallery / Space / Admin 边界

公共 Gallery 负责公开作品发现与搜索，不加批量管理条，不暴露审核 / ID。Space 负责某个权限空间内浏览、成员与批量操作；Admin 负责审核与管理效率，表格合理。

未来共享到图片渲染、比例算法、占位、纯只读查看能力为止；搜索条件、空间上下文、批量选中、上传目标、权限动作由各页面负责。本阶段不让 Space / Admin 被动消费新样式，也不修改旧 PictureList API。

## 9. Image-first 原则与六个产品回答

1. **定位**：相册式浏览 + 可检索公共图片库的混合形态。不是默认带收藏发现机制的 Pinterest，也不是以审核 / 文件字段为中心的资产后台；不强加人工策展语义。
2. **第一动作假设**：直接进入者可能先扫图再点开；从“图库搜索”进入者可能先输入关键词。没有行为统计，这是设计假设，不是用户数据。默认同时容纳扫图与一个明显但紧凑的搜索入口。
3. **默认信息**：A/B 每项只见图片；一行轻量总数 / 筛选摘要属于列表；标题在 hover/focus，更多元数据在 Viewer。C 只常驻短标题题签，作为较慢的对照。
4. **看图 / 管理分层**：列表 → 只读查看 → 独立详情中的授权动作；不在 hover 上塞编辑、删除、审核。
5. **公共 / Space 共享程度**：共享无领域渲染能力，不共享权限和批量工作流，Space 以后自行接入。
6. **第一眼变化**：图片更靠前、更完整；没有厚边框和常驻黑遮罩；控制只占小区域，筛选存在时又能一眼看清当前条件。

保持 R01 雾屿纸境语义 tokens；不新增主题，不引入 Noto Serif SC，不做紫色渐变、硬偏移阴影或全局暗房切换。

## 10. 布局比较与比例策略

| 方式 | 优点 | 代价 / 与现状适配 |
| --- | --- | --- |
| Masonry 瀑布流 | 原比例自然、竖图表现好、浏览连续 | CSS columns 的视觉 / 键盘顺序按列；追加后可能重排。自定义最短列需要处理顺序与 resize；不凭现代感直接选它 |
| Justified 行式相册 | 行序与 DOM 顺序一致，原比例、密度和分页易兼容 | 需小型行分配算法；极端比例、尾行和 resize 要测试。真实 picWidth/Height 已支持它 |
| CSS Grid + 自适应比例 | CSS 简单，读序稳定，尺寸易预留 | 同行被最高竖图撑高，横图下留下空白；不能靠 dense 排序破坏读序来掩盖 |
| 规则网格 + 局部比例变化 | 易实现，少量跨行可增加节奏 | 固定格仍可能强裁；contain 会有留白，跨格容易引入排序暗示，适合作为保守 fallback 而非最终目标 |
| Editorial / Museum | 大幅作品、留白、题签适合慢看 | 密度低、滚动长；自动突出首项不等于“精选”。不增加后台策展字段 |

推荐 A 的列表策略：先验证 picWidth / picHeight，算精确 ratio，按行分配；不统一裁 4:3。普通 ratio 完整展示；过宽 / 过高的极端图用有限尺寸槽 + contain，接受留边，不通过 cover 隐藏问题；尾行左对齐，不为铺满任意拉伸。若字段缺失，可用有效 picScale 作近似；再缺失用稳定 4:3 占位 + contain，加载后不要突然大幅改整页布局。比例异常率需在实施 / 验收记录。

详情 / Viewer 一律完整图 contain，不承诺像素级 1:1 放大、拖拽、手势缩放已具备。这里的“完整”指不裁切，不是强制下载原始字节。

原型素材事实：8 张本地 JPG 为 600×400、600×766、800×492、600×899、800×632、600×900、600×397、800×533。重复为 32 项，默认 16；部分模拟槽为 2.8:1、0.48:1、1:1、2:1，用 contain 保留原素材全图，题签说明“容器比例模拟”。这验证的是布局候选，不证明线上尺寸数据质量。

## 11. 三套候选方向

原型入口：[方向比较](design-exploration/r04-gallery/index.html)。三套共享样本与 tokens，结构和流程不同。

| 比较维度 | A：行式相册（建议） | B：自由瀑布流 | C：留白展墙 |
| --- | --- | --- | --- |
| 1 Desktop | 横向整行、窄缝、紧凑控制 | 多列上下自然流动 | 大幅 / 双幅交替、轻题签 |
| 2 Mobile | 通常两项成行，必要时单幅，8px 缝 | 双列、8px 缝 | 480 以下单列，以上双幅，留白较多 |
| 3 排列 | ratio 算行高 / 宽，保留数据顺序 | 原型 CSS columns，读序按列 | 稳定 Grid 节奏，不额外策展 |
| 4 宽高比 | 自然比例，极端 contain 槽 | 自然高度，极端槽限制 | 完整画面置于有限展示区域 |
| 5 默认信息 | 只看图 | 只看图 | 图下一个短标题 |
| 6 Hover / Focus | 局部淡入标题，不缩图 / 位移 | 同样只露标题 | 已有标题，focus ring 标识当前项 |
| 7 点击 | 打开全屏轻量 Viewer | 打开右侧大 Drawer | 进入完整详情页式界面 |
| 8 Search / Filters | 单行搜索 + 内联展开筛选 | 搜索 + 筛选 Drawer | 搜索 + 默认可见紧凑筛选行 |
| 9 分页 | 页码 / 前后页，建议 16/页 | 显式 Load More，原型最多 32 | 页码 / 前后页，16/页会很长 |
| 10 Detail | Viewer + 可展开信息；管理仍去独立页 | 图 + 信息常驻侧栏内 | 图与信息完整并排，手机上下 |
| 11 返回上下文 | 关闭即回原位置 / 焦点；路由详情另存来源 | Drawer 保留背景；追加列表需恢复 loaded pages | 独立路由要恢复 query、数据、scroll |
| 12 Loading | 与预期比例相近的静态占位，更新保留已有图 | 列式占位；append 状态在底部 | 大幅空位 + 轻量加载提示 |
| 13 Empty | 区分库空 / 搜索空，清除条件 | 同 A，加载失败不丢旧列表 | 同 A，空区不做情绪化插画 |
| 14 性能 | 单页有界，行布局可计算 | 累积 DOM、图片内存、列重排风险更高 | 大图尺寸 / 原图回退成本较高 |
| 15 App Shell | 不加重复大 H1，不改 Shell | 同 A，侧栏叠加可能挤压工作区 | 搜索 / 筛选常驻更高，空间效率弱 |
| 16 Space 复用 | 可以后 opt-in tile / 行布局 | columns 可复用但批量顺序须重审 | 不推荐 Space 管理采用展墙节奏 |
| 17 优点 | 平衡原构图、找图、上下文和页码 | 竖图多时连贯、发现感强 | 每幅更有观看空间、信息易读 |
| 18 风险 | 行算法、未知 ratio、低清缩略图 | 视觉读序、追加重排、跨页恢复 | 首屏密度低、很长、容易暗示精选 |

每套都有 Default / Hover 示意 / Filtered / Search Empty / Empty / Loading / Error 状态。当前共享的加载演示是静态混合比例骨架，用于表达密度，不是每种算法的生产 skeleton 已实现。A/B 查看器和 C“页式详情”都只在离线文档中模拟；C 不是已经接入 Vue 路由的详情页。

## 12. Desktop / Mobile

统一断点 480 / 768 / 1024，CSS 中用字面量；图片列表不设强制 1200 / 1440 hard max-width。大屏增加每行 / 列容纳量，不把每幅无止境放大。App 可用宽度扣除侧栏后计算，不能只依赖 viewport。

手机点击图片直接查看，不发明长按和双击行为。标题可在查看器中读取；不要求先 hover 才能操作。搜索保持一行可编辑控件与提交；筛选要有可见“筛选”文字和已应用条件摘要。A 内联折叠；B 全高筛选 Drawer；C 持续显示但在手机重排。触控目标至少 44px，保留底部导航与安全区空间。

Viewer：桌面 A 图片为主，信息 330px 左右可展开；手机同一查看任务改为全屏图 / 下方信息，提供固定可见关闭。B 桌面侧栏、手机全屏；C 桌面独立详情两列、手机单列。这不是全站响应式 R13。

## 13. 搜索与筛选设计及 URL 影响

推荐搜索字段显式 label，placeholder“搜索图片”，辅助说明“按名称或简介”；输入为草稿，Enter / 搜索才应用，不逐键请求。默认仅搜索、筛选、轻量总数。分类 / 格式 / 时间排序 / tags 放第二层；多标签注明“同时满足”，不改变后端语义。

筛选存在时列出 chips，单项移除与“清除筛选”均可操作，清除恢复默认最新排序及第一页。应用筛选统一回第一页；无结果不能把整个工具栏隐藏。

建议后续轻量 URL：`q`、`category`、`format`、重复 `tag`、`order=ascend`、`page`。默认值省略；后端仍接收现有 searchText / category / picFormat / tags / sortOrder，固定 sortField=createTime，不传任意排序字段。解析时规范 array/string、正整数 page，过滤无效枚举、限制输入长度；处理越界页。

状态路径：URL 的规范化快照 → 一次 watch → 列表请求。页面动作只更新 URL，避免 action 与 watcher 双请求；初始化也走同一入口。用户提交搜索 / 翻页建议 push，修正非法 query 用 replace；不把输入中的每个字符塞入 history。后退可回上个已应用查询。保留不属于 Gallery 的安全 query，绝不能继续无差别重建对象抹掉它们。

影响：历史链接 q/page 继续可用；新增筛选可分享，但 query 会暴露搜索词到地址栏 / 浏览器历史；不存敏感私有正文。只改 Gallery 局部状态处理，不重写 Router / Pinia / 搜索架构。请求序号或取消机制保证只应用最新结果；元数据请求与列表解耦，分类失败仍可关键词找图。

离线原型仅模拟可见交互，**没有实现上述业务 URL 同步或浏览器 back 协议**；这些是下一阶段待选择后实现的设计要求。

## 14. Hover / Focus

A/B 默认无遮罩；桌面 hover 或 keyboard focus 淡入标题，不加入不存在的收藏。仅局部底部渐变用于标题可读性，不给整个图片染黑；不默认露上传者、时间、标签、审核和 ID。不放必须 hover 才能发现的管理动作。

真实实施用原生 button（开 Viewer）与可访问名称；若选择直接路由方案，则用 router-link，保留在新标签打开语义。不要给 div 堆鼠标事件。focus-visible 独立可见、无硬阴影，不能以颜色作为唯一状态提示。普通 UI 动效约 fast 160ms；reduce 下不做淡入 / 位移。无持续 Ambient 动画。

## 15. Viewer / Detail 取舍

| 方式 | 优点 | 局限 / 决策 |
| --- | --- | --- |
| 保留独立 PictureDetail | 已有完整业务与深链 | 离开列表，必须补来源与恢复；C 对照 |
| Gallery 内 Drawer | 同时看到列表与信息 | 宽图被挤，叠加 App 侧栏后更明显；B 对照 |
| 纯 Lightbox | 最少干扰、完整看图 | 元数据与动作入口不足 |
| Lightbox + 可展开详情 | 图优先、信息按需、上下文稳定 | 焦点 / history / 移动面板需认真处理；A 推荐 |
| Desktop / Mobile 不同呈现 | 各自利用屏幕 | 共享同一数据 / 权限来源，不做两份详情业务 |

建议 A 首轮边界：Viewer 为只读预览，标题、简介、tags、createTime、真实空间归属；公开图库只标“公开图库”，不用 spaceId 猜空间名。预览先用已有列表 VO，再需要时复用 getPictureVOById；失败有重试 / 独立详情入口，权限错误不能借缓存继续展示不可访问图片。

下载、分享、编辑、删除继续在现有 Detail，不制造伪快捷动作。若用户后续要求把下载迁进 Viewer，需要作为实施范围决策单列，复用现有受控下载逻辑并验证授权，而不是直接下载 originalUrl。暂不搬 ImageEditModal / 协同、审核、删除确认。

上一张 / 下一张只在当前已加载列表范围内，端点 disabled；显示“当前页第 n 张”而不是假装已掌握全库序列。暂不跨页预取或循环到第一张。

正式实现必须有：dialog 名称、初始焦点、焦点限制、Esc 关闭、背景不可交互、body 滚动锁、关闭后触发项 focus 与 scroll 恢复、减少动态效果。优先复用现有可访问 Overlay 基础；原生 dialog 原型只是交互候选，不等同生产兼容性已验证。层级高于 Companion，按 R01 drawer/overlay/modal 体系，不开启 B 暗房主题切换。

## 16. 浏览位置保留

当前存在“不保证保留”的实质风险：详情路由替换列表，重建后只有部分 URL 状态，列表加载时高度收缩，Router 没有 savedPosition 策略。不能只说浏览器 history 会自动处理。

推荐路径：

```text
查询快照 Q / 第 p 页 / tile id / scrollY
  → 打开只读 Viewer（列表保留）
  → 关闭 / Esc / 浏览器后退
  → 同一列表、同一滚动、触发项焦点
  → 若从 Viewer 去独立详情：另保存来源 fullPath 与位置
  → 返回：先恢复查询和占位 / 结果，再恢复锚点与像素偏移
```

Viewer history 建议在 Gallery 原 URL 上增加轻量 view 参数并 push 一层；按钮 / Esc 关闭消耗自己创建的层，浏览器后退也关闭；直接带 view 打开的链接没有内部历史标记时 replace 去除 view，不能无条件 back 离站。列表 watch 只比较搜索分页字段，不因 view 改变重取。独立详情分享继续 `/picture/:id`，不需要业务新路由或鉴权改造。

独立详情返回：轻量 Gallery 专用内存快照（查询 key、锚点 id、scrollY、有限当前页数据、来源标识），不新加 Pinia、不全站 KeepAlive。刷新 / 直达无快照时以 URL 重建，缺失来源回 Gallery，而非带着“返回图库”文案无条件离站。删除导致锚点消失时回邻近项或页首；区分 Space/Admin 来源，不将其它入口强送 Gallery。若严格需要共享 Router scrollBehavior，只允许 Gallery 相关分支，须在实施前单列该微小范围；优先局部恢复，避免碰全站滚动。

候选 B 的 loaded pages 需额外恢复，复杂度明显高于分页；因此不推荐在第一版同时采用无限累积。

## 17. Loading / Empty / Error

| 状态 | 呈现与下一步 |
| --- | --- |
| 首次 loading | 有限数量、近似比例静态 skeleton + “正在加载图片”；无需巨型 spinner / 闪烁 |
| 已有数据更新 | 保留旧结果区域高度，标记正在更新；不要让每次筛选先塌为短文本。旧结果不可误称为新条件结果 |
| 空图库 | “暂无图片”“公开图库还没有可展示的图片。”有真实上传能力才显示上传入口；访客不被命令上传 |
| 搜索空 | “没有符合条件的图片”，保留条件 chips，提供清除筛选 |
| 请求错误 | “图片列表加载失败” + 重试，保留条件，不伪装成暂无图片 |
| 单图失败 | 原尺寸占位 + “图片暂时无法显示”；允许重试 / 进入详情，不塌陷，不死循环换源 |
| 详情失败 / 无权 | 明确失败位置；关闭可回原列表，不显示缓存中不再有权访问的私图 |

可复用 LpStateBlock 的 status / action slot，但 Gallery 局部不必套厚重卡片底；不要全局改 primitive 去满足单页。Error 有文字 / 动作，不能只靠红色。上述遵守 R16 文案原则，不表示启动 R16 全站改造。

## 18. 分页策略

| 策略 | URL / 返回 | 性能 / 找图 | 建议 |
| --- | --- | --- | --- |
| 分页 | page 可分享，状态有界，容易回到原页 | footer 可达，适合精确搜索；页切换有显式动作 | **A / C 保留** |
| Load More | 需记录起始条件、loaded pages 和位置 | 自主节奏但 DOM 增长；追加失败可局部重试 | B 展示，先设上限再评估 |
| Infinite Scroll | 分享位置和恢复更复杂 | footer 难到达、内存持续增长、请求竞态更多 | 首轮不做 |

推荐 A 将每页从 12 试到 16（原型密度），仍小于现有接口上限 20，不更改 contract。这是待用户确认的前端参数，不是已变更配置；清晰度 / 流量不佳可先保留 12。保留页码 / 前后页；紧凑跳页是否必要可在实现迭代时决定，不为此设计大型导航。查询变化回第一页，翻页滚到 Gallery 列表起点而非无条件整个页面顶部；尊重 reduced-motion。

## 19. 性能预算与限制

- 列表仍优先 thumbnailUrl，缺失才 url；不要整页使用 originalUrl。256px 缩略图在大屏 / 高 DPR 可能偏糊，无 srcset 多档返回能力，必须记录真实清晰度结果；本轮不改压缩 / COS / CDN。
- 真实图片尺寸填 width/height，布局按 ratio 预留；异步 decode 不应重排。decode 错误仍保留占位。
- 首屏最可能 LCP 的 1 张可 high / eager，其余首行适当 eager；非首屏 lazy。不能全页 high，也不应像当前一样一律 lazy。最终以真实网络测量确定，不提前承诺得分。
- 只在打开 Viewer 时使用 url 清晰图，关闭移除不需要的大图引用；不跨全库预取。相邻最多有限预热且应受流量条件约束，第一版可不做。
- 单页推荐 16、硬上限遵从 API 20，DOM / 元数据常量级；不引入虚拟列表库。内存快照有界，不缓存所有历史筛选结果和 decoded 图。
- 行布局只依赖尺寸与容器宽，在 resize 时合并到一帧；不在每张 load 时反复全页测量。原型算法是小型演示，不直接当已测试生产模块。
- 元数据与图片列表并行；防重复提交、取消或序号保护；慢请求返回不得覆盖新条件。
- 开始前没有生产网络瀑布、CLS、LCP、图片平均体积、CDN 命中数据。验收需真实弱网 / 高 DPR / 竖图 / 缺尺寸场景；本报告不作性能完成声明。

## 20. 组件复用建议

采用“先局部稳定，再允许接入”，不一次制造通用相册平台：

- `GalleryImageTile`：无领域、图片源 / 尺寸 / 名称、激活事件、失败占位 / focus；不要内置鉴权 / 收藏 / 审核。
- `GalleryImageGrid`：只布局与比例，数据序列不重排；A 确认后可将 row 算法独立为小型纯函数便于测试。
- `GalleryViewer`：只读图 / 元信息 / close / prev / next / detail-link，不承担编辑、删除、协同状态。
- Toolbar / chips 初期可留 GalleryView；复杂到妨碍理解才拆，不预造全套搜索 framework。
- LpButton / LpInput / LpStateBlock 与 R01 tokens 可复用。无需先补齐 LpDrawer / LpPopover / LpImage 清单。
- 旧 PictureList、SpaceDetail、PictureManage 保持不动；新 tile 的稳定 API 将来再支持 Space opt-in。

## 21. R16 文案

用：图库、搜索图片、筛选、应用筛选、清除筛选、暂无图片、没有符合条件的图片、图片列表加载失败、重试、上一张、下一张、图片信息、关闭。

不写智能语义搜索、灵感汇聚、探索视觉世界、Oops 或客服口吻；没有真实功能不出现收藏。普通 tile 不显示数据库 ID / 审核状态。原型工具条清楚标“设计原型 / 演示”，避免与产品真实能力混淆。

## 22. 推荐方案与理由

**推荐 A「行式相册」；当前只是建议，不替用户选定。**

现有正向条件是分页接口、picWidth/picHeight、独立详情和已经稳定的 Shell。A 可以利用这些而不引入无限列表或重写管理体系：行序可预测，竖图 / 横图保留构图，默认图像面积更大，搜索与筛选仍可见，单页性能有界。

B 更适合将来“连续发现”目标明确、竖图占比较高且愿意承担滚动恢复时采用；C 更适合少量作品的慢看，不适合作为当前大量可检索公共图片的默认布局。

推荐首轮组合：A 布局 + A 内联筛选 + 分页 16（可退回 12）+ 只读 Viewer / 可展开信息 + 现有独立详情管理动作。浅色 Viewer 延续纸境，不等于实现新暗房主题。

## 23. 后续实现文件范围（尚未授权实施）

| 范围 | 预计改动 |
| --- | --- |
| `src/views/GalleryView.vue` | 内容结构、控件、query watch、状态、分页和 Viewer 编排 |
| `src/components/gallery/GalleryImageTile.vue` | 图片呈现与可访问激活 |
| `src/components/gallery/GalleryImageGrid.vue` | A 方向选择后才新增行布局 |
| `src/components/gallery/GalleryViewer.vue` | 有界只读查看器、焦点和关闭恢复 |
| `src/utils/galleryLayout.js`、`src/utils/galleryQuery.js` | 有需要时才提取纯布局 / query 解析，文件名可按仓库约定小调 |
| Gallery 局部 context helper | 仅为详情往返保留有界快照，不加 store 框架 |
| `src/views/PictureDetailView.vue` | 最多来源感知返回与必要衔接，不重写编辑 / 删除 / 协同 / 审核业务 |
| 前端对应 tests / e2e | 下一阶段按最终方案补 query、比例、竞态、键盘、恢复与边界测试 |

以上均为候选，方向确认后再给实施计划。本轮没有创建这些正式文件；通常不需要修改 router/index.js、shell metadata 或 API helper，如实施发现必须变更，先说明最小原因。

## 24. 明确不修改范围

本轮未改任何正式 Vue、CSS、JS、业务 Router、Pinia、依赖；后端只做相关只读核实。没有修改 GalleryView、PictureDetailView、SpaceDetailView、PictureList、管理后台、App Shell、Landing、权限、API contract、数据库、存储、压缩、缩略图服务或 CDN。

R05 Avatar、R06 状态协议、R07 动画、R08 Companion 交互、R09 Room、R10 Feeding、R11 情绪关系、R12 Chat/Proposal、R13 全站响应式、R15 登录均未启动；R16 仅引用文案原则。

## 25. 风险与已知限制

| 风险 | 控制方式 |
| --- | --- |
| 历史尺寸错误 / 缺失 | 校验正数、有限 fallback、稳定 contain 占位；不伪造字段 |
| 256px 缩略图大屏模糊 | 验收记录清晰度；不偷加后端变更或整页原图 |
| query / history 循环与双请求 | 单向解析、按列表 key 请求、Viewer 参数独立、最新请求胜出 |
| 离开详情位置丢失 | 来源标记 + 有界快照 + 数据 / 布局恢复后滚动；直达有 fallback |
| 删除 / 编辑权限现有不一致 | 不复制 / 改造此判断；管理动作留在现有 Detail，另记业务风险 |
| B 列顺序与 append 重排 | 原型暴露这一差异；正式采用需重新验证键盘 / 视觉顺序和稳定布局 |
| C 放大首项的精选暗示 | 明确只是布局节奏，不声称精选或推荐算法 |
| 弹层叠加 Shell / Companion | R01 层级、背景 inert、焦点与滚动锁，下一阶段实测 |
| 原型与生产差距 | 样本重复且 metadata 模拟；C 不是真路由；无真实授权、URL 恢复、API 请求和编辑动作 |
| 浏览器检查受限 | 本地 HTML 预览被工具安全策略阻止；未绕过。不能声称 Desktop / Mobile 实测通过，需用户本地打开和后续验收 |

## 26. 验收标准与本阶段验证

方向选择后的实施验收清单（不是本轮完成结果）：

- 图片不再全裁 4:3；横 / 竖 / 全景 / 方图 / 缺尺寸均稳定，不出现首次 decode 大幅跳动。
- 默认没有常驻厚遮罩、硬阴影、审核字段或虚构收藏；标题 hover/focus 可读，手机点击可完整查看。
- 相同 fixture / 1440×900 / App 下，首图建议 y≤180，图片框首屏面积目标≥55%（包括按比例图片，不把骨架算图片）；与旧页面采用一致口径测量。目标需实测，不能用原型估算冒充通过。
- 访客 / 普通登录者 / 管理员的 Gallery 外壳和真实业务能力不变；不叠三层大 Header。
- 首次 q、同页 q 变化、分类、多标签 AND、格式、排序、page、清除、back/forward、非法参数和过期请求都有明确结果。
- 查看器保持完整图，上一张 / 下一张限制在已加载范围；Esc / 关闭 / 浏览器后退、信息展开、Tab、触控均可靠；不误触背后 Shell。
- 路由详情返回恢复 query + page + 位置；从 Space/Admin 来不被错误送回 Gallery；直达 / 删除锚点有 fallback。
- 空库、搜索空、列表失败、单图失败、无权、重试彼此区分；不能把 error 变成“上传第一张”。
- 320/390/480/768/1024/1440/1920 宽度无非预期横滚；主要目标≥44px；reduced-motion 下无持续动画。
- 单次 pageSize≤20，不加载全库原始图；真实网络条件下检查清晰度、DOM 数量、内存、请求竞态与布局偏移。
- SpaceDetail / PictureList / Admin 图片审核 / Shell / Landing 不被连带迁移。

本轮实际检查：

- 相关源代码、API / VO / 上传路径只读核实；8 张现有图片读取文件尺寸。
- `node --check docs/design-exploration/r04-gallery/prototype.js` 通过。
- 6 个原型文件、25 处静态资源引用和 8 张本地素材检查通过；原型源码无外部 HTTP(S) 资源地址。A 的行算法按 292 / 362 / 452 / 648 / 888 / 1168 / 1648px 内容宽度做数值检查，无负高度或行宽溢出。这不等同 CSS 渲染验证；不安装依赖，不调用图片 API，不下载图片。
- `git diff --check` 通过；交付前原有 tracked diff 指纹保持 `8f2a73781b11702a9fcf9c82aa17c91690a8ce9d`，索引 diff 指纹保持 `881b564dd16ac1a933c67448ccc4d80953286061`（同一 PowerShell 管道计算）。本轮仅多出未跟踪的报告及原型目录，未动既有 tracked 改动和索引。
- 浏览器工具尝试打开 `file://` 原型被安全策略拒绝；未换端口、替代浏览器或其它方式绕过。**浏览器视觉、交互、焦点、手机与错误状态实测未完成**，代码中已提供对应演示入口供用户打开检查。
- 未运行生产全量 npm test / lint / build / E2E；本轮没有改生产代码，不扩张为正式验收。

## 27. 未决问题

必须由用户选择后才能继续：

1. A 行式相册、B 自由瀑布流还是 C 留白展墙？也可明确组合布局与详情方案。
2. 若选 A，是否接受首版 Viewer 只读、管理动作留独立详情的推荐边界？若希望 Viewer 内直接下载，需要追加明确范围。

不阻塞本轮设计、实施时验证：每页 12 还是 16 的真实网络成本；历史 ratio 异常比例；高 DPR 缩略图清晰度；单页尾行 / 极端比例尺寸；业务权限删除 gate 另行处理；是否需要紧凑跳页。

没有方向选择就不启动实现；不能把“推荐 A”当用户已批准。

## 28. 本阶段结论

已形成基于真实代码的调查、三套离线 Gallery 方向及实现边界。`ui-ux-pro-max` 用于焦点、触控、响应式与减少动效约束，遵从 R01 色彩，不引入新 palette；`superpowers-lite` 限定过程成本与阶段；`prototype` 用于让三套布局 / 详情流程产生实质差异，按用户要求只放文档目录，没有接生产路由或自动提交。

新增：本报告、比较首页、A/B/C 三页、共享 prototype.css / prototype.js。原型 HTML 可在本地浏览器直接打开，不依赖 Vite 或 API。

**当前最终状态：R04「需求确认 / 设计」已按用户要求冻结，全部成果提交推送供 Kimi 复核；没有开始 R04「实现 / 迭代」。不继续新增方案。**
