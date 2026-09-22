# R04 设计探索 · Kimi 版「纸境图墙」

本目录是 **R04 — Image-first Gallery** 的 Kimi 版设计草图，与 astra 的 `r04-gallery/`（已冻结归档）相互独立。

## 约束声明

- 独立静态原型，与正式项目隔离：不接真实 API、无真实数据、不引入依赖
- 图片素材引用 `li-picture-cloud-frontend/public/images/mosaic/`（与 astra 原型同源的本地照片）
- 令牌值摘自 `src/styles/tokens.css`（2026-09-22 快照），内联以便 `file://` 直接打开
- 只表达**一个方向**（应用户要求"设计一版"），不做多方案并列
- 不构成最终方案；正式代码、样式、配置、依赖均未修改

## 文件

| 文件 | 内容 |
| --- | --- |
| `index.html` | 「纸境图墙」完整草图：mock App Shell + 图库页 + Viewer + 六种状态演示 |

直接用浏览器打开 `index.html`。底部演示条可切换：默认 / 已应用筛选 / 加载中 / 搜索无结果 / 空图库 / 加载失败；点击任意图片打开 Viewer（Esc 关闭，←/→ 切换）。

设计论证见 `docs/R04-Image-first-Gallery-设计提案-kimi.md`。
