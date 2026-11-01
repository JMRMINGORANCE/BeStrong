const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();
const list=process.argv.slice(2).length?process.argv.slice(2):['r1','r2','r3','r4','r5'];
for(const f of list){const p=await b.newPage({viewport:{width:1400,height:1000},deviceScaleFactor:2});await p.goto('file://'+__dirname+'/'+f+'.html',{waitUntil:'networkidle'});await p.evaluate(()=>document.fonts.ready);await p.screenshot({path:__dirname+'/../'+f+'.png',fullPage:true});}
await b.close()})();
