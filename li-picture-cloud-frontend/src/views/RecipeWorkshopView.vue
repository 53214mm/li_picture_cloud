<template>
  <div class="recipe-workshop" data-testid="recipe-workshop">
    <header class="recipe-header">
      <h1>玩法配方工坊</h1>
      <p>用「什么时候 + 条件 + 动作」组合白名单能力，配方只会收紧权限与费用，不会扩大。</p>
    </header>

    <div v-if="error" class="panel-error" role="alert">{{ error }}</div>

    <section class="recipe-templates" aria-labelledby="templates-title">
      <h2 id="templates-title">官方模板</h2>
      <ul v-if="templates.length" class="template-list">
        <li v-for="template in templates" :key="template.code" class="template-card"
            :data-template="template.code">
          <strong>{{ template.name }}</strong>
          <p>{{ template.description }}</p>
          <p class="template-summary">
            {{ summaryText(template) }}
          </p>
          <button class="btn btn-sm" type="button" :disabled="busy"
                  @click="createFromTemplate(template)">
            用这个模板创建
          </button>
        </li>
      </ul>
      <p v-else class="empty-state">模板加载中…</p>
    </section>

    <section class="recipe-mine" aria-labelledby="mine-title">
      <h2 id="mine-title">我的配方</h2>
      <ul v-if="recipes.length" class="recipe-list" data-testid="recipe-list">
        <li v-for="recipe in recipes" :key="recipe.id" class="recipe-row"
            :data-status="recipe.status">
          <div class="recipe-main">
            <span class="recipe-status" :class="{ on: recipe.status === 'ENABLED' }">
              {{ recipeStatusLabel(recipe.status) }}
            </span>
            <strong>{{ recipe.name }}</strong>
            <span class="recipe-version" v-if="recipe.latestVersion != null">
              版本 v{{ recipe.latestVersion }}
            </span>
          </div>
          <div class="recipe-actions">
            <button class="btn btn-sm" type="button" :disabled="busy"
                    @click="select(recipe)">查看</button>
            <button v-if="recipe.status === 'DRAFT' || recipe.status === 'DISABLED'"
                    class="btn btn-sm" type="button" :disabled="busy"
                    @click="toggle(recipe, 'enable')">启用</button>
            <button v-if="recipe.status === 'ENABLED'" class="btn btn-sm" type="button"
                    :disabled="busy" @click="toggle(recipe, 'disable')">停用</button>
            <button class="btn btn-sm btn-danger" type="button" :disabled="busy"
                    @click="remove(recipe)">删除</button>
          </div>
        </li>
      </ul>
      <p v-else class="empty-state" data-testid="recipe-empty">
        还没有配方。从上面的官方模板开始，或直接创建后组合自己的触发与动作。
      </p>
    </section>

    <section v-if="selected" class="recipe-detail" aria-labelledby="detail-title">
      <h2 id="detail-title">{{ selected.recipe.name }}</h2>
      <div class="definition-box" v-if="selected.latest">
        <p data-testid="recipe-definition">
          <strong>触发：</strong>{{ whenLabel(selected.latest.whenJson) }}
          <template v-if="conditions(selected.latest.ifJson).length">
            · <strong>条件：</strong>
            <span v-for="condition in conditions(selected.latest.ifJson)" :key="condition.type">
              {{ conditionText(condition) }}；
            </span>
          </template>
          <strong>动作：</strong>{{ capabilityLabel(selected.latest.thenJson) }}
        </p>
        <p class="version-hint">版本 v{{ selected.latest.version }}（共 {{ selected.versions.length }} 个版本）</p>
      </div>
      <p v-else class="empty-state">这个配方还没有发布定义版本。</p>

      <div class="recipe-editor" data-testid="recipe-editor">
        <h3>组合编辑（发布新版本）</h3>
        <form class="editor-form" @submit.prevent="publish">
          <label class="editor-field">触发时机
            <select v-model="editor.when" aria-label="触发时机">
              <option v-for="(label, type) in RECIPE_WHEN_LABEL" :key="type" :value="type">
                {{ label }}
              </option>
            </select>
          </label>
          <fieldset class="editor-field">
            <legend>条件（最多 5 条，全部满足才执行；条件只会收紧范围与费用）</legend>
            <div class="editor-conditions">
              <span v-for="condition in editor.conditions" :key="condition.key" class="editor-chip">
                {{ editorConditionLabel(condition) }}
                <button type="button" class="chip-remove"
                        :aria-label="`移除条件 ${editorConditionLabel(condition)}`"
                        @click="removeCondition(condition.key)">×</button>
              </span>
              <p v-if="!editor.conditions.length" class="editor-empty">无条件（每次触发都会执行动作）。</p>
            </div>
            <div class="editor-adds">
              <button type="button" class="btn btn-sm"
                      :disabled="busy || editor.conditions.length >= 5 || editor.spacePrivate"
                      @click="addCondition('SOURCE_SPACE_PRIVATE')">+ 仅私有空间图片</button>
              <button type="button" class="btn btn-sm"
                      :disabled="busy || editor.conditions.length >= 5 || editor.categoryAdded"
                      @click="addCondition('SOURCE_CATEGORY')">+ 仅指定分类</button>
              <button type="button" class="btn btn-sm"
                      :disabled="busy || editor.conditions.length >= 5 || editor.costAdded"
                      @click="addCondition('MAX_TRIAL_COST')">+ 试用额度上限</button>
            </div>
            <label v-if="editor.categoryAdded" class="editor-field">图片分类（安全纯文本）
              <input v-model="editor.category" type="text" maxlength="16" placeholder="如 旅行">
            </label>
            <label v-if="editor.costAdded" class="editor-field">试用额度上限（单位）
              <input v-model="editor.cost" type="number" min="1" max="1000000">
            </label>
          </fieldset>
          <label class="editor-field">动作（白名单能力）
            <select v-model="editor.then" aria-label="动作">
              <option v-for="(label, capability) in RECIPE_CAPABILITY_LABEL" :key="capability"
                      :value="capability">{{ label }}</option>
            </select>
          </label>
          <button class="btn" type="submit" :disabled="busy || !editorValid">发布新版本</button>
          <p class="dry-run-hint">新版本发布后，试运行与执行都会按最新版本重新评估。</p>
        </form>
      </div>

      <div class="dry-run" v-if="pictures.length">
        <h3>试运行（不会产生真实创作）</h3>
        <div class="recipe-pictures">
          <label v-for="picture in pictures.slice(0, 12)" :key="picture.id"
                 class="recipe-picture-choice"
                 :class="{ checked: selectedIds.includes(String(picture.id)) }">
            <input type="checkbox" :value="String(picture.id)" v-model="selectedIds">
            <img :src="picture.thumbnailUrl || picture.url" :alt="picture.name || '图片'"
                 loading="lazy" width="80" height="60">
            <span>{{ picture.name || '未命名图片' }}</span>
          </label>
        </div>
        <button class="btn" type="button" :disabled="busy || selectedIds.length === 0"
                @click="dryRun">试运行</button>
        <p class="dry-run-hint">试运行会按当前定义评估条件并给出费用报价，确认后才产生真实创作任务。</p>
      </div>
      <p v-else class="empty-state">私有图库里暂时没有可用图片，试运行需要先有授权图片。</p>

      <div class="executions" v-if="executions.length">
        <h3>执行回放</h3>
        <ul class="execution-list" data-testid="recipe-executions">
          <li v-for="execution in executions" :key="execution.id" class="execution-row"
              :data-execution-status="execution.status">
            <span class="execution-status">{{ recipeExecutionStatusLabel(execution.status) }}</span>
            <span class="execution-meta">v{{ execution.recipeVersion }} · {{ execution.triggeredTime }}</span>
            <span v-if="execution.creationTaskId" class="execution-task">
              创作任务 #{{ execution.creationTaskId }}
            </span>
            <span v-if="execution.safeErrorCode" class="execution-error">
              错误码 {{ execution.safeErrorCode }}
            </span>
            <p class="execution-quote" v-if="execution.quoteJson">
              报价：{{ quoteText(execution.quoteJson) }}
            </p>
            <p class="execution-matched" v-if="execution.matchedJson">
              命中：{{ matchedText(execution.matchedJson) }}
            </p>
            <button v-if="execution.status === 'DRY_RUN' && selected.recipe.status === 'ENABLED'"
                    class="btn btn-sm" type="button" :disabled="busy || selectedIds.length === 0"
                    @click="execute(execution)">
              确认执行（使用当前所选图片）
            </button>
          </li>
        </ul>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createRecipeFromTemplate,
  deleteRecipe,
  disableRecipe,
  dryRunRecipe,
  enableRecipe,
  executeRecipe,
  getRecipeDetail,
  listRecipeExecutions,
  listRecipes,
  listRecipeTemplates,
  publishRecipeVersion
} from '@/api/recipe'
import {
  RECIPE_CAPABILITY_LABEL,
  RECIPE_WHEN_LABEL,
  recipeCapabilityLabel,
  recipeConditionLabel,
  recipeExecutionStatusLabel,
  recipeStatusLabel,
  recipeWhenLabel
} from '@/constants/recipe'
import { listSpaceVOByPage } from '@/api/space'
import { listPictureVOByPageUncached } from '@/api/picture'
import { buildCompanionPictureQuery, selectOldestPrivateSpace } from '@/utils/companion'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const templates = ref([])
const recipes = ref([])
const selected = ref(null)
const executions = ref([])
const pictures = ref([])
const selectedIds = ref([])
const busy = ref(false)
const error = ref('')

