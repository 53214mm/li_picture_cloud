<template>
  <div class="picture-list">
    <!-- 工具栏：搜索 + 筛选 + 操作 -->
    <div class="toolbar" v-if="showToolbar !== false">
      <div class="toolbar-row">
        <div class="search-box-inline">
          <input
            v-model="searchText"
            class="input"
            placeholder="搜索图片名称或简介…"
            @keyup.enter="$emit('search', searchText)"
          />
          <button class="btn btn-primary" @click="$emit('search', searchText)">搜索</button>
        </div>
        <div class="toolbar-actions">
          <slot name="toolbar-actions"></slot>
        </div>
      </div>
      <div class="filter-row">
        <select v-model="category" class="input filter-select" @change="$emit('filter-change', filters)">
          <option value="">全部分类</option>
          <option v-for="c in categoryList" :key="c" :value="c">{{ c }}</option>
        </select>
        <select v-model="picFormat" class="input filter-select" @change="$emit('filter-change', filters)">
          <option value="">全部格式</option>
          <option value="jpg">JPG</option>
          <option value="jpeg">JPEG</option>
          <option value="png">PNG</option>
          <option value="webp">WEBP</option>
        </select>
        <select v-model="sortOrder" class="input filter-select" @change="$emit('filter-change', filters)">
          <option value="descend">最新优先</option>
          <option value="ascend">最早优先</option>
        </select>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading">加载中…</div>

    <!-- 空状态 -->
    <div v-else-if="pictures.length === 0" class="empty-state">
      <p>{{ emptyText }}</p>
    </div>

    <!-- 图片网格 -->
    <div v-else class="gallery-grid">
      <div
        v-for="pic in pictures"
        :key="pic.id"
        class="gallery-card"
        @click="goDetail(pic.id)"
      >
        <img
          :src="pic.thumbnailUrl || pic.url"
          :alt="pic.name || '图片'"
          class="card-img"
          loading="lazy"
        />
        <div class="card-overlay">
          <h3 class="card-name">{{ pic.name || '未命名' }}</h3>
          <p class="card-meta" v-if="pic.user">
            {{ pic.user.userName }} · {{ formatDate(pic.createTime) }}
          </p>
          <div class="card-tags" v-if="pic.tags && pic.tags.length">
            <span v-for="tag in pic.tags.slice(0, 3)" :key="tag" class="mini-tag">{{ tag }}</span>
          </div>
        </div>

        <!-- 选择框（批量模式） -->
        <div v-if="selectable" class="card-check" @click.stop>
          <input
            type="checkbox"
            :checked="isSelected(pic.id)"
            @change="$emit('toggle-select', pic.id)"
          />
        </div>

        <!-- 快捷操作按钮 -->
        <div class="card-actions" v-if="showActions !== false" @click.stop>
          <slot name="actions" :picture="pic">
            <button class="action-btn" @click.stop="goDetail(pic.id)" title="查看详情">
              <span class="action-icon">🔍</span>
            </button>
          </slot>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="totalPages > 1">
      <button :disabled="currentPage <= 1" @click="$emit('page-change', currentPage - 1)">上一页</button>
      <span>第 {{ currentPage }} / {{ totalPages }} 页 (共 {{ total }} 张)</span>
      <button :disabled="currentPage >= totalPages" @click="$emit('page-change', currentPage + 1)">下一页</button>
      <span class="jumper">跳至
        <input
          v-model.number="jumpInput"
          class="jump-input"
          @keyup.enter="handleJump"
          placeholder="页数"
        />
        页
        <button class="btn-jump" @click="handleJump">GO</button>
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  pictures: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  current: { type: Number, default: 1 },
  pageSize: { type: Number, default: 12 },
  categoryList: { type: Array, default: () => [] },
  emptyText: { type: String, default: '暂无图片' },
  showToolbar: { type: Boolean, default: true },
  showActions: { type: Boolean, default: false },
  selectable: { type: Boolean, default: false },
  selectedIds: { type: Array, default: () => [] }
})

const emit = defineEmits(['search', 'filter-change', 'page-change', 'jump', 'toggle-select'])

const router = useRouter()

const searchText = ref('')
const category = ref('')
const picFormat = ref('')
const sortOrder = ref('descend')
const jumpInput = ref(null)

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
const currentPage = computed(() => props.current)

const filters = computed(() => ({
  searchText: searchText.value,
  category: category.value,
  picFormat: picFormat.value,
  sortOrder: sortOrder.value
}))

function handleJump() {
  const page = jumpInput.value
  if (page && page >= 1 && page <= totalPages.value) {
    emit('page-change', page)
    jumpInput.value = null
  }
}

function isSelected(id) {
  return props.selectedIds.includes(id)
}

function goDetail(id) {
  router.push(`/picture/${id}`)
}

function formatDate(d) {
  return d ? new Date(d).toLocaleDateString('zh-CN') : '-'
}
</script>

