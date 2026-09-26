# 绫页生产资产 v1 — 生成记录

2026-09-24，使用内置 imagegen。角色设定参考同仓库 concept-a.png 左侧成年正常比例角色；该概念图没有直接裁切上线。其余生成依赖独立成年母版；Idle 修订使用第一轮图集作参考。最终母版为同目录 PNG；未采用的初版图集留在本机生成目录，没有作为运行资源。

这些是可继续编辑的透明栅格母版，不是分层 PSD、Live2D 或 Spine 模型。按本项目已选原创设定生成；没有做商标或相似性法律检查，不把生成来源声明当成权利保证。2026-09-26 用户已授权随 R05 第二阶段提交推送。

导出脚本仅拆分生产帧窗口、等比缩放和 WebP 编码，不绘制或补造角色。原 PNG 保留；应用只引用 src/assets/companion/lingye 下的 WebP。尺寸、透明边界、文件哈希和预算见该目录 manifest.json。闭眼帧只播放 140ms，4200ms 为静态睁眼停留。

## body

```text
Use case: identity-preserve.
Asset type: production 2D game/Web companion sprite, one adult full-body neutral standing cutout.
Input image 1 is a character identity reference sheet. Use ONLY the tall normal-proportion ADULT woman at LEFT, not the child, egg, or chibi. Make a new standalone production asset of that SAME character, Lingye, paper-wing moth species.
Preserve exactly the adult's slender normal proportions, dark brown chin-length bob with broad ivory inner lock, gray-green eyes, two folded ivory paper antennae, cream high collar and long sleeves, indigo sleeveless long pleated dress with ochre ties, small ochre leaf-paper pendant, broad folded ivory paper wings edged dark indigo, dark brown boots. Calm neutral face, same standing three-quarter pose and hand near collar. Refine clean edges while retaining delicate muted hand-painted anime illustration.
Composition: one complete character alone centered in a portrait 1024x1536 canvas. The whole silhouette including antenna tips, wings and soles must fit, at least 5% empty margin each edge. Soles on the same horizontal baseline near 94% canvas height. Preserve mature normal body proportions, about 6.5 heads tall.
Background: actual transparent alpha, no background, no floor, no cast shadow, no checkerboard painted in the image.
No other figures, panels, labels, text, letters, color swatches or watermark. Not chibi. Not a collage. This is the definitive neutral first frame, eyes open, mouth closed.
```

## portrait

```text
Use case: identity-preserve. Production normal-proportion neutral conversation bust portrait of the exact adult woman in input image 1. Preserve face, gray-green eyes, dark brown bob and large ivory inner streak, ivory folded-paper moth antennae, cream high collar, indigo sleeveless clothing, ochre leaf pendant, paper-wing shoulders. Face quietly attentive, eyes open, mouth closed, natural relaxed expression. Frame one head, BOTH whole antennae, shoulders, chest and both hands down to mid torso. Head should occupy about half the height (normal bust framing, NOT enlarged chibi proportions). One standalone illustration, transparent alpha background with clean fully transparent pixels outside silhouette, no glow, no fog or shadow around character. Keep at least 6% clear margin at top and sides. Square 768 by 768 canvas. No text or panels. Match refined painted linework and muted palette. Do not add props.
```

## shell

```text
Use case: identity-preserve. Asset: very small Web navigation avatar of Lingye for a 40x44 CSS pixel slot.
Reference image 1 shows the adult body. Make a NEW standalone simplified head-and-upper-shoulders icon of this exact ADULT character with NORMAL FACE AND HEAD PROPORTIONS; the framing crops the torso, do NOT make a chibi body. Adult refined face, gray-green eyes, dark brown bob with broad ivory streak, TWO folded-paper antennae complete, cream high collar and indigo shoulder clothing with a visible hint of folded ivory paper wings.
Simplify to clean flat cel-shaded shapes and clear dark outlines, eliminate fine paper texture and tiny clothing folds, recognizable at 40px. Quiet neutral expression. Fill central 85% square composition. Absolutely transparent background outside character, no backdrop, no haze, no glow, no shadow, no border, no badge. No lettering. Square 256x256 intended output, small optimized icon. Preserve adult identity; do not use juvenile or chibi proportions.
```

## idle

```text
Use case: identity-preserve. Asset type: precisely registered TWO-FRAME blink sprite sheet for a production 2D Web companion.
Input image is the approved adult standing character. Create a single transparent PNG sheet with TWO equal 768x1152 cells side by side, total canvas 1536x1152. EXACTLY the SAME complete adult character at the SAME scale and SAME local position in BOTH cells. LEFT cell: eyes open, neutral. RIGHT cell: eyelids fully closed for one gentle blink, everything else IDENTICAL. No mouth movement, no change of head pose, arms, hands, wings, antennae, clothing, boots, lines, shading or silhouette. Copy the left frame unchanged for right frame and edit ONLY the eyelids.
Each cell has comfortable 6% transparent padding and identical feet baseline at 94% height, character centered. Whole antennae, both wings, soles must remain within each cell. Normal adult proportions, not chibi. Preserve dark brown bob with ivory streak, folded paper antennae, cream and indigo clothing, ochre pendant, paper wings and brown boots.
Outside silhouette must be fully transparent alpha, no cast shadows, no glow or haze, no ground, no checkerboard drawn in. No labels, panel borders, letters or text. Sprite alignment is more important than adding any detail.
```

## idle_refinement

```text
Use case: precise-object-edit. Fix this two-frame production sprite atlas.
Keep the exact adult woman, identity, normal adult proportions, clothing, expression pair (open eyes left, closed eyes right), colors, pose and TWO frames. The current frames are too large, touch each other and touch the top edge.
Recompose into a landscape 1536x1024 transparent canvas, TWO equal 768x1024 cells. Shrink each full-body figure to only 82% of its cell height. EACH character must have 80 pixels of empty transparent space above and below and at least 90 pixels empty transparent space at both cell sides. There must be a BIG clear transparent vertical gutter between the two figures. Both antennae complete, wings and soles not clipped. Align exact local center x=384 in each cell and same soles y=944. The right figure must be a duplicate of left translated exactly 768 pixels, with ONLY eyelids changed to closed. No other motion or pose change, no resizing between frames.
Transparency must be clean outside silhouettes, no glows, outlines around alpha, haze, shadows, backdrop, labels or panel dividers. Do not crop the transparent canvas or trim margins. This will be consumed as a strict equal-cell sprite sheet.
```
