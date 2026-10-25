import fs from 'node:fs';
import path from 'node:path';
const config = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const targets = await (await fetch(`http://127.0.0.1:${config.debugPort}/json/list`)).json();
const target = targets.find(t=>t.type==='page');
if (!target) throw new Error('No Chrome page target');
const socket = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve,reject)=>{ socket.addEventListener('open',resolve,{once:true});socket.addEventListener('error',reject,{once:true}); });
let sequence = 0;
const pending = new Map();
socket.addEventListener('message',event=>{
  const value=JSON.parse(event.data), request=pending.get(value.id);
  if (!request) return;
  pending.delete(value.id); clearTimeout(request.timer);
  value.error ? request.reject(new Error(JSON.stringify(value.error))) : request.resolve(value.result);
});
function send(method,params={}) {
  return new Promise((resolve,reject)=>{
    const id=++sequence;
    const timer=setTimeout(()=>{pending.delete(id);reject(new Error(`CDP timeout: ${method}`));},180000);
    pending.set(id,{resolve,reject,timer});socket.send(JSON.stringify({id,method,params}));
  });
}
async function evaluate(expression, awaitPromise=false) {
  const result=await send('Runtime.evaluate',{expression,awaitPromise,returnByValue:true});
  if(result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}
try {
  await send('Page.enable'); await send('Runtime.enable');
  await send('Page.navigate',{url:config.pageUrl});
  let ready=false;
  for(let i=0;i<100;i++) {
    try { ready=await evaluate('window.benchmarkReady === true'); } catch {}
    if(ready) break;
    await new Promise(r=>setTimeout(r,100));
  }
  if(!ready) throw new Error('Browser harness did not load');
  const modes=['trie_only','trie_fallback','trie_cache_fallback','api_only','cache_api'];
  for(let repeat=1;repeat<=config.repeats;repeat++) {
    const order=modes.slice((repeat-1)%modes.length).concat(modes.slice(0,(repeat-1)%modes.length));
    for(const mode of order) {
      await send('HeapProfiler.collectGarbage');
      const token=`r${repeat}-${mode}`;
      console.log(`Browser: ${mode}, repeat ${repeat}`);
      fs.writeFileSync(path.join(config.output,'browser-progress.json'),JSON.stringify({mode,repeat}));
      const point=await evaluate(`window.runAutocompletePoint(${JSON.stringify({...config,mode,repeat,token})})`,true);
      fs.writeFileSync(path.join(config.output,`${mode}-r${repeat}.json`),JSON.stringify(point,null,2));
    }
  }
  const screenshot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});
  fs.writeFileSync(path.join(config.output,'browser-results.png'),Buffer.from(screenshot.data,'base64'));
  const version=await send('Browser.getVersion');
  fs.writeFileSync(path.join(config.output,'browser-version.json'),JSON.stringify(version,null,2));
} finally { socket.close(); }
