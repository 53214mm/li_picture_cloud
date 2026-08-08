# LiPictureCloud Full-Site Mobile Adaptation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all 13 Vue routes usable from 360px phones through tablets without page-level horizontal overflow while preserving the existing desktop design and business behavior.

**Architecture:** Add a shared responsive foundation in `src/style.css`, derive desktop and mobile navigation from one permission-aware navigation model, then adapt each existing view inside its current component boundary. Keep REST, SSE, WebSocket, Pinia, and router data flows unchanged; only navigation visibility and local presentation state are new.

**Tech Stack:** Vue 3, Vue Router 4, Pinia 2, CSS media queries, ECharts 6, Node.js 22 native test runner, ESLint 9, Vite 7.

## Global Constraints

- 360–767px is the complete phone layout; validate 360, 390, and 430px.
- 768–1023px is the tablet transition layout; 1024px and above preserves the desktop baseline.
- The page body must not scroll horizontally; only explicit editor/code/data subregions may scroll internally.
- Interactive controls must expose at least a 44×44px touch area with at least 8px spacing between primary controls.
- Preserve browser zoom and support `prefers-reduced-motion`, keyboard focus, portrait, and landscape.
- Do not change backend endpoints, permissions, collaboration protocol, database, or route URLs.
- Do not add a UI framework, icon library, PWA layer, or a second set of mobile business components.
- Preserve the current Swiss graphic style, semantic color tokens, and desktop information architecture.

## File Structure

- `src/style.css`: global responsive tokens, container gutters, touch/focus rules, safe-area handling, modal defaults, and reduced-motion behavior.
- `src/constants/navigation.js`: one permission-aware navigation model shared by desktop links and the mobile drawer.
- `src/components/NavBar.vue`: desktop navigation plus the grouped mobile drawer and its local open/close behavior.
- Existing view/component `.vue` files: scoped, feature-specific layout adaptations; no duplicated API or store logic.
- `tests/responsiveFoundation.test.mjs`: global CSS and viewport contracts.
- `tests/navigation.test.mjs`: navigation permissions and drawer accessibility contracts.
- `tests/responsiveViews.test.mjs`: source contracts for public, media, space, admin, editor, and analysis views.
- `docs/testing/mobile-responsive-checklist.md`: repeatable route-by-route visual verification matrix.

---

### Task 1: Responsive foundation

**Files:**
- Create: `li-picture-cloud-frontend/tests/responsiveFoundation.test.mjs`
- Modify: `li-picture-cloud-frontend/src/style.css`

**Interfaces:**
- Consumes: existing CSS variables `--black`, `--white`, `--red`, `--gray-*`, and `.container`, `.btn`, `.input`.
- Produces: global phone/tablet gutters, 44px touch controls, `:focus-visible`, safe-area support, overflow containment, and reduced-motion behavior used by every later task.

- [ ] **Step 1: Write the failing responsive foundation test**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const css = readFileSync(join(root, 'src/style.css'), 'utf8')

test('defines phone and tablet responsive foundations', () => {
  assert.match(css, /@media \(max-width: 1023px\)/)
  assert.match(css, /@media \(max-width: 767px\)/)
  assert.match(css, /@media \(max-width: 480px\)/)
  assert.match(css, /min-height:\s*44px/)
  assert.match(css, /:focus-visible/)
  assert.match(css, /safe-area-inset/)
  assert.match(css, /prefers-reduced-motion:\s*reduce/)
})
```

- [ ] **Step 2: Run the test and verify RED**

Run: `cd li-picture-cloud-frontend && node --test tests/responsiveFoundation.test.mjs`

Expected: FAIL because the global stylesheet does not yet contain the three breakpoint contracts or reduced-motion block.

- [ ] **Step 3: Implement the global foundation**

Add concrete rules to `src/style.css` using this structure:

```css
html, body, #app { min-width: 0; min-height: 100%; }
body { overflow-x: clip; }
button, a, input, select, textarea { touch-action: manipulation; }
:where(a, button, input, select, textarea):focus-visible {
  outline: 3px solid var(--blue);
  outline-offset: 3px;
}

@media (max-width: 1023px) {
  .container { padding-inline: 1.5rem; }
}

@media (max-width: 767px) {
  .container { padding-inline: max(1rem, env(safe-area-inset-left)); }
  .btn, .input, button, select { min-height: 44px; }
  .btn { padding-inline: 1.25rem; }
}

