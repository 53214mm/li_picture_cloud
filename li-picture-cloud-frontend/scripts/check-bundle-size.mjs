import { readdir, stat } from 'node:fs/promises'
import { resolve } from 'node:path'

const maxChunkBytes = 500 * 1024
const assetsDirectory = resolve('dist/assets')

try {
  const assetNames = (await readdir(assetsDirectory))
    .filter((name) => name.endsWith('.js'))

  if (assetNames.length === 0) {
    throw new Error(`No JavaScript chunks found in ${assetsDirectory}`)
  }

  const chunks = await Promise.all(assetNames.map(async (name) => ({
    name,
    size: (await stat(resolve(assetsDirectory, name))).size
  })))
  const oversizedChunks = chunks.filter(({ size }) => size > maxChunkBytes)

  if (oversizedChunks.length > 0) {
    const details = oversizedChunks
      .map(({ name, size }) => `${name}: ${size} bytes`)
      .join('\n')
    throw new Error(`JavaScript chunks exceed ${maxChunkBytes} bytes:\n${details}`)
  }

  const largestChunk = chunks.reduce((largest, chunk) => (
    chunk.size > largest.size ? chunk : largest
  ))
  console.log(`Bundle budget passed. Largest chunk: ${largestChunk.name} (${largestChunk.size} bytes)`)
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
}
