# Frontend Delivery Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add enforceable Vue static analysis and bundle-size controls to local development and CI.

**Architecture:** ESLint 9 flat configuration checks Vue and JavaScript sources without imposing unrelated formatting. Vite splits framework and charting dependencies into stable vendor chunks, while a small Node script enforces a 500 KiB post-build JavaScript budget.

**Tech Stack:** Node.js 22, npm 10, ESLint 9, eslint-plugin-vue, Vite 5, Vue 3, GitHub Actions

## Global Constraints

- Do not change page behavior, API contracts, backend business logic, or visual styling.
- Frontend source linting covers `src/**/*.{js,vue}` and `vite.config.js`.
- Every generated JavaScript chunk must be at most 500 KiB.
- CI uses `actions/checkout@v5`, `actions/setup-node@v6`, and `actions/setup-java@v5`.
- Existing backend test-profile verification remains required.

---

### Task 1: Establish the ESLint baseline

**Files:**
- Modify: `li-picture-cloud-frontend/package.json`
- Modify: `li-picture-cloud-frontend/package-lock.json`
- Create: `li-picture-cloud-frontend/eslint.config.js`
- Modify: only frontend source files reported by the selected rules

**Interfaces:**
- Consumes: Vue SFC and JavaScript source files.
- Produces: `npm run lint`, which exits zero only when the checked source has no ESLint errors.

- [ ] **Step 1: Add lint dependencies and a deliberately strict command**

Install `eslint@^9`, `eslint-plugin-vue@^10`, and `globals@^16` as development dependencies. Add `"lint": "eslint . --max-warnings 0"` to scripts and create flat config using `@eslint/js` recommended plus `eslint-plugin-vue` flat essential rules.

- [ ] **Step 2: Run lint to capture the failing baseline**

Run: `npm run lint`

Expected: non-zero exit with concrete existing source violations, proving the gate is active.

- [ ] **Step 3: Fix only reported correctness violations**

Remove unused imports or variables, make browser and Node globals explicit through config, and preserve runtime behavior. Do not reformat unrelated code.

- [ ] **Step 4: Verify lint and production build**

Run: `npm run lint && npm run build`

Expected: lint and build both succeed.

- [ ] **Step 5: Commit**

Commit message: `chore: add frontend lint baseline`

### Task 2: Enforce the bundle budget

**Files:**
- Modify: `li-picture-cloud-frontend/vite.config.js`
- Modify: `li-picture-cloud-frontend/package.json`
- Create: `li-picture-cloud-frontend/scripts/check-bundle-size.mjs`

**Interfaces:**
- Consumes: Vite module IDs and generated files under `dist/assets`.
- Produces: `npm run check:bundle`, enforcing a maximum JavaScript chunk size of 512000 bytes.

- [ ] **Step 1: Create a failing bundle-budget check**

Implement `check-bundle-size.mjs` to enumerate `dist/assets/*.js`, fail when no JavaScript assets exist, and fail with filenames and byte sizes when any file exceeds 512000 bytes. Add `"check:bundle": "node scripts/check-bundle-size.mjs"`.

- [ ] **Step 2: Verify the current output exceeds the budget**

Run: `npm run build && npm run check:bundle`

Expected before splitting: non-zero exit naming the roughly 570 KB `SpaceAnalyzeView` chunk.

- [ ] **Step 3: Add minimal Vite vendor splitting**

Configure Rollup `manualChunks` so Vue ecosystem packages map to `vendor-vue`, ECharts modules map to `vendor-echarts`, and zrender modules map to `vendor-zrender`.

- [ ] **Step 4: Verify the budget and lint**

Run: `npm run lint && npm run build && npm run check:bundle`

Expected: all commands succeed and Vite emits no chunk-over-500-KB warning.

- [ ] **Step 5: Commit**

Commit message: `perf: split charting dependencies into vendor chunks`

### Task 3: Upgrade and extend CI quality gates

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `doc/开发环境配置.md`

**Interfaces:**
- Consumes: `npm run lint`, `npm run build`, and `npm run check:bundle` from prior tasks.
- Produces: CI that rejects lint, build, bundle-budget, or backend-test regressions.

- [ ] **Step 1: Upgrade action runtimes and add frontend gates**

Use `actions/checkout@v5`, `actions/setup-java@v5`, and `actions/setup-node@v6`. Add lint and bundle-budget steps around the existing frontend build.

- [ ] **Step 2: Document the complete local contract**

Add `npm run lint`, `npm run build`, and `npm run check:bundle` to the submission workflow and explain the 500 KiB budget.

- [ ] **Step 3: Run final verification**

Run frontend clean install, lint, build, and bundle check; run backend test-profile package; parse `.github/workflows/ci.yml`; run `git diff --check`.

Expected: all commands return zero and the working tree contains only intended changes.

- [ ] **Step 4: Commit**

Commit message: `ci: enforce frontend quality gates`
