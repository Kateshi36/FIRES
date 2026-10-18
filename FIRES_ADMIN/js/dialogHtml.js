// The markup of the two-button confirmation dialog on #dlg. Pure, so it can be tested on its own.
// W11 (make / remove responder) and W13 (False report / Duplicate) both use it through confirmDlg()
// in app.js. The ids are what that code looks for: dlg-t (the dialog's aria-labelledby), dg (the
// message under the buttons), dno (Cancel, the safe default) and dyes (the confirm button).
//
// `title` and `confirm` are plain text and are escaped here. `body` is HTML that the caller has already
// made safe (names and e-mails come out of mapUser() escaped; the rest is fixed text).
import { esc } from "./mapping.js";

export const confirmHtml = ({ title, body, confirm }) =>
  `<h2 id="dlg-t">${esc(title)}</h2>${body || ""}
<div class="ferr" id="dg" role="alert"></div>
<div class="acts"><button class="btn ghost" id="dno">Cancel</button><button class="btn" id="dyes">${esc(confirm)}</button></div>`;
