# R05 — Imagegen prompts

日期：2026-09-23。工具：内置 `image_gen.imagegen`。用途：第一阶段概念参考，非生产资产。三个方向分别独立生成，没有输入第三方角色图片。输出原始图均为 1536×1024，A 为 RGB，B/C 为 RGBA。原文件从 Codex generated_images 复制入 assets，不改写原图或 alpha。生成结果偏差见角色概念文档。

## Concept A

```text
Use case: stylized-concept. Asset type: exploratory anime character design sheet for LiPictureCloud R05, NOT production art. Landscape 3:2 sheet on warm ivory paper #f6f4ef, editorial clean framing, no typography except small A.
Design a completely original fantasy species "Paperwing Moth", provisional character Lingye. Not a reference to an existing franchise. A memorable adult female anime moth person, two large folded paperlike moth wings shaped like overlapping offset picture mats, wide bell silhouette; wings are biological, not costume. Two short flat folded antennae, dark warm-brown blunt bob with one broad ivory underside panel, gray-green eyes, quiet curious expression. Modest high collar ivory tunic with indigo pleated sleeveless outer robe, practical boots, one ochre bookmark-shaped tab at chest. Signature motif: one missing rectangular corner in each broad wing's indigo edge; no logos, camera icons, AI symbols. Large blocks of ivory, deep ink indigo #374761, small honey ochre #c49245. Clean deliberate anime lines, expressive face, controlled rich contrast; fine paper grain only on wings, not gray washed out.
Layout with generous separation: left 45% is one full body ADULT 6-head proportion front three-quarter view; upper right a close adult conversation portrait matching her exactly; lower right is THREE clearly separated miniatures: an egg, juvenile, and chibi ADULT sprite. Egg is a faceted flattened pear with folded ridges and one ochre tablike biological seam, two asymmetric indigo offset-corner markings, not chicken egg or cocoon. Juvenile is an immature fantasy morph with short unopened paper wings and simple short tunic, not a toddler, entirely nonsexual. Chibi ADULT is 3-head full-body version of the adult with FULL developed wings and matching clothing; do not confuse chibi with juvenile. Adult and juvenile fully clothed, no revealing designs. White/ivory backdrop, consistent feet baseline for miniatures, no extra decorative props or scenic background. Avoid cat ears, elf-only design, blue-haired technology girls, fairy glow balls, ornate jewelry, extra limbs, feathery angel wings.
```

## Concept B

```text
Use case: stylized-concept. Asset type: exploratory anime character design sheet for LiPictureCloud R05, NOT production art. Landscape 3:2 sheet warm ivory #f6f4ef, only small letter B, no labels. Completely original fantasy humanoid "Tide-shell Nautilus", provisional Xiyan. Adult female anime sea mollusk person, clearly NOT mermaid, catgirl, human with shell handbag. One broad LOW spiral nacre shell is a biological part of her back, visible beyond right side, big comma silhouette counterbalancing head. Two short rounded sensory ribbon fins emerging behind jaw, and a broad flattened tapering mollusk tail following the ground, two humanlike arms and legs. No pointy elf ears, horns or extra accessories. Deep plum auburn hair in sculpted short wave bob, moss-teal eyes, one ivory curved forelock; calm slightly skeptical expression. Modest cream sailorless wrap blouse, deep petrol teal asymmetrical knee-length culottes and short matching outer jacket, matte boots, one small rust-orange cord. NO sailor collar or naval uniform. Shell has a single offset rectangular notch on outer rim echoing picture mat corners, subtle concentric memory rings, never literal photos or UI icons. Shell cream and muted rose mother-of-pearl, teal #315c5d, terracotta #b9654b accent. Deliberate clean anime linework, restrained painterly fill, rich focal contrast.
Composition: adult 6-head full body front three-quarter view left 45%; upper right large matching adult half-body conversation portrait showing edge of shell and sensory fins; lower right three separated figures with clear baseline: egg, juvenile, CHIBI ADULT sprite. Egg oblate spiral lens with nacre overlapping bands, teal rim and single rust-orange notch, NOT normal chicken egg. Juvenile is fantasy undeveloped morphology, small smooth unspiraled shell and SINGLE broad sensory fin fold, simple straight cream smock over teal shorts, short thick tail, NOT miniature adult costume, NOT toddler; entirely modest and nonsexual. Chibi ADULT has fully developed spiral shell, split adult fins, adult clothes and long flattened tail, 3-head proportions. Keep figures distinct. No scenic backdrop, no aquatic sparkle effects, no wet sexual look, no cleavage. Maintain stable original identity across adult and chibi.
```

