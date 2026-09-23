'use strict'

// Isolated visual exploration only. No API, business state, storage, or runtime dependency.
const concepts = {
  a: { name: '绫页', species: '纸翼蛾族', file: 'assets/concept-a.png', previewFile: 'assets/concept-a-ui.png',
    crops: { adult: [0, 0, 760, 1000], pet: [1120, 535, 410, 455], juvenile: [897, 535, 220, 455], juvenilePortrait: [918, 540, 190, 162], egg: [690, 720, 208, 278], portrait: [850, 0, 600, 510] },
    home: '「右边那一小块，你也想留在画面里吗？」', chat: '城墙的轮廓很清楚。你想先给我看哪一部分？',
    dna: '折脊、缺角、一点赭色。', identity: '她先注意到被留在画面边缘的细节。双折页翼把蛋、身体和小尺寸轮廓连在一起；不靠 Logo 才能认识她。',
    colors: ['#374761', '#eee7d8', '#c49245'],
    scale: '正常比例成熟体的 48 / 64 / 80px 预览。小尺寸优先看翼形；面部细节不足时仍保留文字入口，不自动改回 Q 版。',
    risk: '待修订：未成熟体仍太像缩小的成年人；下一版要换成短闭翼袋与直罩衫。',
    caption: 'A 绫页暂定保留。左侧正常比例全身立绘用于 Web 成熟体；右上为对话半身；右下 Q 版仅留作 Codex 式桌宠参考。蛋与未成熟体仍是未定稿探索。' },
  b: { name: '汐砚', species: '潮壳螺族', frozen: true, file: 'assets/concept-b.png',
    crops: { adult: [1180, 575, 345, 425], juvenile: [909, 638, 272, 355], egg: [691, 735, 235, 255], portrait: [745, 0, 785, 579] },
    home: '「拍的时候，也这么安静吗？」', chat: '图里看起来很安静。那天是什么样的？如果你愿意，可以慢慢讲。',
    dna: '一圈壳纹，一段慢慢形成的关系。', identity: '背壳与低尾形成不对称的逗号轮廓。她不急着赞同，也不把画面里出现的东西当成你的个人经历。',
    colors: ['#315c5d', '#eee2d0', '#b9654b'],
    scale: '重点看壳与尾能否分开。珠光在小尺寸里容易糊成一团，生产版应改成稳定色块。',
    risk: '待修订：未成熟体偏低龄；壳的珠光和不对称转身会提高制作成本。',
    caption: 'B / 潮壳螺族。背壳是身体结构，不是背包。RGBA 原图保留 alpha；纸底展示只为对比。幼体年龄感、壳高光仍需修订。' },
  c: { name: '陶眠', species: '陶鳞蜥族', frozen: true, file: 'assets/concept-c.png',
    crops: { adult: [1208, 525, 322, 482], juvenile: [940, 540, 255, 451], egg: [709, 740, 222, 253], portrait: [745, 0, 650, 515] },
    home: '「这块接缝是后来补的？我想听你怎么改的。」', chat: '墙角的颜色不太一样。那是后来补上的吗？我只是猜的。',
    dna: '陶鳞、宽尾，愿意先试一下。', identity: '她关注作品留下的修改痕迹。厚尾与站姿让身体有重量；从蛋壳的嵌带到成年陶板，保留同一套材料语言。',
    colors: ['#b5684e', '#343333', '#bc8d44'],
    scale: '重点看耳板与尾端。64px 应合并碎鳞片；尾巴需要预留横向空间。',
    risk: '待修订：尾部动作需要独立包围盒；未成熟鳍与成年陶板的差异还要加强。',
    caption: 'C / 陶鳞蜥族。短尾与长分节尾表达发育差异；成年 Q 版仍是同一成熟伙伴。概念图没有骨骼、拆层或帧动画。' }
}
const state = { concept: 'a', view: 'home', stage: 'adult', enabled: true }
const q = (selector) => document.querySelector(selector)
const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)')

function cropArt(concept, kind, height) {
  const [x, y, width, sourceHeight] = concept.crops[kind]
  const frame = document.createElement('span')
  frame.className = 'art-crop'
  frame.dataset.artKind = kind
  if (concept === concepts.a && kind === 'adult') frame.classList.add('art-crop--adult-full')
  frame.style.aspectRatio = `${width} / ${sourceHeight}`
  if (height) frame.style.height = `${height}px`
  const img = document.createElement('img')
  img.src = concept.previewFile || concept.file
  img.alt = ''
  img.width = 1536
  img.height = 1024
  img.draggable = false
  Object.assign(img.style, { width: `${1536 / width * 100}%`, height: `${1024 / sourceHeight * 100}%`, left: `${-x / width * 100}%`, top: `${-y / sourceHeight * 100}%` })
  frame.append(img)
  return frame
}

function updateMotion() {
  q('#motion').disabled = motionPreference.matches
  q('#motion').title = motionPreference.matches ? '系统已要求减少动态效果' : '仅示意，不是帧动画'
  document.body.classList.toggle('is-breathing', q('#motion').checked && !motionPreference.matches && state.enabled && !document.hidden)
}

