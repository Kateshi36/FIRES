import {
  U, I, RS, ACT, fl, photoBlock,
  loadUsers, loadIncidentsOnce, listenIncidents, incidentsAreFresh, loadActiveAssignments, loadDetail, loadRange, INCIDENT_LIMIT, RANGE_LIMIT, loadAssignmentsFor, logActivity, notifyAssigned, removeAssignment, listenActivity,
  createResponderAuth, saveResponderProfile, sendResponderReset, changeUserRole, verifyIncident,
  newAssignmentId, saveAssignment, refreshAssignments
} from "./data.js";
import { esc, fmtPH } from "./mapping.js";
import {
  FIRE_TYPES, typeLabel, fireName, CHIPS, isClosedOut, filterRecords, chipCounts, applyChip, sortRecords, pageSlice, responderText, ageText,
  medianResolve, monthCounts, weeklyMedians, typeCounts, purokCounts, DAYS, HOUR_LABELS, hourHeat, busiestSlot, withinHour,
  ACTIVITY_CHIPS, kindInfo, incidentEvents, mergeEvents, filterEvents, dayHeading, clockText, activityDoc, newReports, unassignedTooLong
} from "./viewRules.js";
import { UNITS, validateResponder } from "./validators.js";
import { rowAction } from "./roleRules.js";
import { canVerify, reviewBlockHtml, reviewDialog, doneText, redrawAfterReview, drawerAfterReview } from "./verifyRules.js";
import { saveFailText, REVIEW_STALE_TEXT, REVIEW_BLOCKED_TEXT, guarded } from "./saveMessages.js";
import {
  assignButtonHtml, assignDialogHtml, availableResponders, unitFor, canAssign, MSG as ASSIGN_MSG,
  createAssignSession, submitAssignment, resultView, ERROR_IDS, SAVE_LABEL, assignedCount, redrawAfterAssign
} from "./assignRules.js";
import { confirmHtml } from "./dialogHtml.js";
import {
  defaultRange, lastDays, rangeBounds, rangeError, fmtDay, bucketCounts, severityCounts, resolvedCount,
  resolveStats, fmtMin, reportCsv, csvFileName, todayPH, daysInRange, toCsv
} from "./reportRules.js";
import { auth, db, onAuthStateChanged, signOut, doc, getDoc } from "./firebase.js";
import { AI_ENDPOINT } from "./config.js";
import { aiPanelHtml, buildPayload, requestSuggestion } from "./aiSuggest.js";
const $=s=>document.querySelector(s);let tab="over";
const mins=i=>Math.round((i.resolvedMs-i.submittedMs)/6e4);
let openId=null;
let toastTimer=null;
function toast(t,ms=2200){const e=$("#toast");e.textContent=t;e.style.display="block";clearTimeout(toastTimer);toastTimer=setTimeout(()=>e.style.display="none",ms)}
function close(){openId=null;$("#drawer").classList.remove("open");$("#scrim").classList.remove("open")}
function open(h){$("#drawer").innerHTML=h;$("#drawer").classList.add("open");$("#scrim").classList.add("open")}
$("#scrim").onclick=close;
document.querySelectorAll("aside nav button").forEach(b=>b.onclick=()=>{tab=b.dataset.t;document.querySelectorAll("aside nav button").forEach(x=>x.classList.toggle("on",x===b));close();render()});
function stats(){const done=I.filter(i=>i.dateResolved&&i.submittedMs!=null);return{total:I.length,active:I.filter(ACT).length,res:I.filter(i=>i.rawStatus==="resolved").length,avg:done.length?Math.round(done.reduce((a,i)=>a+mins(i),0)/done.length):0}}
const screens={over,map,resp,rec,rep,act};
let unsub=null,usersAt=0,renderSeq=0;
const live=()=>tab==="over"||tab==="map"||tab==="act";
const stopLive=()=>{if(unsub){unsub();unsub=null}if(unsubAct){unsubAct();unsubAct=null}};
// What the responder's phone shows when they are assigned or taken off a report (max 200 characters).
const assignedText=i=>`${i.code} \u00B7 ${i.loc}. ${typeLabel(i.fireType)}${i.trapped?", possibly trapped":""}. Tap to open and start your response.`.slice(0,200);
const removedText=i=>`An administrator took you off ${i.code}. No action needed.`;
const errMsg=e=>e&&e.code==="permission-denied"?"Your account is not allowed to read this data. Check that your role is admin and that firestore.rules is deployed."
    :e&&(e.code==="unavailable"||e.code==="network-request-failed")?"No internet connection. Check your network and try again."
    :e&&e.code==="failed-precondition"?"This query needs a Firestore index. Open the browser console (F12) and use the link in the error to create it."
    :"Could not load the data. Please try again.";
