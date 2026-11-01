%%HEAD%%
<style>
.tl{display:grid;grid-template-columns:repeat(8,1fr);gap:3px;position:relative}
.tl .w{height:44px;border-radius:5px;display:flex;flex-direction:column;justify-content:flex-end;padding:5px;font:700 10px/1 'Roboto Mono'}
.blk{display:grid;grid-template-columns:3fr 3fr 1fr 1fr;gap:3px;margin-top:6px}
.blk span{font:600 8.5px/1 'Roboto Mono';letter-spacing:.6px;text-transform:uppercase;color:var(--muted);border-top:2px solid;padding-top:5px;white-space:nowrap;overflow:hidden}
.now{position:absolute;top:-17px;font:700 8.5px/1 'Roboto Mono';color:#ff6a5f;letter-spacing:1px;transform:translateX(-50%)}
.now:after{content:"";position:absolute;left:50%;top:11px;margin-left:-4px;border:4px solid transparent;border-top-color:#ff6a5f}
.chart{display:flex;align-items:flex-end;gap:10px;height:110px;padding:0 4px;margin-top:12px;border-bottom:1px solid #2a2a2e}
.chart .c{flex:1;position:relative;display:flex;align-items:flex-end;justify-content:center;height:100%}
.chart .pl{position:absolute;bottom:0;width:100%;border:1.5px dashed #55565b;border-bottom:none;border-radius:4px 4px 0 0}
.chart .dn{width:62%;border-radius:3px 3px 0 0;position:relative}
.xl{display:flex;gap:10px;padding:6px 4px 0}.xl span{flex:1;text-align:center;font:600 9px 'Roboto Mono';color:var(--muted)}
.ses div{display:grid;grid-template-columns:30px 1fr auto;gap:10px;align-items:center;padding:9px 0;border-top:1px solid #222226;font-size:13.5px}
.ses div:first-child{border-top:none}
.ses .d{font:700 10px 'Roboto Mono';color:var(--muted)}
.st{font:700 10px 'Roboto Mono';padding:4px 7px;border-radius:5px}
.fold{width:800px;height:470px;border-radius:34px;padding:9px;background:#0b0b0c;box-shadow:0 0 0 1.5px #3a3a3e,0 40px 80px -20px rgba(0,0,0,.55)}
.fold .scr{border-radius:26px;display:grid;grid-template-columns:64px 1fr}
.rail{background:#0e0e10;border-right:1px solid #1f1f23;display:flex;flex-direction:column;align-items:center;gap:26px;padding-top:34px;color:#66676b;font-size:21px}
.rail .on{color:var(--accent)}
.grid{display:grid;grid-template-columns:128px repeat(8,1fr);gap:3px}
.grid .h{font:700 9px 'Roboto Mono';color:var(--muted);text-align:center;padding:4px 0;letter-spacing:.6px;white-space:nowrap}
.grid .n{font:700 13px/1.15 'Titillium Web';display:flex;flex-direction:column;justify-content:center;padding-left:2px}
.grid .n small{font:500 9.5px 'Roboto Mono';color:var(--muted)}
.grid .cell{height:40px;border-radius:5px;display:grid;place-items:center;font:700 11px 'Roboto Mono'}
</style></head><body><div class="sheet">
<aside class="meta"><div class="num">Propuesta 06</div><h1>Periodización visual</h1>
<p class="lead">El programa y el mesociclo como una línea temporal por bloques —acumulación, intensificación y descarga— con la semana actual marcada y la carga prevista frente a la hecha. Se ve de un vistazo cuándo toca descargar y si vas por delante o por detrás.</p>
<h4>Pantalla</h4><ul><li><b>Planificar</b> › nueva vista «Programa»; en el Fold desplegado, rejilla de intensidad</li></ul>
<h4>Reutiliza</h4><ul><li>Mesociclos con descarga automática y detector de fatiga</li><li>Programas 5/3/1 BBB y GZCLP con su training max</li><li>Volumen semanal hecho, del historial</li></ul>
<h4>Interacción</h4><ul><li>Toca una semana para ver sus sesiones y cargas</li><li>Mantén pulsada la descarga para adelantarla o retrasarla</li><li>Cambia entre volumen e intensidad en el Fold</li></ul></aside>
<div class="dev"><div class="phone"><div class="scr">%%SB%%
<div class="pad">
 <div class="hdr"><div><div class="lbl">Programa · ciclo 3</div><h2>5/3/1 BBB</h2></div><div class="ib"><i data-i="more"></i></div></div>
 <div class="panel cf" style="padding:28px 12px 12px">
  <div class="tl" id="tl"></div>
  <div class="blk"><span style="border-color:#FFD400">Acumulación</span><span style="border-color:#E10600">Intensific.</span><span style="border-color:#00D26A">Desc.</span><span style="border-color:#B14CFF">Test</span></div>
 </div>
 <div class="panel" style="padding:12px 14px;margin-top:10px">
  <div style="display:flex;justify-content:space-between;align-items:flex-end"><div><div class="lbl">Semana 5 de 8</div><div style="font:900 italic 20px/1 'Titillium Web';margin-top:7px;text-transform:uppercase">Carga semanal</div></div>
  <div style="text-align:right"><div class="mono" style="font-weight:700;font-size:18px">14,2 t</div><div class="mono" style="font-size:10px;color:var(--grn);margin-top:3px">▲ 4 % vs plan</div></div></div>
  <div class="chart" id="ch"></div><div class="xl"><span>S1</span><span>S2</span><span>S3</span><span>S4</span><span>S5</span><span>S6</span><span>S7</span></div>
  <div style="display:flex;gap:14px;margin-top:8px;font:600 9px 'Roboto Mono';color:var(--muted)"><span style="display:flex;gap:5px;align-items:center"><i style="width:10px;height:8px;border:1.5px dashed #55565b;display:block"></i>PREVISTA</span><span style="display:flex;gap:5px;align-items:center"><i style="width:10px;height:8px;background:var(--accent);display:block;border-radius:2px"></i>HECHA</span></div>
 </div>
 <div class="panel" style="padding:11px 14px;margin-top:10px;background:linear-gradient(100deg,#00261a,#141416 65%);box-shadow:inset 0 0 0 1px #0c4a30">
  <div style="display:flex;align-items:center;gap:12px"><div class="ib" style="color:var(--grn)"><i data-i="down"></i></div>
  <div style="flex:1"><div style="font:700 15px/1.2 'Titillium Web'">Descarga en 2 semanas</div><div class="mono" style="font-size:10.5px;color:#aeb0b4;margin-top:2px">S7 · 14–20 dic · cargas al 90 %</div></div><i data-i="chev" style="color:#77787c"></i></div>
 </div>
 <div class="panel ses" style="padding:2px 14px;margin-top:10px">
  <div><span class="d">LUN</span><span>Press militar 5/3/1</span><span class="st" style="background:#0b3b22;color:var(--grn)">HECHO</span></div>
  <div><span class="d">MIÉ</span><span>Peso muerto 5/3/1</span><span class="st" style="background:#3a0400;color:#ff6a5f">HOY</span></div>
  <div><span class="d">VIE</span><span>Press banca 5/3/1</span><span class="st" style="background:#1c1c1f;color:var(--muted)">VIE</span></div>
 </div>
</div>
<div class="nav"><div><i data-i="home"></i>Inicio</div><div><i data-i="hist"></i>Historial</div><div class="on"><i data-i="cal"></i>Plan</div><div><i data-i="chart"></i>Progreso</div><div><i data-i="user"></i>Perfil</div></div>
</div></div><div class="cap">Móvil · programa</div></div>
<div class="dev"><div class="fold"><div class="scr"><div class="rail"><i data-i="home"></i><i data-i="hist"></i><i data-i="cal" class="on"></i><i data-i="chart"></i><i data-i="user"></i></div>
<div style="padding:24px 26px">
 <div class="hdr" style="margin-top:0;margin-bottom:16px"><div><div class="lbl">Intensidad por ejercicio · % del training max</div><h2>Ciclo 3 · semanas 1–8</h2></div><div style="display:flex;gap:6px"><span class="chip">Volumen</span><span class="chip on">Intensidad</span></div></div>
 <div class="grid" id="gr"></div>
 <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px;margin-top:18px">
  <div class="panel" style="padding:12px 14px"><div class="lbl">Training max</div><div class="mono" style="font-weight:700;font-size:19px;margin-top:9px">Banca 97,5</div><div class="mono" style="font-size:10.5px;color:var(--grn);margin-top:5px">▲ +2,5 kg al cerrar ciclo</div></div>
  <div class="panel" style="padding:12px 14px"><div class="lbl">Fatiga</div><div class="mono" style="font-weight:700;font-size:19px;margin-top:9px">RPE 8,1</div><div class="mono" style="font-size:10.5px;color:var(--yel);margin-top:5px">● Subiendo 3 sesiones</div></div>
  <div class="panel" style="padding:12px 14px"><div class="lbl">Cumplimiento</div><div class="mono" style="font-weight:700;font-size:19px;margin-top:9px">13 / 14</div><div class="mono" style="font-size:10.5px;color:var(--muted);margin-top:5px">sesiones del ciclo</div></div>
 </div>
</div></div></div><div class="cap">Fold desplegado · rejilla de intensidad</div></div>
</div>
<script>
icons();
const cols=['#FFD400','#FFD400','#FFD400','#E10600','#E10600','#E10600','#00D26A','#B14CFF'];
const tl=document.getElementById('tl');
cols.forEach((c,i)=>{const d=document.createElement('div');d.className='w';const done=i<4,cur=i===4;
d.style.background=done?c:(cur?'#2a0200':'#18181b');d.style.boxShadow=cur?'inset 0 0 0 2px '+c:(done?'none':'inset 0 0 0 1px #2e2e33');
d.style.opacity=done?.85:1;d.style.color=done?(c==='#E10600'?'#fff':'#141100'):(cur?'#fff':'#9a9a9e');d.textContent='S'+(i+1);tl.appendChild(d)});
const now=document.createElement('div');now.className='now';now.textContent='HOY';tl.appendChild(now);
requestAnimationFrame(()=>{const w=tl.children[4];now.style.left=(w.offsetLeft+w.offsetWidth/2)+'px'});
const pl=[9.2,10.1,11.0,12.2,13.6,14.6,8.4],dn=[9.4,10.0,11.3,12.6,14.2,null,null],mx=15.5;
const ch=document.getElementById('ch');pl.forEach((p,i)=>{const c=document.createElement('div');c.className='c';
c.innerHTML='<div class="pl" style="height:'+(p/mx*100)+'%"></div>'+(dn[i]?'<div class="dn" style="height:'+(dn[i]/mx*100)+'%;background:'+(i===4?'var(--accent)':'#7a1410')+'"></div>':'');ch.appendChild(c)});
const gr=document.getElementById('gr');let h='<div class="h"></div>';for(let i=1;i<=8;i++)h+='<div class="h" style="'+(i===5?'color:#ff6a5f':'')+'">S'+i+(i===5?' · HOY':'')+'</div>';
const rows=[['Press banca','TM 97,5 kg',[65,70,75,85,90,95,60,100]],['Sentadilla','TM 140 kg',[65,70,75,85,90,95,60,100]],['Peso muerto','TM 170 kg',[65,70,75,85,90,95,60,100]],['Press militar','TM 60 kg',[65,70,75,85,90,95,60,100]],['BBB 5×10','accesorio',[50,50,55,55,60,60,40,'—']]];
function col(v,i){if(v==='—')return'#18181b';if(i===6)return'#0b3b22';const t=(v-40)/60;return 'rgba(225,'+Math.round(60-50*t)+','+Math.round(20-20*t)+','+(0.18+0.75*t)+')'}
rows.forEach(([n,s,v])=>{h+='<div class="n">'+n+'<small>'+s+'</small></div>';v.forEach((x,i)=>{h+='<div class="cell" style="background:'+col(x,i)+';'+(i===4?'box-shadow:inset 0 0 0 2px #ff6a5f;':'')+(i<4?'opacity:.55;':'')+'color:'+(i===6?'#00D26A':'#fff')+'">'+x+(x==='—'?'':'%')+'</div>'})});
gr.innerHTML=h;
</script></body></html>