function render() {
  const concept = concepts[state.concept]
  document.querySelectorAll('[data-concept]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.concept === state.concept)))
  document.querySelectorAll('[data-view]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.view === state.view)))
  document.querySelectorAll('.character-name').forEach(element => { element.textContent = concept.name })
  for (const view of ['shell', 'home', 'conversation']) q(`#${view}-view`).hidden = !state.enabled || state.view !== view
  q('#disabled-view').hidden = state.enabled
  q('#presence').hidden = !state.enabled
  q('.sidebar-footnote').hidden = !state.enabled
  q('#mobile-companion').hidden = !state.enabled
  q('#breadcrumb').textContent = !state.enabled ? '图库 / 功能关闭示意' : ({ shell: '图库 / 全部图片', home: '伙伴 / 住处', conversation: '伙伴 / 对话' })[state.view]
  q('#gallery-nav').classList.toggle('selected', state.view === 'shell' || !state.enabled)
  q('.presence-art').replaceChildren(cropArt(concept, state.stage))
  q('.presence-art .art-crop').style.height = '100%'
  q('#home-character').replaceChildren(cropArt(concept, state.stage))
  q('#home-character').dataset.stage = state.stage
  q('.room-caption').hidden = state.stage === 'adult'
  q('#home-character').setAttribute('aria-label', `${concept.name} ${state.stage === 'adult' ? '成熟体正常比例全身立绘' : state.stage + ' 概念图裁切'}`)
  const portraitKind = state.stage === 'adult' ? 'portrait' : state.stage === 'juvenile' && concept.crops.juvenilePortrait ? 'juvenilePortrait' : state.stage
  q('#portrait').replaceChildren(cropArt(concept, portraitKind))
  q('#portrait').setAttribute('aria-label', `${concept.name} ${portraitKind === 'portrait' ? '成年半身' : portraitKind === 'juvenilePortrait' ? '未成熟体半身' : state.stage} 概念参考`)
  q('#portrait-description').textContent = portraitKind === 'portrait' ? '同一角色的半身视角' : portraitKind === 'juvenilePortrait' ? '未成熟体的半身视角' : '阶段参考裁切 · 非正式 Portrait'
  q('#home-line').textContent = state.stage === 'egg' ? '蛋的近景示意；尚未设计真实孵化与交流流程。' : `${concept.home}（台词示例）`
  q('#conversation-line').textContent = state.stage === 'egg' ? '卵的交流能力尚未定义。这里不借用成年角色说话。' : concept.chat
  q('#context-note').textContent = state.stage === 'egg' ? '只检视构图；不表示蛋已能聊天。' : '示例假设用户主动展示图片且可看到内容；不能套用到元数据或视觉失败状态。'
  q('#identity-title').textContent = concept.dna
  q('#identity-copy').textContent = concept.identity
  q('#scale-note').textContent = concept.scale
  q('#concept-risk').textContent = concept.risk
  q('#sheet-caption').textContent = concept.caption
  q('#concept-sheet').src = concept.file
  q('#concept-sheet').alt = `${concept.name} ${concept.species} 正常比例成熟体、Portrait、蛋、未成熟体和桌宠Q版参考`
  q('#sheet-link').href = concept.file
  q('#preview-disclosure').textContent = `A ${concept.name}暂留 · ${state.stage} · Web 成熟体采用正常比例全身立绘；Q 版仅留作桌宠参考。B / C 冻结，技术路线与最终方向待定。`
  q('#pet-reference-art').replaceChildren(cropArt(concepts.a, 'pet', 144))
  q('#scale-row').replaceChildren(...[48, 64, 80].map(height => {
    const item = document.createElement('div')
    item.className = 'scale-item'
    const label = document.createElement('small')
    label.textContent = `${height}px · Adult`
    item.append(cropArt(concept, 'adult', height), label)
    return item
  }))
  q('#palette').replaceChildren(...concept.colors.map(color => {
    const item = document.createElement('span')
    item.className = 'swatch'
    const swatch = document.createElement('i')
    swatch.style.background = color
    item.append(swatch, color)
    return item
  }))
  updateMotion()
}

document.querySelectorAll('[data-concept]').forEach(button => button.addEventListener('click', () => {
  if (concepts[button.dataset.concept]?.frozen) return
  state.concept = button.dataset.concept
  render()
}))
document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => { state.view = button.dataset.view; render() }))
q('#life-stage').addEventListener('change', event => { state.stage = event.target.value; render() })
q('#enabled').addEventListener('change', event => { state.enabled = event.target.checked; render() })
q('#motion').addEventListener('change', updateMotion)
motionPreference.addEventListener('change', updateMotion)
document.addEventListener('visibilitychange', updateMotion)
for (const selector of ['#presence', '#mobile-companion']) q(selector).addEventListener('click', () => { state.view = 'home'; render() })
q('#open-conversation').addEventListener('click', () => { state.view = 'conversation'; render() })
q('#restore-enabled').addEventListener('click', () => { state.enabled = true; q('#enabled').checked = true; render() })
q('#open-overlay').addEventListener('click', () => {
  document.body.classList.add('overlay-open')
  q('.app-preview').inert = true
  q('#overlay-demo').showModal()
})
q('#overlay-demo').addEventListener('close', () => {
  q('.app-preview').inert = false
  document.body.classList.remove('overlay-open')
  q('#open-overlay').focus()
})
render()
