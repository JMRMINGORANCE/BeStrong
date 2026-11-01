const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();
const list=process.argv.slice(2).length?process.argv.slice(2):['c01','c02','c03','c04','c05'];
for(const f of list){const p=await b.newPage({viewport:{width:1720,height:900},deviceScaleFactor:2});
await p.goto('file://'+__dirname+'/'+f+'.html',{waitUntil:'networkidle'});await p.evaluate(()=>document.fonts.ready);await p.waitForTimeout(300);
const fonts=await p.evaluate(()=>[...document.fonts].filter(x=>x.status==='loaded').map(x=>x.family+' '+x.weight).join(', '));
console.log(f,'fonts:',fonts.slice(0,300));
await p.screenshot({path:__dirname+'/../'+f+'.png',fullPage:true});}
await b.close()})();
