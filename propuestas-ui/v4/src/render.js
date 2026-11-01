const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();
const list=process.argv.slice(2).length?process.argv.slice(2):['i01','i02','i03','i04','i05'];
for(const f of list){const p=await b.newPage({viewport:{width:1720,height:900},deviceScaleFactor:2});
await p.goto('file://'+__dirname+'/'+f+'.html',{waitUntil:'networkidle'});await p.evaluate(()=>document.fonts.ready);await p.waitForTimeout(300);
await p.screenshot({path:__dirname+'/../'+f+'.png',fullPage:true});console.log(f)}
await b.close()})();
