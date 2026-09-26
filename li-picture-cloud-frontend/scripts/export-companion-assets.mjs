import { createRequire } from 'node:module'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

// Offline format/size export only. Character drawing and blink frames are imagegen outputs.
// Use a locally available Sharp installation; this is not a browser/runtime dependency.
const require = createRequire(import.meta.url)
const sharp = require(process.env.R05_SHARP_MODULE || 'sharp')
const frontend = fileURLToPath(new URL('../', import.meta.url))
const source = path.resolve(frontend, '../docs/design-exploration/r05-companion/production/lingye-v1')
const output = path.resolve(frontend, 'src/assets/companion/lingye')
await mkdir(output, { recursive: true })

// The generated sheet has a 740px translation, not equal cells. Record and normalize
// the two transparent source windows; no painted pixels or poses are synthesized.
const frames = await Promise.all([0, 740].map(left => sharp(path.join(source, 'adult-idle-source.png'))
  .extract({ left, top: 0, width: 768, height: 1024 }).resize(576, 768).png().toBuffer()))
await sharp(frames[0]).webp({ quality: 90, alphaQuality: 100 }).toFile(path.join(output, 'adult-home-v1.webp'))
await sharp({ create: { width: 1152, height: 768, channels: 4, background: '#00000000' } })
  .composite(frames.map((input, index) => ({ input, left: index * 576, top: 0 })))
  .webp({ quality: 90, alphaQuality: 100 }).toFile(path.join(output, 'adult-idle-v1.webp'))
await sharp(path.join(source, 'neutral-source.png')).resize(384, 384)
  .webp({ quality: 88, alphaQuality: 100 }).toFile(path.join(output, 'adult-neutral-v1.webp'))
await sharp(path.join(source, 'shell-source.png')).resize(128, 128)
  .webp({ quality: 88, alphaQuality: 100 }).toFile(path.join(output, 'adult-shell-v1.webp'))

const budgets = { 'adult-home-v1.webp': 240, 'adult-idle-v1.webp': 480, 'adult-neutral-v1.webp': 120, 'adult-shell-v1.webp': 16 }
const assets = []
for (const [filename, budgetKiB] of Object.entries(budgets)) {
  const buffer = await readFile(path.join(output, filename))
  const { data, info } = await sharp(buffer).ensureAlpha().raw().toBuffer({ resolveWithObject: true })
  let left = info.width, top = info.height, right = -1, bottom = -1
  for (let y = 0; y < info.height; y++) for (let x = 0; x < info.width; x++) {
    if (data[(y * info.width + x) * 4 + 3] > 0) {
      left = Math.min(left, x); top = Math.min(top, y)
      right = Math.max(right, x); bottom = Math.max(bottom, y)
    }
  }
  if (buffer.length > budgetKiB * 1024) throw new Error(`${filename} exceeds ${budgetKiB} KiB`)
  assets.push({ filename, width: info.width, height: info.height, bytes: buffer.length,
    decodedBytes: info.width * info.height * 4, budgetKiB,
    alphaBounds: { left, top, right, bottom }, sha256: createHash('sha256').update(buffer).digest('hex') })
}
const manifest = {
  version: 'lingye-adult-v1', generator: 'built-in imagegen',
  sourceDirectory: 'docs/design-exploration/r05-companion/production/lingye-v1',
  sourceFrames: [{ left: 0, top: 0, width: 768, height: 1024 }, { left: 740, top: 0, width: 768, height: 1024 }],
  footAnchor: { x: 0.495, y: 0.934 }, frameDurationsMs: [4200, 140],
  fallback: 'adult-home-v1.webp', assets,
  totalBytes: assets.reduce((sum, asset) => sum + asset.bytes, 0),
  totalDecodedBytes: assets.reduce((sum, asset) => sum + asset.decodedBytes, 0)
}
if (manifest.totalDecodedBytes > 10 * 1024 * 1024) throw new Error('Decoded assets exceed 10 MiB')
await writeFile(path.join(output, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
console.log(JSON.stringify(manifest, null, 2))
