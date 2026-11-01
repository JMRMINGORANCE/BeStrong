const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();
for(const [f,w,h] of [['p1',940,940],['p2',940,940],['p3',940,940],['p4',940,940],['p5',1100,1060]]){const p=await b.newPage({viewport:{width:w,height:h},deviceScaleFactor:2});await p.goto('file://'+__dirname+'/'+f+'.html');await p.screenshot({path:__dirname+'/../'+f+'.png',fullPage:true});}
await b.close()})();
