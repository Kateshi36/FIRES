// Form checks for Add responder (W10). Same rules and wording style as the Android app's
// Validators.kt, so the website and the app accept and reject the same input.
// Each check returns null when fine, or the message to show under the field.

// Same list as AssignmentRules.UNITS in the app.
export const UNITS = ["B-FLARE", "BDRRMO", "BFP", "Neighboring barangay"];

const EMAIL = /^[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}$/;
const PH_MOBILE = /^(09|\+?639)\d{9}$/;

// Spaces and dashes are ignored and not stored: "0917 123 4567" becomes "09171234567".
export const normalizePhone = v => String(v ?? "").replace(/[ -]/g, "");

export const nameError = v => {
  const t = String(v ?? "").trim();
  return !t ? "Enter the responder's full name." : t.length > 100 ? "The name is too long (100 characters at most)." : null;
};
export const emailError = v => {
  const t = String(v ?? "").trim();
  return !t ? "Enter an email address." : !EMAIL.test(t) ? "Enter a valid email address, like name@example.com." : null;
};
export const phoneError = v => {
  const p = normalizePhone(v);
  return !p ? "Enter a contact number." : !PH_MOBILE.test(p) ? "Enter a valid mobile number, like 09171234567." : null;
};
export const unitError = v => UNITS.includes(v) ? null : "Choose the responder's unit.";

// Returns { errors: {name,email,contact,unit}, ok, clean } where clean is what gets saved.
export function validateResponder(f) {
  const errors = {
    name: nameError(f.name), email: emailError(f.email),
    contact: phoneError(f.contact), unit: unitError(f.unit)
  };
  return {
    errors,
    ok: !Object.values(errors).some(Boolean),
    clean: {
      fullName: String(f.name ?? "").trim(),
      email: String(f.email ?? "").trim().toLowerCase(),
      contactNo: normalizePhone(f.contact),
      address: f.unit
    }
  };
}

// Long random password nobody ever sees: the responder sets their own through the reset e-mail.
export function randomPassword(len = 24, rnd = globalThis.crypto) {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%*-_";
  const buf = new Uint32Array(len);
  rnd.getRandomValues(buf);
  return Array.from(buf, n => chars[n % chars.length]).join("");
}
