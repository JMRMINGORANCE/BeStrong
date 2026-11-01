const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();
const list=process.argv.slice(2).length?process.argv.slice(2):['c06','c07','c08','c09','c10'];
for(const f of list){const p=await b.newPage({viewport:{width:1920,height:1000},deviceScaleFactor:2});await p.goto('file://'+__dirname+'/'+f+'.html');await p.evaluate(()=>document.fonts.ready);await p.waitForTimeout(600);
const ok=await p.evaluate(()=>[...document.fonts].filter(f=>f.status==='loaded').map(f=>f.family).join(','));console.log(f,ok);
await p.screenshot({path:__dirname+'/../'+f+'.png',fullPage:true});}
await b.close()})();
