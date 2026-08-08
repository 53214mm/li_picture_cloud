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

test('picture workflows define phone-safe modals and editor regions', () => {
  for (const file of [
    'views/PictureDetailView.vue',
    'views/PictureUploadView.vue',
    'components/ShareModal.vue',
    'components/ImageEditModal.vue',
    'components/ImageEditor.vue'
  ]) {
    assert.match(read(`src/${file}`), /@media \(max-width: 767px\)/, file)
  }
  assert.match(read('src/components/ImageEditor.vue'), /overflow-x:\s*auto|flex-wrap:\s*wrap/)
  assert.match(read('src/components/ImageEditModal.vue'), /safe-area-inset-bottom/)
})

test('space and AI surfaces define phone layouts', () => {
  for (const file of [
    'views/MySpaceView.vue',
    'views/SpaceCreateView.vue',
    'views/SpaceManageView.vue',
    'views/SpaceDetailView.vue',
    'components/space/SpaceCard.vue',
    'components/space/SpaceMemberPanel.vue',
    'components/AiAgentPanel.vue'
  ]) {
    assert.match(read(`src/${file}`), /@media \(max-width: 767px\)/, file)
  }
  assert.match(read('src/components/space/SpaceMemberPanel.vue'), /width:\s*100%/)
  assert.match(read('src/components/AiAgentPanel.vue'), /overflow-x:\s*auto|flex-wrap:\s*wrap/)
})