@media (max-width: 480px) {
  .container { padding-inline: max(0.875rem, env(safe-area-inset-left)); }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    scroll-behavior: auto !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
```

- [ ] **Step 4: Run the focused test and lint**

Run: `cd li-picture-cloud-frontend && node --test tests/responsiveFoundation.test.mjs && npm run lint`

Expected: the focused test passes and ESLint exits 0.

- [ ] **Step 5: Commit the foundation**

```bash
git add li-picture-cloud-frontend/src/style.css li-picture-cloud-frontend/tests/responsiveFoundation.test.mjs
git commit -m "feat: add responsive layout foundation"
```

---

### Task 2: Permission-aware mobile drawer navigation

**Files:**
- Create: `li-picture-cloud-frontend/src/constants/navigation.js`
- Create: `li-picture-cloud-frontend/tests/navigation.test.mjs`
- Modify: `li-picture-cloud-frontend/src/components/NavBar.vue`

**Interfaces:**
- Consumes: `useUserStore().isLoggedIn`, `useUserStore().isAdmin`, `useRoute()`, and `useRouter()`.
- Produces: `buildNavigationGroups({ isLoggedIn, isAdmin }) -> Array<{ id, label, items }>` and one responsive navigation component for desktop and mobile.

- [ ] **Step 1: Write failing navigation model and accessibility tests**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { join } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const navigationModule = await import(pathToFileURL(join(root, 'src/constants/navigation.js')))
const navSource = readFileSync(join(root, 'src/components/NavBar.vue'), 'utf8')

test('builds user and admin navigation from one model', () => {
  const user = navigationModule.buildNavigationGroups({ isLoggedIn: true, isAdmin: false })
  const admin = navigationModule.buildNavigationGroups({ isLoggedIn: true, isAdmin: true })
  assert.deepEqual(user.map(group => group.id), ['browse', 'workspace', 'account'])
  assert.deepEqual(admin.map(group => group.id), ['browse', 'workspace', 'admin', 'account'])
  assert.equal(user.flatMap(group => group.items).some(item => item.to === '/admin/users'), false)
  assert.equal(admin.flatMap(group => group.items).some(item => item.to === '/admin/users'), true)
})

test('mobile drawer exposes accessible state and close controls', () => {
  assert.match(navSource, /aria-expanded/)
  assert.match(navSource, /aria-controls="mobile-navigation"/)
  assert.match(navSource, /@keydown\.esc/)
  assert.match(navSource, /class="mobile-nav-overlay"/)
})
```

- [ ] **Step 2: Run the test and verify RED**

Run: `cd li-picture-cloud-frontend && node --test tests/navigation.test.mjs`

Expected: FAIL because `src/constants/navigation.js` and drawer markup do not exist.

- [ ] **Step 3: Add the shared navigation model**

Create `src/constants/navigation.js` with explicit groups and permission filtering:

```js
export function buildNavigationGroups({ isLoggedIn, isAdmin }) {
  const groups = [{ id: 'browse', label: '浏览', items: [
    { label: '首页', to: '/' }, { label: '探索图库', to: '/gallery' }
  ] }]
  if (isLoggedIn) groups.push({ id: 'workspace', label: '工作空间', items: [
    { label: '上传图片', to: '/upload' }, { label: '我的空间', to: '/space/my' },
    { label: '空间管理', to: '/spaces' }, { label: '图库分析', to: '/space/analyze' }
  ] })
  if (isLoggedIn && isAdmin) groups.push({ id: 'admin', label: '管理', items: [
    { label: '图片审核', to: '/admin/pictures' }, { label: '用户管理', to: '/admin/users' }
  ] })
  groups.push({ id: 'account', label: '账户', items: isLoggedIn
    ? [{ label: '退出登录', action: 'logout', danger: true }]
    : [{ label: '登录', to: '/login' }, { label: '注册', to: '/register' }] })
  return groups
}
```

- [ ] **Step 4: Implement the drawer and shared rendering**

In `NavBar.vue`, derive `navigationGroups`, add `mobileOpen`, close it on route changes and Escape, restore body scrolling on close/unmount, and render:

```vue
<button class="mobile-menu-btn" aria-label="打开导航菜单"
  aria-controls="mobile-navigation" :aria-expanded="mobileOpen" @click="openMobileNav">☰</button>
<div v-if="mobileOpen" class="mobile-nav-overlay" @click.self="closeMobileNav" @keydown.esc="closeMobileNav">
  <aside id="mobile-navigation" class="mobile-nav-drawer" aria-label="移动端主导航">
    <button class="mobile-nav-close" aria-label="关闭导航菜单" @click="closeMobileNav">×</button>
    <section v-for="group in navigationGroups" :key="group.id" class="mobile-nav-group">
      <h2>{{ group.label }}</h2>
      <template v-for="item in group.items" :key="item.to || item.action">
        <router-link v-if="item.to" :to="item.to" @click="closeMobileNav">{{ item.label }}</router-link>
        <button v-else :class="{ danger: item.danger }" @click="handleNavigationItem(item)">{{ item.label }}</button>
      </template>
    </section>
  </aside>
</div>
```

Use this exact action bridge so logout also closes the drawer:

```js
async function handleNavigationItem(item) {
  if (item.action === 'logout') await handleLogout()
  closeMobileNav()
}
```

Add desktop/mobile visibility rules at 767px and safe-area padding for the drawer.

- [ ] **Step 5: Verify GREEN and regression safety**

Run: `cd li-picture-cloud-frontend && node --test tests/navigation.test.mjs && npm run lint && npm run build`

Expected: both navigation tests pass, lint exits 0, and Vite builds successfully.

- [ ] **Step 6: Commit navigation**

```bash
git add li-picture-cloud-frontend/src/constants/navigation.js li-picture-cloud-frontend/src/components/NavBar.vue li-picture-cloud-frontend/tests/navigation.test.mjs
git commit -m "feat: add accessible mobile navigation drawer"
```

---

### Task 3: Public pages, authentication, galleries, and pagination

**Files:**
- Create: `li-picture-cloud-frontend/tests/responsiveViews.test.mjs`
- Modify: `li-picture-cloud-frontend/src/components/HeroSection.vue`
- Modify: `li-picture-cloud-frontend/src/components/PictureList.vue`
- Modify: `li-picture-cloud-frontend/src/views/HomeView.vue`
- Modify: `li-picture-cloud-frontend/src/views/LoginView.vue`
- Modify: `li-picture-cloud-frontend/src/views/RegisterView.vue`
- Modify: `li-picture-cloud-frontend/src/views/GalleryView.vue`

**Interfaces:**
- Consumes: global responsive foundation and existing component props/events.
- Produces: phone-safe hero/search, authentication forms, picture grids, filters, card actions, and pagination without changing emitted event names.

- [ ] **Step 1: Add failing source contracts for public views**

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const read = path => readFileSync(join(root, path), 'utf8')

test('public views define phone layouts', () => {
  for (const file of ['components/HeroSection.vue', 'components/PictureList.vue', 'views/HomeView.vue',
    'views/LoginView.vue', 'views/RegisterView.vue', 'views/GalleryView.vue']) {
    assert.match(read(`src/${file}`), /@media \(max-width: 767px\)/, file)
  }
  assert.match(read('src/components/PictureList.vue'), /@media \(hover: none\)/)
  assert.match(read('src/views/GalleryView.vue'), /grid-template-columns:\s*1fr/)
})
```

- [ ] **Step 2: Run the public-view test and verify RED**

Run: `cd li-picture-cloud-frontend && node --test --test-name-pattern="public views" tests/responsiveViews.test.mjs`

Expected: FAIL for the views that lack phone media queries and touch-device card actions.

- [ ] **Step 3: Implement public and authentication layouts**

Add scoped 767px rules so Hero search stacks below 480px, tags have 44px targets, home sections use reduced spacing, auth cards use 1rem gutters, and primary form buttons are full-width on phones:

```css
@media (max-width: 767px) {
  .hero { min-height: auto; }
  .hero-content { padding-block: 3rem 2rem; }
  .hero-desc { font-size: 1rem; }
  .tag { min-height: 44px; }
  .section { padding-block: 3rem; }
}
@media (max-width: 480px) {
  .search-box { flex-direction: column; }
  .search-btn { min-height: 48px; justify-content: center; }
}
```

- [ ] **Step 4: Implement gallery, toolbar, card-action, and pagination layouts**

At 767px, remove filter `max-width`, make grids single-column at 480px, keep card actions visible on non-hover devices, and split pagination into wrapped rows:

```css
@media (max-width: 767px) {
  .search-box-inline, .filter-row { width: 100%; }
  .filter-select { max-width: none; flex: 1 1 10rem; }
  .gallery-grid { grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr)); gap: 1rem; }
  .pagination { gap: .75rem; margin-top: 2rem; }
}
@media (max-width: 480px) { .gallery-grid { grid-template-columns: 1fr; } }
@media (hover: none) { .card-actions { opacity: 1; } }
```

- [ ] **Step 5: Verify GREEN**

Run: `cd li-picture-cloud-frontend && node --test --test-name-pattern="public views" tests/responsiveViews.test.mjs && npm run lint && npm run build`

Expected: the public-view contract passes and the application builds.

- [ ] **Step 6: Commit public surfaces**

```bash
git add li-picture-cloud-frontend/tests/responsiveViews.test.mjs li-picture-cloud-frontend/src/components/HeroSection.vue li-picture-cloud-frontend/src/components/PictureList.vue li-picture-cloud-frontend/src/views/HomeView.vue li-picture-cloud-frontend/src/views/LoginView.vue li-picture-cloud-frontend/src/views/RegisterView.vue li-picture-cloud-frontend/src/views/GalleryView.vue
git commit -m "feat: adapt public picture flows for mobile"
```

---

### Task 4: Picture detail, upload, sharing, and editor

**Files:**
- Modify: `li-picture-cloud-frontend/tests/responsiveViews.test.mjs`
- Modify: `li-picture-cloud-frontend/src/views/PictureDetailView.vue`
- Modify: `li-picture-cloud-frontend/src/views/PictureUploadView.vue`
- Modify: `li-picture-cloud-frontend/src/components/ShareModal.vue`
- Modify: `li-picture-cloud-frontend/src/components/ImageEditModal.vue`
- Modify: `li-picture-cloud-frontend/src/components/ImageEditor.vue`

**Interfaces:**
- Consumes: existing upload/edit emits, collaboration operations, fullscreen state, and global modal/touch rules.
- Produces: single-column picture workflow, phone-safe modals, persistent editor actions, and explicit internal editor scrolling; emitted payloads remain unchanged.

- [ ] **Step 1: Extend the contract test and verify RED**

```js
test('picture workflows define phone-safe modals and editor regions', () => {
  for (const file of ['views/PictureDetailView.vue', 'views/PictureUploadView.vue',
    'components/ShareModal.vue', 'components/ImageEditModal.vue', 'components/ImageEditor.vue']) {
    assert.match(read(`src/${file}`), /@media \(max-width: 767px\)/, file)
  }
  assert.match(read('src/components/ImageEditor.vue'), /overflow-x:\s*auto|flex-wrap:\s*wrap/)
  assert.match(read('src/components/ImageEditModal.vue'), /safe-area-inset-bottom/)
})
```

Run: `cd li-picture-cloud-frontend && node --test --test-name-pattern="picture workflows" tests/responsiveViews.test.mjs`

Expected: FAIL because the five components do not yet satisfy the phone contracts.

- [ ] **Step 2: Adapt detail, upload, and sharing surfaces**

Implement one-column detail layout, stacked review/actions, full-width upload controls, modal viewport gutters, scrollable content, and responsive QR sizing. Use `max-height: min(90dvh, 48rem)` and `padding-bottom: max(1rem, env(safe-area-inset-bottom))` for mobile modal panels.

- [ ] **Step 3: Adapt the editor without changing collaboration data flow**

Keep rotation/zoom/crop emit names intact. Make `.editor-toolbar` wrap at tablet widths and horizontally scroll at phone widths, give `.editor-canvas-wrap` explicit `overflow: auto`, and keep `.modal-footer` outside the editor scroll region. Add `aria-label` to icon-only close and fullscreen controls.

- [ ] **Step 4: Verify GREEN**

Run: `cd li-picture-cloud-frontend && node --test --test-name-pattern="picture workflows" tests/responsiveViews.test.mjs && npm run lint && npm run build`

Expected: the workflow contract passes with no lint or build failures.

- [ ] **Step 5: Commit picture workflows**

```bash
git add li-picture-cloud-frontend/tests/responsiveViews.test.mjs li-picture-cloud-frontend/src/views/PictureDetailView.vue li-picture-cloud-frontend/src/views/PictureUploadView.vue li-picture-cloud-frontend/src/components/ShareModal.vue li-picture-cloud-frontend/src/components/ImageEditModal.vue li-picture-cloud-frontend/src/components/ImageEditor.vue
git commit -m "feat: adapt picture workflows and editor for mobile"
```

---

### Task 5: Spaces, members, and AI panel

**Files:**
- Modify: `li-picture-cloud-frontend/tests/responsiveViews.test.mjs`
- Modify: `li-picture-cloud-frontend/src/views/MySpaceView.vue`
- Modify: `li-picture-cloud-frontend/src/views/SpaceCreateView.vue`
- Modify: `li-picture-cloud-frontend/src/views/SpaceManageView.vue`
- Modify: `li-picture-cloud-frontend/src/views/SpaceDetailView.vue`
- Modify: `li-picture-cloud-frontend/src/components/space/SpaceCard.vue`
- Modify: `li-picture-cloud-frontend/src/components/space/SpaceMemberPanel.vue`
- Modify: `li-picture-cloud-frontend/src/components/AiAgentPanel.vue`

**Interfaces:**
- Consumes: existing space permissions, upload/batch actions, member emits, SSE message rendering, and `PictureList` responsive behavior.
- Produces: phone-safe space cards/forms/toolbars, full-width member drawer, and bounded AI message/input regions.

- [ ] **Step 1: Add failing space and AI contracts**

```js
test('space and AI surfaces define phone layouts', () => {
  for (const file of ['views/MySpaceView.vue', 'views/SpaceCreateView.vue', 'views/SpaceManageView.vue',
    'views/SpaceDetailView.vue', 'components/space/SpaceCard.vue',
    'components/space/SpaceMemberPanel.vue', 'components/AiAgentPanel.vue']) {
    assert.match(read(`src/${file}`), /@media \(max-width: 767px\)/, file)
  }
  assert.match(read('src/components/space/SpaceMemberPanel.vue'), /width:\s*100%/)
  assert.match(read('src/components/AiAgentPanel.vue'), /overflow-x:\s*auto|flex-wrap:\s*wrap/)
})
```

Run: `cd li-picture-cloud-frontend && node --test --test-name-pattern="space and AI" tests/responsiveViews.test.mjs`

Expected: FAIL for components without a 767px layout contract.

- [ ] **Step 2: Adapt space lists, forms, detail toolbars, and member panel**

Use one-column grids on phones, remove rigid input `max-width`, stack capacity meters and batch controls, make modal actions wrap, and set the member panel to `width: min(100%, 34rem)` on desktop and `width: 100%` on phones with safe-area padding.

- [ ] **Step 3: Adapt AI panel and streamed content**

Replace rigid heights with `min-height: min(28rem, 65dvh)` and `max-height` bounded by the viewport, wrap or internally scroll quick actions, expand message bubbles to `max-width: 90%` on phones, and constrain Markdown `pre`, `table`, and images inside the message region.

- [ ] **Step 4: Verify GREEN**

Run: `cd li-picture-cloud-frontend && node --test --test-name-pattern="space and AI" tests/responsiveViews.test.mjs && npm run lint && npm run build`

Expected: the space/AI contract passes and the application builds.

- [ ] **Step 5: Commit spaces and AI**

```bash
git add li-picture-cloud-frontend/tests/responsiveViews.test.mjs li-picture-cloud-frontend/src/views/MySpaceView.vue li-picture-cloud-frontend/src/views/SpaceCreateView.vue li-picture-cloud-frontend/src/views/SpaceManageView.vue li-picture-cloud-frontend/src/views/SpaceDetailView.vue li-picture-cloud-frontend/src/components/space/SpaceCard.vue li-picture-cloud-frontend/src/components/space/SpaceMemberPanel.vue li-picture-cloud-frontend/src/components/AiAgentPanel.vue
git commit -m "feat: adapt spaces and AI panel for mobile"
```

---

### Task 6: Administrator tables and analytics

**Files:**
- Modify: `li-picture-cloud-frontend/tests/responsiveViews.test.mjs`
- Modify: `li-picture-cloud-frontend/src/views/AdminUserView.vue`
- Modify: `li-picture-cloud-frontend/src/views/PictureManageView.vue`
- Modify: `li-picture-cloud-frontend/src/views/SpaceAnalyzeView.vue`

**Interfaces:**
- Consumes: existing table rows, filter state, review/delete handlers, and ECharts option objects.
- Produces: semantic card-like table rows below 767px, stacked admin tools, single-column charts, and touch-readable chart options without changing API queries.

- [ ] **Step 1: Add failing admin and analytics contracts**

```js
test('admin tables and analytics have phone contracts', () => {
  const users = read('src/views/AdminUserView.vue')
  const pictures = read('src/views/PictureManageView.vue')
  const analyze = read('src/views/SpaceAnalyzeView.vue')
  assert.match(users, /data-label="账号"/)
  assert.match(pictures, /data-label="审核状态"/)
  assert.match(users, /@media \(max-width: 767px\)/)
  assert.match(pictures, /@media \(max-width: 767px\)/)
  assert.match(analyze, /@media \(max-width: 767px\)/)
  assert.match(analyze, /grid-template-columns:\s*1fr/)
})
```

Run: `cd li-picture-cloud-frontend && node --test --test-name-pattern="admin tables" tests/responsiveViews.test.mjs`

Expected: FAIL because table cells lack mobile labels and analytics lacks a phone grid.

- [ ] **Step 2: Convert admin rows into semantic mobile cards**

Add literal `data-label` values to each meaningful `<td>`, wrap tables in `.table-scroll`, and below 767px visually hide `<thead>`, render each `<tr>` as a bordered block, and render each cell with `td::before { content: attr(data-label) }`. Keep table elements in the DOM so desktop and assistive semantics remain intact.

- [ ] **Step 3: Adapt filters and chart options**

Stack admin filters/batch panels and remove inline width constraints via mobile selectors. Change `.charts-grid` to one column below 1023px, reduce chart padding/height below 767px, and set small-screen ECharts options to shorter legends and axis label intervals while keeping `autoresize`.

- [ ] **Step 4: Verify GREEN**

Run: `cd li-picture-cloud-frontend && node --test --test-name-pattern="admin tables" tests/responsiveViews.test.mjs && npm run lint && npm run build`

Expected: admin/analysis contracts pass and no compilation regression appears.

- [ ] **Step 5: Commit admin and analytics**

```bash
git add li-picture-cloud-frontend/tests/responsiveViews.test.mjs li-picture-cloud-frontend/src/views/AdminUserView.vue li-picture-cloud-frontend/src/views/PictureManageView.vue li-picture-cloud-frontend/src/views/SpaceAnalyzeView.vue
git commit -m "feat: adapt admin and analytics views for mobile"
```

---

### Task 7: Full verification and repeatable viewport checklist

**Files:**
- Create: `docs/testing/mobile-responsive-checklist.md`
- Modify only if verification finds a regression: the owning view/component and its existing contract test from Tasks 1–6.

**Interfaces:**
- Consumes: all responsive contracts and the 13 route definitions in `src/router/index.js`.
- Produces: a checked route/viewport matrix plus clean test, lint, build, and bundle-size evidence.

- [ ] **Step 1: Create the exact verification matrix**

Write `docs/testing/mobile-responsive-checklist.md` with rows for `/`, `/login`, `/register`, `/gallery`, `/picture/:id`, `/upload`, `/spaces`, `/space/create`, `/space/my`, `/space/:id`, `/space/analyze`, `/admin/pictures`, and `/admin/users`; columns are 360×800, 390×844, 430×932, 768×1024, 1024×768, reduced motion, keyboard, and no body overflow.

- [ ] **Step 2: Run all automated frontend checks**

Run: `cd li-picture-cloud-frontend && npm test && npm run lint && npm run build && npm run check:bundle`

Expected: every test passes, ESLint reports zero warnings, Vite exits 0, and bundle thresholds pass.

- [ ] **Step 3: Inspect the final diff and whitespace**

Run: `git diff --check && git diff --stat && git status --short`

Expected: no whitespace errors; only responsive implementation/test/checklist files plus the user's pre-existing unrelated changes appear.

- [ ] **Step 4: Execute the viewport matrix**

Start the existing Vite development server, visit all 13 routes using representative guest, user, and administrator states, and mark each cell only after confirming the named viewport has no page-level horizontal overflow and its primary actions remain reachable. For routes requiring backend data, verify the loading/error/empty state when fixtures are unavailable and record that state in the checklist.

- [ ] **Step 5: Re-run automated checks after any visual corrections**

Run: `cd li-picture-cloud-frontend && npm test && npm run lint && npm run build && npm run check:bundle`

Expected: all four commands exit 0 after the final correction.

- [ ] **Step 6: Commit verification artifacts and final corrections**

```bash
git add docs/testing/mobile-responsive-checklist.md li-picture-cloud-frontend/src/style.css li-picture-cloud-frontend/src/constants/navigation.js li-picture-cloud-frontend/src/components li-picture-cloud-frontend/src/views li-picture-cloud-frontend/tests/responsiveFoundation.test.mjs li-picture-cloud-frontend/tests/navigation.test.mjs li-picture-cloud-frontend/tests/responsiveViews.test.mjs
git commit -m "test: verify full-site mobile adaptation"
```
