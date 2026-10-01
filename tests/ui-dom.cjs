const assert=require('node:assert/strict');
const fs=require('node:fs');
const {JSDOM}=require('jsdom');
const base='app/src/main/assets/';
const tick=()=>new Promise(r=>setTimeout(r,5));
function setup({saved,handler}={}){
 const dom=new JSDOM(fs.readFileSync(base+'index.html','utf8'),{runScripts:'dangerously',url:'https://arca.local/'});
 const w=dom.window;w.TextEncoder=TextEncoder;
 const errors=[];w.addEventListener('error',e=>errors.push(e.message));
 if(saved)w.localStorage.setItem('arca-save',saved);
 w.eval(fs.readFileSync(base+'core.js','utf8'));
 const calls=[];
 if(handler)w.AndroidBridge={call(id,method,args){args=JSON.parse(args);calls.push({method,args});Promise.resolve().then(()=>handler(method,args)).then(data=>w.NativeCallbacks.receive(id,{ok:true,data}),e=>w.NativeCallbacks.receive(id,{ok:false,error:e.message}));}};
 w.eval(fs.readFileSync(base+'ui.js','utf8'));
 const buttons=()=>[...w.document.querySelectorAll('button')];
 const find=t=>buttons().find(b=>b.textContent===t||b.getAttribute('aria-label')===t);
 const click=async t=>{const b=find(t);assert.ok(b,'button missing: '+t);b.click();await tick();};
 const fill=(id,value)=>{const n=w.document.getElementById(id);assert.ok(n,'field missing '+id);n.value=value;n.dispatchEvent(new w.Event('input'));};
 return {dom,w,errors,calls,find,click,fill};
}
function campaign(mode='demo'){const d=setup();const c=d.w.GameCore.newCampaign({name:'보존된 모험',job:'검사',world:'판타지',mode});d.dom.window.close();return JSON.stringify({version:1,activeId:c.id,campaigns:[c]});}
async function demo(){let d=setup();await tick();await d.click('새로운 모험 시작');d.fill('hero-name','오준');await d.click('연습 모드');await d.click('모험 시작');d.fill('action','경비와 대화한다');await d.click('행동 보내기');assert.match(d.w.document.body.textContent,/경비 아린/);await d.click('가방');assert.match(d.w.document.body.textContent,/회복 물약/);const saved=d.w.localStorage.getItem('arca-save');d.dom.window.close();d=setup({saved});await tick();await d.click('동료');assert.match(d.w.document.body.textContent,/경비 아린/);await d.click('모험');d.fill('action','<img src=x onerror="window.injected=true">');await d.click('행동 보내기');assert.equal(d.w.injected,undefined);assert.equal(d.w.document.querySelectorAll('.message.user img').length,0);assert.match([...d.w.document.querySelectorAll('.message.user')].at(-1).textContent,/<img/);await d.click('설정 열기');d.fill('api-key','sk-ui-test-not-real');await d.click('설정 저장');assert.equal(d.w.document.getElementById('api-key').value,'');assert.ok(!JSON.stringify(d.w.localStorage).includes('sk-ui-test'));assert.deepEqual(d.errors,[]);d.dom.window.close();}
async function configFailure(){const saved=campaign();const d=setup({handler:(m,a)=>{if(m==='configGet')throw Error('config unavailable');if(m==='load')return saved;if(m==='save')throw Error('unexpected save');}});try{await tick();await tick();assert.ok(d.calls.some(x=>x.method==='load'),'must load campaigns even when configGet fails');assert.match(d.w.document.body.textContent,/보존된 모험/);assert.equal(d.calls.filter(x=>x.method==='save').length,0);}finally{d.dom.window.close();}}
async function loadFailure(){const d=setup({handler:(m,a)=>{if(m==='configGet')return {hasKey:false,model:'test'};if(m==='load')throw Error('disk unreadable');if(m==='save')return true;}});try{await tick();await tick();await d.click('새로운 모험 시작');if(d.w.document.getElementById('hero-name')){await d.click('모험 시작');}assert.equal(d.calls.filter(x=>x.method==='save').length,0,'creating must not overwrite unread save');assert.match(d.w.document.body.textContent,/불러|기록|저장/);}finally{d.dom.window.close();}}
async function pendingRetry(){const saved=campaign('ai');let failSave=true;const d=setup({handler:(m,a)=>{if(m==='configGet')return {hasKey:true,model:'test'};if(m==='load')return saved;if(m==='save'){if(failSave){failSave=false;throw Error('disk full');}return true;}if(m==='request')return {output_text:JSON.stringify({narration:'받은 AI 응답',summary:'이어진 이야기',choices:['쉬기'],hpDelta:0,mpDelta:0,xpDelta:0,goldDelta:0,inventoryChanges:[],rewardId:'',location:'광장',quest:'조사',companions:[]})};}});try{await tick();await tick();d.fill('action','주위를 본다');await d.click('행동 보내기');await tick();assert.ok(d.find('받은 응답 다시 저장'),'received response retry shown');await d.click('모험 목록');assert.ok(d.find('받은 응답 다시 저장'),'navigation must keep retry reachable');failSave=false;await d.click('받은 응답 다시 저장');assert.equal(d.calls.filter(x=>x.method==='request').length,1,'retry must not request AI again');assert.match(d.w.document.body.textContent,/받은 AI 응답/);const write=d.calls.filter(x=>x.method==='save').at(-1);assert.equal(JSON.parse(write.args.data).campaigns[0].turnCount,1);}finally{d.dom.window.close();}}
(async()=>{let failures=0;for(const [name,test] of [['demo UI integration',demo],['config failure preserves campaign load',configFailure],['load failure blocks overwrite',loadFailure],['pending response remains retryable without new API',pendingRetry]]){try{await test();console.log('PASS',name);}catch(e){failures++;console.error('FAIL',name,e.message);}}process.exitCode=failures?1:0;})();
