/* OFFLINE design prototypes, not production components.
 * A: justified rows / inline filters / viewer. B: masonry / filter drawer / detail drawer.
 * C: editorial / visible filters / page-like detail. All data and actions are local mocks.
 * D: feedback iteration — equal 4:3 tiles, 12 per page; crop only in the list.
 */
(() => {
  'use strict'
  const direction = document.body.dataset.direction
  const names = { a: '行式相册', b: '自由瀑布流', c: '留白展墙', d: '整齐网格' }
  const pageSize = direction === 'd' ? 12 : 16
  const collapsibleInfo = direction === 'a' || direction === 'd'
  const base = '../../../li-picture-cloud-frontend/public/images/mosaic/'
  const sources = [
    ['nature', '山间水岸', 600, 400, '风景', ['自然'], '山间、水面与绿色。'],
    ['architecture', '建筑的线条', 600, 766, '建筑', ['城市'], '建筑立面的线条。'],
    ['city', '城市一隅', 800, 492, '建筑', ['城市'], '城市中停留的片刻。'],
    ['portrait', '人物习作', 600, 899, '人物', ['人物'], '人物摄影样本。'],
    ['travel', '旅途之间', 800, 632, '风景', ['自然'], '旅行途中的景色。'],
    ['bw', '黑白时刻', 600, 900, '摄影', ['黑白'], '黑白摄影样本。'],
    ['still-life', '日常静物', 600, 397, '摄影', ['静物'], '日常静物的光与形。'],
    ['abstract', '色彩片段', 800, 533, '摄影', ['抽象'], '抽象色彩样本。']
  ]
  // First 8 use real file dimensions. Later repeats include mock panorama/square/tall slots.
  // A/B/C contain the source; D deliberately crops to equal 4:3 list tiles.
  const mockRatios = { 8: 2.8, 11: .48, 14: 1, 15: 2 }
  const data = Array.from({ length: 32 }, (_, i) => {
    const [file, name, width, height, category, tags, introduction] = sources[i % 8]
    return { id: i, file, name: name + (i >= 8 ? ` · 样本 ${Math.floor(i / 8) + 1}` : ''), width, height, category, tags, introduction,
      ratio: mockRatios[i % 16] || width / height, simulated: Boolean(mockRatios[i % 16]), format: i % 3 === 0 ? 'png' : 'jpg' }
  })
  let query = { q: '', category: '', format: '', tags: [], sort: 'descend' }
  let mode = 'default', page = 1, visible = [], activeIndex = 0, loadedPages = 1
  let opener = null, scrollBefore = 0
  const filterMarkup = `
    <form class="filter-pane" id="filters">
      <label>分类<select name="category"><option value="">全部分类</option><option>风景</option><option>建筑</option><option>人物</option><option>摄影</option></select></label>
      <label>格式<select name="format"><option value="">全部格式</option><option value="jpg">JPG</option><option value="png">PNG</option></select></label>
      <label>排序<select name="sort"><option value="descend">最新上传</option><option value="ascend">最早上传</option></select></label>
      <fieldset><legend>标签（同时满足）</legend>${['自然', '城市', '黑白'].map(tag => `<label class="check"><input type="checkbox" name="tag" value="${tag}">${tag}</label>`).join('')}</fieldset>
      <button class="apply" type="submit">应用筛选</button>
    </form>`
  document.body.dataset.shell = 'app'
  document.getElementById('prototype').innerHTML = `
    <aside class="mock-side" aria-label="App Shell 尺寸参照，不可导航"><span class="brand">LiPictureCloud</span><div class="side-items"><span>空间</span><span class="active">图库</span></div><div class="presence">伙伴<small>R02 存在位 · 仅示意</small></div></aside>
    <div class="shell-body">
      <header class="mock-toolbar mock-app"><span>图库</span><div class="mock-actions"><span class="mock-search">⌕ 图库搜索</span><span>↑ 上传</span><span>账户</span></div></header>
      <header class="mock-toolbar mock-public"><span class="brand">LiPictureCloud</span><div class="mock-actions"><span>首页</span><span>图库</span><span>登录</span><span>注册</span></div></header>
      <main class="gallery" id="gallery-main">
        <div class="gallery-bar"><h1 class="gallery-title">图库</h1>
          <form class="search" role="search"><label class="sr-only" for="search">搜索图片名称或简介</label><input id="search" name="q" type="search" placeholder="搜索图片" autocomplete="off"><button type="submit">搜索</button></form>
          <button class="filter-toggle" aria-expanded="${direction === 'c'}" aria-controls="${direction === 'b' ? 'filter-dialog' : 'inline-filters'}">筛选 <span aria-hidden="true">≡</span></button>
          <span class="gallery-count" aria-live="polite"></span>
        </div>
        <div id="inline-filters" ${direction === 'c' ? '' : 'hidden'}>${direction === 'b' ? '' : filterMarkup}</div>
        <div class="chips" aria-label="已应用条件"></div>
        <div class="image-grid" aria-label="图片列表"></div>
        <div class="state" hidden></div><nav class="pagination" aria-label="图库分页"></nav>
        <p class="list-footnote">${direction === 'd' ? 'D · 整齐网格：列表统一 4:3 裁切，点开查看完整图片。' : '设计样本：部分容器比例用于模拟。'}8 张本地图片重复组成 32 项，每页 ${pageSize} 项；名称、分类、日期为模拟。图库主体不设统一最大宽度。</p>
      </main>
      <div class="mock-bottom" aria-label="移动 App 导航尺寸参照"><span>空间</span><span>图库</span><span>伙伴</span><span>更多</span></div>
    </div>
    <dialog class="viewer" aria-labelledby="viewer-title">
      <header class="viewer-head"><h2 id="viewer-title"></h2><span class="viewer-count"></span><button id="prev-image" aria-label="上一张">←</button><button id="next-image" aria-label="下一张">→</button><button id="toggle-info" aria-expanded="false" aria-controls="view-info">信息</button><button id="close-viewer" aria-label="关闭查看器，返回图库">关闭</button></header>
      <div class="viewer-body"><div class="view-stage"><img id="view-image" alt=""></div><section class="view-info" id="view-info" hidden></section></div>
    </dialog>
    <dialog class="filter-dialog" id="filter-dialog" aria-labelledby="filter-title"><header><h2 id="filter-title">筛选</h2><button id="close-filter" aria-label="关闭筛选">关闭</button></header>${direction === 'b' ? filterMarkup : ''}</dialog>
    <nav class="lab" aria-label="设计原型切换，不属于产品界面"><a href="index.html" aria-label="返回方向比较">比较</a><a id="previous-variant" aria-label="上一方案">←</a><span class="variant-name">${direction.toUpperCase()} · ${names[direction]}</span><a id="next-variant" aria-label="下一方案">→</a><details><summary>演示设置</summary><div>
      <label>外壳尺寸参照<select id="shell"><option value="app">App · 已登录</option><option value="public">Public · 访客</option></select></label>
      <label>页面状态<select id="state"><option value="default">Default · 默认</option><option value="hover">Hover / Focus · 首项信息</option><option value="filtered">Filtered · 风景</option><option value="empty">Search Empty · 无结果</option><option value="no-data">Empty · 空图库</option><option value="loading">Loading · 比例占位</option><option value="error">Error · 重试</option></select></label>
      <p>本地模拟，非真实 API / 登录状态。Viewer 内不执行下载、编辑或删除。窗口缩窄即可查看移动版。</p>
    </div></details></nav>
    <p id="announcement" class="sr-only status-live" role="status" aria-live="polite"></p>`
  const $ = selector => document.querySelector(selector)
  const grid = $('.image-grid'), viewer = $('.viewer'), info = $('#view-info'), filterDialog = $('#filter-dialog')
  const variantKeys = ['a', 'b', 'c', 'd'], variantIndex = variantKeys.indexOf(direction)
  $('#previous-variant').href = `direction-${variantKeys[(variantIndex + variantKeys.length - 1) % variantKeys.length]}.html`
  $('#next-variant').href = `direction-${variantKeys[(variantIndex + 1) % variantKeys.length]}.html`
  const text = (tag, value, className) => { const el = document.createElement(tag); el.textContent = value; if (className) el.className = className; return el }
  function syncInputs() {
    $('#search').value = query.q
    $('#filters [name="category"]').value = query.category
    $('#filters [name="format"]').value = query.format
    $('#filters [name="sort"]').value = query.sort
    document.querySelectorAll('[name="tag"]').forEach(el => { el.checked = query.tags.includes(el.value) })
  }
  function reset() {
    query = { q: '', category: '', format: '', tags: [], sort: 'descend' }
    mode = 'default'; page = 1; loadedPages = 1; $('#state').value = 'default'; syncInputs(); render()
  }
  function tile(item, index) {
    const button = document.createElement('button')
    button.className = 'tile'; button.type = 'button'; button.dataset.id = item.id; button.dataset.ratio = item.ratio
    button.setAttribute('aria-label', `查看图片：${item.name}`)
    const img = document.createElement('img')
    img.src = `${base}${item.file}.jpg`; img.alt = item.name; img.width = item.width; img.height = item.height
    img.loading = index < 3 ? 'eager' : 'lazy'; img.decoding = 'async'
    if (index === 0) img.fetchPriority = 'high'
    img.style.aspectRatio = direction === 'd' ? '4 / 3' : String(item.ratio)
    const caption = text('span', item.name, 'tile-info')
    if (item.simulated && direction !== 'd') caption.append(text('small', '容器比例模拟 · 原素材完整保留'))
    button.append(img, caption)
    button.addEventListener('click', () => openViewer(index, button))
    return button
  }
  // Small deterministic row allocator, not a proposed production algorithm.
  function justifiedLayout() {
    if (direction !== 'a' || grid.hidden) return
    const tiles = [...grid.querySelectorAll('.tile')]
    if (!tiles.length) return
    const width = grid.clientWidth, gap = 8, target = width < 480 ? 154 : width < 768 ? 185 : 230
    grid.replaceChildren()
    let row = [], ratioSum = 0
    function flush(last = false) {
      if (!row.length) return
      const height = Math.min(last ? target : target * 1.3, (width - gap * (row.length - 1)) / ratioSum)
      const container = document.createElement('div'); container.className = 'justified-row'
      row.forEach(button => { button.style.width = `${height * Number(button.dataset.ratio)}px`; button.style.height = `${height}px`; container.append(button) })
      grid.append(container); row = []; ratioSum = 0
    }
    tiles.forEach(button => {
      row.push(button); ratioSum += Number(button.dataset.ratio)
      if (ratioSum * target + gap * (row.length - 1) >= width) flush()
    })
    flush(true)
  }
  function renderChips() {
    const chips = $('.chips'); chips.replaceChildren()
    const applied = [['q', query.q && `搜索：${query.q}`], ['category', query.category], ['format', query.format && query.format.toUpperCase()], ['sort', query.sort === 'ascend' && '最早上传'], ...query.tags.map(t => [`tag:${t}`, `标签：${t}`])]
    applied.filter(([, value]) => value).forEach(([key, value]) => {
      const chip = text('button', `${value} ×`, 'chip'); chip.setAttribute('aria-label', `移除条件 ${value}`)
      chip.onclick = () => { if (key.startsWith('tag:')) query.tags = query.tags.filter(t => t !== key.slice(4)); else query[key] = key === 'sort' ? 'descend' : ''; page = loadedPages = 1; syncInputs(); render() }
      chips.append(chip)
    })
    if (chips.children.length) { const clear = text('button', '清除筛选', 'reset'); clear.onclick = reset; chips.append(clear) }
  }
  function render() {
    document.body.dataset.hover = mode === 'hover' ? 'true' : 'false'
    const filtered = data.filter(item => (!query.q || `${item.name} ${item.introduction}`.includes(query.q)) && (!query.category || item.category === query.category) && (!query.format || item.format === query.format) && query.tags.every(tag => item.tags.includes(tag)))
    if (query.sort === 'ascend') filtered.reverse()
    const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize)); page = Math.min(page, totalPages)
    visible = direction === 'b' ? filtered.slice(0, pageSize * loadedPages) : filtered.slice((page - 1) * pageSize, page * pageSize)
    renderChips(); grid.replaceChildren(); $('.pagination').replaceChildren(); $('.state').replaceChildren()
    grid.hidden = false; $('.state').hidden = true
    $('.gallery-count').textContent = `${filtered.length} 张 · 本地样本`
    if (mode === 'loading') {
      grid.className = 'image-grid skeletons'; grid.setAttribute('aria-busy', 'true'); grid.setAttribute('aria-label', '正在加载图片')
      for (let i = 0; i < 12; i++) grid.append(text('div', '', 'skeleton'))
      $('.gallery-count').textContent = '正在加载图片'; return
    }
    grid.removeAttribute('aria-busy'); grid.setAttribute('aria-label', '图片列表')
    if (['no-data', 'error'].includes(mode) || !visible.length) {
      grid.hidden = true; $('.state').hidden = false
      const failure = mode === 'error', empty = mode === 'no-data'
      $('.gallery-count').textContent = failure ? '加载失败' : '0 张'
      $('.state').append(text('h2', failure ? '图片列表加载失败' : empty ? '暂无图片' : '没有符合条件的图片'), text('p', failure ? '请重试。筛选条件将保留。' : empty ? '公开图库还没有可展示的图片。' : '试试其他关键词，或清除筛选。'))
      const action = text('button', failure ? '重试' : empty ? '恢复样本（演示）' : '清除筛选')
      action.onclick = failure ? () => { mode = 'default'; $('#state').value = 'default'; render() } : reset
      $('.state').append(action); return
    }
    grid.className = `image-grid ${direction === 'd' ? 'regular-grid' : direction === 'a' ? 'justified' : direction === 'b' ? 'masonry' : 'editorial'}`
    visible.forEach((item, index) => grid.append(tile(item, index))); justifiedLayout()
    const pagination = $('.pagination')
    if (direction === 'b') {
      pagination.append(text('span', `已显示 ${visible.length} / ${filtered.length} 张`))
      if (visible.length < filtered.length) { const more = text('button', '加载更多'); more.onclick = () => { const oldY = scrollY; loadedPages++; render(); window.scrollTo(0, oldY) }; pagination.append(more) }
    } else {
      const prev = text('button', '上一页'), next = text('button', '下一页')
      prev.disabled = page === 1; next.disabled = page >= totalPages
      prev.onclick = () => changePage(page - 1); next.onclick = () => changePage(page + 1)
      pagination.append(prev, text('span', `${page} / ${totalPages}`), next)
    }
  }
  function changePage(value) { page = value; render(); $('#gallery-main').scrollIntoView({ block: 'start' }); grid.querySelector('.tile')?.focus({ preventScroll: true }) }
  function updateViewer() {
    const item = visible[activeIndex]
    $('#viewer-title').textContent = item.name
    $('.viewer-count').textContent = `${activeIndex + 1} / ${visible.length}`
    $('#view-image').src = `${base}${item.file}.jpg`; $('#view-image').alt = item.name
    $('#view-image').width = item.width; $('#view-image').height = item.height
    $('#prev-image').disabled = activeIndex === 0; $('#next-image').disabled = activeIndex === visible.length - 1
    info.replaceChildren(text('h3', item.name), text('p', item.introduction))
    const dl = document.createElement('dl')
    const fields = [['分类', item.category], ['标签', item.tags.join('、')], ['上传日期', '2026-09-22（模拟）'], ['归属', '公开图库（模拟）'], ['素材尺寸', `${item.width} × ${item.height}`]]
    fields.forEach(([key, value]) => dl.append(text('dt', key), text('dd', value))); info.append(dl)
    info.append(text('p', '离线设计演示。这里只展示查看与返回体验。现有下载、分享、编辑、删除仍属于正式详情页；本原型不会执行这些操作。', 'mock-notice'))
    const detail = text('button', '打开独立详情 →（流程示意）', 'detail-stub')
    detail.onclick = () => { $('#announcement').textContent = '正式实现将打开 /picture/:id；离线原型不会离开当前列表。'; detail.textContent = '正式实现跳转 /picture/:id，原型不跳转' }
    info.append(detail)
    ;['下载 / 分享 · 未接入', '编辑 / 删除 · 留在现有权限详情'].forEach(label => { const action = text('button', label); action.disabled = true; info.append(action) })
    $('#announcement').textContent = `查看 ${item.name}，当前已加载图片中的第 ${activeIndex + 1} 张`
  }
  function openViewer(index, button) {
    activeIndex = index; opener = button; scrollBefore = scrollY; info.hidden = collapsibleInfo
    $('#toggle-info').setAttribute('aria-expanded', String(!info.hidden)); $('#toggle-info').hidden = !collapsibleInfo
    updateViewer(); viewer.showModal(); $('#close-viewer').focus()
  }
  function closeViewer() { viewer.close() }
  viewer.addEventListener('close', () => { window.scrollTo(0, scrollBefore); opener?.focus({ preventScroll: true }) })
  $('#close-viewer').onclick = closeViewer
  $('#toggle-info').onclick = () => { info.hidden = !info.hidden; $('#toggle-info').setAttribute('aria-expanded', String(!info.hidden)); if (!info.hidden && innerWidth <= 768) info.scrollIntoView({ block: 'start' }) }
  $('#prev-image').onclick = () => { if (activeIndex > 0) { activeIndex--; updateViewer() } }
  $('#next-image').onclick = () => { if (activeIndex + 1 < visible.length) { activeIndex++; updateViewer() } }
  viewer.addEventListener('keydown', event => {
    if (event.key === 'ArrowLeft') { event.preventDefault(); $('#prev-image').click() }
    if (event.key === 'ArrowRight') { event.preventDefault(); $('#next-image').click() }
  })
  $('.search').onsubmit = event => { event.preventDefault(); query.q = $('#search').value.trim(); mode = 'default'; page = loadedPages = 1; $('#state').value = 'default'; render() }
  $('#filters').onsubmit = event => {
    event.preventDefault(); const values = new FormData(event.currentTarget)
    query.category = values.get('category'); query.format = values.get('format'); query.sort = values.get('sort'); query.tags = values.getAll('tag')
    mode = 'default'; page = loadedPages = 1; $('#state').value = 'default'
    if (filterDialog.open) filterDialog.close()
    else if (direction !== 'c') { $('#inline-filters').hidden = true; $('.filter-toggle').setAttribute('aria-expanded', 'false') }
    render()
  }
  $('.filter-toggle').onclick = () => {
    if (direction === 'b') { filterDialog.showModal(); $('.filter-toggle').setAttribute('aria-expanded', 'true') }
    else { $('#inline-filters').hidden = !$('#inline-filters').hidden; $('.filter-toggle').setAttribute('aria-expanded', String(!$('#inline-filters').hidden)) }
  }
  $('#close-filter').onclick = () => filterDialog.close()
  filterDialog.addEventListener('close', () => { $('.filter-toggle').setAttribute('aria-expanded', 'false'); $('.filter-toggle').focus() })
  $('#shell').onchange = event => { document.body.dataset.shell = event.target.value; requestAnimationFrame(justifiedLayout) }
  $('#state').onchange = event => {
    const requested = event.target.value; reset(); mode = requested; $('#state').value = requested
    if (mode === 'filtered') query.category = '风景'
    if (mode === 'empty') query.q = '不存在的图片'
    syncInputs(); render()
  }
  document.addEventListener('keydown', event => {
    if (viewer.open || filterDialog.open || event.target.matches('input, select, textarea, [contenteditable]')) return
    if (event.key === 'ArrowLeft') $('#previous-variant').click()
    if (event.key === 'ArrowRight') $('#next-variant').click()
  })
  let resizeFrame = 0, previousWidth = 0
  new ResizeObserver(entries => {
    const width = entries[0].contentRect.width
    if (width === previousWidth) return
    previousWidth = width
    cancelAnimationFrame(resizeFrame); resizeFrame = requestAnimationFrame(justifiedLayout)
  }).observe(grid)
  render()
})()
