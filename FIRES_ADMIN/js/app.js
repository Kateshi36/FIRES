import {
  U, I, RS, ACT, fl, photoBlock,
  loadUsers, loadIncidentsOnce, listenIncidents, incidentsAreFresh, loadActiveAssignments, loadDetail, loadRange, INCIDENT_LIMIT, RANGE_LIMIT,
  createResponderAuth, saveResponderProfile, sendResponderReset, changeUserRole, verifyIncident,
  newAssignmentId, saveAssignment, refreshAssignments
} from "./data.js";
import { esc } from "./mapping.js";
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
  resolveStats, fmtMin, reportCsv, csvFileName
} from "./reportRules.js";
import { auth, db, onAuthStateChanged, signOut, doc, getDoc } from "./firebase.js";
const $=s=>document.querySelector(s);let tab="over";
const mins=i=>Math.round((i.resolvedMs-i.submittedMs)/6e4);
let openId=null;
let toastTimer=null;
function toast(t,ms=2200){const e=$("#toast");e.textContent=t;e.style.display="block";clearTimeout(toastTimer);toastTimer=setTimeout(()=>e.style.display="none",ms)}
function close(){openId=null;$("#drawer").classList.remove("open");$("#scrim").classList.remove("open")}
function open(h){$("#drawer").innerHTML=h;$("#drawer").classList.add("open");$("#scrim").classList.add("open")}
$("#scrim").onclick=close;
document.querySelectorAll("aside button").forEach(b=>b.onclick=()=>{tab=b.dataset.t;document.querySelectorAll("aside button").forEach(x=>x.classList.toggle("on",x===b));close();render()});
function stats(){const done=I.filter(i=>i.dateResolved&&i.submittedMs!=null);return{total:I.length,active:I.filter(ACT).length,res:I.filter(i=>i.rawStatus==="resolved").length,avg:done.length?Math.round(done.reduce((a,i)=>a+mins(i),0)/done.length):0}}
const screens={over,map,resp,rec,rep};
let unsub=null,usersAt=0,renderSeq=0;
const live=()=>tab==="over"||tab==="map";
const stopLive=()=>{if(unsub){unsub();unsub=null}};
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
function drawLive(){if(!live())return;screens[tab]();loadActiveAssignments().then(did=>{if(did&&live())screens[tab]()})}
async function render(){
  const my=++renderSeq;
  if(tab!=="map")destroyMap();
  if(tab!=="rec")recSrc=null;
  try{
    if(live()){
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
function over(){const s=stats();$("#main").innerHTML=`<h1>Overview</h1><p class="lead">Incident activity and responder accounts at a glance.</p>
<div class="grid g4"><div class="card stat hot"><b>${s.active}</b><span>Active incidents</span></div><div class="card stat"><b>${I.filter(i=>i.isDuplicate||i.isSuspicious).length}</b><span>Flagged for review</span></div><div class="card stat"><b>${s.avg} min</b><span>Average time to resolve</span></div><div class="card stat"><b>${RS().length}</b><span>Responder accounts</span></div></div>
<div class="grid g2" style="margin-top:14px"><div class="card"><h3>Latest incidents</h3><div class="wrap"><table><tbody>${I.slice(0,4).map(i=>`<tr data-id="${i.id}"><td><b>${i.code}</b><br><span class="muted">${i.loc}</span></td><td><span class="pill ${i.severity}">${i.severity}</span></td><td><span class="pill ${i.status}">${i.status}</span> ${fl(i)}</td></tr>`).join("")}</tbody></table></div></div>
<div class="card"><h3>Responder workload</h3>${RS().map(r=>{const n=assignedCount(I,r.id);return`<div class="row"><span class="l">${r.fullName.split(" ")[0]}</span><span class="t"><i style="width:${n*25}%;background:var(--red)"></i></span><b>${n}</b></div>`}).join("")}<p class="muted" style="margin-bottom:0">Incidents assigned per responder.</p></div></div>`;
document.querySelectorAll("tbody tr").forEach(t=>t.onclick=()=>detail(t.dataset.id))}
function resp(){$("#main").innerHTML=`<h1>User accounts</h1><p class="lead">Create responder accounts for B-FLARE, BDRRMO and BFP, and remove or restore responder access.</p>
<div class="tools"><input id="q" placeholder="Search by name or email" aria-label="Search accounts"><select id="ro" aria-label="Filter by role"><option value="">All roles</option><option value="responder">Responders</option><option value="citizen">Citizens</option></select><button class="btn" id="add">Add responder</button></div>
<div class="card wrap"><table><thead><tr><th>Name</th><th>Contact</th><th>Unit / address</th><th>Role</th><th>Assigned</th><th></th></tr></thead><tbody id="tb"></tbody></table></div>`;
const me=auth.currentUser&&auth.currentUser.uid;
// The role button for one row. Administrators get a disabled button: nobody, including the signed-in
// admin, is demoted from the website, so the last administrator can never be removed.
const act=r=>{const a=rowAction(r,me);
  return a.enabled?`<button class="btn ghost" data-act="${a.kind}" data-id="${r.id}">${a.label}</button>`
  :`<button class="btn ghost" disabled title="${a.reason}" aria-label="${a.label}. ${a.reason}">${a.label}</button><br><span class="muted" style="font-size:12px">${a.note}</span>`};
const draw=()=>{const q=$("#q").value.toLowerCase(),ro=$("#ro").value;$("#tb").innerHTML=U.filter(r=>(r.fullName+r.email).toLowerCase().includes(q)&&(!ro||r.role===ro)).map(r=>`<tr style="cursor:default"><td><b>${r.fullName}</b>${r.id===me?' <span class="muted">(you)</span>':""}<br><span class="muted">${r.email}</span></td><td>${r.contactNo}</td><td>${r.address}</td><td>${r.role}</td><td>${r.role==="responder"?assignedCount(I,r.id):I.filter(i=>i.reporterId===r.id).length+" reports"}</td><td>${act(r)}</td></tr>`).join("")||`<tr><td colspan=6 class="muted">No accounts match. Clear the search or add a new account.</td></tr>`;
document.querySelectorAll("[data-act]").forEach(b=>b.onclick=()=>askRole(b.dataset.id,b.dataset.act,b))};
$("#q").oninput=draw;$("#ro").onchange=draw;draw();
$("#add").onclick=()=>addResponder()}
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
function redrawUsers(){if(tab!=="resp")return;const q=$("#q")?.value||"",ro=$("#ro")?.value||"";screens.resp();$("#q").value=q;$("#ro").value=ro;$("#q").oninput()}
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
      if(res.ok){redrawUsers();toast(rm?"Responder access removed. The sign-in account was kept.":"They are now a responder.",4000);return}
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
function rec(){$("#main").innerHTML=`<h1>Incident records</h1><p class="lead">Search incident records by date, status and severity. Flagged reports are marked for review.</p>
<div class="tools"><input id="q" placeholder="Search ID, description or location" aria-label="Search records"><select id="sv" aria-label="Filter by severity"><option value="">All severities</option><option>Critical</option><option>High</option><option>Medium</option><option>Low</option></select><select id="st" aria-label="Filter by status"><option value="">All statuses</option><option>Reported</option><option>Verified</option><option>Ongoing</option><option>Resolved</option><option>False</option><option>Duplicate</option></select><select id="fg" aria-label="Filter by flags"><option value="">All reports</option><option value="1">Flagged only</option></select><input type="date" id="d1" aria-label="From date" style="flex:0 0 auto;min-width:0"><input type="date" id="d2" aria-label="To date" style="flex:0 0 auto;min-width:0"></div>
<p class="muted" id="hist" aria-live="polite" style="margin:0 0 12px"></p>
<div class="card wrap"><table><thead><tr><th>ID</th><th>Incident</th><th>Severity</th><th>Status</th><th>Submitted</th><th>Resolved</th></tr></thead><tbody id="tb"></tbody></table></div>`;
const draw=()=>{const q=esc($("#q").value.toLowerCase()),v=$("#sv").value,st=$("#st").value,f=$("#fg").value,d1=$("#d1").value,d2=$("#d2").value;
// Text is stored HTML-escaped (mapping.js), so the search text is escaped the same way before comparing.
const src=recSrc&&recCovers(recSrc,d1,d2)?recSrc.list:I;
const oldest=I.length?I[I.length-1].submittedAt.slice(0,10):"";
const need=src===I&&I.length>=INCIDENT_LIMIT&&!(d1&&d2&&d1>d2)&&((d1&&d1<oldest)||(d2&&d2<oldest));
$("#hist").innerHTML=d1&&d2&&d1>d2?"The start date is after the end date."
  :need?`Showing only the newest ${INCIDENT_LIMIT} incidents. <button class="btn ghost" id="hb" style="display:inline-block;padding:5px 12px;margin-left:6px">Load incidents for these dates</button> <span id="hm" class="ferr" style="display:inline"></span>`
  :src!==I?`Showing ${src.length} incident${src.length===1?"":"s"} loaded for these dates.${recSrc.capped?` Only the newest ${RANGE_LIMIT} are shown, so choose a shorter range.`:""}`:"";
if($("#hb"))$("#hb").onclick=async()=>{
  const b=$("#hb");b.disabled=true;b.textContent="Loading...";
  try{
    const r=await loadRange(d1?rangeBounds(d1,d1).fromMs:null,d2?rangeBounds(d2,d2).toMs:null);
    recSrc={...r,fromDay:d1||null,toDay:d2||null};draw();
  }catch(e){console.error(e);if($("#hb")){b.disabled=false;b.textContent="Load incidents for these dates";setTxt("#hm",errMsg(e))}}
};
$("#tb").innerHTML=src.filter(i=>(i.code+i.description+i.loc).toLowerCase().includes(q)&&(!v||i.severity===v)&&(!st||i.status===st)&&(!f||i.isDuplicate||i.isSuspicious)&&(!d1||i.submittedAt.slice(0,10)>=d1)&&(!d2||i.submittedAt.slice(0,10)<=d2)).map(i=>`<tr tabindex="0" data-id="${i.id}"><td><b>${i.code}</b>${i.photo?`<br><span class="muted">Photo</span>`:""}</td><td>${i.description}<br><span class="muted">${i.loc}</span></td><td><span class="pill ${i.severity}">${i.severity}</span></td><td><span class="pill ${i.status}">${i.status}</span><br>${fl(i)}</td><td>${i.submittedAt}</td><td>${i.dateResolved||"-"}</td></tr>`).join("")||`<tr><td colspan=6 class="muted">No incidents match these filters.</td></tr>`;
document.querySelectorAll("tbody tr[data-id]").forEach(t=>{t.onclick=()=>detail(t.dataset.id);t.onkeydown=e=>{if(e.key==="Enter")detail(t.dataset.id)}})};
["q","sv","st","fg","d1","d2"].forEach(x=>$("#"+x).oninput=draw);draw()}
function detail(id){const i=findIncident(id);if(!i)return;
openId=id;const p=loadDetail(i);drawerFor(i);p.then(()=>{if(openId===id)drawerFor(i)})}
function drawerFor(i){const ld=i.detailState==="loading",er=i.detailState==="error";
const asn=ld?"Loading...":er?"Could not load.":i.res.map(r=>`${r[2]} - ${r[1]}${r[3].length?" ("+r[3].join(", ")+")":""}`).join("<br>")||"None assigned yet";
const msg=ld?"Loading...":er?"Could not load.":i.msgs.map(m=>`<div class="msg"><b>${m[0]}</b><br>${m[1]}</div>`).join("")||"No messages";
open(`<h2>${i.code}</h2><span class="pill ${i.severity}">${i.severity}</span> <span class="pill ${i.status}">${i.status}</span>
<div class="kv"><small>Photo from reporter</small>${photoBlock(i)}</div><div class="kv"><small>Description</small>${i.description}</div><div class="kv"><small>Location</small>${i.loc}</div><div class="kv"><small>Reported by</small>${i.reporterName}${i.reporterContact?" ("+i.reporterContact+")":""}</div>
<div class="kv"><small>Submitted</small>${i.submittedAt}</div><div class="kv"><small>Date resolved</small>${i.dateResolved?i.dateResolved+" ("+mins(i)+" min)":ld?"Loading...":i.rawStatus==="resolved"?"Resolved (record not found)":"Not yet resolved"}</div>
<div class="kv"><small>Review flags</small>${fl(i)||"None"}</div>${reviewBlockHtml(i,rv.id===i.id?rv:{})}<div class="kv"><small>Remarks</small>${i.remarks||"None yet"}</div>
<div class="kv"><small>Assigned responders</small>${asn}${assignButtonHtml(i)}</div>
<div class="kv"><small>Messages</small>${msg}</div><button class="btn ghost" id="cx">Close</button>`);$("#cx").onclick=close;wireReview(i.id);wireAssign(i.id)}
// ---- Assign responder (W13.15 to W13.19) ----
// The button is drawn by assignButtonHtml() under "Assigned responders", on open reports only.
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
    if(v.close){closeDlg();toast(v.toast);afterAssign(id);return}
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
function downloadCsv(){
  if(!rp||!rp.list.length)return;
  // The byte order mark makes Excel read the file as UTF-8.
  const url=URL.createObjectURL(new Blob(["\ufeff"+reportCsv(rp.list)],{type:"text/csv;charset=utf-8"}));
  const a=document.createElement("a");a.href=url;a.download=csvFileName(rp.from,rp.to);
  document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
function rep(){
  const r=rpRange||defaultRange(),ok=rp&&rp.from===r.from&&rp.to===r.to&&!rpErr&&!rpLoading;
  const label=!rangeError(r.from,r.to)?`${fmtDay(r.from)} to ${fmtDay(r.to)}`:"the chosen dates";
  let body;
  if(rpLoading)body='<p class="muted">Loading...</p>';
  else if(!ok)body="";
  else{
    const list=rp.list,sev=severityCounts(list),done=resolvedCount(list),rs=resolveStats(list),bc=bucketCounts(list,rp.from,rp.to);
    const mx=Math.max(1,...bc.buckets.map(x=>x.count)),every=Math.ceil(bc.buckets.length/(bc.unit==="day"?31:12));
    const col={Critical:"var(--red2)",High:"var(--red)",Medium:"var(--warn)",Low:"var(--ok)"};
    body=`${rp.capped?`<div class="card" style="margin-bottom:14px"><b>Only the newest ${REPORT_LIMIT} incidents in this range are counted.</b> Choose a shorter range for complete figures.</div>`:""}
${list.length?"":`<p class="muted">No incidents were submitted in this range.</p>`}
<div class="grid g4"><div class="card stat"><b>${list.length}</b><span>Total incidents</span></div><div class="card stat"><b>${done}</b><span>Resolved</span></div><div class="card stat"><b>${fmtMin(rs.avgMin)}</b><span>Average time to resolve</span></div><div class="card stat"><b>${sev.Critical+sev.High}</b><span>High or critical</span></div></div>
${rs.n<done?`<p class="muted" style="margin:8px 0 0">The average uses ${rs.n} of ${done} resolved incidents. The others have no resolve record yet.</p>`:""}
<div class="grid g2" style="margin-top:14px"><div class="card"><h3>Incidents per ${bc.unit}</h3><div class="bar" style="gap:${bc.buckets.length>14?3:10}px">${bc.buckets.map((x,k)=>`<div title="${x.title}: ${x.count}">${x.count}<i style="height:${x.count?x.count/mx*100:2}%"></i>${k%every===0?x.label:"&nbsp;"}</div>`).join("")}</div></div>
<div class="card"><h3>By severity</h3>${["Critical","High","Medium","Low"].map(k=>`<div class="row"><span class="l">${k}</span><span class="t"><i style="width:${list.length?sev[k]/list.length*100:0}%;background:${col[k]}"></i></span><b>${sev[k]}</b></div>`).join("")}
<div class="noprint" style="margin-top:12px"><button class="btn" id="pr" style="display:inline-block">Print report</button> <button class="btn ghost" id="csv" style="display:inline-block"${list.length?"":" disabled"}>Export CSV</button></div></div></div>`;
  }
  $("#main").innerHTML=`<h1>Reports</h1><p class="lead">Summary computed from the stored incident records, ${label}.</p>
<div class="tools noprint"><label class="muted" style="display:flex;align-items:center;gap:6px">From <input type="date" id="rf" value="${r.from}" style="flex:0 0 auto;min-width:0"></label><label class="muted" style="display:flex;align-items:center;gap:6px">To <input type="date" id="rt" value="${r.to}" style="flex:0 0 auto;min-width:0"></label><button class="btn" id="go" style="display:inline-block">Show report</button><button class="btn ghost" data-days="7">Last 7 days</button><button class="btn ghost" data-days="30">Last 30 days</button><button class="btn ghost" data-days="90">Last 90 days</button></div>
<div class="ferr" role="alert" style="margin-bottom:12px">${rpErr}</div>${body}`;
  const go=()=>{rpRange={from:$("#rf").value,to:$("#rt").value};repShow(++renderSeq)};
  $("#go").onclick=go;
  document.querySelectorAll("[data-days]").forEach(b=>b.onclick=()=>{rpRange=lastDays(+b.dataset.days);repShow(++renderSeq)});
  if($("#pr"))$("#pr").onclick=()=>window.print();
  if($("#csv"))$("#csv").onclick=downloadCsv;
}
// Live map (W9): Leaflet + OpenStreetMap. The map is created once per visit to this tab and kept,
// so a new report or a click updates markers and the side panel without resetting zoom or position.
let selId=null,mf="act",lmap=null,mlayer=null,fitted=false;
const markers=new Map();
const SEV_COLOR={Critical:"#b71c1c",High:"#e53935",Medium:"#ef8f00",Low:"#2e7d32"},GREY="#8d8482";
// Text is stored HTML-escaped (see mapping.js); decode it for places that need plain text (tooltips).
const plain=h=>{const t=document.createElement("textarea");t.innerHTML=h;return t.value};
function destroyMap(){if(lmap){lmap.remove();lmap=null}mlayer=null;markers.clear();fitted=false}
function pinIcon(i,on){const c=i.active?(SEV_COLOR[i.severity]||GREY):GREY,z=on?26:20;
return L.divIcon({className:"pin",html:`<span class="dot${on?" on":""}${i.active?" act":""}" style="background:${c}"></span>`,iconSize:[z,z],iconAnchor:[z/2,z/2]})}
function buildMap(){
destroyMap();
$("#main").innerHTML=`<h1>Live map</h1><p class="lead">Where each emergency is, plotted from the latitude and longitude in the incident report.</p>
<div class="tools"><select id="fl" aria-label="Filter map"><option value="act">Active (reported, verified, ongoing)</option><option value="all">All incidents</option><option>Resolved</option><option>Duplicate</option><option>False</option></select></div>
<div class="mapwrap"><div><div class="card mapbox"><div id="lmap" role="region" aria-label="Map of incidents"></div></div>
<div class="legend"><span><i style="background:#b71c1c"></i>Critical</span><span><i style="background:#e53935"></i>High</span><span><i style="background:#ef8f00"></i>Medium</span><span><i style="background:#2e7d32"></i>Low</span><span><i style="background:#8d8482"></i>Not active</span></div></div>
<div id="side"></div></div>`;
$("#fl").value=mf;$("#fl").onchange=e=>{mf=e.target.value;fitted=false;map()};
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
$("#side").innerHTML=(cur?`<div class="card" style="margin-bottom:12px"><h3>${cur.code} <span class="pill ${cur.severity}">${cur.severity}</span> <span class="pill ${cur.status}">${cur.status}</span> ${fl(cur)}</h3>${cur.photoUrl?photoBlock(cur)+"<br>":""}<div class="kv" style="margin-top:0"><small>Location</small>${cur.loc}</div><div class="kv"><small>Coordinates (${cur.locSource})</small>${cur.lat!=null?cur.lat.toFixed(4)+", "+cur.lng.toFixed(4):"None"}</div><div class="kv"><small>Responders</small>${cur.res.map(r=>r[2]+" - "+r[1]).join("<br>")||"None assigned yet"}</div><button class="btn" id="dt">Open full record</button></div>`:"")+
list.map(x=>`<div class="li ${cur&&x.id===cur.id?"sel":""}" data-id="${x.id}"><b>${x.code}</b> <span class="pill ${x.severity}">${x.severity}</span><br><span class="muted">${x.loc}</span></div>`).join("")||`<div class="card muted">No incidents match this filter.</div>`;
if(cur)$("#dt").onclick=()=>detail(cur.id);
document.querySelectorAll(".li").forEach(e=>e.onclick=()=>pick(e.dataset.id))}
const pick=id=>{selId=id;map();const i=I.find(x=>x.id===id);if(lmap&&i&&i.lat!=null)lmap.panTo([i.lat,i.lng])};
document.addEventListener("click",e=>{const t=e.target.closest(".ph");if(t){const i=I.find(x=>x.id===t.dataset.id);if(!i||!i.photoUrl)return;$("#lb").innerHTML=`<img src="${i.photoUrl}" alt="Enlarged photo for ${i.code}">`;$("#lb").classList.add("open")}else if(e.target.closest("#lb"))$("#lb").classList.remove("open")});
document.addEventListener("keydown",e=>{if(e.key==="Escape")$("#lb").classList.remove("open")});
$("#today").textContent=new Date().toLocaleDateString("en-PH",{weekday:"long",year:"numeric",month:"long",day:"numeric"});

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
  $("#me-name").textContent=name;$("#me-email").textContent=email;
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
