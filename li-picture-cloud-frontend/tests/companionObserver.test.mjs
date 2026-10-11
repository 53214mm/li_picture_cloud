import test from 'node:test'
import assert from 'node:assert/strict'
import { createCompanionObserver } from '../src/presentation/companionObserver.js'

function fixture({ expiresAt } = {}) {
 let now=0,number=0;const timers=new Map();const calls={open:0,prepare:0,observe:0,close:0};let callback;
 const api={status:async()=>({enabled:true,textReceiver:{provider:'FIXTURE'},textMax:500,visionMax:100}),
  open:async data=>{calls.open++;return {sessionId:`round-${calls.open}`,textRemaining:data.textLimit,visionRemaining:data.visionLimit,expiresAt}},
  prepare:async()=>{calls.prepare++;return {snapshotId:'snapshot',visualSkip:'VISION_OFF'}},
  observe:async data=>{calls.observe++;return callback?callback(data):{...data,parsed:{action:'SPEAK',text:'我有个小话题'},reason:null,textRemaining:5,visionRemaining:0,nextObservationSeconds:90}},close:async()=>{calls.close++}}
 const observer=createCompanionObserver({api,now:()=>now,setTimer:(fn,delay)=>{const id=++number;timers.set(id,{fn,at:now+delay});return id},clearTimer:id=>timers.delete(id),collect:()=>({page:'COMPANION',pictureIds:[],rects:[]})})
 async function advance(ms){now+=ms;for(const [id,timer] of [...timers])if(timer.at<=now){timers.delete(id);timer.fn()}for(let i=0;i<15;i++)await Promise.resolve()}
 return {observer,api,calls,advance,respond:fn=>{callback=fn}}
}
test('default off; no-click timer speaks once while dense operations do not reschedule or add calls',async()=>{
 const f=fixture();f.observer.setOwner(1);await f.advance(100000);assert.equal(f.calls.observe,0)
 await f.observer.start({textLimit:500,visionLimit:0,intensity:'LOW',auto:true});
 await f.advance(45000);for(let i=0;i<100;i++)f.observer.update({page:'COMPANION',pictureIds:[],rects:[]});
 await f.advance(45000);assert.equal(f.calls.observe,1);assert.equal(f.observer.state().presentation.text,'我有个小话题');assert.equal(f.calls.prepare,1)
})
test('new chat, owner, clear and late completion cannot resurrect a trace or bubble',async()=>{
 const f=fixture();let resolve;f.respond(()=>new Promise(done=>{resolve=done}));f.observer.setOwner(1);await f.observer.start({textLimit:6,visionLimit:0,intensity:'NORMAL',auto:true});await f.advance(90000)
 f.observer.clear();resolve({parsed:{action:'SPEAK',text:'stale'},textRemaining:5});await f.advance(0)
 assert.equal(f.observer.state().records.length,0);assert.equal(f.observer.state().presentation,null)
 f.observer.setOwner(2);assert.equal(f.observer.state().enabled,false);assert.equal(f.observer.state().records.length,0)
})
test('plain clicks keep pending request, dependency change revokes it; hidden/chat suppresses',async()=>{
 const f=fixture();let resolve;f.respond(()=>new Promise(done=>{resolve=done}));f.observer.setOwner(1);await f.observer.start({textLimit:6,visionLimit:0,intensity:'NORMAL',auto:true});await f.advance(90000)
 f.observer.update({page:'COMPANION',pictureIds:[],rects:[]});assert.equal(f.calls.close,0)
 f.observer.suppress(true);resolve({parsed:{action:'SPEAK',text:'stale'},textRemaining:5});await f.advance(0);assert.equal(f.observer.state().presentation,null)
 await f.advance(200000);assert.equal(f.calls.observe,1)
})
test('budget exhausted pauses, empty is recorded honestly, explicit high limit is never clamped to twelve',async()=>{
 const f=fixture();f.respond(data=>({...data,parsed:null,reason:'EMPTY_OUTPUT',modelDispatched:true,textRemaining:0,visionRemaining:0,nextObservationSeconds:90}));f.observer.setOwner(1)
 await assert.rejects(f.observer.start({textLimit:501,visionLimit:0,intensity:'NORMAL',auto:true}),/BUDGET_OUT_OF_RANGE/)
 await f.observer.start({textLimit:500,visionLimit:0,intensity:'NORMAL',auto:true});assert.equal(f.observer.state().textRemaining,500);await f.advance(90000)
 assert.equal(f.observer.state().records[0].response.reason,'EMPTY_OUTPUT');assert.equal(f.observer.state().enabled,false);await f.advance(900000);assert.equal(f.calls.observe,1)
})
test('last authorized SPEAK remains visible after budget exhaustion and clears when chat starts',async()=>{
 const f=fixture();f.respond(data=>({...data,parsed:{action:'SPEAK',text:'last accepted'},modelDispatched:true,textRemaining:0,visionRemaining:0,daily:{text:7,vision:0},nextObservationSeconds:90}));f.observer.setOwner(1);await f.observer.start({textLimit:1,visionLimit:0,intensity:'NORMAL',auto:true});await f.advance(90000)
 assert.equal(f.observer.state().enabled,false);assert.equal(f.observer.state().presentation.text,'last accepted');assert.equal(f.observer.state().status.counts.text,7);f.observer.suppress(true);assert.equal(f.observer.state().presentation,null)
})
test('opening interrupted by chat is fully revoked and never leaves a stuck opening phase',async()=>{
 let resolve;let reads=0;const api={status:()=>++reads===1?new Promise(done=>{resolve=done}):Promise.resolve({enabled:true,textReceiver:{token:'fixture'}}),open:()=>assert.fail('must not open'),close:async()=>true}
 const observer=createCompanionObserver({api});observer.setOwner(1);const pending=observer.start({textLimit:6,visionLimit:0,intensity:'NORMAL',auto:true});observer.suppress(true);resolve({enabled:true,textReceiver:{token:'fixture'}});await pending;assert.notEqual(observer.state().phase,'opening');assert.equal(observer.state().enabled,false)
})
test('visible object changes without mutation discard the attempt while keeping authorization',async()=>{
 for(const boundary of ['prepare','observe']){
  let current={page:'GALLERY',pictureIds:['101'],rects:[],dependency:'101:1'},release,calls=0;
  const api={status:async()=>({enabled:true,textReceiver:{token:'fixture'}}),open:async data=>({sessionId:'s',textRemaining:data.textLimit,visionRemaining:0}),close:async()=>true,
   prepare:async()=>boundary==='prepare'?new Promise(done=>{release=()=>done({snapshotId:'p'})}):{snapshotId:'p'},
   observe:async data=>{calls++;return new Promise(done=>{release=()=>done({...data,parsed:{action:'SPEAK',text:'stale'},textRemaining:1})})}};
  const observer=createCompanionObserver({api,collect:()=>current});observer.setOwner(1);await observer.start({textLimit:2,visionLimit:0,intensity:'LOW',auto:false});const pending=observer.observeNow();
  for(let i=0;i<15;i++)await Promise.resolve();current={...current,pictureIds:['102'],dependency:'102:1'};release();await pending;
  assert.equal(observer.state().records.length,1);assert.equal(observer.state().records[0].response.reason,'MATERIAL_CHANGED');assert.equal(observer.state().presentation,null);assert.equal(observer.state().enabled,true);assert.equal(calls,boundary==='prepare'?0:1);
 }
})

