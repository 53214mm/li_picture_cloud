import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import {
  recipeCapabilityLabel,
  recipeConditionLabel,
  recipeExecutionIsAwaiting,
  recipeExecutionStatusLabel,
  recipeStatusLabel,
  recipeWhenLabel
} from '../src/constants/recipe.js'

test('recipe labels resolve known codes and fall back safely', () => {
  assert.equal(recipeStatusLabel('DRAFT'), '草稿')
  assert.equal(recipeStatusLabel('ENABLED'), '已启用')
  assert.equal(recipeStatusLabel('DISABLED'), '已停用')
  assert.equal(recipeExecutionStatusLabel('DRY_RUN'), '试运行')
  assert.equal(recipeExecutionStatusLabel('PENDING_CONFIRM'), '待确认（机会触发）')
  assert.equal(recipeExecutionStatusLabel('REJECTED'), '条件未命中')
  assert.equal(recipeWhenLabel('WEEKLY_REVIEW'), '每周回顾时')
  assert.equal(recipeConditionLabel('SOURCE_CATEGORY'), '仅指定分类')
  assert.equal(recipeCapabilityLabel('IMAGE_FUSION'), '多图融合')
  assert.equal(recipeStatusLabel('UNKNOWN'), 'UNKNOWN')
  assert.equal(recipeStatusLabel(null), '未知状态')
  // 只有"等待确认"的状态可以确认执行：试运行与机会触发的待确认记录。
  assert.equal(recipeExecutionIsAwaiting('DRY_RUN'), true)
  assert.equal(recipeExecutionIsAwaiting('PENDING_CONFIRM'), true)
  assert.equal(recipeExecutionIsAwaiting('EXECUTED'), false)
  assert.equal(recipeExecutionIsAwaiting('REJECTED'), false)
})

test('recipe api mirrors the workshop endpoints', async () => {
  const api = await readFile(fileURLToPath(new globalThis.URL('../src/api/recipe.js', import.meta.url)), 'utf8')

  assert.match(api, /request\.get\('\/recipe\/templates'\)/)
  assert.match(api, /request\.get\('\/recipe\/capabilities'\)/)
  assert.match(api, /request\.post\('\/recipe', data\)/)
  assert.match(api, /request\.post\('\/recipe\/from-template', data\)/)
  assert.match(api, /request\.post\(`\/recipe\/\$\{id\}\/versions`, data\)/)
  assert.match(api, /request\.post\(`\/recipe\/\$\{id\}\/enable`\)/)
  assert.match(api, /request\.delete\(`\/recipe\/\$\{id\}`\)/)
  assert.match(api, /request\.post\(`\/recipe\/\$\{id\}\/dry-run`, data\)/)
  assert.match(api, /request\.post\(`\/recipe\/\$\{id\}\/executions\/\$\{executionId\}\/execute`, data\)/)
  assert.match(api, /request\.get\(`\/recipe\/\$\{id\}\/executions`/)
})

test('recipe workshop view keeps whitelist semantics and safe display', async () => {
  const view = await readFile(fileURLToPath(new globalThis.URL('../src/views/RecipeWorkshopView.vue', import.meta.url)), 'utf8')

  // 模板起点 + 我的配方 + 组合编辑 + 试运行 + 执行回放。
  assert.match(view, /官方模板/)
  assert.match(view, /我的配方/)
  assert.match(view, /组合编辑（发布新版本）/)
  assert.match(view, /发布新版本/)
  assert.match(view, /试运行（不会产生真实创作）/)
  assert.match(view, /执行回放/)
  assert.match(view, /data-testid="recipe-list"/)
  assert.match(view, /data-testid="recipe-editor"/)
  assert.match(view, /data-testid="recipe-executions"/)
  // 条件集合封闭：编辑器只提供三种收紧条件与白名单动作，没有自由文本提示词入口。
  assert.match(view, /SOURCE_SPACE_PRIVATE/)
  assert.match(view, /SOURCE_CATEGORY/)
  assert.match(view, /MAX_TRIAL_COST/)
  assert.match(view, /RECIPE_CAPABILITY_LABEL/)
  // 试运行只携带图片 ID 列表；确认执行不带图片，只认执行记录里的来源图片快照。
  assert.match(view, /pictureIds: selectedIds\.value/)
  assert.match(view, /executeRecipe\(selected\.value\.recipe\.id, execution\.id, \{\}\)/)
  assert.doesNotMatch(view, /pictureIds: selectedIds\.value \}\)\)/)
  assert.doesNotMatch(view, /prompt|apiKey|token/)
  // 未开放能力必须被禁用并给出原因，模板同样按可用性禁用。
  assert.match(view, /data-testid="template-unavailable"/)
  assert.match(view, /data-testid="capability-unavailable"/)
  assert.match(view, /:disabled="busy \|\| !template\.available"/)
  assert.match(view, /listRecipeCapabilities/)
  // 预览—确认边界：来源图片快照不一致时必须提示重新试运行。
  assert.match(view, /data-testid="snapshot-changed"/)
  assert.match(view, /snapshotMatchesSelection/)
  // 机会触发的待确认记录与快照信息在回放中可见。
  assert.match(view, /data-testid="execution-opportunity"/)
  assert.match(view, /sourcePictureIds/)
  // 图片雪花 ID 以字符串传递，绝不 Number() 折损精度。
  assert.match(view, /String\(picture\.id\)/)
  // 定义回显走标签映射，不渲染原始 JSON 之外的敏感内容。
  assert.match(view, /recipeCapabilityLabel|recipeWhenLabel|recipeConditionLabel/)
})