function showError(e,retry=render){
  console.error(e);
  const m=errMsg(e);
  $("#main").innerHTML=`<h1>Something went wrong</h1><div class="card"><p>${m}</p><button class="btn" id="retry" style="display:inline-block">Try again</button></div>`;
  $("#retry").onclick=()=>retry();
}
// Draw the live screens, then fill in responder names for the few active incidents and redraw.
function drawLive(){if(!live())return;checkNew();screens[tab]();loadActiveAssignments().then(did=>{if(did&&live())screens[tab]()})}
async function render(){
  const my=++renderSeq;
  if(tab!=="map")destroyMap();
  if(tab!=="rec")recSrc=null;
  try{
    if(live()){
      if(tab==="act"&&!unsubAct)unsubAct=listenActivity(list=>{actEvents=list;if(tab==="act")act()},e=>console.warn("activity read failed",e));
      if(tab!=="act"&&unsubAct){unsubAct();unsubAct=null}
      if(!unsub){
        if(!incidentsAreFresh())$("#main").innerHTML='<p class="muted">Loading...</p>';
        else drawLive();
        // Listener: new reports appear without refreshing. Stopped when the tab changes (below).
        unsub=listenIncidents(drawLive,e=>{if(auth.currentUser)showError(e)});
      }else drawLive();
      return;
    }
    stopLive();
    if(tab==="rep"){await repShow(my);return}   // Reports load their own date range (W12)
    if(!incidentsAreFresh()||tab==="resp"&&Date.now()-usersAt>6e4)$("#main").innerHTML='<p class="muted">Loading...</p>';
    if(!incidentsAreFresh())await loadIncidentsOnce();
    if(tab==="resp"&&Date.now()-usersAt>6e4){await loadUsers();usersAt=Date.now()}
    if(my!==renderSeq)return;
    screens[tab]();
    if(tab==="resp")loadActiveAssignments().then(did=>{if(did&&my===renderSeq)screens[tab]()});
  }catch(e){if(my===renderSeq)showError(e)}
}
function greeting(){const h=new Date().getHours(),part=h<12?"Good morning":h<18?"Good afternoon":"Good evening",n=($("#nm")&&$("#nm").textContent||"").split(" ")[0];return n?part+", "+n:part}
// ---- shared page pieces (admin redesign, round 2) ----
const todayText=()=>new Date().toLocaleDateString("en-PH",{weekday:"long",month:"long",day:"numeric"});
const head=(t,sub,act="")=>`<header class="ph2"><div><h1>${t}</h1><span class="hsub">${sub}</span></div><div class="hact">${act}</div></header>`;
const initials=n=>esc(String(n||"?").trim().split(/\s+/).slice(0,2).map(w=>w[0]).join("").toUpperCase()||"?");
const ICON_SEARCH='<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>';
function saveCsvFile(text,name){
  // The byte order mark makes Excel read the file as UTF-8.
  const url=URL.createObjectURL(new Blob(["﻿"+text],{type:"text/csv;charset=utf-8"}));
  const a=document.createElement("a");a.href=url;a.download=name;
  document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
const goTab=t=>{const b=document.querySelector(`aside nav button[data-t="${t}"]`);if(b)b.click()};
const connHtml=()=>`<span class="conn${navigator.onLine?"":" off"}" id="conn"><i></i><span>${navigator.onLine?"Connected":"Offline"}</span></span>`;
function syncConn(){const e=$("#conn");if(!e)return;e.classList.toggle("off",!navigator.onLine);e.lastChild.textContent=navigator.onLine?"Connected":"Offline"}
addEventListener("online",syncConn);addEventListener("offline",syncConn);
const names=r=>r[0][2]+(r.length>1?` +${r.length-1}`:"");   // assignment names are already HTML-escaped (mapAssignment)

// ---- Overview ----
function over(){
const now=Date.now(),mc=monthCounts(I,now),med=medianResolve(I.filter(x=>x.rawStatus==="resolved")),
awaiting=I.filter(i=>i.active&&i.rawStatus==="reported").length,busy=new Set();
I.filter(ACT).forEach(i=>i.res.forEach(r=>busy.add(r[0])));
const diff=mc.thisMonth-mc.lastMonth,rank=i=>i.rawStatus==="reported"?0:i.rawStatus==="verified"?1:2;
const attn=I.filter(ACT).sort((a,b)=>rank(a)-rank(b)||(b.submittedMs||0)-(a.submittedMs||0)).slice(0,5);
const wk=weeklyMedians(I,now),mx=Math.max(1,...wk.map(w=>w.value||0)),lastWk=wk[wk.length-1];
$("#main").innerHTML=head(greeting(),`${todayText()} · live`,`${connHtml()}<button class="btn" id="exp">Export records</button>`)+
`<section class="kpis">
<div class="card kpi"><span class="kl">Active incidents</span><span class="kb acc">${I.filter(ACT).length}</span><span class="kn">${awaiting} awaiting verification</span></div>
<div class="card kpi"><span class="kl">Reports this month</span><span class="kb">${mc.thisMonth}</span><span class="kn">${diff===0?"Same as last month":Math.abs(diff)+(diff>0?" more":" fewer")+" than last month"}</span></div>
<div class="card kpi"><span class="kl">Median time to resolve</span><span class="kb">${med==null?"-":Math.round(med)}${med==null?"":'<span class="u"> min</span>'}</span><span class="kn">${I.filter(x=>x.rawStatus==="resolved").length} resolved in the loaded reports</span></div>
<div class="card kpi"><span class="kl">Responders</span><span class="kb">${RS().length}</span><span class="kn">${busy.size} currently assigned</span></div></section>
<section class="split"><div class="card panel grow2"><div class="phead"><h2>Needs attention</h2><button type="button" class="lnkbtn" id="orec">Open records</button></div>
<div class="wrap"><table class="mt"><thead><tr><th>Location</th><th>Severity</th><th>Status</th><th>Assigned</th><th>Age</th></tr></thead><tbody>${attn.map(i=>`<tr tabindex="0" data-id="${i.id}"><td><b>${i.loc}</b><br><span class="muted">${i.code}</span></td><td><span class="pill ${i.severity}">${i.severity}</span></td><td><span class="pill ${i.status}">${i.status}</span> ${fl(i)}</td><td>${i.res.length?names(i.res):`<span class="lnk">${i.rawStatus==="reported"?"Review":"Assign"}</span>`}</td><td class="muted">${ageText(i.submittedMs,now)}</td></tr>`).join("")||'<tr><td colspan="5" class="muted">No open incidents right now.</td></tr>'}</tbody></table></div></div>
<div class="card panel grow1"><div class="phead"><h2>Time to resolve, last 6 weeks</h2></div>
<div class="wkchart" role="img" aria-label="Median minutes to resolve per week, last six weeks">${wk.map((w,k)=>`<div class="wkbar${k===wk.length-1?" cur":""}${w.value==null?" none":""}" style="height:${w.value==null?2:Math.max(6,w.value/mx*100)}%" title="Week ${k+1}: ${w.value==null?"nothing resolved":Math.round(w.value)+" min (median)"}"></div>`).join("")}</div>
<div class="wklab"><span>Wk 1</span><span>Wk 6</span></div>
<p class="muted small">Median minutes from report to resolved, per week. ${lastWk&&lastWk.value!=null?`Latest week: ${Math.round(lastWk.value)} min.`:"Nothing was resolved in the latest week."}</p></div></section>`;
$("#exp").onclick=()=>{if(!I.length){toast("There are no records to export yet.");return}saveCsvFile(reportCsv(I),`fires-records-${todayPH()}.csv`)};
$("#orec").onclick=()=>goTab("rec");
document.querySelectorAll("tbody tr[data-id]").forEach(t=>{t.onclick=()=>detail(t.dataset.id);t.onkeydown=e=>{if(e.key==="Enter")detail(t.dataset.id)}})}

// ---- User accounts ----
const us={q:"",role:"all",sel:""};
const ROLE_CHIPS=[["all","All"],["responder","Responders"],["citizen","Citizens"],["admin","Admins"]];
const roleLabel=r=>r==="responder"?"Responder":r==="admin"?"Admin":"Citizen";
function resp(){
const cnt=r=>U.filter(u=>r==="all"||u.role===r).length;
$("#main").innerHTML=head("User accounts",`${U.length} account${U.length===1?"":"s"} · ${RS().length} responder${RS().length===1?"":"s"} · new responders get an e-mail to set their own password`,`<button class="btn" id="add">Add responder</button>`)+
`<section class="bar2"><label class="search">${ICON_SEARCH}<input id="q" placeholder="Search name or email" aria-label="Search accounts" value="${esc(us.q)}"></label><div class="chips" role="group" aria-label="Filter by role">${ROLE_CHIPS.map(([v,t])=>`<button type="button" class="chip${us.role===v?" on":""}" data-r="${v}" aria-pressed="${us.role===v}">${t} · ${cnt(v)}</button>`).join("")}</div></section>
<section class="usplit"><div class="card tcard"><div class="wrap"><table class="ut"><thead><tr><th>Person</th><th>Role</th><th>Status</th><th>Contact</th><th>Activity</th><th></th></tr></thead><tbody id="tb"></tbody></table></div></div><div class="card sidep" id="upanel" aria-live="polite"></div></section>`;
$("#q").oninput=()=>{us.q=$("#q").value;drawUsers()};
document.querySelectorAll("[data-r]").forEach(b=>b.onclick=()=>{us.role=b.dataset.r;document.querySelectorAll("[data-r]").forEach(c=>{const on=c.dataset.r===us.role;c.classList.toggle("on",on);c.setAttribute("aria-pressed",on)});drawUsers()});
drawUsers();
$("#add").onclick=()=>addResponder()}
function drawUsers(){
const me=auth.currentUser&&auth.currentUser.uid,q=esc(us.q.toLowerCase());
const busy=new Set();I.filter(ACT).forEach(i=>i.res.forEach(r=>busy.add(r[0])));
const list=U.filter(r=>(r.fullName+" "+r.email).toLowerCase().includes(q)&&(us.role==="all"||r.role===us.role));
const sel=list.find(r=>r.id===us.sel)||list.find(r=>r.role==="responder")||list[0]||null;
us.sel=sel?sel.id:"";
const status=r=>r.role==="responder"?(busy.has(r.id)?'<span class="pill Medium">Responding</span>':'<span class="pill Low">Available</span>'):'<span class="pill Low">Active</span>';
const activity=r=>r.role==="responder"?assignedCount(I,r.id)+" active":I.filter(i=>i.reporterId===r.id).length+" report"+(I.filter(i=>i.reporterId===r.id).length===1?"":"s");
$("#tb").innerHTML=list.map(r=>`<tr data-uid="${r.id}" class="${sel&&sel.id===r.id?"selrow":""}" tabindex="0"><td><div class="pcell"><span class="av">${initials(r.name)}</span><div><b>${r.fullName}</b>${r.id===me?' <span class="muted">(you)</span>':""}<br><span class="muted small">${r.email}</span></div></div></td><td><span class="pill ${r.role==="responder"?"Verified":"Reported"}">${roleLabel(r.role)}</span></td><td>${status(r)}</td><td>${r.contactNo||"-"}</td><td>${activity(r)}</td><td class="tr"><button type="button" class="btn ghost sm" data-m="${r.id}">Manage ▾</button></td></tr>`).join("")||'<tr><td colspan="6" class="muted">No accounts match. Clear the search or add a new account.</td></tr>';
const pick=id=>{us.sel=id;drawUsers()};
document.querySelectorAll("#tb tr[data-uid]").forEach(t=>{t.onclick=()=>pick(t.dataset.uid);t.onkeydown=e=>{if(e.key==="Enter")pick(t.dataset.uid)}});
drawUserPanel(sel,me)}
function drawUserPanel(r,me){
const p=$("#upanel");if(!p)return;
if(!r){p.innerHTML='<p class="muted">Select an account to see its details.</p>';return}
const a=rowAction(r,me),mine=I.filter(i=>r.role==="responder"?i.res.some(x=>x[0]===r.id):i.reporterId===r.id).slice(0,3);
p.innerHTML=`<div class="pcell"><span class="av big">${initials(r.name)}</span><div><b class="pname">${r.fullName}</b><br><span class="muted">${roleLabel(r.role)}${r.address?" · "+r.address:""}</span></div></div>
<div class="statbox"><div><span>${r.role==="responder"?"Active calls":"Reports"}</span><b>${r.role==="responder"?assignedCount(I,r.id):I.filter(i=>i.reporterId===r.id).length}</b></div><div><span>Contact</span><b>${r.contactNo||"-"}</b></div><div><span>Email</span><b class="brk">${r.email}</b></div>${r.purok?`<div><span>Purok</span><b>${r.purok}</b></div>`:""}</div>
<span class="eyebrow">Recent</span><div class="recent">${mine.map(i=>`<span>${i.code} · ${i.loc} · ${i.status}</span>`).join("")||'<span class="muted">Nothing in the loaded reports.</span>'}</div>
<hr><button type="button" class="btn ghost" id="preset">Send password reset email</button>
${a.enabled?`<button type="button" class="btn dng" data-act="${a.kind}" data-id="${r.id}">${a.label}</button>`:`<button type="button" class="btn dng" disabled title="${a.reason}" aria-label="${a.label}. ${a.reason}">${a.label}</button><span class="muted small">${a.note}</span>`}
<span class="muted small">Role changes are saved in the activity log.</span>`;
document.querySelectorAll("#upanel [data-act]").forEach(b=>b.onclick=()=>askRole(b.dataset.id,b.dataset.act,b));
$("#preset").onclick=async()=>{const b=$("#preset");b.disabled=true;try{await sendResponderReset(plain(r.email));toast("Password reset e-mail sent to "+plain(r.email),4000)}catch(e){console.error(e);toast("Could not send the e-mail. Try again in a moment.",4000)}finally{if($("#preset"))$("#preset").disabled=false}}}

// ---- Remove responder access / Make responder (W11) ----
// Only users/{uid}.role changes (responder <-> citizen). The sign-in account stays, because a browser
// cannot delete other people's accounts; delete it in the Firebase Console if needed.
let dlgReturn=null,dlgBusy=false;
function openDlg(html,from){dlgReturn=from||document.activeElement;$("#dlg").innerHTML=`<div class="box">${html}</div>`;$("#dlg").classList.add("open")}
function closeDlg(){const d=$("#dlg");d.classList.remove("open");d.innerHTML="";dlgBusy=false;if(dlgReturn&&document.contains(dlgReturn))dlgReturn.focus();dlgReturn=null}
// Backdrop click and Escape cancel, but not while the change is being saved.
$("#dlg").addEventListener("click",e=>{if(e.target.id==="dlg"&&!dlgBusy)closeDlg()});
document.addEventListener("keydown",e=>{if(e.key==="Escape"&&$("#dlg").classList.contains("open")&&!dlgBusy)closeDlg()});
// The confirmation dialog, shared by W11 (make / remove responder) and W13 (False report / Duplicate).
// Two buttons; Cancel is focused, so Enter never confirms by accident.
//   title, confirm   plain text (escaped); body is HTML the caller has made safe
//   from             the element to give the focus back to when the dialog closes
//   run()            what the confirm button does; it returns the result (it may throw)
//   done(result)     called AFTER the dialog has closed, with that result
//   fail(e)          the message to show under the buttons if run() throws; default: saveFail
// While run() is working both buttons are disabled and Escape / backdrop do nothing. If it throws, the
// dialog stays open with the message, and the confirm button says "Try again".
function confirmDlg({title,body,confirm,from,run,done,fail=saveFail}){
  openDlg(confirmHtml({title,body,confirm}),from);
  $("#dno").focus();
  $("#dno").onclick=closeDlg;
  $("#dyes").onclick=async()=>{
    const y=$("#dyes"),n=$("#dno");
    dlgBusy=true;y.disabled=true;n.disabled=true;y.textContent="Saving...";setTxt("#dg","");
    let result;
    try{result=await run()}
    catch(e){
      dlgBusy=false;setTxt("#dg",fail(e));
      if($("#dyes")){y.disabled=false;n.disabled=false;y.textContent="Try again"}
      return;
    }
    closeDlg();
    try{await done(result)}catch(e){console.error(e)}
  };
}
// Redraw the list but keep the search text and role filter the admin typed.
function redrawUsers(){if(tab!=="resp")return;screens.resp()}
// The wording lives in saveMessages.js, so W11 and W13 fail in the same way (and it can be tested).
function saveFail(e){console.error(e);return saveFailText(e)}
function askRole(id,kind,from){
  const r=U.find(u=>u.id===id),rm=kind==="remove";
  if(!r||(rm?r.role!=="responder":r.role!=="citizen")||id===(auth.currentUser&&auth.currentUser.uid))return;
  // r.fullName and r.email were HTML-escaped when the user was loaded (mapUser), so they go in as they are.
  confirmDlg({
    title:rm?"Remove responder access?":"Make this person a responder?",
    body:`<p><b>${r.fullName}</b><br><span class="muted">${r.email}</span></p>
${rm?`<p>This person becomes a <b>citizen</b>. They can no longer use the responder screens, see other people's reports, or assign themselves. Their past assignments and records are kept.</p>
<p class="muted">Their sign-in account stays, so they can still log in as a citizen. To delete the account itself, use the Firebase Console.</p>`
:`<p>This person becomes a <b>responder</b> after they next sign in. They will see every report, can change its status, and can assign themselves.</p>
<p class="muted">Only do this for people from B-FLARE, BDRRMO or BFP.</p>`}`,
    confirm:rm?"Remove access":"Make responder",
    from,
    run:()=>changeUserRole(id,kind),
    done:async res=>{
      if(res.ok){logAct("role",rm?`removed responder access for ${r.name}`:`made ${r.name} a responder`);redrawUsers();toast(rm?"Responder access removed. The sign-in account was kept.":"They are now a responder.",4000);return}
      // Someone else changed this account meanwhile (or it was deleted): show the current state instead.
      try{await loadUsers();usersAt=Date.now()}catch(e){console.warn("user reload failed",e)}
      redrawUsers();toast("This account was changed by someone else. The list is up to date now.",5000);
    }
  });
}
// ---- Add responder (W10) ----
// 1) create the sign-in account (second Firebase app), 2) write users/{uid} with role = responder,
// 3) e-mail a password-setup link. If step 2 fails the uid is kept in `pendingResp` for a retry,
// because an account without a users document is treated as a citizen.
let pendingResp=null; // { uid, clean }
const setTxt=(sel,t)=>{const e=$(sel);if(e)e.textContent=t};
function addResponder(){
  if(pendingResp){retryPanel();return}
  open(`<h2>Add responder</h2><p class="muted">Creates a sign-in account with the responder role. The person gets an e-mail to set their own password.</p><div class="form">
<label>Full name<input id="f1" autocomplete="off" maxlength="100"><span class="ferr" id="e1"></span></label>
<label>Email<input id="f2" type="email" autocomplete="off"><span class="ferr" id="e2"></span></label>
<label>Contact number<input id="f3" inputmode="tel" autocomplete="off" placeholder="09171234567"><span class="ferr" id="e3"></span></label>
<label>Unit<select id="f4"><option value="">Choose a unit</option>${UNITS.map(u=>`<option>${u}</option>`).join("")}</select><span class="ferr" id="e4"></span></label>
<div class="ferr" id="eg" role="alert"></div>
<button class="btn" id="save" style="text-align:center">Create account</button><button class="btn ghost" id="cx" style="text-align:center">Cancel</button></div>`);
  $("#cx").onclick=close;$("#save").onclick=submitResponder;
}
function createFail(e){
  console.error(e);
  switch(e&&e.code){
    case "auth/email-already-in-use":return{field:"#e2",msg:"This email is already registered. If it belongs to a citizen, use Make responder on that account in the list instead."};
    case "auth/invalid-email":return{field:"#e2",msg:"Enter a valid email address, like name@example.com."};
    case "auth/network-request-failed":return{msg:"No internet connection. Check your network and try again."};
    case "auth/operation-not-allowed":return{msg:"Email/password sign-in is turned off in Firebase Authentication. Turn it on in the Console."};
    case "auth/too-many-requests":return{msg:"Too many attempts. Wait a few minutes and try again."};
    default:return{msg:"Could not create the account. Please try again."};
  }
}
// Steps 2 and 3, using the uid kept in pendingResp. Returns {retry:true} or {done:true,mail}.
async function saveResponder(){
  const{uid,clean}=pendingResp;
  try{await saveResponderProfile(uid,clean)}catch(e){console.error(e);return{retry:true}}
  pendingResp=null;
  let mail=true;
  try{await sendResponderReset(clean.email)}catch(e){console.error(e);mail=false}
  try{await loadUsers();usersAt=Date.now()}catch(e){console.warn("user reload failed",e)}
  logAct("create",`created a responder account for ${clean.fullName}`);
  return{done:true,mail,email:clean.email};
}
function finishResponder(r){
  if(r.retry){retryPanel();return}
  close();
  if(tab==="resp")screens.resp();
  toast(r.mail?`Responder created. A password e-mail was sent to ${r.email}.`
    :"Responder created, but the password e-mail could not be sent. They can use Forgot password in the app.",5000);
}
async function submitResponder(){
  const v=validateResponder({name:$("#f1").value,email:$("#f2").value,contact:$("#f3").value,unit:$("#f4").value});
  ["name","email","contact","unit"].forEach((k,n)=>setTxt("#e"+(n+1),v.errors[k]||""));
  setTxt("#eg","");
  if(!v.ok)return;
  const dup=U.find(u=>u.email.toLowerCase()===v.clean.email);
  if(dup){setTxt("#e2",dup.role==="citizen"?"This email belongs to a citizen account. Close this and use Make responder on its row instead.":"This email is already registered.");return}
  const btn=$("#save");btn.disabled=true;btn.textContent="Creating...";
  let uid;
  try{uid=await createResponderAuth(v.clean.email)}
  catch(e){
    const f=createFail(e);
    if(f.field)setTxt(f.field,f.msg);else setTxt("#eg",f.msg);
    if($("#save")){btn.disabled=false;btn.textContent="Create account"}
    return;
  }
  pendingResp={uid,clean:v.clean};
  finishResponder(await saveResponder());
}
function retryPanel(){
  const p=pendingResp;
  open(`<h2>Finish creating the account</h2><p>The sign-in account for <b>${esc(p.clean.email)}</b> was created, but its profile could not be saved. Until it is saved, this account counts as a citizen.</p>
<div class="ferr" id="eg" role="alert"></div>
<div class="form"><button class="btn" id="retry2" style="text-align:center">Retry</button><button class="btn ghost" id="cx" style="text-align:center">Close</button></div>
<p class="muted">Closing keeps this pending account until you reload the page. If you reload, create the document users/${esc(p.uid)} in the Firebase Console instead (same fields, role = responder).</p>`);
  $("#cx").onclick=close;
  $("#retry2").onclick=async()=>{
    const b=$("#retry2");b.disabled=true;b.textContent="Retrying...";
    const r=await saveResponder();
    if(r.retry){setTxt("#eg","Still could not save. Check your connection and that the Firestore rules allow admins to create users, then try again.");if($("#retry2")){b.disabled=false;b.textContent="Retry"}return}
    finishResponder(r);
  };
}
// ---- Incident records (W12) ----
// Filters (text, severity, status, flags, dates) work on what is loaded: the newest INCIDENT_LIMIT
// incidents. When the dates reach back further than that, a button loads the incidents submitted in
// that date range from Firestore (recSrc), so old history is fetched on request, not all at once.
let recSrc=null; // { list, capped, fromDay, toDay } or null
const recCovers=(src,d1,d2)=>(src.fromDay===null||(!!d1&&d1>=src.fromDay))&&(src.toDay===null||(!!d2&&d2<=src.toDay));
// ---- Incident records ----
const rs={q:"",sv:"",ty:"",d1:"",d2:"",chip:"all",sort:"sev",page:1,sel:new Set(),dates:false};
const PAGE_SIZE=10;
function rec(){
$("#main").innerHTML=head("Incident records",'<span id="rsub"></span>',`<button type="button" class="btn ghost noprint" id="rprint">Print</button><button type="button" class="btn noprint" id="rcsv">Export CSV</button>`)+
`<section class="bar2 noprint"><label class="search">${ICON_SEARCH}<input id="q" placeholder="Search address, reporter or code" aria-label="Search records" value="${esc(rs.q)}"></label>
<button type="button" class="selbtn" id="dbtn" aria-expanded="${rs.dates}" aria-controls="dpop"></button>
<select class="selbtn" id="sv" aria-label="Filter by severity"><option value="">Severity ▾</option>${["Critical","High","Medium","Low"].map(v=>`<option${rs.sv===v?" selected":""}>${v}</option>`).join("")}</select>
<select class="selbtn" id="ty" aria-label="Filter by type"><option value="">Type ▾</option>${Object.entries(FIRE_TYPES).map(([v,t])=>`<option value="${v}"${rs.ty===v?" selected":""}>${t}</option>`).join("")}</select></section>
<div class="dpop noprint" id="dpop"${rs.dates?"":" hidden"}><label>From <input type="date" id="d1" value="${rs.d1}"></label><label>To <input type="date" id="d2" value="${rs.d2}"></label><button type="button" class="btn ghost" id="dclr">Clear dates</button></div>
<p class="muted" id="hist" aria-live="polite"></p>
<section class="chips noprint" id="rchips" role="group" aria-label="Filter by status"></section>
<section class="card tcard"><div id="selbar"></div><div class="wrap"><table class="rt"><thead><tr><th class="ck"><input type="checkbox" id="selall" aria-label="Select all on this page"></th><th>Code</th><th>Location</th><th>Type</th><th><button type="button" class="thb" data-s="sev"></button></th><th>Status</th><th>Responder</th><th><button type="button" class="thb" data-s="new"></button></th></tr></thead><tbody id="tb"></tbody></table></div><div class="pager noprint" id="pager"></div></section>`;
$("#q").oninput=()=>{rs.q=$("#q").value;rs.page=1;drawRec()};
$("#sv").onchange=()=>{rs.sv=$("#sv").value;rs.page=1;drawRec()};
$("#ty").onchange=()=>{rs.ty=$("#ty").value;rs.page=1;drawRec()};
$("#d1").oninput=$("#d2").oninput=()=>{rs.d1=$("#d1").value;rs.d2=$("#d2").value;rs.page=1;drawRec()};
$("#dclr").onclick=()=>{rs.d1=rs.d2="";$("#d1").value=$("#d2").value="";rs.page=1;drawRec()};
$("#dbtn").onclick=()=>{rs.dates=!rs.dates;$("#dpop").hidden=!rs.dates;$("#dbtn").setAttribute("aria-expanded",rs.dates)};
$("#rprint").onclick=()=>window.print();
document.querySelectorAll(".thb").forEach(b=>b.onclick=()=>{rs.sort=b.dataset.s;rs.page=1;drawRec()});
$("#selall").onchange=()=>{const ids=recPage.items.map(i=>i.id);ids.forEach(id=>$("#selall").checked?rs.sel.add(id):rs.sel.delete(id));drawRec()};
drawRec()}
let recPage={items:[]},recList=[];
function drawRec(){
const q=esc(rs.q.toLowerCase()),d1=rs.d1,d2=rs.d2;
// Text is stored HTML-escaped (mapping.js), so the search text is escaped the same way before comparing.
const src=recSrc&&recCovers(recSrc,d1,d2)?recSrc.list:I;
const oldest=I.length?I[I.length-1].submittedAt.slice(0,10):"";
const need=src===I&&I.length>=INCIDENT_LIMIT&&!(d1&&d2&&d1>d2)&&((d1&&d1<oldest)||(d2&&d2<oldest));
$("#hist").innerHTML=d1&&d2&&d1>d2?"The start date is after the end date."
  :need?`Showing only the newest ${INCIDENT_LIMIT} incidents. <button type="button" class="btn ghost sm" id="hb">Load incidents for these dates</button> <span id="hm" class="ferr" style="display:inline"></span>`
  :src!==I?`Showing ${src.length} incident${src.length===1?"":"s"} loaded for these dates.${recSrc.capped?` Only the newest ${RANGE_LIMIT} are shown, so choose a shorter range.`:""}`:"";
if($("#hb"))$("#hb").onclick=async()=>{
  const b=$("#hb");b.disabled=true;b.textContent="Loading...";
  try{
    const r=await loadRange(d1?rangeBounds(d1,d1).fromMs:null,d2?rangeBounds(d2,d2).toMs:null);
    recSrc={...r,fromDay:d1||null,toDay:d2||null};drawRec();
  }catch(e){console.error(e);if($("#hb")){b.disabled=false;b.textContent="Load incidents for these dates";setTxt("#hm",errMsg(e))}}
};
const base=filterRecords(src,{q,sv:rs.sv,ty:rs.ty,d1,d2}),counts=chipCounts(base);
recList=sortRecords(applyChip(base,rs.chip),rs.sort);
const pg=recPage=pageSlice(recList,rs.page,PAGE_SIZE);rs.page=pg.page;
$("#dbtn").innerHTML=(d1||d2?`${d1||"..."} to ${d2||"..."}`:"All dates")+' <span>▾</span>';
$("#rsub").textContent=`${recList.length} report${recList.length===1?"":"s"} in this view · click a row to open the review drawer`;
$("#rchips").innerHTML=CHIPS.map(c=>`<button type="button" class="chip${rs.chip===c.id?" on":""}" data-c="${c.id}" aria-pressed="${rs.chip===c.id}">${c.label} · ${counts[c.id]}</button>`).join("");
document.querySelectorAll("#rchips .chip").forEach(b=>b.onclick=()=>{rs.chip=b.dataset.c;rs.page=1;drawRec()});
document.querySelectorAll(".thb").forEach(b=>b.textContent=b.dataset.s==="sev"?"Severity"+(rs.sort==="sev"?" ↓":""):"Reported"+(rs.sort==="new"?" ↓":""));
const n=rs.sel.size;
$("#selbar").innerHTML=n?`<div class="selbar noprint"><span>${n} selected</span><span class="selact"><button type="button" class="btn light sm" id="xsel">Export selected (CSV)</button><button type="button" class="btn light sm" id="clrsel">Clear</button></span></div>`:"";
if(n){
  $("#xsel").onclick=()=>{const all=[...I,...(recSrc?recSrc.list:[])],seen=new Set(),pick=[];all.forEach(i=>{if(rs.sel.has(i.id)&&!seen.has(i.id)){seen.add(i.id);pick.push(i)}});saveCsvFile(reportCsv(pick),`fires-selected-${todayPH()}.csv`)};
  $("#clrsel").onclick=()=>{rs.sel.clear();drawRec()};
}
$("#rcsv").onclick=()=>{if(!recList.length){toast("There are no records to export in this view.");return}saveCsvFile(reportCsv(recList),`fires-records-${todayPH()}.csv`)};
$("#selall").checked=pg.items.length>0&&pg.items.every(i=>rs.sel.has(i.id));
$("#tb").innerHTML=pg.items.map(i=>{const r=responderText(i);return`<tr tabindex="0" data-id="${i.id}"><td class="ck"><input type="checkbox" data-sel="${i.id}" aria-label="Select ${i.code}"${rs.sel.has(i.id)?" checked":""}></td><td class="muted"><b>${i.code}</b>${i.photo?`<br><span class="small">Photo</span>`:""}</td><td><b>${i.loc}</b></td><td>${typeLabel(i.fireType)}</td><td><span class="pill ${i.severity}">${i.severity}</span></td><td><span class="pill ${i.status}">${i.status}</span> ${fl(i)}</td><td class="muted">${r==null?"...":r}</td><td class="muted nw">${i.submittedAt}</td></tr>`}).join("")||`<tr><td colspan="8" class="muted">No incidents match these filters.</td></tr>`;
$("#pager").innerHTML=recList.length?`<span>Showing ${pg.from}-${pg.to} of ${pg.total}</span><span class="pgs"><button type="button" class="pgb" data-p="${pg.page-1}"${pg.page<=1?" disabled":""}>Previous</button>${pageButtons(pg)}<button type="button" class="pgb" data-p="${pg.page+1}"${pg.page>=pg.pages?" disabled":""}>Next</button></span>`:"";
document.querySelectorAll(".pgb[data-p]").forEach(b=>b.onclick=()=>{rs.page=+b.dataset.p;drawRec()});
document.querySelectorAll("#tb [data-sel]").forEach(c=>{c.onclick=e=>e.stopPropagation();c.onchange=()=>{c.checked?rs.sel.add(c.dataset.sel):rs.sel.delete(c.dataset.sel);drawRec()}});
document.querySelectorAll("#tb tr[data-id]").forEach(t=>{t.onclick=()=>detail(t.dataset.id);t.onkeydown=e=>{if(e.key==="Enter"&&e.target===t)detail(t.dataset.id)}});
// Responder names for the rows on this page (read once each, then cached).
const want=pg.items.filter(i=>!i.assignLoaded);
if(want.length)loadAssignmentsFor(want).then(did=>{if(did&&tab==="rec")drawRec()})}
function pageButtons(pg){
const keep=new Set([1,pg.pages,pg.page-1,pg.page,pg.page+1]),out=[];let prev=0;
for(let p=1;p<=pg.pages;p++){if(!keep.has(p))continue;if(p-prev>1)out.push('<span class="gap">...</span>');out.push(`<button type="button" class="pgb${p===pg.page?" on":""}" data-p="${p}"${p===pg.page?' aria-current="page"':""}>${p}</button>`);prev=p}
return out.join("")}

function detail(id){const i=findIncident(id);if(!i)return;
openId=id;const p=loadDetail(i);drawerFor(i);p.then(()=>{if(openId===id)drawerFor(i)})}
function drawerFor(i){const ld=i.detailState==="loading",er=i.detailState==="error";
const canRm=!ld&&!er&&i.assignLoaded===true&&canAssign(i);
const asn=ld?"Loading...":er?"Could not load.":i.res.map(r=>`<div class="arow">${r[2]} - ${r[1]}${r[3].length?" ("+r[3].join(", ")+")":""}${canRm?` <button type="button" class="lnk rmasg" data-r="${esc(r[0])}">Remove</button>`:""}</div>`).join("")||"None assigned yet";
const msg=ld?"Loading...":er?"Could not load.":i.msgs.map(m=>`<div class="msg"><b>${m[0]}</b><br>${m[1]}</div>`).join("")||"No messages";
open(`<small class="muted">Incident</small><h2>${i.code}</h2><div class="pills"><span class="pill ${i.severity}">${i.severity}</span> <span class="pill ${i.status}">${i.status}</span></div>
<div class="kv"><small>Photo from reporter</small>${photoBlock(i)}</div><div class="kv"><small>Description</small>${i.description}</div><div class="kv"><small>Location</small>${i.loc}</div><div class="kv"><small>Reported by</small>${i.reporterName}${i.reporterContact?" ("+i.reporterContact+")":""}</div>
<div class="kv"><small>Submitted</small>${i.submittedAt}</div><div class="kv"><small>Date resolved</small>${i.dateResolved?i.dateResolved+" ("+mins(i)+" min)":ld?"Loading...":i.rawStatus==="resolved"?"Resolved (record not found)":"Not yet resolved"}</div>
<div class="kv"><small>Review flags</small>${fl(i)||"None"}</div>${aiPanelHtml(i,aiSt[i.id],AI_ENDPOINT,I)}<div class="step"><h3 class="stepn"><span>1</span>Review</h3>${reviewBlockHtml(i,rv.id===i.id?rv:{})}</div><div class="kv"><small>Remarks</small>${i.remarks||"None yet"}</div>
<div class="step"><h3 class="stepn"><span>2</span>Responders</h3><div class="kv"><small>Assigned responders</small>${asn}${assignButtonHtml(i)}</div></div>
<div class="kv"><small>Messages</small>${msg}</div><button class="btn ghost" id="cx">Close</button>`);$("#cx").onclick=close;wireReview(i.id);wireAssign(i.id);wireRemove(i.id);wireAi(i.id)}
// ---- AI suggestion (advice only, nothing is written) ----
// The panel asks the Cloudflare Worker (config.js) for a severity and duplicate hint, only when the
// administrator presses the button. If it fails the rest of the drawer works exactly as before.
const aiSt={};
function redrawAi(id){if(openId!==id)return;const e=$("#aip"),i=findIncident(id);if(!i)return;
  const h=aiPanelHtml(i,aiSt[id],AI_ENDPOINT,I);if(e)e.outerHTML=h;wireAi(id)}
function wireAi(id){
  const b=$("#aib");
  if(b)b.onclick=async()=>{const i=findIncident(id);if(!i)return;
    aiSt[id]={status:"loading"};redrawAi(id);
    let r;
    try{const tok=await auth.currentUser.getIdToken();r=await requestSuggestion(AI_ENDPOINT,tok,buildPayload(i,I))}
    catch(e){r={ok:false,text:"Sign in again, then try once more."}}
    aiSt[id]=r.ok?{status:"done",answer:r.answer}:{status:"error",text:r.text};redrawAi(id)};
  document.querySelectorAll("#aip [data-aid]").forEach(a=>a.onclick=e=>{e.preventDefault();detail(a.dataset.aid)})}
// ---- Assign responder (W13.15 to W13.19) ----
// The button is drawn by assignButtonHtml() under "Assigned responders", on open reports only.
// Remove an assigned responder (admin). Asks first; the responder is told on their phone.
function wireRemove(id){document.querySelectorAll(".rmasg").forEach(b=>b.onclick=()=>askRemove(id,b.dataset.r,b))}
function askRemove(id,rid,from){
  const i=findIncident(id);if(!i||!canAssign(i))return;
  const row=i.res.find(r=>r[0]===rid);if(!row)return;
  confirmDlg({
    title:"Remove this responder?",
    body:`${row[2]} will be taken off ${i.code} and told on their phone. You can assign them again later.`,
    confirm:"Remove responder",from,
    run:()=>removeAssignment(id,rid,i),
    done:async()=>{logAct("unassign",`removed ${plain(row[2])} from ${i.code}`,id);notifyAssigned(rid,id,removedText(i),"removed");toast("Responder removed.");afterAssign(id)}
  });
}
function wireAssign(id){const b=$("#asg");if(b)b.onclick=()=>askAssign(id,b)}
// The Assign dialog. W13.16: the report and the responder list (accounts with the responder role from U, without
// the ones already assigned, or a short message when nobody is left). W13.17: the unit select, preselected from the
// chosen responder until the administrator picks a unit themselves, and the two optional resource checkboxes.
// W13.18: Save, the error under each field, and Try again. Everything that decides something lives in
// assignRules.js (submitAssignment, resultView); this only reads the form and applies what it answers.
function askAssign(id,from){
  const i=findIncident(id);
  if(!canAssign(i)){toast(ASSIGN_MSG.closed,4000);return}
  if(i.assignLoaded!==true){toast(ASSIGN_MSG.notLoaded,4000);return}
  openDlg(assignDialogHtml(i,U),from);
  const first=$("#ar")||$("#dno");first.focus();
  $("#dno").onclick=closeDlg;
  const ar=$("#ar"),au=$("#au");
  if(!ar||!au)return;                                  // nobody left to assign: the dialog only has Close
  const list=availableResponders(i,U);let touched=false;
  // ONE assignment id for this dialog: every Save and Try again reuses it (see createAssignSession).
  const session=createAssignSession(()=>newAssignmentId(id));
  const boxes=()=>[...document.querySelectorAll("#ares input")];
  const clear=(...keys)=>keys.forEach(k=>setTxt(ERROR_IDS[k],""));
  ar.onchange=()=>{au.value=unitFor(au.value,touched,list.find(r=>r.id===ar.value));clear("responder","unit","general")};
  au.onchange=()=>{touched=true;clear("unit","general")};   // only a person's own pick fires this, not the preselection above
  boxes().forEach(b=>b.onchange=()=>clear("general"));
  // While saving nothing can be changed or closed, so the answer always belongs to what was sent.
  const busy=on=>{dlgBusy=on;[ar,au,...boxes()].forEach(c=>c.disabled=on);$("#dno").disabled=on;$("#asv").disabled=on};
  $("#asv").onclick=async()=>{
    const btn=$("#asv");
    busy(true);btn.textContent=SAVE_LABEL.busy;clear("responder","unit","general");
    // The live list is rebuilt on every change, so look the report up again instead of trusting the old object.
    const cur=findIncident(id)||i;
    const form={responderId:ar.value,unit:au.value,resources:boxes().filter(b=>b.checked).map(b=>b.value)};
    let r;
    try{
      r=await submitAssignment({incident:cur,form,responders:list,session,
        save:(payload,aid)=>guarded(()=>saveAssignment(id,aid,payload,cur)),
        refresh:()=>refreshAssignments(id,cur)});
    }catch(e){r={kind:"failed",text:saveFail(e)}}
    if(!$("#asv"))return;                              // the dialog is gone
    const v=resultView(r);
    if(r.kind==="failed")console.error(r.error);
    Object.keys(ERROR_IDS).forEach(k=>setTxt(ERROR_IDS[k],v.errors[k]||""));
    if(v.close){closeDlg();toast(v.toast);const who=list.find(x=>x.id===form.responderId);logAct("assign",`assigned ${who?who.name:"a responder"} to ${cur.code}`,id);notifyAssigned(form.responderId,id,assignedText(cur));afterAssign(id);return}
    busy(false);btn.textContent=v.button;
    if(v.lock){btn.disabled=true;$("#dno").textContent=v.cancel}
    if(v.focus==="responder")ar.focus();else if(v.focus==="unit")au.focus();
  };
}
// An assignment was saved (W13.19). It is already in the report's list and in the cache (saveAssignment), so
// nothing is read again: 1) redraw the tab behind the drawer (Overview workload card, Live map panel, the
// "Assigned" column on User accounts), 2) redraw the open drawer for this report, found again by id, so the
// new responder is listed at once and the Assign button leaves the list without them.
function afterAssign(id){
  const how=redrawAfterAssign(tab);
  if(how==="screen")screens[tab]();
  else if(how==="filters"){const q=$("#q");if(q&&q.oninput)q.oninput()}
  if(openId!==id)return;                    // the drawer was closed or moved to another report meanwhile
  const i=findIncident(id),d=drawerAfterReview(i);
  if(d==="close"){close();return}
  if(d==="redraw")drawerFor(i);else detail(id);
  const b=$("#asg")||$("#cx");if(b)b.focus();
}
// ---- Review this report: Verify / False report / Duplicate (W13) ----
// The block is drawn by reviewBlockHtml() only while the report is still "reported". False report and
// Duplicate ask first (confirmDlg); Verify does not, so its saving / error state sits in the block.
// `rv` remembers a Verify that is saving or failed, so redrawing the drawer cannot lose it.
let rv={id:null,kind:null,busy:false,error:""};
// The incident as it is NOW: the live list is rebuilt on every change, so an object kept from earlier
// can be out of date. Records can also hold copies loaded for a date range.
const findIncident=id=>I.find(x=>x.id===id)||(recSrc&&recSrc.list.find(x=>x.id===id))||null;
function wireReview(id){document.querySelectorAll("[data-rv]").forEach(b=>b.onclick=()=>reviewClick(id,b.dataset.rv,b))}
// Redraw only the block (not the whole drawer), so the photo, messages and scroll position stay as they are.
function paintReview(id){const el=$("#rvw"),i=findIncident(id);if(!el||!i||openId!==id)return;el.outerHTML=reviewBlockHtml(i,rv.id===id?rv:{});wireReview(id)}
// The write, with the two ways it must never hang: no connection (fails at once) and no answer (fails after
// 20 s). Either way the person gets the "No internet connection" message and can try again.
const reviewWrite=(id,kind,fallback)=>guarded(()=>verifyIncident(id,kind,findIncident(id)||fallback));
function reviewClick(id,kind,from){
  if(rv.busy)return;
  const i=findIncident(id);
  if(!i||!canVerify(i)){reviewStale();return}
  const d=reviewDialog(i,kind);
  if(d){confirmDlg({...d,from,run:()=>reviewWrite(id,kind,i),done:r=>reviewDone(id,kind,r)});return}
  runVerify(id,kind);
}
async function runVerify(id,kind){
  rv={id,kind,busy:true,error:""};paintReview(id);
  let r;
  try{r=await reviewWrite(id,kind)}
  catch(e){rv={id,kind,busy:false,error:saveFail(e)};paintReview(id);return}
  rv={id:null,kind:null,busy:false,error:""};
  reviewDone(id,kind,r);
}
function reviewDone(id,kind,r){
  if(r.blocked){toast(REVIEW_BLOCKED_TEXT,5000);return}   // not a valid request: nothing was written
  if(!r.ok){reviewStale();return}
  afterReview(id,kind);
}
// A review was saved (W13.14). The report's own copy was already patched by verifyIncident, so nothing is
// read again: 1) say what happened, 2) redraw the tab behind the drawer, 3) redraw the open drawer for
// this report, found again by id, so the Review buttons are gone and the new status shows.
function afterReview(id,kind){
  toast(doneText(kind));
  const rc=findIncident(id);if(rc)logAct(kind==="verify"?"verify":kind==="false"?"false":"duplicate",kind==="verify"?`verified ${rc.code} (${rc.severity})`:kind==="false"?`marked ${rc.code} as a false report`:`marked ${rc.code} as a duplicate`,id);
  redrawTab();
  if(openId!==id)return;                    // the drawer was closed or moved to another report meanwhile
  const i=findIncident(id),how=drawerAfterReview(i);
  if(how==="close"){close();return}
  if(how==="redraw")drawerFor(i);else detail(id);
  const cx=$("#cx");if(cx)cx.focus();       // the button that was clicked is gone, so keep the focus in the drawer
}
function redrawTab(){
  const how=redrawAfterReview(tab);
  if(how==="screen")screens[tab]();
  else if(how==="filters"){const q=$("#q");if(q&&q.oninput)q.oninput()}
}
// Someone else reviewed this report first (the app, or another administrator), or it left the list.
// Nothing was written. The drawer is closed so nobody clicks on a screen that is out of date.
function reviewStale(){toast(REVIEW_STALE_TEXT,5000);close()}
// ---- Reports (W12) ----
// A date range (default: the last 30 days, Philippine time). The incidents submitted in that range
// are read once with a submittedAt query; counts per day and per severity are computed from them.
// The average resolve time is records.dateResolved minus incident.submittedAt. Read-only.
const REPORT_LIMIT=1000;
let rpRange=null,rp=null,rpLoading=false,rpErr="";  // chosen range, loaded result, status
async function repShow(my){
  if(!rpRange)rpRange=defaultRange();
  rpErr=rangeError(rpRange.from,rpRange.to)||"";
  if(rpErr){rpLoading=false;rep();return}
  if(rp&&rp.from===rpRange.from&&rp.to===rpRange.to&&Date.now()-rp.at<6e4){rpLoading=false;rep();return}
  rpLoading=true;rep();
  try{
    const{fromMs,toMs}=rangeBounds(rpRange.from,rpRange.to);
    const r=await loadRange(fromMs,toMs,REPORT_LIMIT);
    if(my!==renderSeq)return;   // the tab changed while loading
    rp={...r,from:rpRange.from,to:rpRange.to,at:Date.now()};
  }catch(e){if(my!==renderSeq)return;console.error(e);rpErr=errMsg(e)}
  rpLoading=false;rep();
}
// ---- Reports ----
let rpMode="30";   // 7, 30, q (this quarter) or custom
const quarterRange=()=>{const t=todayPH(),[y,m]=t.split("-").map(Number),qm=Math.floor((m-1)/3)*3+1;return{from:`${y}-${String(qm).padStart(2,"0")}-01`,to:t}};
function rep(){
const r=rpRange||defaultRange(),ok=rp&&rp.from===r.from&&rp.to===r.to&&!rpErr&&!rpLoading;
const label=!rangeError(r.from,r.to)?`${fmtDay(r.from)} to ${fmtDay(r.to)}`:"the chosen dates";
let body="";
if(rpLoading)body='<p class="muted">Loading...</p>';
else if(ok){
  const list=rp.list,done=resolvedCount(list),rs2=resolveStats(list),med=medianResolve(list),bc=bucketCounts(list,rp.from,rp.to),
  mx=Math.max(1,...bc.buckets.map(x=>x.count)),peak=bc.buckets.reduce((a,x)=>x.count>a.count?x:a,{count:0}),
  conf=list.filter(i=>!isClosedOut(i)).length,days=daysInRange(rp.from,rp.to),w1=withinHour(list),
  types=typeCounts(list),tmx=Math.max(1,...types.map(t=>t.n)),pur=purokCounts(list,U).slice(0,5),pmx=Math.max(1,...pur.map(p=>p.n)),
  grid=hourHeat(list),gmx=Math.max(1,...grid.flat()),busy=busiestSlot(grid);
  const TCOL=["var(--brand)","#B8741A","#1A5FD0","#14602F","#8A817B"];
  body=`${rp.capped?`<div class="card note"><b>Only the newest ${REPORT_LIMIT} incidents in this range are counted.</b> Choose a shorter range for complete figures.</div>`:""}
${list.length?"":`<p class="muted">No incidents were submitted in this range.</p>`}
<section class="kpis"><div class="card kpi"><span class="kl">Reports</span><span class="kb acc">${list.length}</span><span class="kn">${(list.length/days).toFixed(1)} per day on average</span></div>
<div class="card kpi"><span class="kl">Confirmed fires</span><span class="kb">${conf}</span><span class="kn">${list.length-conf} false alarm${list.length-conf===1?"":"s"} or duplicate${list.length-conf===1?"":"s"}</span></div>
<div class="card kpi"><span class="kl">Median time to resolve</span><span class="kb">${med==null?"-":Math.round(med)}${med==null?"":'<span class="u"> min</span>'}</span><span class="kn">${rs2.n<done?`Based on ${rs2.n} of ${done} resolved`:`${done} resolved`}</span></div>
<div class="card kpi"><span class="kl">Resolved within 1 h</span><span class="kb">${w1.n?Math.round(w1.within/w1.n*100)+"%":"-"}</span><span class="kn">${list.filter(i=>i.active).length} still open</span></div></section>
<section class="split"><div class="card panel grow2"><div class="phead"><h2>Reports per ${bc.unit}</h2><span class="muted small">${peak.count?`Peak: ${peak.count} on ${peak.title}`:""}</span></div>
<div class="daybars" role="img" aria-label="Reports per ${bc.unit}">${bc.buckets.map(x=>`<div class="db${x.count&&x.count===peak.count?" peak":""}" title="${x.title}: ${x.count}" style="height:${x.count?Math.max(4,x.count/mx*100):2}%"></div>`).join("")}</div>
<div class="wklab"><span>${bc.buckets.length?bc.buckets[0].label:""}</span><span>${bc.buckets.length>2?bc.buckets[bc.buckets.length>>1].label:""}</span><span>${bc.buckets.length?bc.buckets[bc.buckets.length-1].label:""}</span></div></div>
<div class="card panel grow1"><div class="phead"><h2>By type</h2></div>${types.map((t,k)=>`<div class="tb"><div class="tbl"><b>${t.label}</b><span>${t.n} · ${Math.round(t.n/list.length*100)}%</span></div><div class="tbt"><i style="width:${t.n/tmx*100}%;background:${TCOL[k%TCOL.length]}"></i></div></div>`).join("")||'<p class="muted small">Nothing to show.</p>'}</div></section>
<section class="split"><div class="card panel grow2"><div class="phead"><h2>When fires get reported</h2><span class="muted small">${busy?"Busiest: "+busy:""}</span></div>
<div class="heat"><span></span>${HOUR_LABELS.map(h=>`<span class="hh">${h}</span>`).join("")}${grid.map((row,d)=>`<span class="hd">${DAYS[d]}</span>`+row.map((n,h)=>`<div class="hc" title="${DAYS[d]} ${HOUR_LABELS[h]}: ${n}" style="background:color-mix(in srgb,var(--brand) ${n?Math.round(10+90*n/gmx):4}%,transparent)"></div>`).join("")).join("")}</div></div>
<div class="card panel grow1"><div class="phead"><h2>By purok</h2></div>${pur.map(p=>`<div class="pb"><span class="pn">${esc(p.label)}</span><div class="pbt"><i style="width:${p.n/pmx*100}%"></i></div><b>${p.n}</b></div>`).join("")||'<p class="muted small">Reporters have not set a purok yet.</p>'}${pur.length?`<p class="muted small">${esc(pur[0].label)} has the most reports. Useful when deciding where to place equipment.</p>`:""}</div></section>
${rs2.n<done?`<p class="muted small">Times use the ${rs2.n} resolved incidents that have a resolve record; the others have none yet.</p>`:""}`;
}
$("#main").innerHTML=head("Reports",`${label} · counts come from the incident records`,`<button type="button" class="btn ghost noprint" id="pr">Print</button><button type="button" class="btn noprint" id="csv"${ok&&rp.list.length?"":" disabled"}>Export CSV</button>`)+
`<section class="chips noprint" role="group" aria-label="Date range">${[["7","Last 7 days"],["30","Last 30 days"],["q","This quarter"],["custom","Custom range"]].map(([v,t])=>`<button type="button" class="chip${rpMode===v?" on":""}" data-days="${v}" aria-pressed="${rpMode===v}">${t}</button>`).join("")}</section>
<div class="dpop noprint" id="rcustom"${rpMode==="custom"?"":" hidden"}><label>From <input type="date" id="rf" value="${r.from}"></label><label>To <input type="date" id="rt" value="${r.to}"></label><button type="button" class="btn" id="go">Show report</button></div>
<div class="ferr" role="alert" style="margin-bottom:12px">${rpErr}</div>${body}`;
document.querySelectorAll("[data-days]").forEach(b=>b.onclick=()=>{
  const v=b.dataset.days;rpMode=v;
  if(v==="custom"){$("#rcustom").hidden=false;document.querySelectorAll("[data-days]").forEach(c=>{c.classList.toggle("on",c===b);c.setAttribute("aria-pressed",c===b)});return}
  rpRange=v==="q"?quarterRange():lastDays(+v);repShow(++renderSeq)});
$("#go").onclick=()=>{rpRange={from:$("#rf").value,to:$("#rt").value};repShow(++renderSeq)};
$("#pr").onclick=()=>window.print();
if($("#csv"))$("#csv").onclick=()=>{if(rp&&rp.list.length)saveCsvFile(reportCsv(rp.list),csvFileName(rp.from,rp.to))};
}
// ---- Activity log ----
let actEvents=[],unsubAct=null,lastFresh=null;
const as={chip:"all",q:""};
const seenIds=new Set(),warned=new Set();
const PREF_KEY="fires-alerts";
function loadPrefs(){let p={};try{p=JSON.parse(localStorage.getItem(PREF_KEY)||"{}")||{}}catch(e){}return{sound:p.sound!==false,notify:p.notify!==false,warn:p.warn===true}}
let prefs=loadPrefs();
function savePrefs(){try{localStorage.setItem(PREF_KEY,JSON.stringify(prefs))}catch(e){}}
const notifyOk=()=>typeof Notification!=="undefined"&&Notification.permission==="granted";
function beep(){try{const C=window.AudioContext||window.webkitAudioContext;if(!C)return;const c=new C(),o=c.createOscillator(),g=c.createGain();o.frequency.value=880;g.gain.value=.08;o.connect(g);g.connect(c.destination);o.start();setTimeout(()=>{o.stop();c.close()},350)}catch(e){}}
// Called on every change of the live list: tells the administrator about reports that just arrived.
function checkNew(){
  const fresh=newReports(I,seenIds);
  if(!fresh.length)return;
  const i=fresh[0];
  lastFresh={id:i.id,code:i.code,loc:i.loc,type:fireName(i.fireType),ms:Date.now()};
  if(prefs.sound)beep();
  if(prefs.notify&&notifyOk()){try{new Notification("New fire report",{body:plain(i.loc)})}catch(e){}}
  if(tab!=="act")toast(`New report: ${plain(i.loc)}`,6000);
}
setInterval(()=>{
  if(!prefs.warn||!live())return;
  unassignedTooLong(I).filter(i=>!warned.has(i.id)).forEach(i=>{warned.add(i.id);if(prefs.sound)beep();toast(`${i.code} has had no responder for over 5 minutes.`,8000)});
},30000);
function logAct(type,text,incidentId){
  const me=auth.currentUser,d=activityDoc(type,text,{id:me&&me.uid,name:($("#nm")&&$("#nm").textContent)||"Admin"},incidentId);
  if(d)logActivity(d);
}
function act(){
const evs=filterEvents(mergeEvents(incidentEvents(I,U),actEvents),as.chip,as.q.toLowerCase());
const groups=[];evs.forEach(e=>{const h=dayHeading(e.ms);let g=groups[groups.length-1];if(!g||g.h!==h){g={h,items:[]};groups.push(g)}g.items.push(e)});
const on=k=>!!(prefs[k]&&(k!=="notify"||notifyOk()));
const tgl=(k,t)=>`<div class="trow"><span>${t}</span><button type="button" class="tgl${on(k)?" on":""}" role="switch" aria-checked="${on(k)}" aria-label="${t}" data-p="${k}"><i></i></button></div>`;
const fresh=lastFresh&&Date.now()-lastFresh.ms<30*60000?lastFresh:null;
$("#main").innerHTML=head("Activity log","Everything that happened, in order · updates by itself",`<button type="button" class="btn ghost noprint" id="xact">Export CSV</button>`)+
(fresh?`<section class="banner"><i></i><b>New report: ${fresh.type[0].toUpperCase()+fresh.type.slice(1)} at ${fresh.loc}</b><span>${ageText(fresh.ms)} ago${prefs.sound?" · sound is on":""}</span><span class="sp"></span><button type="button" class="bbtn" id="rnow">Review now</button></section>`:"")+
`<section class="bar2 noprint"><label class="search">${ICON_SEARCH}<input id="q" placeholder="Search the log" aria-label="Search the log" value="${esc(as.q)}"></label><div class="chips" role="group" aria-label="Filter the log">${ACTIVITY_CHIPS.map(c=>`<button type="button" class="chip${as.chip===c.id?" on":""}" data-c="${c.id}" aria-pressed="${as.chip===c.id}">${c.label}</button>`).join("")}</div></section>
<section class="split start"><div class="card panel grow2 feed">${groups.map(g=>`<div class="phead"><h2>${g.h}</h2><span class="muted small">${g===groups[0]?"Live, newest first":""}</span></div>${g.items.map(e=>{const k=kindInfo(e.kind),go=e.id&&findIncident(e.id);return`<div class="ev${go?" go":""}"${go?` tabindex="0" role="button" data-id="${e.id}"`:""}><div class="evi ${k.tone}" aria-hidden="true">${k.icon}</div><div class="evt"><b>${e.who}</b> ${e.verb} ${e.what}</div><span class="evtime">${clockText(e.ms)}</span></div>`}).join("")}`).join("")||'<p class="muted">Nothing here yet. Reports and administrator actions appear as they happen.</p>'}</div>
<div class="grow1 stack"><div class="card panel"><div class="phead"><h2>Alerts</h2></div>${tgl("sound","Sound on new report")}${tgl("notify","Browser notification")}${tgl("warn","Warn if unassigned over 5 min")}</div>
<div class="card panel"><div class="phead"><h2>Why a log</h2></div><p class="muted">Every review, assignment and account change you make is saved here with who did it and when. Reports and resolved incidents come from the records themselves.</p></div></div></section>`;
$("#q").oninput=()=>{as.q=$("#q").value;const p=$("#q").selectionStart;act();const q=$("#q");q.focus();q.setSelectionRange(p,p)};
document.querySelectorAll(".chip[data-c]").forEach(b=>b.onclick=()=>{as.chip=b.dataset.c;act()});
document.querySelectorAll(".tgl").forEach(b=>b.onclick=async()=>{
  const k=b.dataset.p;
  if(k==="notify"&&!on("notify")){
    if(typeof Notification==="undefined"){toast("This browser cannot show notifications.");return}
    let ok=Notification.permission==="granted";
    if(!ok&&Notification.permission!=="denied"){try{ok=(await Notification.requestPermission())==="granted"}catch(e){}}
    if(!ok){toast("Notifications are blocked. Allow them in the browser's site settings.",5000);return}
    prefs.notify=true;
  }else prefs[k]=!on(k);
  savePrefs();if(prefs.sound&&k==="sound")beep();act();
});
document.querySelectorAll(".ev.go").forEach(t=>{t.onclick=()=>detail(t.dataset.id);t.onkeydown=e=>{if(e.key==="Enter")detail(t.dataset.id)}});
if($("#rnow"))$("#rnow").onclick=()=>{const id=lastFresh&&lastFresh.id;lastFresh=null;if(id)detail(id);act()};
$("#xact").onclick=()=>{if(!evs.length){toast("The log is empty.");return}saveCsvFile(toCsv([["When (PH time)","Who","What"],...evs.map(e=>[fmtPH(e.ms),plain(e.who),plain((e.verb+" "+e.what).trim())])]),`fires-activity-${todayPH()}.csv`)};
}
// Live map (W9): Leaflet + OpenStreetMap. The map is created once per visit to this tab and kept,
// so a new report or a click updates markers and the side panel without resetting zoom or position.
let selId=null,mf="act",lmap=null,mlayer=null,fitted=false;
const markers=new Map();
const SEV_COLOR={Critical:"#8F1D14",High:"#D3281C",Medium:"#B45309",Low:"#16803D"},GREY="#8d8482";
// Text is stored HTML-escaped (see mapping.js); decode it for places that need plain text (tooltips).
const plain=h=>{const t=document.createElement("textarea");t.innerHTML=h;return t.value};
function destroyMap(){if(lmap){lmap.remove();lmap=null}mlayer=null;markers.clear();fitted=false}
function pinIcon(i,on){const c=i.active?(SEV_COLOR[i.severity]||GREY):GREY,z=on?26:20;
return L.divIcon({className:"pin",html:`<span class="dot${on?" on":""}${i.active?" act":""}" style="background:${c}"></span>`,iconSize:[z,z],iconAnchor:[z/2,z/2]})}
function buildMap(){
destroyMap();
$("#main").innerHTML=`<h1>Live map</h1><p class="lead">Where each emergency is, plotted from the latitude and longitude in the incident report.</p>
<div class="chips" role="group" aria-label="Filter map">${[["act","Active"],["all","All"],["Resolved","Resolved"],["Duplicate","Duplicate"],["False","False"]].map(([v,t])=>`<button type="button" class="chip${mf===v?" on":""}" data-f="${v}" aria-pressed="${mf===v}">${t}</button>`).join("")}</div>
<div class="mapwrap"><div><div class="card mapbox"><div id="lmap" role="region" aria-label="Map of incidents"></div></div>
<div class="legend"><span><i style="background:#8F1D14"></i>Critical</span><span><i style="background:#D3281C"></i>High</span><span><i style="background:#B45309"></i>Medium</span><span><i style="background:#16803D"></i>Low</span><span><i style="background:#8d8482"></i>Not active</span></div></div>
<div id="side"></div></div>`;
document.querySelectorAll(".chip").forEach(b=>b.onclick=()=>{mf=b.dataset.f;fitted=false;document.querySelectorAll(".chip").forEach(c=>{const on=c.dataset.f===mf;c.classList.toggle("on",on);c.setAttribute("aria-pressed",on)});map()});
if(typeof L==="undefined"){$("#lmap").innerHTML='<p class="muted" style="padding:16px">The map could not load. Check your internet connection and reload the page.</p>';return}
lmap=L.map("lmap").setView([14.52,121.05],13);
L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png",{maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors'}).addTo(lmap);
mlayer=L.layerGroup().addTo(lmap);
requestAnimationFrame(()=>{if(lmap)lmap.invalidateSize()})}
function map(){
if(!$("#lmap"))buildMap();
const list=I.filter(i=>mf==="act"?ACT(i):mf==="all"||i.status===mf);
const cur=list.find(x=>x.id===selId)||list[0];
if(lmap){
const keep=new Set();
list.forEach(i=>{if(i.lat==null)return;keep.add(i.id);
const on=!!cur&&cur.id===i.id;let m=markers.get(i.id);
if(!m){m=L.marker([i.lat,i.lng],{icon:pinIcon(i,on),title:plain(i.code+" "+i.loc),alt:plain(i.code+" "+i.loc),keyboard:true}).addTo(mlayer);
m.on("click",()=>pick(i.id));markers.set(i.id,m)}
else{m.setLatLng([i.lat,i.lng]);m.setIcon(pinIcon(i,on))}
m.setZIndexOffset(on?1000:0)});
[...markers.keys()].forEach(id=>{if(!keep.has(id)){mlayer.removeLayer(markers.get(id));markers.delete(id)}});
if(!fitted&&keep.size){fitted=true;const pts=[...keep].map(id=>{const i=I.find(x=>x.id===id);return[i.lat,i.lng]});
if(pts.length===1)lmap.setView(pts[0],16);else lmap.fitBounds(L.latLngBounds(pts),{padding:[40,40],maxZoom:16})}}
$("#side").innerHTML=(cur?`<div class="card" style="margin-bottom:12px"><span class="muted">Selected incident</span><h3 style="margin:2px 0 8px;font-size:22px">${cur.code}</h3><div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:10px"><span class="pill ${cur.severity}">${cur.severity}</span><span class="pill ${cur.status}">${cur.status}</span> ${fl(cur)}</div>${cur.photoUrl?photoBlock(cur)+"<br>":""}<div class="kv" style="margin-top:0"><small>Location</small>${cur.loc}</div><div class="kv"><small>Coordinates (${cur.locSource})</small>${cur.lat!=null?cur.lat.toFixed(4)+", "+cur.lng.toFixed(4):"None"}</div><div class="kv"><small>Responders</small>${cur.res.map(r=>r[2]+" - "+r[1]).join("<br>")||"None assigned yet"}</div><button class="btn" id="dt" style="width:100%">Open full record</button></div>`:"")+
(list.length>1?`<small class="muted" style="display:block;margin:4px 2px 8px;font-weight:700;letter-spacing:.06em;text-transform:uppercase">Other incidents</small>`:"")+
list.filter(x=>!cur||x.id!==cur.id).map(x=>`<div class="li" data-id="${x.id}" tabindex="0" role="button"><b>${x.code}</b> <span class="pill ${x.severity}">${x.severity}</span><br><span class="muted">${x.loc}</span></div>`).join("")||(cur?"":`<div class="card muted">No incidents match this filter.</div>`);
if(cur)$("#dt").onclick=()=>detail(cur.id);
document.querySelectorAll(".li").forEach(e=>{e.onclick=()=>pick(e.dataset.id);e.onkeydown=k=>{if(k.key==="Enter"||k.key===" "){k.preventDefault();pick(e.dataset.id)}}})}
const pick=id=>{selId=id;map();const i=I.find(x=>x.id===id);if(lmap&&i&&i.lat!=null)lmap.panTo([i.lat,i.lng])};
document.addEventListener("click",e=>{const t=e.target.closest(".ph");if(t){const i=I.find(x=>x.id===t.dataset.id);if(!i||!i.photoUrl)return;$("#lb").innerHTML=`<img src="${i.photoUrl}" alt="Enlarged photo for ${i.code}">`;$("#lb").classList.add("open")}else if(e.target.closest("#lb"))$("#lb").classList.remove("open")});
document.addEventListener("keydown",e=>{if(e.key==="Escape")$("#lb").classList.remove("open")});

// Guard (W6). The page stays hidden (style#guard in index.html) until this check finishes.
// It only decides what the page shows; the real protection is firestore.rules.
const toLogin=()=>location.replace("login.html");
let started=false;
onAuthStateChanged(auth,async user=>{
  if(!user){stopLive();toLogin();return}
  let u;
  try{
    const snap=await getDoc(doc(db,"users",user.uid));
    u=snap.exists()?snap.data():null;
  }catch(e){toLogin();return}
  if(!u||u.role!=="admin"){await signOut(auth);toLogin();return}
  if(started)return;
  started=true;
  const name=u.fullName||user.email||"Administrator",email=u.email||user.email||"";
  $("#nm").textContent=name;$("#av").textContent=name[0].toUpperCase();
  $("#me-email").textContent=email;
  document.getElementById("guard")?.remove();
  boot();
});
async function boot(){
  $("#main").innerHTML='<p class="muted">Loading...</p>';
  try{await loadUsers();usersAt=Date.now()}catch(e){showError(e,boot);return}
  render();
}
$("#out").onclick=async()=>{stopLive();try{await signOut(auth)}finally{toLogin()}};
// Back button after logout can show a cached page; reload so the guard runs again.
addEventListener("pageshow",e=>{if(e.persisted)location.reload()});