test('accepted speech history survives silence, pause, navigation and bubble dismissal without another request',async()=>{
 const f=fixture();f.observer.setOwner(1);await f.observer.start({textLimit:6,visionLimit:0,intensity:'NORMAL',auto:false});await f.observer.observeNow();
 assert.equal(f.observer.state().speeches[0].text,'我有个小话题');
 f.observer.dismissSpeech();assert.equal(f.observer.state().speechVisible,false);assert.equal(f.observer.state().speeches.length,1);
 f.respond(data=>({...data,parsed:{action:'SILENT',text:null},reason:null,textRemaining:4,visionRemaining:0}));await f.observer.observeNow();
 f.observer.update({page:'GALLERY',pictureIds:[],rects:[]});f.observer.pause();assert.equal(f.observer.state().speeches.length,1);assert.equal(f.calls.observe,2);
 f.observer.setOwner(2);assert.deepEqual(f.observer.state().speeches,[]);
})
test('failed, empty and explicitly undispatched output never becomes a speech quote',async()=>{
 const f=fixture();f.observer.setOwner(1);
 for(const response of [{parsed:null,reason:'EMPTY_OUTPUT',modelDispatched:true},{parsed:{action:'SPEAK',text:'not-sent'},reason:null,modelDispatched:false},{parsed:{action:'SPEAK',text:'failed-partial'},reason:'MODEL_FAILED',modelDispatched:true}]){
  f.respond(data=>({...data,...response,textRemaining:2,visionRemaining:0}));await f.observer.start({textLimit:3,visionLimit:0,intensity:'LOW',auto:false});await f.observer.observeNow();assert.deepEqual(f.observer.state().speeches,[]);
 }
})
test('clear and account switches prevent delayed speech from reviving recent history',async()=>{
 for(const boundary of ['clear','owner']){const f=fixture();let resolve;f.respond(()=>new Promise(done=>{resolve=done}));f.observer.setOwner(1);await f.observer.start({textLimit:6,visionLimit:0,intensity:'NORMAL',auto:false});const pending=f.observer.observeNow();for(let i=0;i<15;i++)await Promise.resolve();if(boundary==='clear')f.observer.clear();else f.observer.setOwner(2);resolve({parsed:{action:'SPEAK',text:'late-secret'},textRemaining:5});await pending;assert.deepEqual(f.observer.state().speeches,[]);}
})

