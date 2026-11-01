const P={
home:'<path d="M3.5 10.2 12 3.5l8.5 6.7V20a.5.5 0 0 1-.5.5h-5.2v-6h-5.6v6H4a.5.5 0 0 1-.5-.5z"/>',
hist:'<path d="M3.5 12a8.5 8.5 0 1 0 2.6-6.1"/><path d="M3.5 4.5v4h4"/><path d="M12 7.5V12l3 2"/>',
dumb:'<path d="M6.5 6.5v11M3.5 9v6M17.5 6.5v11M20.5 9v6M6.5 12h11"/>',
chart:'<path d="M4 20.5h16"/><path d="M6.5 17v-5M11 17V7M15.5 17v-8M20 17V4"/>',
user:'<circle cx="12" cy="8" r="4"/><path d="M4.5 20.5c.8-3.8 3.9-5.5 7.5-5.5s6.7 1.7 7.5 5.5"/>',
play:'<path d="M7.5 4.8v14.4a.6.6 0 0 0 .9.5l11.4-7.2a.6.6 0 0 0 0-1L8.4 4.3a.6.6 0 0 0-.9.5z" fill="currentColor"/>',
watch:'<rect x="6" y="6.5" width="12" height="11" rx="3.5"/><path d="M8.5 6.5 9 3h6l.5 3.5M8.5 17.5 9 21h6l.5-3.5"/>',
heart:'<path d="M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z" fill="currentColor" stroke="none"/>',
check:'<path d="m5 12.5 4.5 4.5L19 7.5"/>',
plus:'<path d="M12 5v14M5 12h14"/>',
minus:'<path d="M5 12h14"/>',
timer:'<circle cx="12" cy="13.5" r="7.5"/><path d="M12 9.5v4l2.5 1.5M9.5 2.5h5M19 6l1.5-1.5"/>',
flame:'<path d="M12 21c-4 0-6.5-2.6-6.5-6.2 0-3.6 2.7-5.6 3.8-8.8.4 1.8 1.6 3 2.8 3.6.2-2.6 1.3-4.6 3.1-6.1-.2 3.3 3.3 5.6 3.3 10.6 0 4-2.6 6.9-6.5 6.9z"/>',
pause:'<path d="M8 5v14M16 5v14"/>',
more:'<circle cx="12" cy="5.5" r="1.3" fill="currentColor"/><circle cx="12" cy="12" r="1.3" fill="currentColor"/><circle cx="12" cy="18.5" r="1.3" fill="currentColor"/>',
skip:'<path d="M5.5 5v14l10-7z"/><path d="M19 5v14"/>',
back:'<path d="M15 5l-7 7 7 7"/>',
bolt:'<path d="M13 2.5 5 13.5h6l-1 8 8-11h-6z"/>',
target:'<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.5"/><circle cx="12" cy="12" r="1" fill="currentColor"/>',
trophy:'<path d="M7.5 4h9v5a4.5 4.5 0 0 1-9 0z"/><path d="M7.5 6H4.5a3 3 0 0 0 3 4M16.5 6h3a3 3 0 0 1-3 4M12 13.5V17M8.5 20.5h7M9.5 17h5v3.5h-5z"/>',
wifi:'<path d="M2.5 9a14 14 0 0 1 19 0M5.5 12.3a9.5 9.5 0 0 1 13 0M8.6 15.5a5 5 0 0 1 6.8 0"/><circle cx="12" cy="19" r="1.2" fill="currentColor"/>',
bat:'<rect x="2.5" y="7.5" width="17" height="9" rx="2.5"/><rect x="4.5" y="9.5" width="11" height="5" rx="1" fill="currentColor" stroke="none"/><path d="M21.5 10.5v3"/>',
sig:'<path d="M4 19v-3M9 19v-6M14 19V9M19 19V5" />',
cal:'<rect x="3.5" y="5" width="17" height="15.5" rx="3"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
swap:'<path d="M7 4 3.5 7.5 7 11M3.5 7.5h13M17 13l3.5 3.5L17 20M20.5 16.5h-13"/>',
note:'<path d="M5 4.5h10l4 4V19.5H5z"/><path d="M8.5 11h7M8.5 15h5"/>'
};
function icons(sw){document.querySelectorAll('i[data-i]').forEach(e=>{const w=e.dataset.w||sw||1.8;e.classList.add('ic');e.innerHTML=`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round">${P[e.dataset.i]}</svg>`})}
/* marcas de una esfera: n marcas entre r1 y r2 (mayores cada k, de r1m) */
function ticks(el,{n=60,cx=96,cy=96,r=90,len=6,major=5,mlen=12,color='#fff',mcolor,w=1,mw=2,from=0,to=360,opacity=1}={}){
 let s='';for(let i=0;i<n;i++){const a=(from+(to-from)*i/(to===360?n:n-1)-90)*Math.PI/180;const M=major&&i%major===0;const L=M?mlen:len;
 const x1=cx+Math.cos(a)*r,y1=cy+Math.sin(a)*r,x2=cx+Math.cos(a)*(r-L),y2=cy+Math.sin(a)*(r-L);
 s+=`<line x1="${x1.toFixed(2)}" y1="${y1.toFixed(2)}" x2="${x2.toFixed(2)}" y2="${y2.toFixed(2)}" stroke="${M?(mcolor||color):color}" stroke-width="${M?mw:w}" stroke-linecap="round" opacity="${opacity}"/>`}
 el.insertAdjacentHTML('beforeend',s)}
/* forma «cookie» de Material 3 Expressive */
function cookie(r=50,lobes=9,amp=.08,cx=50,cy=50){let d='';for(let i=0;i<=360;i+=2){const a=i*Math.PI/180;const rr=r*(1-amp+amp*Math.cos(lobes*a));const x=cx+rr*Math.cos(a),y=cy+rr*Math.sin(a);d+=(i?'L':'M')+x.toFixed(2)+' '+y.toFixed(2)}return d+'Z'}
function arc(cx,cy,r,a0,a1){const p=a=>[cx+r*Math.cos((a-90)*Math.PI/180),cy+r*Math.sin((a-90)*Math.PI/180)];const[x0,y0]=p(a0),[x1,y1]=p(a1);return `M${x0.toFixed(2)} ${y0.toFixed(2)}A${r} ${r} 0 ${a1-a0>180?1:0} 1 ${x1.toFixed(2)} ${y1.toFixed(2)}`}
Object.assign(P,{
flag:'<path d="M5 21V4M5 4.5h12l-2.5 4 2.5 4H5"/>',
drop:'<path d="M12 3.5s-6 6.6-6 10.7a6 6 0 0 0 12 0c0-4.1-6-10.7-6-10.7z"/>',
star:'<path d="m12 3.5 2.6 5.4 5.9.8-4.3 4.1 1 5.8-5.2-2.8-5.2 2.8 1-5.8-4.3-4.1 5.9-.8z"/>',
compass:'<circle cx="12" cy="12" r="8.5"/><path d="m15.5 8.5-2 5-5 2 2-5z"/>',
gauge:'<path d="M4 16a8 8 0 1 1 16 0"/><path d="M12 16l4-5"/>'
});