<style scoped>
.picture-list { width: 100%; }

/* 工具栏 */
.toolbar { margin-bottom: 2rem; }
.toolbar-row {
  display: flex; align-items: center; justify-content: space-between;
  gap: 1rem; margin-bottom: 0.75rem; flex-wrap: wrap;
}
.search-box-inline { display: flex; gap: 0.5rem; flex: 1; max-width: 480px; }
.filter-row { display: flex; gap: 0.75rem; flex-wrap: wrap; }
.filter-select { max-width: 160px; }

/* 状态 */
.loading { text-align: center; padding: 4rem 0; color: var(--gray-400); font-size: 1.125rem; }
.empty-state { text-align: center; padding: 5rem 0; color: var(--gray-400); }
.empty-state p { font-size: 1.125rem; }

/* 图片网格 */
.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.5rem;
}
.gallery-card {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid var(--gray-200);
  transition: transform 0.3s, box-shadow 0.3s;
}
.gallery-card:hover { transform: translate(-2px, -2px); box-shadow: 6px 6px 0 var(--black); }
.card-img {
  width: 100%; height: 100%;
  object-fit: cover;
  transition: transform 0.4s;
}
.gallery-card:hover .card-img { transform: scale(1.05); }

.card-overlay {
  position: absolute; bottom: 0; left: 0; right: 0;
  padding: 1.5rem 1.25rem;
  background: linear-gradient(transparent, rgba(0,0,0,0.75));
  color: var(--white);
}
.card-name { font-size: 1rem; font-weight: 600; margin-bottom: 0.25rem; }
.card-meta { font-size: 0.75rem; opacity: 0.85; margin-bottom: 0.5rem; }
.card-tags { display: flex; flex-wrap: wrap; gap: 0.375rem; }
.mini-tag {
  padding: 0.125rem 0.5rem; font-size: 0.6875rem;
  background: rgba(255,255,255,0.2);
}

/* 快捷操作 */
.card-actions {
  position: absolute; top: 0.5rem; right: 0.5rem;
  display: flex; gap: 0.375rem; opacity: 0;
  transition: opacity 0.2s;
}
.gallery-card:hover .card-actions { opacity: 1; }

/* 复选框 */
.card-check {
  position: absolute; top: 0.5rem; left: 0.5rem; z-index: 5;
}
.card-check input[type="checkbox"] {
  width: 20px; height: 20px; cursor: pointer;
  accent-color: var(--black);
}

/* 有勾选时卡片高亮 */
.gallery-card:has(.card-check input:checked) {
  border-color: var(--black);
  box-shadow: 0 0 0 2px var(--black);
}
.action-btn {
  width: 32px; height: 32px;
  border: none; background: rgba(0,0,0,0.6); color: var(--white);
  border-radius: 4px; cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 0.75rem; transition: background 0.2s;
}
.action-btn:hover { background: var(--red); }
.action-icon { font-size: 0.875rem; }

/* 分页 */
.pagination {
  display: flex; align-items: center; justify-content: center; gap: 1.5rem;
  margin-top: 3rem; font-size: 0.875rem; flex-wrap: wrap;
}
.pagination button { font-weight: 600; padding: 0.5rem 1rem; border: 2px solid var(--black); background: var(--white); cursor: pointer; }
.pagination button:disabled { opacity: 0.3; cursor: default; }
.pagination button:hover:not(:disabled) { background: var(--black); color: var(--white); }
.jumper { font-size: 0.8125rem; color: var(--gray-600); display: flex; align-items: center; gap: 0.375rem; }
.jump-input {
  width: 56px; padding: 0.375rem 0.5rem; text-align: center;
  border: 1.5px solid var(--gray-200); font-size: 0.8125rem; font-family: inherit; outline: none;
}
.jump-input:focus { border-color: var(--black); }
.btn-jump { padding: 0.25rem 0.625rem; font-size: 0.75rem; font-weight: 600; border: 1.5px solid var(--black); background: var(--white); cursor: pointer; }
.btn-jump:hover { background: var(--black); color: var(--white); }

@media (max-width: 767px) {
  .toolbar-row, .search-box-inline { width: 100%; }
  .search-box-inline { max-width: none; }
  .filter-row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filter-select { max-width: none; }
  .gallery-grid { grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr)); gap: 1rem; }
  .card-check input[type="checkbox"] { width: 28px; height: 28px; }
  .action-btn { width: 44px; height: 44px; }
  .pagination { gap: 0.75rem; margin-top: 2rem; }
}

@media (max-width: 480px) {
  .search-box-inline { flex-direction: column; }
  .filter-row, .gallery-grid { grid-template-columns: 1fr; }
  .jumper { display: none; }
}

@media (hover: none) {
  .card-actions { opacity: 1; }
  .gallery-card:hover { transform: none; box-shadow: none; }
  .gallery-card:hover .card-img { transform: none; }
}
</style>
