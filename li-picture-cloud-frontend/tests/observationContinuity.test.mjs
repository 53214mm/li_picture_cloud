import test from 'node:test'
import assert from 'node:assert/strict'
import { createCompanionObserver } from '../src/presentation/companionObserver.js'

function fixture({expiresAt,capture}={}) {
 let clock=0,id=0,page={page:'GALLERY',pictureIds:['1'],rects:[],dependency:'1:1'},respond,prepare,visionBudget=0;
 const timers=new Map(),calls={open:0,close:0,prepare:0,observe:0};
 const api={status:async()=>({enabled:true,textReceiver:{token:'fixture'},visionReceiver:{token:'vision-fixture'}}),open:async data=>{calls.open++;visionBudget=data.visionLimit;return{sessionId:'original-grant',textRemaining:data.textLimit,visionRemaining:data.visionLimit,expiresAt}},close:async()=>{calls.close++},prepare:async()=>{calls.prepare++;return prepare?prepare():{snapshotId:'snap'}},observe:async data=>{calls.observe++;return respond?respond(data):{...data,parsed:{action:'SPEAK',text:'原话题'},modelDispatched:true,visionDispatched:false,textRemaining:8,visionRemaining:visionBudget,nextObservationSeconds:300}}};
 const observer=createCompanionObserver({api,capture,collect:()=>page,now:()=>clock,setTimer:(fn,delay)=>{const n=++id;timers.set(n,{fn,at:clock+delay});return n},clearTimer:n=>timers.delete(n)});
 observer.setOwner(1);
 const settle=async()=>{for(let i=0;i<30;i++)await Promise.resolve()};
 return{observer,api,calls,async advance(ms){clock+=ms;for(const [n,t]of [...timers])if(t.at<=clock){timers.delete(n);t.fn()}await settle()},setPage(next){page=next;observer.update(page)},respond(fn){respond=fn},prepare(fn){prepare=fn},settle};
}
const options={textLimit:10,visionLimit:0,intensity:'NORMAL',auto:true};
test('supported and unknown route changes preserve the grant, speech and original next deadline',async()=>{
 const f=fixture();await f.observer.start(options);await f.advance(90000);const next=f.observer.state().nextAt;
 for(const page of ['PICTURE_DETAIL','COMPANION','UNKNOWN','GALLERY']){f.setPage({page,pictureIds:[],rects:[]});assert.equal(f.observer.state().enabled,true);assert.equal(f.observer.state().nextAt,next);assert.equal(f.observer.state().presentation.text,'原话题');}
 assert.equal(f.calls.open,1);assert.equal(f.calls.close,0);assert.equal(f.calls.observe,1);await f.advance(299999);assert.equal(f.calls.observe,1);await f.advance(1);assert.equal(f.calls.observe,2);
})
test('unknown pages skip the scheduled turn without opening or dispatching and return on the existing cadence',async()=>{
 const f=fixture();await f.observer.start(options);f.setPage({page:'UNKNOWN',pictureIds:[],rects:[]});await f.advance(90000);assert.equal(f.observer.state().enabled,true);assert.equal(f.calls.prepare,0);assert.equal(f.calls.close,0);assert.equal(f.calls.open,1);const next=f.observer.state().nextAt;f.setPage({page:'COMPANION',pictureIds:[],rects:[]});assert.equal(f.observer.state().nextAt,next);assert.equal(f.calls.observe,0);await f.advance(89999);assert.equal(f.calls.observe,0);await f.advance(1);assert.equal(f.calls.observe,1);
})
test('page changes discard a late dependent reply, reconcile consumed balance and resume without overlap or new grant',async()=>{
 const f=fixture();let release;f.respond(data=>new Promise(done=>{release=()=>done({...data,parsed:{action:'SPEAK',text:'旧页迟到'},modelDispatched:true,textRemaining:9,visionRemaining:0,nextObservationSeconds:300})}));await f.observer.start(options);await f.advance(90000);f.setPage({page:'PICTURE_DETAIL',pictureIds:['2'],rects:[]});assert.equal(f.observer.state().enabled,true);assert.equal(f.calls.close,0);await f.advance(90000);assert.equal(f.calls.observe,1);release();await f.settle();assert.equal(f.observer.state().speeches.length,0);assert.equal(f.observer.state().textRemaining,9);assert.equal(f.calls.open,1);f.respond(null);await f.advance(299999);assert.equal(f.calls.observe,1);await f.advance(1);assert.equal(f.calls.observe,2);
})
test('manual pause, owner change, expiry and exhausted budget are terminal and never auto-open',async()=>{
 for(const cause of ['pause','owner','expiry','budget']){const f=fixture({expiresAt:cause==='expiry'?new Date(90000).toISOString():undefined});if(cause==='budget')f.respond(data=>({...data,parsed:{action:'SPEAK',text:'最后一条'},modelDispatched:true,textRemaining:0,visionRemaining:0,nextObservationSeconds:90}));await f.observer.start(options);if(cause==='pause')f.observer.pause();if(cause==='owner')f.observer.setOwner(2);await f.advance(90000);f.setPage({page:'UNKNOWN',pictureIds:[],rects:[]});f.setPage({page:'GALLERY',pictureIds:['1'],rects:[]});await f.advance(900000);assert.equal(f.observer.state().enabled,false);assert.equal(f.calls.open,1);assert.equal(f.calls.observe,cause==='budget'?1:0);}
})
test('layout movement with the same visible image and version is not a material change, including no-user-interaction prepare',async()=>{
 const f=fixture();let release;f.prepare(()=>new Promise(done=>{release=()=>done({snapshotId:'snap',preview:null,visualSkip:'CAPTURE_FORMAT_UNSUPPORTED'})}));await f.observer.start({...options,visionLimit:2});const pending=f.observer.observeNow();await f.settle();f.setPage({page:'GALLERY',pictureIds:['1'],dependency:'1:1',rects:[{x:17,y:89,width:100,height:100}]});release();await pending;assert.equal(f.calls.observe,1);assert.equal(f.observer.state().enabled,true);assert.equal(f.observer.state().speeches[0].text,'原话题');assert.equal(f.observer.state().records[0].response.visionDispatched,false);
})
test('prepare failure is recorded as not dispatched; no raw error or fake speech; explicit stop retained',async()=>{
 const f=fixture();f.prepare(async()=>{throw new Error('PRIVATE_ERROR_KEY')});await f.observer.start(options);await f.observer.observeNow();assert.equal(f.observer.state().records.length,1);assert.equal(f.observer.state().records[0].response.stage,'prepare');assert.equal(f.observer.state().records[0].response.modelDispatched,false);assert.equal(JSON.stringify(f.observer.state()).includes('PRIVATE_ERROR_KEY'),false);assert.equal(f.observer.state().speeches.length,0);assert.equal(f.calls.observe,0);
})
test('A to B to A cannot revive a stale reply; navigation holds preserve backoff while real chat still stops',async()=>{
 const f=fixture();await f.observer.start(options);await f.advance(90000);const original=f.observer.state().speeches[0].id,next=f.observer.state().nextAt;f.observer.hold(true);await f.advance(300000);assert.equal(f.calls.observe,1);f.observer.hold(false);assert.ok(f.observer.state().nextAt>next);let release;f.respond(data=>new Promise(done=>{release=()=>done({...data,parsed:{action:'SPEAK',text:'ABA迟到'},modelDispatched:true,textRemaining:7,visionRemaining:0,nextObservationSeconds:300})}));await f.advance(300000);f.setPage({page:'UNKNOWN',pictureIds:[],rects:[]});f.setPage({page:'GALLERY',pictureIds:['1'],dependency:'1:1',rects:[]});release();await f.settle();assert.equal(f.observer.state().speeches[0].id,original);assert.equal(f.observer.state().speeches.length,1);assert.equal(f.observer.state().textRemaining,7);assert.equal(f.calls.close,0);f.observer.suppress(true);f.observer.suppress(false);await f.advance(900000);assert.equal(f.observer.state().enabled,false);assert.equal(f.calls.open,1);
})
test('local capture failure records a not-sent turn and keeps authorization, with no digest bypass or immediate retry',async()=>{
 const f=fixture({capture:async()=>{throw new Error('CAPTURE_IMAGE_UNAVAILABLE')}});f.prepare(async()=>({snapshotId:'snap',preview:'data:image/png;base64,AA=='}));await f.observer.start({...options,visionLimit:2});await f.advance(90000);assert.equal(f.observer.state().enabled,true);assert.equal(f.observer.state().records[0].response.stage,'capture');assert.equal(f.observer.state().records[0].response.modelDispatched,false);assert.equal(f.calls.observe,0);assert.equal(f.observer.state().textRemaining,10);assert.equal(f.observer.state().visionRemaining,2);await f.advance(89999);assert.equal(f.calls.prepare,1);
})
test('observe transport failure records dispatch unknown and stops without retry; prepare denial closes authorization',async()=>{
 for(const stage of ['prepare','observe']){const f=fixture();if(stage==='prepare')f.prepare(async()=>{throw Object.assign(new Error('PRIVATE_DENIAL'),{status:403})});else f.respond(async()=>{throw new Error('PRIVATE_TRANSPORT_KEY')});await f.observer.start(options);await f.advance(90000);const row=f.observer.state().records[0];assert.equal(row.response.stage,stage);assert.equal(row.response.modelDispatched,stage==='prepare'?false:null);assert.equal(f.observer.state().enabled,false);assert.equal(JSON.stringify(f.observer.state()).includes('PRIVATE_'),false);await f.advance(900000);assert.equal(f.calls.open,1);assert.equal(f.calls.observe,stage==='prepare'?0:1);}
})

