import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

export default [
  {
    // E2E 产物目录（trace/截图/报告）不是源码，必须排除，否则 lint 会扫入压缩 JS。
    ignores: ['dist/**', 'node_modules/**', 'playwright-report/**', 'test-results/**']
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    files: ['src/**/*.{js,vue}'],
    languageOptions: {
      globals: globals.browser
    }
  },
  {
    files: [
      'vite.config.js',
      'playwright.config.js',
      'eslint.config.js',
      'scripts/**/*.mjs',
      'e2e/**/*.js'
    ],
    languageOptions: {
      globals: globals.node
    }
  }
]