const editor = reactive({
  when: 'WEEKLY_REVIEW',
  then: 'STORY_DRAFT',
  conditions: [],
  spacePrivate: false,
  categoryAdded: false,
  category: '',
  costAdded: false,
  cost: ''
})

const editorValid = computed(() => {
  if (editor.conditions.length > 5) return false
  if (editor.categoryAdded && !editor.category.trim()) return false
  if (editor.costAdded) {
    const units = Number(editor.cost)
    if (!Number.isInteger(units) || units < 1 || units > 1000000) return false
  }
  return true
})

onMounted(async () => {
  await Promise.all([loadTemplates(), loadRecipes(), loadPictures()])
})

async function loadTemplates() {
  try {
    templates.value = (await listRecipeTemplates()) ?? []
  } catch (failure) {
    error.value = extractMessage(failure, '模板加载失败')
  }
}

async function loadRecipes() {
  try {
    recipes.value = (await listRecipes()) ?? []
  } catch (failure) {
    error.value = extractMessage(failure, '配方列表加载失败')
  }
}

async function loadPictures() {
  try {
    const userId = userStore.currentUser?.id
    if (userId == null) return
    const spacesPage = await listSpaceVOByPage({
      current: 1,
      pageSize: 20,
      userId: String(userId),
      spaceType: 0,
      sortField: 'createTime',
      sortOrder: 'ascend'
    })
    const privateSpace = selectOldestPrivateSpace(spacesPage.records || [], userId)
    if (!privateSpace) return
    const picturePage = await listPictureVOByPageUncached(buildCompanionPictureQuery(privateSpace.id))
    pictures.value = picturePage.records || []
  } catch (failure) {
    error.value = extractMessage(failure, '图片列表加载失败')
  }
}

