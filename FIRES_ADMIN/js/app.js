const $=s=>document.querySelector(s);let tab="over";
const rn=id=>U.find(r=>r.id===id).fullName;
const mins=i=>Math.round((new Date(i.dateResolved.replace(" ","T"))-new Date(i.submittedAt.replace(" ","T")))/6e4);
function toast(t){const e=$("#toast");e.textContent=t;e.style.display="block";setTimeout(()=>e.style.display="none",2200)}
function close(){$("#drawer").classList.remove("open");$("#scrim").classList.remove("open")}
function open(h){$("#drawer").innerHTML=h;$("#drawer").classList.add("open");$("#scrim").classList.add("open")}
$("#scrim").onclick=close;
document.querySelectorAll("aside button").forEach(b=>b.onclick=()=>{tab=b.dataset.t;document.querySelectorAll("aside button").forEach(x=>x.classList.toggle("on",x===b));close();render()});
function stats(){const res=I.filter(i=>i.dateResolved);return{total:I.length,active:I.filter(ACT).length,res:res.length,avg:res.length?Math.round(res.reduce((a,i)=>a+mins(i),0)/res.length):0}}
function render(){({over,map,resp,rec,rep})[tab]()}
function over(){const s=stats();$("#main").innerHTML=`<h1>Overview</h1><p class="lead">Incident activity and responder accounts at a glance.</p>
<div class="grid g4"><div class="card stat hot"><b>${s.active}</b><span>Active incidents</span></div><div class="card stat"><b>${I.filter(i=>i.isDuplicate||i.isSuspicious).length}</b><span>Flagged for review</span></div><div class="card stat"><b>${s.avg} min</b><span>Average time to resolve</span></div><div class="card stat"><b>${RS().length}</b><span>Responder accounts</span></div></div>
<div class="grid g2" style="margin-top:14px"><div class="card"><h3>Latest incidents</h3><div class="wrap"><table><tbody>${I.slice(0,4).map(i=>`<tr data-id="${i.id}"><td><b>${i.id}</b><br><span class="muted">${i.loc}</span></td><td><span class="pill ${i.severity}">${i.severity}</span></td><td><span class="pill ${i.status}">${i.status}</span> ${fl(i)}</td></tr>`).join("")}</tbody></table></div></div>
<div class="card"><h3>Responder workload</h3>${RS().map(r=>{const n=I.filter(i=>i.res.some(x=>x[0]===r.id)).length;return`<div class="row"><span class="l">${r.fullName.split(" ")[0]}</span><span class="t"><i style="width:${n*25}%;background:var(--red)"></i></span><b>${n}</b></div>`}).join("")}<p class="muted" style="margin-bottom:0">Incidents assigned per responder.</p></div></div>`;
document.querySelectorAll("tbody tr").forEach(t=>t.onclick=()=>detail(t.dataset.id))}
function resp(){$("#main").innerHTML=`<h1>User accounts</h1><p class="lead">Create responder accounts for B-FLARE, BDRRMO and BFP, and manage citizen accounts.</p>
<div class="tools"><input id="q" placeholder="Search by name or email" aria-label="Search accounts"><select id="ro" aria-label="Filter by role"><option value="">All roles</option><option value="responder">Responders</option><option value="citizen">Citizens</option></select><button class="btn" id="add">Add responder</button></div>
<div class="card wrap"><table><thead><tr><th>Name</th><th>Contact</th><th>Unit / address</th><th>Role</th><th>Assigned</th><th></th></tr></thead><tbody id="tb"></tbody></table></div>`;
const draw=()=>{const q=$("#q").value.toLowerCase(),ro=$("#ro").value;$("#tb").innerHTML=U.filter(r=>(r.fullName+r.email).toLowerCase().includes(q)&&(!ro||r.role===ro)).map(r=>`<tr style="cursor:default"><td><b>${r.fullName}</b><br><span class="muted">${r.email}</span></td><td>${r.contactNo}</td><td>${r.address}</td><td>${r.role}</td><td>${r.role==="responder"?I.filter(i=>i.res.some(x=>x[0]===r.id)).length:I.filter(i=>i.reporter===r.id).length+" reports"}</td><td><button class="btn ghost" data-rm="${r.id}">Remove</button></td></tr>`).join("")||`<tr><td colspan=6 class="muted">No accounts match. Clear the search or add a new account.</td></tr>`;
document.querySelectorAll("[data-rm]").forEach(b=>b.onclick=()=>{const i=U.findIndex(r=>r.id===b.dataset.rm);const n=U.splice(i,1)[0].fullName;draw();toast(n+" removed")})};
$("#q").oninput=draw;$("#ro").onchange=draw;draw();
$("#add").onclick=()=>{open(`<h2>Add responder</h2><p class="muted">The account is created with the responder role.</p><div class="form">
<label>Full name<input id="f1"></label><label>Email<input id="f2" type="email"></label><label>Contact number<input id="f3"></label><label>Unit / address (B-FLARE, BDRRMO, BFP)<input id="f4"></label><label>Temporary password<input id="f5" type="password"></label>
<button class="btn" id="save" style="text-align:center">Create account</button><button class="btn ghost" id="cx" style="text-align:center">Cancel</button></div>`);
$("#cx").onclick=close;$("#save").onclick=()=>{if(!$("#f1").value||!$("#f2").value){toast("Enter a name and email");return}
U.push({id:"u"+(U.length+9),fullName:$("#f1").value,email:$("#f2").value,contactNo:$("#f3").value,address:$("#f4").value,role:"responder"});close();draw();toast("Responder account created")}}}
function rec(){$("#main").innerHTML=`<h1>Incident records</h1><p class="lead">Search incident records by date, status and severity. Flagged reports are marked for review.</p>
<div class="tools"><input id="q" placeholder="Search description or location" aria-label="Search records"><select id="sv"><option value="">All severities</option><option>High</option><option>Medium</option><option>Low</option></select><select id="st"><option value="">All statuses</option><option>Reported</option><option>Verified</option><option>Ongoing</option><option>Resolved</option><option>False</option><option>Duplicate</option></select><select id="fg"><option value="">All reports</option><option value="1">Flagged only</option></select><input type="date" id="d1" aria-label="From date" style="flex:0 0 auto;min-width:0"><input type="date" id="d2" aria-label="To date" style="flex:0 0 auto;min-width:0"></div>
<div class="card wrap"><table><thead><tr><th>ID</th><th>Incident</th><th>Severity</th><th>Status</th><th>Submitted</th><th>Resolved</th></tr></thead><tbody id="tb"></tbody></table></div>`;
const draw=()=>{const q=$("#q").value.toLowerCase(),v=$("#sv").value,s=$("#st").value,f=$("#fg").value,d1=$("#d1").value,d2=$("#d2").value;
$("#tb").innerHTML=I.filter(i=>(i.description+i.loc).toLowerCase().includes(q)&&(!v||i.severity===v)&&(!s||i.status===s)&&(!f||i.isDuplicate||i.isSuspicious)&&(!d1||i.submittedAt.slice(0,10)>=d1)&&(!d2||i.submittedAt.slice(0,10)<=d2)).map(i=>`<tr tabindex="0" data-id="${i.id}"><td><b>${i.id}</b>${i.photo?`<br><span class="muted">Photo</span>`:""}</td><td>${i.description}<br><span class="muted">${i.loc}</span></td><td><span class="pill ${i.severity}">${i.severity}</span></td><td><span class="pill ${i.status}">${i.status}</span><br>${fl(i)}</td><td>${i.submittedAt}</td><td>${i.dateResolved||"-"}</td></tr>`).join("")||`<tr><td colspan=6 class="muted">No incidents match these filters.</td></tr>`;
document.querySelectorAll("tbody tr[data-id]").forEach(t=>{t.onclick=()=>detail(t.dataset.id);t.onkeydown=e=>{if(e.key==="Enter")detail(t.dataset.id)}})};
["q","sv","st","fg","d1","d2"].forEach(x=>$("#"+x).oninput=draw);draw()}
function detail(id){const i=I.find(x=>x.id===id);open(`<h2>${i.id}</h2><span class="pill ${i.severity}">${i.severity}</span> <span class="pill ${i.status}">${i.status}</span>
<div class="kv"><small>Photo from reporter</small>${photoBlock(i)}</div><div class="kv"><small>Description</small>${i.description}</div><div class="kv"><small>Location</small>${i.loc}</div><div class="kv"><small>Reported by</small>${C[i.reporter]}</div>
<div class="kv"><small>Submitted</small>${i.submittedAt}</div><div class="kv"><small>Date resolved</small>${i.dateResolved?i.dateResolved+" ("+mins(i)+" min)":"Not yet resolved"}</div>
<div class="kv"><small>Review flags</small>${fl(i)||"None"}</div><div class="kv"><small>Remarks</small>${i.remarks||"None yet"}</div>
<div class="kv"><small>Assigned responders</small>${i.res.map(r=>`${rn(r[0])} - ${r[1]}`).join("<br>")}</div>
<div class="kv"><small>Messages</small>${i.msgs.map(m=>`<div class="msg"><b>${m[0]}</b><br>${m[1]}</div>`).join("")||"No messages"}</div><button class="btn ghost" id="cx">Close</button>`);$("#cx").onclick=close}
function rep(){const s=stats(),days=["Sep 24","Sep 25","Sep 26","Sep 27","Sep 28","Sep 29","Sep 30"],n=[24,25,26,27,28,29,30].map(d=>I.filter(i=>+i.submittedAt.slice(8,10)===d).length),mx=Math.max(...n);
const sev=["High","Medium","Low"].map(k=>[k,I.filter(i=>i.severity===k).length]);const col={High:"var(--red)",Medium:"var(--warn)",Low:"var(--ok)"};
$("#main").innerHTML=`<h1>Reports</h1><p class="lead">Summary computed from the stored incident records, 24 to 30 September 2026.</p>
<div class="grid g4"><div class="card stat"><b>${s.total}</b><span>Total incidents</span></div><div class="card stat"><b>${s.res}</b><span>Resolved</span></div><div class="card stat"><b>${s.avg} min</b><span>Average time to resolve</span></div><div class="card stat"><b>${I.filter(i=>i.severity==="High").length}</b><span>High severity</span></div></div>
<div class="grid g2" style="margin-top:14px"><div class="card"><h3>Incidents per day</h3><div class="bar">${n.map((v,k)=>`<div>${v}<i style="height:${v?v/mx*100:2}%"></i>${days[k].slice(4)}</div>`).join("")}</div></div>
<div class="card"><h3>By severity</h3>${sev.map(([k,v])=>`<div class="row"><span class="l">${k}</span><span class="t"><i style="width:${v/I.length*100}%;background:${col[k]}"></i></span><b>${v}</b></div>`).join("")}<button class="btn" id="pr" style="margin-top:12px;display:inline-block">Print report</button></div></div>`;
$("#pr").onclick=()=>window.print()}
const GEO={"INC-1042":[14.5195,121.0530,"GPS"],"INC-1041":[14.5350,121.0710,"GPS"],"INC-1038":[14.5050,121.0420,"Manual"],"INC-1035":[14.5480,121.0570,"GPS"],"INC-1029":[14.5290,121.0350,"Manual"],"INC-1024":[14.5140,121.0640,"GPS"],"INC-1043":[14.5380,121.0400,"GPS"],"INC-1040":[14.5198,121.0533,"Manual"]};
const ST=[["B-FLARE post",14.5230,121.0480],["BDRRMO office",14.5400,121.0650],["BFP Taguig",14.5080,121.0560]];
const XY=(la,lo)=>[(lo-121.02)/0.07*800,(14.56-la)/0.07*520];
let selId="INC-1042",mf="act";
function map(){
const list=I.filter(i=>mf==="act"?ACT(i):mf==="all"||i.status===mf),sc={High:"#e53935",Medium:"#ef8f00",Low:"#2e7d32"};
const pins=list.map(i=>{const [x,y]=XY(GEO[i.id][0],GEO[i.id][1]),c=!ACT(i)?"#8d8482":sc[i.severity],on=i.id===selId;
return`<g class="pin" tabindex="0" role="button" aria-label="${i.id} ${i.loc}" data-id="${i.id}">${!ACT(i)?"":`<circle class="pulse" cx="${x}" cy="${y}" r="16" fill="${c}"/>`}<circle class="c" cx="${x}" cy="${y}" r="${on?12:9}" fill="${c}" stroke="#fff" stroke-width="3"/><text x="${x}" y="${y-16}" text-anchor="middle" font-size="12" font-weight="700" fill="var(--ink)" stroke="var(--card)" stroke-width="3" paint-order="stroke">${i.id.slice(4)}</text></g>`}).join("");
const sts=ST.map(([n,la,lo])=>{const [x,y]=XY(la,lo);return`<g><rect x="${x-8}" y="${y-8}" width="16" height="16" rx="3" fill="#231f1f" stroke="#fff" stroke-width="2"/><text x="${x}" y="${y+4}" text-anchor="middle" font-size="10" font-weight="800" fill="#fff">F</text><text x="${x+13}" y="${y+4}" font-size="11" fill="var(--mute)" stroke="var(--card)" stroke-width="3" paint-order="stroke">${n}</text></g>`}).join("");
$("#main").innerHTML=`<h1>Live map</h1><p class="lead">Where each emergency is, plotted from the latitude and longitude in the incident report.</p>
<div class="tools"><select id="fl" aria-label="Filter map"><option value="act">Active (reported, verified, ongoing)</option><option value="all">All incidents</option><option>Resolved</option><option>Duplicate</option><option>False</option></select></div>
<div class="mapwrap"><div><div class="card mapbox"><svg class="map" viewBox="0 0 800 520" role="img" aria-label="Schematic map of incidents around Taguig">
<rect width="800" height="520" fill="var(--line)" opacity=".35"/>
<path d="M-10 60 C150 110 260 40 420 120 S640 200 810 150" fill="none" stroke="#9cc6e0" stroke-width="26" opacity=".8"/>
<rect x="470" y="230" width="140" height="90" rx="10" fill="#8fc48f" opacity=".45"/><rect x="60" y="330" width="120" height="80" rx="10" fill="#8fc48f" opacity=".45"/>
<g fill="none" stroke="var(--card)" stroke-linecap="round"><path d="M0 300 H800" stroke-width="14"/><path d="M380 0 V520" stroke-width="14"/><path d="M0 470 C200 400 500 470 800 380" stroke-width="10"/><path d="M180 0 L260 520" stroke-width="8"/><path d="M560 0 L640 520" stroke-width="8"/><path d="M0 190 H800" stroke-width="7"/><path d="M0 400 H800" stroke-width="6"/></g>
<text x="20" y="500" font-size="11" fill="var(--mute)">Schematic view. Live version uses Google Maps.</text>${sts}${pins}</svg></div>
<div class="legend"><span><i style="background:#e53935"></i>High</span><span><i style="background:#ef8f00"></i>Medium</span><span><i style="background:#2e7d32"></i>Low</span><span><i style="background:#8d8482"></i>Not active</span><span><i style="background:#231f1f;border-radius:2px"></i>Response base</span></div></div>
<div id="side"></div></div>`;
$("#fl").value=mf;$("#fl").onchange=e=>{mf=e.target.value;map()};
const side=()=>{const i=I.find(x=>x.id===selId)||list[0];const g=i?GEO[i.id]:0;
$("#side").innerHTML=(i?`<div class="card" style="margin-bottom:12px"><h3>${i.id} <span class="pill ${i.severity}">${i.severity}</span> <span class="pill ${i.status}">${i.status}</span> ${fl(i)}</h3>${i.photo?photoBlock(i)+"<br>":""}<div class="kv" style="margin-top:0"><small>Location</small>${i.loc}</div><div class="kv"><small>Coordinates (${g[2]})</small>${g[0].toFixed(4)}, ${g[1].toFixed(4)}</div><div class="kv"><small>Responders</small>${i.res.map(r=>rn(r[0])+" - "+r[1]).join("<br>")}</div><button class="btn" id="dt">Open full record</button></div>`:"")+
list.map(x=>`<div class="li ${x.id===selId?"sel":""}" data-id="${x.id}"><b>${x.id}</b> <span class="pill ${x.severity}">${x.severity}</span><br><span class="muted">${x.loc}</span></div>`).join("")||`<div class="card muted">No incidents match this filter.</div>`;
if(i)$("#dt").onclick=()=>detail(i.id);
document.querySelectorAll(".li").forEach(e=>e.onclick=()=>pick(e.dataset.id))};
const pick=id=>{selId=id;map()};
document.querySelectorAll(".pin").forEach(e=>{e.onclick=()=>pick(e.dataset.id);e.onkeydown=k=>{if(k.key==="Enter")pick(e.dataset.id)}});
side()}
document.addEventListener("click",e=>{const t=e.target.closest(".ph");if(t){const i=I.find(x=>x.id===t.dataset.id);$("#lb").innerHTML=`<img src="${ph(i)}" alt="Enlarged photo for ${i.id}">`;$("#lb").classList.add("open")}else if(e.target.closest("#lb"))$("#lb").classList.remove("open")});
document.addEventListener("keydown",e=>{if(e.key==="Escape")$("#lb").classList.remove("open")});
$("#today").textContent=new Date().toLocaleDateString("en-PH",{weekday:"long",year:"numeric",month:"long",day:"numeric"});
const nm=sessionStorage.getItem("fires_admin_name")||"Administrator";$("#nm").textContent=nm;$("#av").textContent=nm[0];
$("#out").onclick=()=>{sessionStorage.removeItem("fires_admin");sessionStorage.removeItem("fires_admin_name");location.href="login.html"};
render();