## Concept C

```text
Use case: stylized-concept. Asset type: original anime fantasy companion CONCEPT sheet, not production art, landscape 3:2, light warm ivory #f6f4ef FLAT backdrop everywhere, no dark gradient. Only small C, no other text.
Species "Kilnscale Salamander", provisional Taomian, a clearly adult female anthropomorphic ceramic-scaled salamander from a quiet island pottery workshop. Athletic practical grounded build, humanlike face with subtle ceramic scales on cheeks, large ochre eyes and confident mildly wry smile. Short square-cut charcoal hair, two broad rounded finlike ear plates (not cat ears or elf ears), exactly one large low-slung segmented clay salamander tail, wide shovel-shaped tail tip with offset rectangular picture-mat notch. Two arms, two legs. No horns, wings, halo, weapons. Matte warm terracotta scales #b5684e along forearms and tail, cream rectangular inlaid plates with deliberate dark seams, NOT glowing cracks or fire magic. Loose ochre cropped work jacket over high-neck cream shirt, charcoal roomy trousers cuffed at ankles, flat practical dark boots, short unadorned apron with one square patch. No huge tools, goggles, pouches or jewelry. Deliberate clean anime linework, controlled warm color blocks and ceramic textures, expressive face, restrained but not desaturated.
Layout: left 45% full ADULT 6-head proportion front three-quarter body showing broad tail footprint; upper right large adult half-body portrait matching same scales, ear plates, hairstyle, color palette and costume; lower right THREE spaced miniatures: egg, juvenile and CHIBI ADULT sprite.
Egg is a flattened asymmetric pebble-shaped ceramic egg, terracotta ceramic overlapping scales, single cream inlaid offset-corner band and sealed ochre seam, unmistakably hatchable, not chicken egg or rock with fire.
Juvenile is a fantasy incompletely developed morph at 4-head proportions: smaller smooth unsegmented stubby tail, soft small unplated ear fins, simple ivory sleeveless OVER-SMOCK over long-sleeve charcoal shirt and cropped trousers, neither toddler nor resized adult. Entirely nonsexual.
CHIBI ADULT sprite is 3-head adult depiction with FULL segmented broad tail, adult work jacket and apron, matching adult face and ear plates. Clearly distinct from juvenile; feet aligned. Use small strong shapes readable at 64px. No scenic backdrop, no glow, no sexualization, no copying existing game/anime characters, no blue tech heroine.
```

## 后续生产前的修订要求（尚未执行）

A：Juvenile 改为短闭翼袋、单折触角、直筒短罩衫；不要缩小成年外衣。

B：Juvenile 调成四头身幻想未成熟体、短鳍与平滑未螺旋壳；降低现实幼儿感；壳的高光简化。

C：加强未成熟耳鳍/尾部与成年陶板的形态差异；Shell 只保留三到四段尾鳞。

所有方案：统一三视图、体型比例、左右特征、脚底锚点与 alpha；需用户选择后才开始正式制作。

## A 的 UI 合成预览修订

使用上面的 A 原图作唯一编辑输入，内置 imagegen 去背景，输出 `assets/concept-a-ui.png`（RGBA，1536×1024）。它仅供 HTML 概念合成，设定页仍保留原图。生成式去背景可能有细节漂移，未宣称逐像素不变或具备生产级 alpha 边缘。

```text
Use case: background-extraction. Edit this exploratory concept sheet only for clean UI compositing. Remove the warm paper background, all background pixels and ground shadows to genuinely transparent alpha. Keep every character, egg, portrait and letter in EXACTLY the same location and size on the same 1536x1024 canvas. Preserve the artwork, identity, colors, wing shape, clothes and facial expressions completely. Do not regenerate, rearrange, add, redraw or improve characters. Preserve light ivory wing and clothing interiors; only remove empty background around subjects. This remains concept preview art, not production asset.
```
