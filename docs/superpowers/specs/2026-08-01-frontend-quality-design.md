# 第三轮工程治理：前端交付质量设计

## 目标

为 Vue 前端建立可执行的静态检查基线，将检查纳入 CI，并通过稳定的 vendor 拆包消除图库分析页面超过 500 KB 的单 chunk 警告。

## 范围

- ESLint 9 flat config 与 Vue 官方规则。
- `npm run lint` 和 CI 前端质量门禁。
- Vite vendor 拆包及可执行的 chunk 体积预算。
- GitHub Actions JavaScript runtime 升级。
- 开发文档中的提交前验证命令。

不改变页面交互、接口路径、后端业务逻辑或视觉样式。

## 静态检查

使用 ESLint 9、`eslint-plugin-vue` 和浏览器全局变量配置。检查 `src/**/*.{js,vue}` 与 `vite.config.js`，忽略 `dist` 和 `node_modules`。规则以 ESLint recommended 和 Vue essential 为基础，只修复实际违规，不做全仓格式化。

## 构建拆包与预算

Vite 的 `manualChunks` 按依赖职责拆分：

- Vue、Vue Router、Pinia 进入 `vendor-vue`。
- ECharts 进入 `vendor-echarts`。
- zrender 进入 `vendor-zrender`。

增加构建后预算校验脚本，读取 `dist/assets` 中的 JavaScript 文件；任何单文件超过 500 KiB 时退出非零。预算校验独立于 Vite 警告，CI 可明确阻止体积回退。

## CI

前端 job 按 `npm ci`、`npm run lint`、`npm run build`、`npm run check:bundle` 顺序执行。Action 版本更新为 `actions/checkout@v5`、`actions/setup-node@v6`、`actions/setup-java@v5`，消除 Node 20 runtime 弃用警告。

## 错误处理

- ESLint 发现错误时直接阻止提交和 CI。
- 构建失败时不运行体积校验。
- 预算脚本在产物目录缺失、没有 JS 文件或文件超限时给出明确错误并返回非零。

## 验收标准

- `npm run lint` 零错误。
- `npm run build` 成功且不出现 500 KB chunk 警告。
- `npm run check:bundle` 成功，所有 JavaScript chunk 不超过 500 KiB。
- 后端 test profile 测试保持通过。
- GitHub Actions YAML 可解析，Action runtime 弃用警告不再由旧主版本触发。
