# 绫页名称与静态形象修正（L-06，2026-10-09）

## 本增量范围

- 对外角色名称固定为「绫页」。成长面板标题不再随内部阶段显示「光点／幼体／伙伴」。
- 成长信息独立显示「初始阶段／成长阶段／成熟阶段」；未知阶段显示「阶段待确认」。内部 `LIGHT / SEEDLING / COMPANION`、等级、生命经验、技能和原阈值均未改动。
- 已实际查看现有 `adult-home-v1.webp` 像素：本轮继续使用同一张既有全身立绘，没有生成、缩放冒充或替换为幼年形象。
- `resolveCompanionVisual` 对所有阶段固定关闭 `allowIdle`。生产 presentation 因此在 idle、feeding、thinking、responding、acknowledging、proposal 和鼠标／焦点 greeting 请求下都映射为 static。`CompanionBody` 不挂载 `SpritePlayer`，不启动其动画时钟、不请求眨眼 atlas。
- 现有所有 clips 都包含 blink frame 1；本轮不是仅删除 idle 的一个眨眼，而是将整套现有 atlas 的生产播放能力关闭。已有控制器代码与生命周期回归保留，测试通过明确标注的合成 motion-capable fixture 继续覆盖中断、过期回调、单计时器、身份切换、隐藏、暂停及一次性 cue。
- 图像不可用时继续走 `CompanionArtwork` 的带标签静态占位；保留可键盘操作的互动按钮、拖放喂图、proposal hint、情绪说明、渲染预算和窄屏布局。移除形象旁及无障碍标签中的既有物种断言，不新增物种或世界观设定。

## 自动验证

Node 路径：`/workspace/scratch/058c18b9b398/tool-cache/node-v22.23.3-linux-x64/bin`，Node 22.23.3。

在前端目录执行：

- 定向 7 文件，59/59 通过：`companionStaticIdentity`、`companionBody`、`companionPresentation`、`companionAnimation`、`companionDisposition`、`companionDispositionPanels`、`companionRenderIntegration`
- 新回归包含真实编译的 Body + Artwork：阶段／活动实时变化、hover/focus、click/drag/drop、预算降级、加载失败、可访问标签和卸载；通过抛错的 SpritePlayer 边界证明从未挂载该播放器
- Stats 真实 SFC 编译回归：三种阶段及未知阶段名称恒为绫页；独立阶段标签、原 200/500 经验与 25% 进度不变；输入快照未被修改
- 2026-10-09 16:29 UTC 当时共享工作树 `npm test`：370/370 通过，0 跳过／失败
- `npm run lint`：严格零警告通过
- `npx playwright test --list`：166 项／26 文件成功收集；旧浏览器 fixture 已更新为静态形象预期，保留行为／网络／可访问性／布局场景

共享工作区后续聚合验证以合并后的最终报告为准；以上时间点全量不冒充后续新增代码的最终验证。本轮不重试已确认 sandbox/IPC 权限阻塞的浏览器启动；Playwright 收集不等于浏览器执行、截图或人工视觉验收。未调用真实收费模型、未做后端变更、未提交／推送／部署。

## 明确未完成

- 从幼年到成熟的阶段专属形象仍未实现，所有阶段仍是同一份成人立绘；阶段文字区分不等于视觉成长完成
- 幼年／成长阶段需新的参考素材与明确角色设计后再实施，不推断物种、不用简单缩小成人像假装完成
- 更自然的预制姿势和动作仍待新素材评审；本轮优先保证静态稳定，不能将遗留控制器合成测试通过当成新动画验收
- 真实浏览器、真机布局与人工角色观感尚待后续可用环境验证