async function createFromTemplate(template) {
  busy.value = true
  try {
    await createRecipeFromTemplate({ templateCode: template.code, name: template.name })
    selectedIds.value = []
    await loadRecipes()
  } catch (failure) {
    error.value = extractMessage(failure, '从模板创建失败')
  } finally {
    busy.value = false
  }
}

async function select(recipe) {
  busy.value = true
  try {
    selected.value = await getRecipeDetail(recipe.id)
    initEditor(selected.value)
    executions.value = (await listRecipeExecutions(recipe.id)) ?? []
    error.value = ''
  } catch (failure) {
    error.value = extractMessage(failure, '配方详情加载失败')
  } finally {
    busy.value = false
  }
}

function initEditor(detail) {
  const latest = detail?.latest
  const parsedConditions = parseJson(latest?.ifJson) ?? []
  editor.when = parseJson(latest?.whenJson)?.type || 'WEEKLY_REVIEW'
  editor.then = parseJson(latest?.thenJson)?.capability || 'STORY_DRAFT'
  editor.conditions = parsedConditions.map(condition => ({
    key: condition.type,
    type: condition.type
  }))
  editor.spacePrivate = parsedConditions.some(condition => condition.type === 'SOURCE_SPACE_PRIVATE')
  editor.categoryAdded = parsedConditions.some(condition => condition.type === 'SOURCE_CATEGORY')
  editor.category = parsedConditions.find(condition => condition.type === 'SOURCE_CATEGORY')?.category ?? ''
  editor.costAdded = parsedConditions.some(condition => condition.type === 'MAX_TRIAL_COST')
  editor.cost = parsedConditions.find(condition => condition.type === 'MAX_TRIAL_COST')?.units ?? ''
}

function addCondition(type) {
  if (editor.conditions.length >= 5) return
  if (type === 'SOURCE_SPACE_PRIVATE') {
    editor.spacePrivate = true
  }
  if (type === 'SOURCE_CATEGORY') {
    editor.categoryAdded = true
  }
  if (type === 'MAX_TRIAL_COST') {
    editor.costAdded = true
  }
  editor.conditions.push({ key: type, type })
}