test('B1 pause invalidates delayed status read without leaving controls stuck loading',async()=>{
 const f=fixture();f.observer.setOwner(1);await f.observer.start({textLimit:6,visionLimit:0,intensity:'LOW',auto:false});let resolve;f.api.status=()=>new Promise(done=>{resolve=done});const pending=f.observer.refreshStatus();assert.equal(f.observer.state().statusPhase,'loading');f.observer.pause();resolve({enabled:true,textReceiver:{provider:'stale'}});await pending;assert.notEqual(f.observer.state().statusPhase,'loading');assert.equal(f.observer.state().status.textReceiver.provider,'FIXTURE');f.api.status=async()=>({enabled:true,textReceiver:{provider:'fresh'}});await f.observer.refreshStatus();assert.equal(f.observer.state().statusPhase,'ready');assert.equal(f.observer.state().status.textReceiver.provider,'fresh');assert.equal(f.calls.observe,0);
})
test('B3 actual closed-or-expired protocol is shown honestly and does not retry or lose accepted speech',async()=>{
 for(const boundary of ['prepare','observe']){const f=fixture();f.observer.setOwner(1);await f.observer.start({textLimit:6,visionLimit:0,intensity:'LOW',auto:false});await f.observer.observeNow();f.api[boundary]=async()=>{throw new Error('SESSION_CLOSED_OR_EXPIRED')};await f.observer.observeNow();assert.equal(f.observer.state().reason,'SESSION_CLOSED_OR_EXPIRED');assert.equal(f.observer.state().enabled,false);assert.equal(f.observer.state().speeches.length,1);await f.advance(900000);assert.equal(f.calls.open,1);}
})
test('B3 locally expired authorization prevents prepare and rejects late speech while preserving prior speech',async()=>{
 const f=fixture({expiresAt:new Date(100).toISOString()});f.observer.setOwner(1);await f.observer.start({textLimit:6,visionLimit:0,intensity:'LOW',auto:false});await f.observer.observeNow();let resolve;f.respond(data=>new Promise(done=>{resolve=()=>done({...data,parsed:{action:'SPEAK',text:'expired-late'},textRemaining:4,visionRemaining:0})}));const pending=f.observer.observeNow();for(let i=0;i<15;i++)await Promise.resolve();await f.advance(100);resolve();await pending;assert.equal(f.observer.state().reason,'SESSION_EXPIRED');assert.equal(f.observer.state().speeches.length,1);const prepares=f.calls.prepare;await f.observer.observeNow();assert.equal(f.calls.prepare,prepares);assert.equal(f.calls.open,1);
})
test('speech history is bounded and only validated nonblank bound replies can enter it',async()=>{
 const f=fixture();f.observer.setOwner(1);await f.observer.start({textLimit:500,visionLimit:0,intensity:'NORMAL',auto:false});for(let i=0;i<51;i++)await f.observer.observeNow();assert.equal(f.observer.state().speeches.length,50);assert.equal(f.observer.state().speeches.at(-1).id,'round-1-2');f.respond(data=>({...data,parsed:{action:'SPEAK',text:'   '},textRemaining:5,visionRemaining:0}));await f.observer.observeNow();assert.equal(f.observer.state().speeches.length,50);f.respond(data=>({...data,snapshotId:'wrong-owner-snapshot',parsed:{action:'SPEAK',text:'unbound'},textRemaining:5,visionRemaining:0}));await f.observer.observeNow();assert.equal(f.observer.state().reason,'OBSERVATION_FAILED');assert.equal(f.observer.state().speeches.length,50);assert.equal(f.observer.state().speeches.some(row=>row.text==='unbound'),false);
})
test('authorized opening across unknown-page navigation keeps the grant but never observes that page',async()=>{
 for(const boundary of ['status','open']){let page='COMPANION',release,opens=0,closes=0;const api={status:()=>boundary==='status'?new Promise(done=>{release=()=>done({enabled:true,textReceiver:{token:'fixture'}})}):Promise.resolve({enabled:true,textReceiver:{token:'fixture'}}),open:async()=>{opens++;return boundary==='status'?{sessionId:'late-session',textRemaining:6,visionRemaining:0}:new Promise(done=>{release=()=>done({sessionId:'late-session',textRemaining:6,visionRemaining:0})})},close:async()=>{closes++}};const observer=createCompanionObserver({api,collect:()=>({page,pictureIds:[],rects:[]})});observer.setOwner(1);const pending=observer.start({textLimit:6,visionLimit:0,intensity:'LOW',auto:false});for(let i=0;i<15;i++)await Promise.resolve();page='UNKNOWN';observer.update({page,pictureIds:[],rects:[]});release();await pending;assert.equal(observer.state().enabled,true);assert.equal(observer.state().phase,'page-skipped');assert.equal(opens,1);assert.equal(closes,0);observer.pause();assert.equal(closes,1);}
})
