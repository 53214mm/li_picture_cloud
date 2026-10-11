import test from 'node:test'
import { createRehearsalHistory } from '../src/presentation/rehearsalHistory.js'
import assert from 'node:assert/strict'
import { Buffer } from 'node:buffer'
import { readFile } from 'node:fs/promises'
import { URL } from 'node:url'
import { parse, compileScript } from '@vue/compiler-sfc'
import { createRenderer, nextTick, reactive, ref } from 'vue'
const moduleUrl = source => `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
import { createRouter, createMemoryHistory } from 'vue-router'
let fixtureSequence = 0
function hostNode(tag, content = '') {
  const listeners = new Map()
  return {
    tag, tagName: tag.toUpperCase(), text: content, props: {}, children: [], parent: null,
    value: '', checked: false, selected: false, selectedIndex: -1,
    get textContent() { return this.tag === '#comment' ? '' : this.text + this.children.map(child => child.textContent).join('') },
    get options() { return descendants(this).filter(node => node.tag === 'option') },
    addEventListener(name, listener) {
      if (!listeners.has(name)) listeners.set(name, [])
      listeners.get(name).push(listener)
    },
    removeEventListener(name, listener) { listeners.set(name, (listeners.get(name) || []).filter(item => item !== listener)) },
    dispatch(name) { for (const listener of listeners.get(name) || []) listener({ target: this }) },
    querySelector() { return null },
    focus() { this.focused = true }
  }
}
const renderer = createRenderer({
  createElement: tag => hostNode(tag),
  createText: content => hostNode('#text', content),
  createComment: content => hostNode('#comment', content),
  setText(node, content) { node.text = content },
  setElementText(node, content) { node.text = content; node.children = [] },
  patchProp(node, key, _previous, value) {
    node.props[key] = value
    if (key === 'value') { node.value = value; node._value = value }
    else if (['checked', 'selected', 'type', 'multiple'].includes(key)) node[key] = value
  },
  insert(node, parent, anchor) {
    if (node.parent) node.parent.children = node.parent.children.filter(child => child !== node)
    node.parent = parent
    const index = parent.children.indexOf(anchor)
    if (index < 0) parent.children.push(node)
    else parent.children.splice(index, 0, node)
  },
  remove(node) {
    if (node.parent) node.parent.children = node.parent.children.filter(child => child !== node)
    node.parent = null
  },
  parentNode: node => node.parent,
  nextSibling(node) { return node.parent?.children[node.parent.children.indexOf(node) + 1] || null }
})
function descendants(node) { return [node, ...node.children.flatMap(descendants)] }
function text(node) { return node.textContent.replace(/\s+/g, ' ').trim() }
function button(root, label) {
  const found = descendants(root).find(node => node.tag === 'button' && text(node) === label)
  assert.ok(found, `Button must be rendered: ${label}`)
  return found
}
async function flush() { for (let i = 0; i < 8; i++) await nextTick() }
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
async function fixture({ enabled = true, vision = false } = {}) {
  const boundaryUrl = moduleUrl(`export const fixture = ${++fixtureSequence}; export const shared = {}; export const useUserStore = () => shared.user; export const useCompanionRehearsalStore = () => shared.rehearsal; export const useCompanionChatStore = () => shared.chat; export const useCompanionInteractionStore = () => shared.interaction; export const useLegacyOverlayIsolation = () => shared.overlay; export const COMPANION_UI_ENABLED = ${enabled}; export const observationApi = { status: (...a) => shared.status(...a), open: (...a) => shared.open(...a), prepare: (...a) => shared.prepare(...a), observe: (...a) => shared.observe(...a), preview: (...a) => shared.preview(...a), close: (...a) => shared.close(...a) };`)
  const { shared } = await import(boundaryUrl); const calls = []; const listeners = new Map(); const timers = new Map(); let clock=0, timerId=0
  shared.rehearsal = { journal: createRehearsalHistory() }
  shared.user = reactive({ currentUser: { id: '1' }, logout: async () => { calls.push('logout') } })
  shared.chat = reactive({ state: { phase: 'idle', draft: '' }, modeBusy: false }); shared.overlay = ref(false)
  shared.interaction = reactive({ requestNumber: 0, open() { calls.push('chat-open'); this.requestNumber++ }, close() {}, reset() {}, cancelDrag() {}, takeDestination() {} })
  shared.status=async()=>{calls.push(['status']);return {enabled:true,realVisualReady:true,textReceiver:{provider:'FIXTURE',model:'fixture',host:'fixture.invalid',name:'mock'},visionReceiver:vision?{provider:'VISION_FIXTURE',model:'fixture',host:'fixture.invalid'}:null,textDayMax:1000,visionDayMax:200,counts:{text:0,vision:0}}}
  shared.open=async data=>{calls.push(['open',data]);return {sessionId:'round',textRemaining:data.textLimit,visionRemaining:data.visionLimit}}
  shared.prepare=async data=>{calls.push(['prepare',data]);return {snapshotId:'snapshot',visualSkip:'VISION_OFF'}}
  shared.observe=async data=>{calls.push(['observe',data]);return {...data,modelDispatched:true,visionDispatched:false,parsed:{action:'SPEAK',text:'no-click-topic'},textRemaining:5,visionRemaining:0,nextObservationSeconds:90}}
  shared.preview=async data=>{calls.push(['preview',data]);return {visualSkip:'UNCONFIRMED_VISIBLE_REGION'}}
  shared.close=async id=>{calls.push(['close',id])}
  shared.now=()=>clock;shared.setTimer=(fn,delay)=>{const id=++timerId;timers.set(id,{fn,at:clock+delay});return id};shared.clearTimer=id=>timers.delete(id)
  const empty=moduleUrl('export default { render() { return null } }')
  const shell=moduleUrl(`export default { inheritAttrs: false, props: ['open'], emits: ['close'], render() { return this.open ? this.$slots.default?.() : null } }`)
  const aliases={vue:import.meta.resolve('vue'),pinia:import.meta.resolve('pinia'),'vue-router':import.meta.resolve('vue-router'),'./user':boundaryUrl}
  for(const path of ['stores/user','stores/companionRehearsal','stores/companionChat','stores/companionInteraction','api/companionObservation','config/features','composables/useLegacyOverlayIsolation'])aliases[`@/${path}`]=boundaryUrl
  for(const path of ['sitePresence','companionPresentation','safeVisualLayout','observationPresentation'])aliases[`@/presentation/${path}`]=new URL(`../src/presentation/${path}.js`,import.meta.url).href
  aliases['@/components/companion/body/lingyeAssets']=moduleUrl('export const lingyeAssets={shell:{src:"local-static-artwork"}}')
  aliases['@/presentation/companionObserver']=moduleUrl(`import {createCompanionObserver as native} from ${JSON.stringify(new URL('../src/presentation/companionObserver.js',import.meta.url).href)};import {shared} from ${JSON.stringify(boundaryUrl)};export const createCompanionObserver=options=>native({...options,now:shared.now,setTimer:shared.setTimer,clearTimer:shared.clearTimer});`)
  const rewrite=source=>source.replace(/from (['"])([^'"]+)\1/g,(_,quote,specifier)=>{assert.ok(aliases[specifier],specifier);return `from ${JSON.stringify(aliases[specifier])}`})
  aliases['@/stores/companionObserver']=moduleUrl(rewrite(await readFile(new URL('../src/stores/companionObserver.js',import.meta.url),'utf8')))
  aliases['@/composables/useCompanionObserver']=moduleUrl(rewrite(await readFile(new URL('../src/composables/useCompanionObserver.js',import.meta.url),'utf8')))
  async function compile(path){const {descriptor}=parse(await readFile(new URL(`../src/${path}`,import.meta.url),'utf8'));return moduleUrl(rewrite(compileScript(descriptor,{id:`${path}-${fixtureSequence}`,inlineTemplate:true}).content))}
  aliases['@/components/companion/CompanionObservationSpeech.vue']=await compile('components/companion/CompanionObservationSpeech.vue')
  aliases['./CompanionObservationSpeech.vue']=aliases['@/components/companion/CompanionObservationSpeech.vue']
  aliases['@/components/companion/CompanionObservationPanel.vue']=await compile('components/companion/CompanionObservationPanel.vue')
  for(const path of ['shell/AppNavigation','shell/AppToolbar','shell/AppMain','shell/CompanionPresenceSlot','shell/SiteFooter','shell/ShellIcon','ui/LpButton','companion/CompanionInteractionPanel'])aliases[`@/components/${path}.vue`]=empty
  aliases['@/components/shell/ShellDialog.vue']=shell
  const component=(await import(await compile('layouts/AppLayout.vue'))).default
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/gallery',name:'gallery',component:{}},{path:'/companion',name:'companion',component:{}},{path:'/space/my',name:'my-space',component:{}},{path:'/picture/:id',name:'picture-detail',component:{}},{path:'/admin',name:'admin-users',component:{}},{path:'/companion/rehearsal',name:'companion-rehearsal',component:{}},{path:'/model-gateway',name:'model-gateway',component:{}}]})
  await router.push('/companion');await router.isReady()
  const originals={document:globalThis.document,window:globalThis.window,MutationObserver:globalThis.MutationObserver}
  globalThis.document={hidden:false,body:{},addEventListener:(name,fn)=>{if(!listeners.has(name))listeners.set(name,new Set());listeners.get(name).add(fn)},removeEventListener:(name,fn)=>listeners.get(name)?.delete(fn),querySelector:()=>null,querySelectorAll:()=>[]}
  globalThis.window={innerWidth:1024,innerHeight:768,addEventListener:(name,fn)=>{if(!listeners.has(name))listeners.set(name,new Set());listeners.get(name).add(fn)},removeEventListener:(name,fn)=>listeners.get(name)?.delete(fn),matchMedia:()=>({addEventListener(){},removeEventListener(){}})}
  globalThis.MutationObserver=class{observe(){}disconnect(){}}
  const {createPinia}=await import('pinia');const root=hostNode('root');const app=renderer.createApp(component);app.use(createPinia());app.use(router);app.mount(root);await flush()
  return {root,router,shared,calls,async advance(ms){clock+=ms;for(const [id,t]of [...timers])if(t.at<=clock){timers.delete(id);t.fn()}await flush();for(let i=0;i<20;i++)await Promise.resolve();await flush()},emit(name,event={}){for(const fn of listeners.get(name)||[])fn(event)},async destroy(){app.unmount();await flush();Object.assign(globalThis,originals)}}
}
test('actual shell, store and observation panel default off; feature off has no API reads',async()=>{
 for(const enabled of [true,false]){const f=await fixture({enabled});try{await f.advance(100000);assert.equal(f.calls.some(x=>['open','observe','prepare','preview'].includes(x[0])),false);assert.equal(text(f.root).includes('独立观察'),enabled);if(enabled){assert.match(text(f.root),/1–500/);assert.match(text(f.root),/默认关闭/);assert.match(text(f.root),/1000/)}else assert.deepEqual(f.calls,[])}finally{await f.destroy()}}
})
test('actual no-click observer starts by explicit button and sends once after timer, not navigation',async()=>{
 const f=await fixture();try{button(f.root,'明确授权开启本轮自动观察').props.onClick();await flush();assert.equal(f.calls.filter(x=>x[0]==='open').length,1);await f.router.push('/gallery');await flush();assert.equal(f.calls.filter(x=>x[0]==='observe').length,0);await f.advance(90000);assert.equal(f.calls.filter(x=>x[0]==='observe').length,1);assert.match(text(f.root),/no-click-topic/);button(f.root,'回复绫页 · 普通聊天').props.onClick();await flush();assert.ok(f.calls.includes('chat-open'));assert.equal(f.calls.some(x=>x[0]==='preview'),false)}finally{await f.destroy()}
})
test('actual shell rejects late output on hidden, chat draft/phase, account and logout boundaries',async()=>{
 for(const cause of ['hidden','draft','phase','account']){const f=await fixture();const pending=deferred();let sent;try{f.shared.observe=data=>{sent=data;return pending.promise};button(f.root,'明确授权开启本轮自动观察').props.onClick();await flush();await f.advance(90000);assert.ok(sent);if(cause==='hidden'){globalThis.document.hidden=true;f.emit('visibilitychange')}if(cause==='draft')f.shared.chat.state.draft='private-draft';if(cause==='phase')f.shared.chat.state.phase='waiting';if(cause==='account')f.shared.user.currentUser={id:'2'};await flush();pending.resolve({...sent,parsed:{action:'SPEAK',text:'stale-output'},textRemaining:5,visionRemaining:0});await flush();assert.doesNotMatch(text(f.root),/stale-output|private-draft/);await f.advance(90000);assert.doesNotMatch(text(f.root),/stale-output/)}finally{await f.destroy()}}
})
test('explicit preview only is zero model calls and sensitive pages report skip',async()=>{
 const f=await fixture({vision:true});try{const checkbox=descendants(f.root).find(n=>n.tag==='input'&&n.props.type==='checkbox');checkbox.checked=true;checkbox.dispatch('change');await flush();button(f.root,'仅预览真实观察区域（无模型调用）').props.onClick();await flush();assert.equal(f.calls.filter(x=>x[0]==='preview').length,1);assert.equal(f.calls.some(x=>['open','observe'].includes(x[0])),false);await f.router.push('/admin');await flush();button(f.root,'仅预览真实观察区域（无模型调用）').props.onClick();await flush();assert.match(text(f.root),/SENSITIVE_OR_UNKNOWN_PAGE/);assert.equal(f.calls.filter(x=>x[0]==='preview').length,1)}finally{await f.destroy()}
})

test('visual checkbox explains missing receiver and active-round lock next to its control',async()=>{
 const f=await fixture();try{assert.match(text(f.root),/当前没有视觉 BYOK 接收方/);button(f.root,'明确授权开启本轮自动观察').props.onClick();await flush();assert.match(text(f.root),/本轮已开启，视觉设置已锁定/);assert.match(text(f.root),/暂停并结束本轮/)}finally{await f.destroy()}
})

test('actual speech reader closes without deleting history, survives navigation and is readable in chat panel',async()=>{
 const f=await fixture();try{button(f.root,'明确授权单次观察').props.onClick();await flush();assert.match(text(f.root),/最近一次实际发言/);assert.match(text(f.root),/no-click-topic/);button(f.root,'关闭气泡').props.onClick();await flush();assert.equal(descendants(f.root).filter(n=>n.props['data-testid']==='observation-speech-body').length,0);assert.match(text(f.root),/查看近期发言（1 \/ 50）/);await f.router.push('/gallery');await flush();assert.match(text(f.root),/no-click-topic/);f.shared.interaction.open();await flush();assert.match(text(f.root),/no-click-topic/);assert.equal(f.calls.filter(x=>x[0]==='observe').length,1);f.shared.user.currentUser={id:'2'};await flush();assert.doesNotMatch(text(f.root),/no-click-topic/);}finally{await f.destroy()}
})



test('actual shell keeps one authorization across gallery detail companion settings return and menu',async()=>{
 const f=await fixture();try{
  button(f.root,'明确授权开启本轮自动观察').props.onClick();await flush();
  for(const route of ['/gallery','/picture/2109139633516339202','/companion','/model-gateway']){await f.router.push(route);await flush();}
  assert.equal(f.calls.filter(x=>x[0]==='open').length,1);assert.equal(f.calls.filter(x=>x[0]==='close').length,0);assert.match(text(f.root),/本页暂不观察/);
  await f.advance(90000);assert.equal(f.calls.filter(x=>x[0]==='observe').length,0);
  await f.router.push('/gallery');await flush();assert.equal(f.calls.filter(x=>x[0]==='observe').length,0);
  await f.advance(90000);assert.equal(f.calls.filter(x=>x[0]==='observe').length,1);assert.match(text(f.root),/no-click-topic/);
  button(f.root,'暂停并结束本轮').props.onClick();await flush();await f.router.push('/companion');await flush();await f.advance(180000);
  assert.equal(f.calls.filter(x=>x[0]==='open').length,1);assert.equal(f.calls.filter(x=>x[0]==='observe').length,1);
 }finally{await f.destroy()}
})
