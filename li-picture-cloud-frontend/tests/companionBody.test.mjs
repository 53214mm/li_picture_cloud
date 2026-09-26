import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { resolveCompanionVisual } from '../src/components/companion/body/companionVisual.js'

test('shipped artwork matches the measured manifest and stays within transfer/decode budgets', async () => {
  const root = new globalThis.URL('../src/assets/companion/lingye/', import.meta.url)
  const manifest = JSON.parse(await readFile(new globalThis.URL('manifest.json', root), 'utf8'))
  let total = 0
  for (const asset of manifest.assets) {
    const data = await readFile(new globalThis.URL(asset.filename, root))
    assert.equal(data.length, asset.bytes)
    assert.equal(createHash('sha256').update(data).digest('hex'), asset.sha256)
    assert.ok(data.length <= asset.budgetKiB * 1024)
    total += data.length
  }
  assert.equal(total, manifest.totalBytes)
  assert.ok(total <= 856 * 1024)
  assert.ok(manifest.totalDecodedBytes <= 10 * 1024 * 1024)
  const home = manifest.assets.find(asset => asset.filename === manifest.fallback)
  const idle = manifest.assets.find(asset => asset.filename.includes('-idle-'))
  assert.equal(idle.width, home.width * manifest.frameDurationsMs.length)
  assert.equal(idle.height, home.height)
})

test('every existing stage receives the same adult representation without translating business stage', () => {
  for (const currentStage of ['LIGHT', 'SEEDLING', 'COMPANION', 'FUTURE_STAGE', null, undefined]) {
    const visual = resolveCompanionVisual(currentStage)
    assert.equal(visual.currentStage, currentStage)
    assert.equal(visual.visualStage, 'adult')
    assert.equal(visual.assetKey, 'lingye-adult-v1')
    assert.equal(visual.allowIdle, ['LIGHT', 'SEEDLING', 'COMPANION'].includes(currentStage))
  }
})
