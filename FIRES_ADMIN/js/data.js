// Sample data shaped like the ERD tables. Replace with Firebase reads.
const U=[
{id:"u01",fullName:"Ramon Dela Cruz",email:"r.delacruz@bflare.ph",contactNo:"0917 555 0101",address:"B-FLARE, Bagumbayan",role:"responder"},
{id:"u02",fullName:"Liza Manalo",email:"l.manalo@bdrrmo.ph",contactNo:"0917 555 0102",address:"BDRRMO, Bagumbayan",role:"responder"},
{id:"u03",fullName:"Joel Bautista",email:"j.bautista@bflare.ph",contactNo:"0917 555 0103",address:"B-FLARE, Bagumbayan",role:"responder"},
{id:"u04",fullName:"Carla Reyes",email:"c.reyes@bfp.gov.ph",contactNo:"0917 555 0104",address:"BFP Taguig",role:"responder"},
{id:"c1",fullName:"Maria Santos",email:"maria.s@mail.com",contactNo:"0918 222 0001",address:"Purok 3, Bagumbayan",role:"citizen"},
{id:"c2",fullName:"Paolo Lim",email:"paolo.l@mail.com",contactNo:"0918 222 0002",address:"Purok 5, Bagumbayan",role:"citizen"},
{id:"c3",fullName:"Grace Tan",email:"grace.t@mail.com",contactNo:"0918 222 0003",address:"Purok 4, Bagumbayan",role:"citizen"},
{id:"c4",fullName:"Ken Ramos",email:"ken.r@mail.com",contactNo:"0918 222 0004",address:"Purok 1, Bagumbayan",role:"citizen"}];
const RS=()=>U.filter(u=>u.role==="responder");
const ACT=i=>["Reported","Verified","Ongoing"].includes(i.status);
const fl=i=>(i.isSuspicious?'<span class="pill flag">Suspicious</span> ':'')+(i.isDuplicate?'<span class="pill flag">Duplicate</span>':'');
const I=[
{id:"INC-1043",reporter:"c4",description:"Smoke reported from a house. Same account submitted twice in 10 minutes.",loc:"Purok 1, Bagumbayan",severity:"Medium",status:"Reported",isSuspicious:true,submittedAt:"2026-09-30 09:05",dateResolved:null,remarks:"",res:[],msgs:[]},
{id:"INC-1042",reporter:"c1",description:"Kitchen fire spreading to the second floor of a row house.",loc:"Purok 3, Bagumbayan",severity:"High",status:"Ongoing",submittedAt:"2026-09-30 08:12",dateResolved:null,remarks:"",res:[["u01","Engine 2"],["u02","Rescue 1"]],msgs:[["Maria Santos","There is thick smoke coming out of the windows."],["Ramon Dela Cruz","Engine 2 en route, ETA 4 minutes."]]},
{id:"INC-1041",reporter:"c2",description:"Electrical panel sparking inside a warehouse.",loc:"Purok 5, Bagumbayan",severity:"Medium",status:"Verified",submittedAt:"2026-09-30 07:40",dateResolved:null,remarks:"",res:[["u03","Engine 1"]],msgs:[]},
{id:"INC-1040",reporter:"c3",description:"Fire at Purok 3 (same location and time as INC-1042).",loc:"Purok 3, Bagumbayan",severity:"High",status:"Duplicate",isDuplicate:true,submittedAt:"2026-09-30 08:15",dateResolved:null,remarks:"Merged with INC-1042.",res:[],msgs:[]},
{id:"INC-1038",reporter:"c3",description:"Grass fire near the barangay road.",loc:"Purok 4, Bagumbayan",severity:"Low",status:"Resolved",submittedAt:"2026-09-29 16:05",dateResolved:"2026-09-29 16:52",remarks:"Extinguished. No injuries.",res:[["u04","Engine 3"]],msgs:[["Grace Tan","It is near dry grass and getting bigger."]]},
{id:"INC-1035",reporter:"c4",description:"Vehicle fire in a parking lot.",loc:"Purok 1, Bagumbayan",severity:"High",status:"Resolved",submittedAt:"2026-09-28 21:18",dateResolved:"2026-09-28 22:31",remarks:"Two vehicles damaged. Cause under review.",res:[["u01","Engine 2"],["u03","Engine 1"]],msgs:[]},
{id:"INC-1029",reporter:"c1",description:"Burning rubbish in a vacant lot.",loc:"Purok 2, Bagumbayan",severity:"Low",status:"Resolved",submittedAt:"2026-09-27 12:02",dateResolved:"2026-09-27 12:35",remarks:"Rubbish fire, put out by responders.",res:[["u02","Engine 4"]],msgs:[]},
{id:"INC-1024",reporter:"c3",description:"Fire at a small eatery, gas tank leak suspected.",loc:"Purok 6, Bagumbayan",severity:"High",status:"Resolved",submittedAt:"2026-09-25 19:44",dateResolved:"2026-09-25 21:10",remarks:"Gas supply shut off. Stall destroyed.",res:[["u04","Engine 3"],["u02","Rescue 1"]],msgs:[]}];
["INC-1042","INC-1041","INC-1038","INC-1035","INC-1024"].forEach(k=>I.find(i=>i.id===k).photo=true);
const ph=i=>{const h=(i.id.slice(4)*37)%40;return"data:image/svg+xml,"+encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 400"><defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2b2422"/><stop offset="1" stop-color="#5a3a30"/></linearGradient></defs><rect width="600" height="400" fill="url(#g)"/><rect y="300" width="600" height="100" fill="#1d1918"/><path d="M${230+h} 320 C200 250 260 220 270 150 C310 200 350 190 340 120 C400 190 420 260 380 320Z" fill="#ef8f00"/><path d="M${260+h} 320 C250 270 290 250 300 210 C330 250 350 270 345 320Z" fill="#ffd54f"/><text x="20" y="380" font-size="18" fill="#fff" opacity=".8" font-family="sans-serif">Sample photo, ${i.id}</text></svg>`)};
const photoBlock=i=>i.photo?`<img class="ph" src="${ph(i)}" alt="Photo submitted by the reporter for ${i.id}" data-id="${i.id}">`:`<span class="muted">No photo attached. Photos are optional.</span>`;
const C=Object.fromEntries(U.map(u=>[u.id,u.fullName]));
