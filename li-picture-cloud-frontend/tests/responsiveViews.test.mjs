import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import { join } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const read = path => readFileSync(join(root, path), 'utf8')

test('public views define phone layouts', () => {
  for (const file of [
    'components/HeroSection.vue',
    'components/PictureList.vue',
    'views/HomeView.vue',
    'views/LoginView.vue',
    'views/RegisterView.vue',
    'views/GalleryView.vue'
  ]) {
    assert.match(read(`src/${file}`), /@media \(max-width: 767px\)/, file)
  }
  assert.match(read('src/components/PictureList.vue'), /@media \(hover: none\)/)
  assert.match(read('src/views/GalleryView.vue'), /grid-template-columns:\s*1fr/)
})
