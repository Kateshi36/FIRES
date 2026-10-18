// Node loader hook for the tests: any import from the Firebase CDN (www.gstatic.com) is answered by
// firebase-stub.mjs, so data.js can be loaded and run in Node without a browser or a network.
const STUB = new URL("./firebase-stub.mjs", import.meta.url).href;

export async function resolve(specifier, context, next) {
  if (specifier.startsWith("https://www.gstatic.com/firebasejs/")) return { url: STUB, shortCircuit: true };
  return next(specifier, context);
}