function removeCondition(key) {
  editor.conditions = editor.conditions.filter(condition => condition.key !== key)
  if (key === 'SOURCE_SPACE_PRIVATE') editor.spacePrivate = false
  if (key === 'SOURCE_CATEGORY') editor.categoryAdded = false
  if (key === 'MAX_TRIAL_COST') editor.costAdded = false
}

function editorConditionLabel(condition) {
  if (condition.type === 'SOURCE_CATEGORY') return `仅「${editor.category || ''}」分类`
  if (condition.type === 'MAX_TRIAL_COST') {
    return `${recipeConditionLabel(condition.type)} ${editor.cost || ''} 单位`
  }
  return recipeConditionLabel(condition.type)
}

async function publish() {
  if (!selected.value || !editorValid.value) return
  busy.value = true
  try {
    const conditions = []
    if (editor.spacePrivate) conditions.push({ type: 'SOURCE_SPACE_PRIVATE' })
    if (editor.categoryAdded) {
      conditions.push({ type: 'SOURCE_CATEGORY', category: editor.category.trim() })
    }
    if (editor.costAdded) conditions.push({ type: 'MAX_TRIAL_COST', units: Number(editor.cost) })
    await publishRecipeVersion(selected.value.recipe.id, {
      when: { type: editor.when },
      conditions,
      then: { capability: editor.then }
    })
    await select(selected.value.recipe)
    await loadRecipes()
    error.value = ''
  } catch (failure) {
    error.value = extractMessage(failure, '版本发布失败')
  } finally {
    busy.value = false
  }
}

async function toggle(recipe, action) {
  busy.value = true
  try {
    if (action === 'enable') await enableRecipe(recipe.id)
    if (action === 'disable') await disableRecipe(recipe.id)
    await loadRecipes()
    if (selected.value?.recipe?.id === recipe.id) await select(recipe)
  } catch (failure) {
    error.value = extractMessage(failure, '配方状态切换失败')
  } finally {
    busy.value = false
  }
}

async function remove(recipe) {
  busy.value = true
  try {
    await deleteRecipe(recipe.id)
    if (selected.value?.recipe?.id === recipe.id) {
      selected.value = null
      executions.value = []
    }
    await loadRecipes()
  } catch (failure) {
    error.value = extractMessage(failure, '配方删除失败')
  } finally {
    busy.value = false
  }
}

async function dryRun() {
  if (!selected.value) return
  busy.value = true
  try {
    await dryRunRecipe(selected.value.recipe.id, { pictureIds: selectedIds.value })
    executions.value = (await listRecipeExecutions(selected.value.recipe.id)) ?? []
    error.value = ''
  } catch (failure) {
    error.value = extractMessage(failure, '试运行失败')
  } finally {
    busy.value = false
  }
}

async function execute(execution) {
  busy.value = true
  try {
    await executeRecipe(selected.value.recipe.id, execution.id, { pictureIds: selectedIds.value })
    executions.value = (await listRecipeExecutions(selected.value.recipe.id)) ?? []
    error.value = ''
  } catch (failure) {
    error.value = extractMessage(failure, '执行失败，请查看回放记录')
    executions.value = (await listRecipeExecutions(selected.value.recipe.id)) ?? []
  } finally {
    busy.value = false
  }
}

function summaryText(template) {
  return `${recipeWhenLabel(parseJson(template.whenJson)?.type)} → ${recipeCapabilityLabel(parseJson(template.thenJson)?.capability)}`
}

function whenLabel(whenJson) {
  return recipeWhenLabel(parseJson(whenJson)?.type)
}

function capabilityLabel(thenJson) {
  return recipeCapabilityLabel(parseJson(thenJson)?.capability)
}

function conditions(ifJson) {
  return parseJson(ifJson) ?? []
}

function conditionText(condition) {
  if (condition.type === 'SOURCE_CATEGORY') return `仅「${condition.category}」分类`
  if (condition.type === 'MAX_TRIAL_COST') return `${recipeConditionLabel(condition.type)} ${condition.units} 单位`
  return recipeConditionLabel(condition.type)
}

function quoteText(quoteJson) {
  const quote = parseJson(quoteJson)
  if (!quote) return ''
  if (quote.byokOnly) return `${recipeCapabilityLabel(quote.capability)} · 用户自带密钥`
  return `${recipeCapabilityLabel(quote.capability)} · 平台额度 ${quote.platformUnits} 单位`
}

