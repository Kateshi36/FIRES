// DEMO ONLY: in the real system, sign in with Firebase Authentication,
// then read the user's role from the User table and allow only "administrator".
const ACCOUNTS=[
 {email:"admin@fires.ph",password:"admin123",role:"administrator",name:"Administrator"},
 {email:"r.delacruz@bflare.ph",password:"responder1",role:"responder",name:"Ramon Dela Cruz"}];
const $=s=>document.querySelector(s),err=$("#err");
const show=m=>{err.textContent=m;err.hidden=false};
$("#eye").onclick=()=>{const p=$("#pw"),h=p.type==="password";p.type=h?"text":"password";$("#eye").textContent=h?"Hide":"Show";$("#eye").setAttribute("aria-label",h?"Hide password":"Show password")};
$("#f").onsubmit=e=>{e.preventDefault();err.hidden=true;
 const em=$("#em").value.trim().toLowerCase(),pw=$("#pw").value;
 if(!em||!pw){show("Enter your email and password.");return}
 const u=ACCOUNTS.find(a=>a.email===em&&a.password===pw);
 if(!u){show("Incorrect email or password.");return}
 if(u.role!=="administrator"){show("This account is not an administrator. Access is limited to administrators.");return}
 $("#go").disabled=true;$("#go").textContent="Signing in...";
 sessionStorage.setItem("fires_admin","1");sessionStorage.setItem("fires_admin_name",u.name);
 setTimeout(()=>location.href="index.html",400)};
