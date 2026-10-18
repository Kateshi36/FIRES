import {
  auth, db, signInWithEmailAndPassword, signOut,
  setPersistence, browserSessionPersistence, doc, getDoc
} from "./firebase.js";

const $=s=>document.querySelector(s),err=$("#err");
const show=m=>{err.textContent=m;err.hidden=false};
const busy=b=>{$("#go").disabled=b;$("#go").textContent=b?"Signing in...":"Sign in"};

$("#eye").onclick=()=>{const p=$("#pw"),h=p.type==="password";p.type=h?"text":"password";$("#eye").textContent=h?"Hide":"Show";$("#eye").setAttribute("aria-label",h?"Hide password":"Show password")};

// Maps Firebase error codes to plain messages. Wrong e-mail and wrong password share one
// message on purpose, so the page does not reveal which e-mails have accounts.
function errorMessage(e){
  switch(e&&e.code){
    case "auth/invalid-credential":
    case "auth/wrong-password":
    case "auth/user-not-found":
    case "auth/invalid-email":
      return "Incorrect email or password.";
    case "auth/too-many-requests":
      return "Too many failed attempts. Wait a few minutes and try again.";
    case "auth/network-request-failed":
    case "unavailable":
      return "No internet connection. Check your network and try again.";
    case "auth/user-disabled":
      return "This account has been disabled. Contact the system administrator.";
    case "permission-denied":
      return "Your account could not be verified. Contact the system administrator.";
    default:
      return "Could not sign in. Please try again.";
  }
}

$("#f").onsubmit=async e=>{
  e.preventDefault();err.hidden=true;
  const em=$("#em").value.trim(),pw=$("#pw").value;
  if(!em||!pw){show("Enter your email and password.");return}
  busy(true);
  try{
    // Session persistence: closing the tab signs the administrator out.
    await setPersistence(auth,browserSessionPersistence);
    const cred=await signInWithEmailAndPassword(auth,em,pw);

    // The role lives in users/{uid}. A missing document counts as a citizen.
    let u=null;
    try{
      const snap=await getDoc(doc(db,"users",cred.user.uid));
      u=snap.exists()?snap.data():null;
    }catch(ex){
      await signOut(auth);
      throw ex;
    }
    if(!u||u.role!=="admin"){
      await signOut(auth);
      show("This account is not an administrator. Access is limited to administrators.");
      busy(false);return;
    }

    location.href="index.html";
  }catch(ex){
    show(errorMessage(ex));
    busy(false);
  }
};