function matchedText(matchedJson) {
  const matched = parseJson(matchedJson)
  if (!matched) return ''
  const conditions = matched.conditions ?? []
  if (!conditions.length) return `${recipeWhenLabel(matched.when)} · 无条件`
  return conditions.map(condition => `${recipeConditionLabel(condition.type)}${condition.matched ? '✓' : '✗'}`).join('，')
}

function parseJson(text) {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function extractMessage(failure, fallback) {
  return failure?.message || fallback
}
</script>

<style scoped>
.recipe-workshop { max-width: 64rem; margin: 0 auto; padding: 1.5rem; }
.recipe-header h1 { font-size: 1.8rem; }
.recipe-header p { color: var(--gray-600); }
.panel-error { margin: 1rem 0; padding: .6rem .8rem; border-left: 4px solid var(--red); background: var(--gray-100); color: var(--red); font-size: .8rem; }
.recipe-templates, .recipe-mine, .recipe-detail { margin-top: 1.5rem; border: 2px solid var(--black); background: var(--white); }
.recipe-templates > h2, .recipe-mine > h2, .recipe-detail > h2 { margin: 0; padding: 1rem 1.25rem; border-bottom: 2px solid var(--black); font-size: 1.1rem; }
.template-list { list-style: none; display: grid; grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr)); gap: .8rem; padding: 1rem 1.25rem; }
.template-card { display: grid; gap: .4rem; padding: .9rem; border: 1px solid var(--gray-400); }
.template-card p { font-size: .8rem; color: var(--gray-600); }
.template-summary { font-weight: 700; color: #075d2a; }
.recipe-list, .execution-list { list-style: none; }
.recipe-row { display: flex; justify-content: space-between; gap: .8rem; padding: .8rem 1.25rem; border-bottom: 1px solid var(--gray-200); }
.recipe-main { display: flex; gap: .6rem; align-items: center; }
.recipe-status { padding: .1rem .45rem; background: var(--gray-100); border: 1px solid var(--gray-400); font-size: .72rem; font-weight: 700; }
.recipe-status.on { background: #e6f4ea; border-color: #075d2a; color: #075d2a; }
.recipe-version { font-size: .75rem; color: var(--gray-600); }
.recipe-actions { display: flex; gap: .5rem; }
.definition-box { padding: 1rem 1.25rem; }
.definition-box p { line-height: 1.7; }
.version-hint { font-size: .75rem; color: var(--gray-600); }
.dry-run { padding: 1rem 1.25rem; border-top: 1px solid var(--gray-200); }
.dry-run h3, .executions h3 { font-size: .95rem; margin-bottom: .6rem; }
.recipe-pictures { display: grid; grid-template-columns: repeat(auto-fill, minmax(7rem, 1fr)); gap: .6rem; margin-bottom: .8rem; }
.recipe-picture-choice { display: grid; gap: .3rem; padding: .4rem; border: 2px solid var(--gray-200); cursor: pointer; font-size: .72rem; font-weight: 700; }
.recipe-picture-choice.checked { border-color: var(--blue); box-shadow: 0 0 0 2px var(--blue); }
.recipe-picture-choice input { position: absolute; opacity: 0; }
.recipe-picture-choice:has(input:focus-visible) { outline: 3px solid var(--blue); outline-offset: 2px; }
.recipe-picture-choice img { width: 100%; aspect-ratio: 4 / 3; object-fit: cover; background: var(--gray-100); }
.dry-run-hint { margin-top: .6rem; font-size: .78rem; color: var(--gray-600); }
.executions { padding: 1rem 1.25rem; border-top: 1px solid var(--gray-200); }
.execution-row { display: grid; gap: .3rem; padding: .7rem 0; border-bottom: 1px dashed var(--gray-300); }
.execution-status { justify-self: start; padding: .1rem .45rem; background: var(--gray-100); border: 1px solid var(--gray-400); font-size: .72rem; font-weight: 700; }
.execution-meta { font-size: .72rem; color: var(--gray-600); }
.execution-task { font-size: .8rem; font-weight: 700; color: #075d2a; }
.execution-error { font-size: .8rem; font-weight: 700; color: var(--red); }
.execution-quote, .execution-matched { font-size: .8rem; }
.empty-state { padding: 1rem 1.25rem; color: var(--gray-600); font-size: .9rem; }
.btn-danger { border-color: var(--red); color: var(--red); }
</style>
