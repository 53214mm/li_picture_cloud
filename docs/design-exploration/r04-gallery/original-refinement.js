/* Offline comparison: preserve the actual Gallery structure, refine only a few styles.
 * No persistence, API requests, real identities, or production route changes.
 */
(() => {
  const samples = [
    ['nature', '山间水岸', '风景', ['自然', '风景'], 600, 400],
    ['architecture', '建筑的线条', '建筑', ['城市', '建筑'], 600, 766],
    ['city', '城市一隅', '建筑', ['城市'], 800, 492],
    ['portrait', '人物习作', '人物', ['人物'], 600, 899],
    ['travel', '旅途之间', '风景', ['风景'], 800, 632],
    ['bw', '黑白时刻', '摄影', ['黑白'], 600, 900],
    ['still-life', '日常静物', '摄影', ['静物'], 600, 397],
    ['abstract', '色彩片段', '摄影', ['抽象'], 800, 533]
  ]
  const pictures = Array.from({ length: 24 }, (_, i) => {
    const [file, name, category, tags, width, height] = samples[i % 8]
    return { id: i, file, name, introduction: `${category}影像样本`, category, tags, width, height, format: ['jpg', 'png', 'webp'][i % 3] }
  })
  const $ = selector => document.querySelector(selector)
  const tags = new Set()
  let current = 1, totalPages = 2, appliedSearch = '', opener = null, savedScroll = 0
  const make = (tag, className, value) => { const el = document.createElement(tag); el.className = className; if (value) el.textContent = value; return el }
  const imageSource = item => `../../../li-picture-cloud-frontend/public/images/mosaic/${item.file}.jpg`
  for (const tag of ['自然', '风景', '城市', '建筑', '人物', '黑白', '静物', '抽象']) {
    const chip = make('button', 'tag-chip', tag); chip.type = 'button'; chip.setAttribute('aria-pressed', 'false')
    chip.onclick = () => { tags.has(tag) ? tags.delete(tag) : tags.add(tag); chip.classList.toggle('active', tags.has(tag)); chip.setAttribute('aria-pressed', String(tags.has(tag))); applySearch() }
    $('.tag-filters').append(chip)
  }
  function render() {
    let found = pictures.filter(item => (!appliedSearch || `${item.name} ${item.introduction}`.includes(appliedSearch)) && (!$('#category').value || item.category === $('#category').value) && (!$('#format').value || item.format === $('#format').value) && [...tags].every(tag => item.tags.includes(tag)))
    if ($('#order').value === 'ascend') found = [...found].reverse()
    totalPages = Math.max(1, Math.ceil(found.length / 12)); current = Math.min(current, totalPages)
    const grid = $('.gallery-grid'); grid.replaceChildren()
    for (const [index, item] of found.slice((current - 1) * 12, current * 12).entries()) {
      const card = make('button', 'gallery-card'); card.type = 'button'; card.setAttribute('aria-label', `查看图片：${item.name}`)
      const img = make('img', 'card-img'); img.src = imageSource(item); img.alt = item.name; img.width = item.width; img.height = item.height; img.loading = index < 3 ? 'eager' : 'lazy'; img.decoding = 'async'
      const overlay = make('span', 'card-overlay')
      overlay.append(make('span', 'card-name', item.name), make('span', 'card-meta', `示例作者 ${item.id % 3 + 1} · 2026/9/22`))
      const tagRow = make('span', 'card-tags'); item.tags.forEach(tag => tagRow.append(make('span', 'mini-tag', tag))); overlay.append(tagRow)
      card.append(img, overlay)
      card.onclick = () => { opener = card; savedScroll = scrollY; $('#detail-title').textContent = item.name; $('#detail-image').src = imageSource(item); $('#detail-image').alt = item.name; $('.detail-demo').showModal(); $('#close-detail').focus() }
      grid.append(card)
    }
    grid.hidden = !found.length; $('.empty-state').hidden = Boolean(found.length)
    $('.pagination').hidden = found.length <= 12
    $('#page-label').textContent = `第 ${current} / ${totalPages} 页 (${found.length} 张)`
    $('#previous').disabled = current === 1; $('#next').disabled = current === totalPages
  }
  function applySearch() { appliedSearch = $('#search').value.trim(); current = 1; render() }
  $('.search-box-inline').onsubmit = event => { event.preventDefault(); applySearch() }
  for (const id of ['category', 'format', 'order']) $(`#${id}`).onchange = applySearch
  $('.clear-filters').onclick = () => {
    $('#search').value = ''; $('#category').value = ''; $('#format').value = ''; $('#order').value = 'descend'; tags.clear()
    document.querySelectorAll('.tag-chip').forEach(chip => { chip.classList.remove('active'); chip.setAttribute('aria-pressed', 'false') }); applySearch()
  }
  function goPage(page) {
    current = Math.max(1, Math.min(totalPages, Math.trunc(Number(page)) || 1)); render(); $('#jump').value = ''
    window.scrollTo({ top: 0, behavior: 'instant' })
  }
  $('#previous').onclick = () => goPage(current - 1); $('#next').onclick = () => goPage(current + 1)
  $('#jump-go').onclick = () => goPage($('#jump').value)
  $('#jump').onkeydown = event => { if (event.key === 'Enter') goPage(event.currentTarget.value) }
  document.querySelectorAll('[data-show]').forEach(button => {
    button.onclick = () => { document.body.dataset.version = button.dataset.show; document.querySelectorAll('[data-show]').forEach(other => other.setAttribute('aria-pressed', String(other === button))) }
  })
  $('#close-detail').onclick = () => $('.detail-demo').close()
  $('.detail-demo').addEventListener('close', () => { window.scrollTo(0, savedScroll); opener?.focus({ preventScroll: true }) })
  render()
})()
