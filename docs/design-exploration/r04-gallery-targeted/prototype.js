/* Isolated, single-direction before/after prototype. No API, persistence, or Viewer.
 * Data helpers are also runnable in Node; that is NOT a browser interaction test.
 */
const GalleryPrototypeModel = (() => {
  const pageSize = 12
  const sources = [
    ['nature', '山间水岸', '清晨的水面与山林', '风景', ['自然', '风景'], 600, 400],
    ['architecture', '建筑的线条', '城市建筑外墙的光影', '建筑', ['建筑', '城市'], 600, 766],
    ['city', '城市一隅', '雨后路口的城市街景', '建筑', ['城市'], 800, 492],
    ['portrait', '窗边人物', '人物摄影中的侧光练习', '人物', ['人物'], 600, 899],
    ['travel', '旅途之间', '旅行途中的自然风景', '风景', ['自然', '风景'], 800, 632],
    ['bw', '黑白时刻', '剧场内的黑白摄影', '摄影', ['黑白'], 600, 900],
    ['still-life', '日常静物', '餐桌上的器物与光线', '摄影', ['静物'], 600, 397],
    ['abstract', '色彩片段', '抽象色块的组合练习', '摄影', ['抽象'], 800, 533]
  ]
  const records = Array.from({ length: 120 }, (_, i) => {
    const [file, name, introduction, category, tags, width, height] = sources[i % sources.length]
    return { id: 1001 + i, file, name: `${name} · ${String(i + 1).padStart(3, '0')}`, introduction, category, tags, width, height,
      author: ['林一', '陈予', '许宁'][i % 3], createTime: Date.UTC(2026, 8, 22) - i * 86400000,
      format: ['jpg', 'jpeg', 'png', 'webp'][Math.floor(i / 8) % 4] }
  })
  const defaults = () => ({ q: '', category: '', format: '', order: 'descend', tags: [] })
  function select(query) {
    const keyword = query.q.trim().toLowerCase()
    const found = records.filter(item => (!keyword || `${item.name} ${item.introduction}`.toLowerCase().includes(keyword)) && (!query.category || item.category === query.category) && (!query.format || item.format === query.format) && query.tags.every(tag => item.tags.includes(tag)))
    return found.sort((a, b) => query.order === 'ascend' ? a.createTime - b.createTime : b.createTime - a.createTime)
  }
  function normalizePage(value, pages) {
    const number = Number(value)
    return Math.max(1, Math.min(pages, Number.isFinite(number) ? Math.trunc(number) || 1 : 1))
  }
  function paginate(query, requested) {
    const filtered = select(query), total = filtered.length, pages = Math.max(1, Math.ceil(total / pageSize))
    const current = normalizePage(requested, pages)
    return { total, pages, current, records: filtered.slice((current - 1) * pageSize, current * pageSize) }
  }
  return { pageSize, records, defaults, select, normalizePage, paginate }
})()
if (typeof module !== 'undefined' && module.exports) module.exports = GalleryPrototypeModel

