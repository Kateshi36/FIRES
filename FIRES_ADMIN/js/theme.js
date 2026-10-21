/* Light / dark mode. Classic script (not a module) so it runs before first paint.
   Saved choice lives in localStorage; with no saved choice the system setting applies. */
(function () {
  var KEY = "fires-theme", root = document.documentElement;
  function saved() { try { return localStorage.getItem(KEY); } catch (e) { return null; } }
  function system() { return window.matchMedia && matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"; }
  function current() { var s = saved(); return s === "dark" || s === "light" ? s : system(); }
  function apply(t) { root.setAttribute("data-theme", t); var m = document.querySelector('meta[name="theme-color"]'); if (m) m.content = t === "dark" ? "#14110F" : "#FAF7F5"; }
  function paint(btn) {
    var dark = current() === "dark";
    btn.setAttribute("aria-pressed", dark ? "true" : "false");
    btn.setAttribute("aria-label", dark ? "Switch to light mode" : "Switch to dark mode");
    var l = btn.querySelector(".tl"); if (l) l.textContent = dark ? "Light mode" : "Dark mode";
  }
  apply(current());
  document.addEventListener("DOMContentLoaded", function () {
    var btns = document.querySelectorAll("[data-theme-toggle]");
    for (var i = 0; i < btns.length; i++) (function (b) {
      paint(b);
      b.addEventListener("click", function () {
        var next = current() === "dark" ? "light" : "dark";
        try { localStorage.setItem(KEY, next); } catch (e) {}
        apply(next);
        var all = document.querySelectorAll("[data-theme-toggle]");
        for (var j = 0; j < all.length; j++) paint(all[j]);
      });
    })(btns[i]);
  });
})();
