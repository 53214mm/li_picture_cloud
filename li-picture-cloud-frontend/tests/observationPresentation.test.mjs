import test from 'node:test'
import assert from 'node:assert/strict'
import { observationActivity, observationStartBlock, observationVisualBlocks } from '../src/presentation/observationPresentation.js'
test('request, model silence, empty failure and undispatched state are distinct notices',()=>{
 assert.match(observationActivity({phase:'observing'}),/请求中/)
 assert.match(observationActivity({records:[{response:{parsed:{action:'SILENT'},modelDispatched:true}}]}),/选择沉默.*不是失败/)
 assert.match(observationActivity({reason:'EMPTY_OUTPUT'}),/本次失败.*不是模型选择沉默/)
 assert.match(observationActivity({records:[{response:{parsed:{action:'SPEAK',text:'must-not-quote'},modelDispatched:false}}]}),/未派发/)
 assert.match(observationActivity({reason:'SESSION_EXPIRED'}),/授权已过期/)
})
test('pending receiver read and opening prevent both authorization buttons from duplicate dispatch',()=>{
 assert.match(observationStartBlock({status:null,statusPhase:'loading'}),/正在读取/)
 assert.match(observationStartBlock({statusPhase:'error'}),/读取失败/)
 assert.match(observationStartBlock({phase:'opening'}),/不会重复提交/)
 assert.match(observationStartBlock({suppressed:true}),/占用/)
 assert.equal(observationStartBlock({status:{enabled:true,textReceiver:{}}}), '')
 assert.match(observationStartBlock({page:'UNKNOWN',status:{enabled:true,textReceiver:{}}}),/当前页面不在观察白名单/)
})
test('screenshot state explains both round lock and absent visual connection; ready state has no blocker',()=>{
 const reasons=observationVisualBlocks({enabled:true,status:{realVisualReady:true,visionReceiver:null}})
 assert.equal(reasons.length,2);assert.match(reasons[0],/设置已锁定/);assert.match(reasons[1],/文本聊天连接不会自动提供视觉能力/)
 assert.deepEqual(observationVisualBlocks({status:{realVisualReady:true,visionReceiver:{}}}),[])
})
test('B2 current pause, chat occupancy and budget stop precede historical decisions',()=>{
 for(const action of ['SPEAK','SILENT','EMOTE']){const records=[{response:{modelDispatched:true,parsed:{action,text:action==='SPEAK'?'prior speech':null}}}];assert.match(observationActivity({records,reason:'PAUSED'}),/^本轮已暂停/);assert.match(observationActivity({records,suppressed:true,reason:'CHAT_HIDDEN_OR_BLOCKED'}),/^聊天/);assert.match(observationActivity({records,reason:'BUDGET_EXHAUSTED'}),/^本轮次数已用完/);}
 assert.match(observationActivity({reason:'SESSION_CLOSED_OR_EXPIRED'}),/已关闭或过期/)
})