test('late transport and permission errors after route or menu changes still stop without a paid retry',async()=>{
 for(const stage of ['prepare','observe'])for(const cause of ['route','hold'])for(const status of [0,401,403]){
  const f=fixture();let reject;const pending=()=>new Promise((_resolve,fail)=>{reject=fail});if(stage==='prepare')f.prepare(pending);else f.respond(pending);
  await f.observer.start(options);await f.advance(90000);if(cause==='hold')f.observer.hold(true);else f.setPage({page:'COMPANION',pictureIds:[],rects:[]});reject(Object.assign(new Error('PRIVATE_FAILURE'),{status}));await f.settle();
  assert.equal(f.observer.state().enabled,false);assert.equal(f.observer.state().records[0].response.modelDispatched,stage==='prepare'?false:null);f.observer.hold(false);await f.advance(900000);assert.equal(f.calls.prepare,1);assert.equal(f.calls.observe,stage==='prepare'?0:1);assert.equal(f.calls.open,1);
 }
})

test('server permission and chat epoch invalidation is terminal even when client material also changed',async()=>{
 for(const reason of ['REVOKED','CONTEXT_CHANGED','CHAT_BUSY','CHAT_CHANGED']){const f=fixture();let release;f.respond(data=>new Promise(done=>{release=()=>done({...data,reason,modelDispatched:false,textRemaining:10,visionRemaining:0})}));await f.observer.start(options);await f.advance(90000);f.setPage({page:'COMPANION',pictureIds:[],rects:[]});release();await f.settle();assert.equal(f.observer.state().enabled,false);await f.advance(900000);assert.equal(f.calls.observe,1);}
})

test('an explicitly authorized single turn held before dispatch resumes once on the original cadence without a new grant or retry',async()=>{
 const f=fixture();await f.observer.start({...options,auto:false});f.setPage({page:'UNKNOWN',pictureIds:[],rects:[]});await f.observer.observeNow();assert.equal(f.calls.observe,0);f.setPage({page:'COMPANION',pictureIds:[],rects:[]});await f.advance(89999);assert.equal(f.calls.observe,0);await f.advance(1);assert.equal(f.calls.observe,1);await f.advance(900000);assert.equal(f.calls.observe,1);assert.equal(f.calls.open,1);
})