if (typeof document !== 'undefined') (() => {
  const model = GalleryPrototypeModel, $ = selector => document.querySelector(selector)
  const grid = $('.gallery-grid'), base = '../../../li-picture-cloud-frontend/public/images/mosaic/'
  let query = model.defaults(), current = 1, mode = 'normal', stress = false, measuring = false
  let result = model.paginate(query, current)
  const el = (tag, className, value) => { const node = document.createElement(tag); node.className = className; if (value) node.textContent = value; return node }
  const longFixture = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="100" height="1200" viewBox="0 0 100 1200"><rect width="100" height="1200" fill="#47618c"/><path d="M0 100H100M0 300H100M0 500H100M0 700H100M0 900H100M0 1100H100" stroke="#fcfbf8" stroke-width="35"/><path d="M15 0V1200M85 0V1200" stroke="#cfc9bd" stroke-width="4"/></svg>')
  for (const tag of ['自然', '风景', '城市', '建筑', '人物', '黑白', '静物', '抽象']) {
    const chip = el('button', 'tag-chip', tag); chip.type = 'button'; chip.dataset.tag = tag; chip.setAttribute('aria-pressed', 'false')
    chip.onclick = () => { query.tags = query.tags.includes(tag) ? query.tags.filter(t => t !== tag) : [...query.tags, tag]; applyInputs() }
    $('.tag-filters').append(chip)
  }
  function drawCard(item, index, skeleton) {
    const card = el('article', 'gallery-card'); card.dataset.id = item?.id || `placeholder-${index}`
    if (skeleton) { card.classList.add('is-skeleton'); card.setAttribute('aria-hidden', 'true'); return card }
    const link = el('a', 'card-open'); link.href = `#picture-${item.id}`; link.setAttribute('aria-label', `查看图片：${item.name}`)
    link.onclick = event => {
      event.preventDefault()
      $('#route-target').textContent = `正式目标：/picture/${item.id}。在详情页中按需放大或下载原图。此离线原型不跳转、不请求 API。`
      $('.route-notice').hidden = false
    }
    const img = el('img', 'card-img'); img.alt = item.name; img.width = item.width; img.height = item.height
    img.loading = index < 3 ? 'eager' : 'lazy'; img.decoding = 'async'
    const message = el('span', 'image-message')
    const fail = () => { card.classList.remove('is-pending'); card.classList.add('is-failed'); message.textContent = '图片暂时无法显示' }
    img.onload = () => { card.classList.remove('is-pending'); message.textContent = '' }
    img.onerror = fail
    const overlay = el('div', 'card-overlay')
    overlay.append(el('h3', 'card-name', item.name), el('p', 'card-meta', `${item.author} · ${new Date(item.createTime).toLocaleDateString('zh-CN', { timeZone: 'UTC' })}`))
    const tagRow = el('div', 'card-tags'); item.tags.slice(0, 3).forEach(tag => tagRow.append(el('span', 'mini-tag', tag))); overlay.append(tagRow)
    link.append(img, message, overlay); card.append(link)
    if (mode === 'image-error' && index === 0) fail()
    else { card.classList.add('is-pending'); message.textContent = '正在加载图片'; img.src = stress && index === 0 ? longFixture : `${base}${item.file}.jpg` }
    return card
  }
  function renderStateText() {
    const targeted = document.body.dataset.version === 'targeted'
    $('#state-copy').textContent = mode === 'error' ? (targeted ? '图片列表加载失败。筛选条件已保留。' : '暂无图片，上传第一张吧！') : (targeted ? '没有符合条件的图片。试试其他关键词或清除筛选。' : '暂无图片，上传第一张吧！')
    $('#state-action').hidden = !targeted
    $('#state-action').textContent = mode === 'error' ? '重试' : '清除筛选'
  }
  function render() {
    result = model.paginate(query, current); current = result.current
    grid.replaceChildren(); grid.hidden = false; grid.removeAttribute('aria-busy'); $('.empty-state').hidden = true
    $('.route-notice').hidden = true
    const terms = [query.q && `关键词：${query.q}`, query.category && `分类：${query.category}`, query.format && `格式：${query.format.toUpperCase()}`, query.order === 'ascend' && '最早优先', ...query.tags.map(tag => `标签：${tag}`)].filter(Boolean)
    $('.applied-summary').textContent = terms.length ? `${terms.join('；')}。${query.tags.length > 1 ? '标签同时满足。' : ''}共 ${result.total} 张。` : ''
    document.querySelectorAll('.tag-chip').forEach(chip => {
      const on = query.tags.includes(chip.dataset.tag); chip.classList.toggle('active', on); chip.setAttribute('aria-pressed', String(on)); chip.textContent = (on ? '✓ ' : '') + chip.dataset.tag
    })
    if (mode === 'loading') { grid.setAttribute('aria-busy', 'true'); for (let i = 0; i < 12; i++) grid.append(drawCard(null, i, true)) }
    else if (mode === 'error' || !result.records.length) { grid.hidden = true; $('.empty-state').hidden = false; renderStateText() }
    else result.records.forEach((item, index) => grid.append(drawCard(item, index, false)))
    $('.pagination').hidden = mode === 'loading' || mode === 'error' || result.total <= 12
    $('#page-label').textContent = `第 ${current} / ${result.pages} 页 (${result.total} 张)`
    $('#previous').disabled = current <= 1; $('#next').disabled = current >= result.pages; $('#jump').max = result.pages
  }
  function applyInputs() {
    query = { ...query, q: $('#search').value.trim(), category: $('#category').value, format: $('#format').value, order: $('#order').value }
    current = 1; mode = 'normal'; $('#demo-state').value = mode; render()
  }
  function reset() {
    query = model.defaults(); current = 1; mode = 'normal'; $('#demo-state').value = mode
    $('#search').value = ''; $('#category').value = ''; $('#format').value = ''; $('#order').value = 'descend'; $('#jump').value = ''; render()
  }
  $('.search-box-inline').onsubmit = event => { event.preventDefault(); applyInputs() }
  for (const name of ['category', 'format', 'order']) $(`#${name}`).onchange = applyInputs
  $('.clear-filters').onclick = reset; $('#lab-reset').onclick = reset
  $('#state-action').onclick = () => { if (mode === 'error') { mode = 'normal'; $('#demo-state').value = mode; render() } else reset() }
  function goPage(requested) {
    current = model.normalizePage(requested, result.pages); mode = 'normal'; $('#demo-state').value = mode; render(); $('#jump').value = ''
    window.scrollTo({ top: 0, behavior: 'instant' })
  }
  $('#previous').onclick = () => goPage(current - 1); $('#next').onclick = () => goPage(current + 1)
  $('.jumper').noValidate = true
  $('.jumper').onsubmit = event => { event.preventDefault(); goPage($('#jump').value) }
  $('#demo-state').onchange = event => { mode = event.target.value; render() }
  $('#stress').onchange = event => { stress = event.target.checked; render() }
  $('#dismiss-route').onclick = () => { $('.route-notice').hidden = true }
  function setVersion(version) {
    document.body.dataset.version = version
    document.querySelectorAll('[data-show]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.show === version)))
    renderStateText()
  }
  document.querySelectorAll('[data-show]').forEach(button => {
    button.onclick = () => { if (measuring) return; const y = scrollY; setVersion(button.dataset.show); requestAnimationFrame(() => window.scrollTo(0, y)) }
  })
  const frame = () => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  const round = value => Math.round(value * 100) / 100
  function collect() {
    const cards = [...grid.querySelectorAll('.gallery-card')], rects = cards.map(card => card.getBoundingClientRect())
    const first = rects[0], row2 = first && rects.find(rect => rect.top > first.top + 2)
    const top = $('.mock-top').getBoundingClientRect().bottom
    const bottomBar = $('.mock-bottom'), bottomRect = bottomBar.getBoundingClientRect()
    const bottom = getComputedStyle(bottomBar).display === 'none' ? innerHeight : bottomRect.top
    const intersection = rect => Math.max(0, Math.min(rect.bottom, bottom) - Math.max(rect.top, top))
    const metadata = cards[0]?.querySelector('.card-meta'), image = cards[0]?.querySelector('img')
    const imageRect = image?.getBoundingClientRect(), name = cards[0]?.querySelector('.card-name'), tagsNode = cards[0]?.querySelector('.card-tags')
    return {
      version: document.body.dataset.version, viewport: { width: innerWidth, height: innerHeight }, scrollY: round(scrollY),
      page: current, pageSize: 12, total: result.total, ids: cards.map(card => card.dataset.id), query: { ...query }, state: mode, stress,
      columns: first ? rects.filter(rect => Math.abs(rect.top - first.top) < 2).length : 0,
      firstFrame: first ? { x: round(first.x), y: round(first.y), width: round(first.width), height: round(first.height), ratio: round(first.width / first.height) } : null,
      allFramesWithinOnePixelOf4to3: rects.length ? rects.every(rect => Math.abs(rect.width * .75 - rect.height) <= 1) : null,
      renderedCards: cards.length, fullCardsInFirstScreen: rects.filter(rect => rect.top >= top && rect.bottom <= bottom).length,
      partialCardsInFirstScreen: rects.filter(rect => intersection(rect) > 0 && !(rect.top >= top && rect.bottom <= bottom)).length,
      nextRow: row2 ? { candidatesVisible: rects.filter(rect => Math.abs(rect.top - row2.top) < 2 && intersection(rect) > 0).length, visibleHeight: round(intersection(row2)), totalHeight: round(row2.height) } : null,
      galleryGridHeight: round(grid.getBoundingClientRect().height), documentYThroughLastCard: rects.length ? round(Math.max(...rects.map(rect => rect.bottom)) + scrollY) : null,
      horizontalOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      metadata: metadata ? { text: metadata.textContent, fontSize: getComputedStyle(metadata).fontSize, opacity: getComputedStyle(metadata).opacity, name: name.textContent, nameFontSize: getComputedStyle(name).fontSize, tags: tagsNode.textContent, tagsDisplay: getComputedStyle(tagsNode).display, overlayDisplay: getComputedStyle(metadata.parentElement).display } : null,
      image: image ? { width: round(imageRect.width), height: round(imageRect.height), naturalWidth: image.naturalWidth, naturalHeight: image.naturalHeight, display: getComputedStyle(image).display, objectFit: getComputedStyle(image).objectFit, complete: image.complete } : null,
      note: '由本地浏览器运行时测得；工具条已隐藏，首屏扣除同一 Shell 顶栏/底栏。比例与尺寸不是视觉认可，亦不代表真实后端验证。'
    }
  }
  $('#measure').onclick = async () => {
    if (measuring) return
    measuring = true; $('#measure').disabled = true
    const saved = { version: document.body.dataset.version, y: scrollY }
    const rows = []
    document.body.classList.add('is-measuring')
    try {
      for (const version of ['baseline', 'targeted']) { setVersion(version); window.scrollTo(0, 0); await frame(); rows.push(collect()) }
      $('#metrics').value = JSON.stringify({ measuredAt: new Date().toISOString(), observations: rows }, null, 2)
    } finally {
      setVersion(saved.version); await frame(); window.scrollTo(0, saved.y); document.body.classList.remove('is-measuring'); measuring = false; $('#measure').disabled = false
    }
  }
  render()
})()
