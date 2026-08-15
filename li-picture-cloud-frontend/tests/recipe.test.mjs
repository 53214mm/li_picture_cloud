import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import {
  recipeCapabilityLabel,
  recipeConditionLabel,
  recipeExecutionStatusLabel,
  recipeStatusLabel,
  recipeWhenLabel
} from '../src/constants/recipe.js'

test('recipe labels resolve known codes and fall back safely', () => {
  assert.equal(recipeStatusLabel('DRAFT'), '草稿')
  assert.equal(recipeStatusLabel('ENABLED'), '已启用')
  assert.equal(recipeStatusLabel('DISABLED'), '已停用')
  assert.equal(recipeExecutionStatusLabel('DRY_RUN'), '试运行')
  assert.equal(recipeExecutionStatusLabel('REJECTED'), '条件未命中')
  assert.equal(recipeWhenLabel('WEEKLY_REVIEW'), '每周回顾时')
  assert.equal(recipeConditionLabel('SOURCE_CATEGORY'), '仅指定分类')
  assert.equal(recipeCapabilityLabel('IMAGE_FUSION'), '多图融合')
  assert.equal(recipeStatusLabel('UNKNOWN'), 'UNKNOWN')
  assert.equal(recipeStatusLabel(null), '未知状态')
})

test('recipe api mirrors the workshop endpoints', async () => {
  const api = await readFile(fileURLToPath(new globalThis.URL('../src/api/recipe.js', import.meta.url)), 'utf8')

  assert.match(api, /request\.get\('\/recipe\/templates'\)/)
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

  // 模板起点 + 我的配方 + 试运行 + 执行回放。
  assert.match(view, /官方模板/)
  assert.match(view, /我的配方/)
  assert.match(view, /试运行（不会产生真实创作）/)
  assert.match(view, /执行回放/)
  assert.match(view, /data-testid="recipe-list"/)
  assert.match(view, /data-testid="recipe-executions"/)
  // 试运行与执行都只携带图片 ID 列表，不携带任何提示词或密钥字段。
  assert.match(view, /pictureIds: selectedIds\.value/)
  assert.doesNotMatch(view, /prompt|apiKey|token/)
  // 图片雪花 ID 以字符串传递，绝不 Number() 折损精度。
  assert.match(view, /String\(picture\.id\)/)
  // 定义回显走标签映射，不渲染原始 JSON 之外的敏感内容。
  assert.match(view, /recipeCapabilityLabel|recipeWhenLabel|recipeConditionLabel/)
})
