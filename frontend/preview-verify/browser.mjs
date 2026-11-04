#!/usr/bin/env node
// THROW-AWAY verification driver (branch verify/v0179-cache only; never merged).
// Drives a REAL Chrome over the DevTools protocol (no dependencies: Node's built-in WebSocket) through the flow Product UX asked
// for: open a page with real next/link anchors, let Next's own viewport prefetch run, then click through client-side
// navigation, on a Vercel Preview (or a local production server) backed by the in-process fake catalog.
//
// Usage: node preview-verify/browser.mjs --base https://<preview-host> [--ttl-ms 120000] [--deadline-ms 90000]
//        [--stub-log <server log file>] [--out results.json]    (VERIFY_COOKIE='_vercel_jwt=...' for a protected Preview)
// --ttl-ms and --deadline-ms are the EFFECTIVE page TTL and walk deadline of the target (a scale-0.1 local run uses 12000 for both).
import { spawn } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const argv = process.argv.slice(2);
const arg = (n, f) => { const i = argv.indexOf(`--${n}`); return i >= 0 ? argv[i + 1] : f; };
const base = (arg("base", "") || "").replace(/\/$/, "");
if (!base) { console.error("--base is required"); process.exit(2); }
const TTL = Number(arg("ttl-ms", "120000"));
const DEADLINE = Number(arg("deadline-ms", "90000"));
const stubLog = arg("stub-log", "");
const only = arg("only", "");
const outFile = arg("out", `browser-verify-results-${Date.now()}.json`);
const cookieHeader = process.env.VERIFY_COOKIE || "";
const CHROME = process.env.CHROME_BIN || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const PORT = 9333;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const results = { tests: [], notes: [], evidence: {} };
const record = (id, name, pass, detail) => { results.tests.push({ id, name, pass, detail }); console.log(`${pass ? "PASS" : "FAIL"}  ${id}  ${name}\n      ${detail}`); };
const note = (id, text) => { results.notes.push({ id, text }); console.log(`NOTE  ${id}  ${text}`); };

// ---------------------------------------------------------------- minimal CDP client
async function launchChrome() {
  const dir = mkdtempSync(join(tmpdir(), "pv-chrome-"));
  const proc = spawn(CHROME, ["--headless=new", `--remote-debugging-port=${PORT}`, `--user-data-dir=${dir}`, "--no-first-run",
    "--no-default-browser-check", "--disable-gpu", "--window-size=1280,2400", "about:blank"], { stdio: "ignore" });
  for (let i = 0; i < 80; i += 1) {
    try { const r = await fetch(`http://127.0.0.1:${PORT}/json/version`); if (r.ok) break; } catch { /* not up yet */ }
    await sleep(250);
  }
  const target = await (await fetch(`http://127.0.0.1:${PORT}/json/new?about:blank`, { method: "PUT" })).json();
  return { proc, wsUrl: target.webSocketDebuggerUrl };
}
function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl); let id = 0; const pending = new Map(); const handlers = [];
    ws.onmessage = (m) => {
      const msg = JSON.parse(m.data);
      if (msg.id && pending.has(msg.id)) { const { res, rej } = pending.get(msg.id); pending.delete(msg.id); msg.error ? rej(new Error(msg.error.message)) : res(msg.result); }
      else if (msg.method) handlers.forEach((h) => h(msg.method, msg.params));
    };
    ws.onopen = () => resolve({
      send: (method, params = {}) => new Promise((res, rej) => { const n = ++id; pending.set(n, { res, rej }); ws.send(JSON.stringify({ id: n, method, params })); }),
      on: (h) => handlers.push(h), close: () => ws.close(),
    });
    ws.onerror = reject;
  });
}

const net = new Map(); // requestId -> { url, path, headers, wall, status, finishedWall, bytes }
const t0 = Date.now();
const allWalkStarts = () => (stubLog && existsSync(stubLog) ? readFileSync(stubLog, "utf8").split("\n").filter((l) => l.includes("PREVIEW_STUB walk-start")).length : null);
const stubWalkStarts = (kind, slug) => {
  if (!stubLog || !existsSync(stubLog)) return null;
  return readFileSync(stubLog, "utf8").split("\n").filter((l) => l.includes(`PREVIEW_STUB walk-start kind=${kind} slug=${slug}`)).length;
};

(async () => {
  const { proc, wsUrl } = await launchChrome();
  const cdp = await connect(wsUrl);
  const evalJs = async (expression) => {
    const r = await cdp.send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.text + " " + (r.exceptionDetails.exception?.description ?? ""));
    return r.result.value;
  };
  const waitFor = async (expr, timeoutMs, step = 100) => {
    const start = Date.now();
    for (;;) {
      let v = false; try { v = await evalJs(expr); } catch { v = false; }
      if (v) return Date.now() - start;
      if (Date.now() - start > timeoutMs) return null;
      await sleep(step);
    }
  };
  cdp.on((method, p) => {
    if (method === "Network.requestWillBeSent") {
      const headers = Object.fromEntries(Object.entries(p.request.headers).map(([k, v]) => [k.toLowerCase(), v]));
      let path = p.request.url; try { path = new URL(p.request.url).pathname; } catch { /* keep */ }
      net.set(p.requestId, { url: p.request.url, path, headers, wall: Date.now() });
      if (headers.rsc) results.evidence.rscRequests ??= [];
    } else if (method === "Network.responseReceived") { const r = net.get(p.requestId); if (r) r.status = p.response.status; }
    else if (method === "Network.loadingFinished") { const r = net.get(p.requestId); if (r) { r.finishedWall = Date.now(); r.bytes = p.encodedDataLength; } }
    else if (method === "Network.loadingFailed") { const r = net.get(p.requestId); if (r) { r.failed = p.errorText; r.finishedWall = Date.now(); } }
  });
  await cdp.send("Network.enable"); await cdp.send("Page.enable"); await cdp.send("Runtime.enable");
  await cdp.send("Emulation.setDeviceMetricsOverride", { width: 1280, height: 2400, deviceScaleFactor: 1, mobile: false });
  if (cookieHeader) {
    const [name, ...rest] = cookieHeader.split("="); const host = new URL(base).hostname;
    await cdp.send("Network.setCookie", { name, value: rest.join("="), domain: host, path: "/", secure: base.startsWith("https"), httpOnly: true });
  }
  const goto = async (path) => { await cdp.send("Page.navigate", { url: `${base}${path}` }); await waitFor("document.readyState === 'complete'", 30000); };
  const SKEL = "document.querySelector('[data-public-page-skeleton]') !== null";
  const hasText = (s) => `document.body.innerText.toLowerCase().includes(${JSON.stringify(s.toLowerCase())})`;  // innerText applies CSS text-transform (the label is uppercase)
  const click = (testid) => evalJs(`document.querySelector('[data-testid="${testid}"]').click(); true`);
  const prefetchesTo = (path) => [...net.values()].filter((r) => r.path === path && r.headers["next-router-prefetch"] && r.headers["rsc"]);

  // ---------------------------------------------------------------- B1: Next's own viewport prefetch (no walk)
  const walksBeforeB1 = allWalkStarts();
  await goto("/preview-verify-links");
  const linksOk = await waitFor("document.querySelectorAll('[data-testid^=\"link-\"]').length >= 5", 15000);
  await evalJs("window.__pvMarker = 'client-side-navigation-marker'; true");
  const prefetchWindowStart = Date.now();
  await sleep(6000); // production-mode viewport prefetch runs shortly after hydration
  const targets = { "fast-subject": "/public/library/verify-alpha", "slow-subject": "/public/library/verify-iota", "failing-subject": "/public/library/verify-nohasmore", "fast-hub": "/exam/ale", "slow-hub": "/exam/ce" };
  const seen = Object.fromEntries(Object.entries(targets).map(([k, p]) => [k, prefetchesTo(p)]));
  const anyPrefetch = Object.values(seen).some((a) => a.length > 0);
  results.evidence.rscHeaders = [...net.values()].filter((r) => r.headers.rsc && /verify-iota|verify-alpha|exam\/ce/.test(r.path)).map((r) => ({ path: r.path, query: r.url.split('?')[1] ?? '', ms: r.finishedWall ? r.finishedWall - r.wall : null, hdr: Object.fromEntries(Object.entries(r.headers).filter(([k]) => /^(rsc|next-router-prefetch|next-router-segment-prefetch|next-url|purpose|sec-purpose|sec-fetch-dest|sec-fetch-mode|priority)$/.test(k))), treeLen: (r.headers['next-router-state-tree'] ?? '').length, tree: decodeURIComponent(r.headers['next-router-state-tree'] ?? '').slice(0, 260) }));
  results.evidence.prefetchRequests = Object.fromEntries(Object.entries(seen).map(([k, a]) => [k, a.map((r) => ({ status: r.status, ms: r.finishedWall ? r.finishedWall - r.wall : null, bytes: r.bytes ?? null, value: r.headers["next-router-prefetch"] }))]));
  const noPrefetchLinks = Object.entries(seen).filter(([, a]) => a.length === 0).map(([k]) => k);
  if (!anyPrefetch) {
    note("L-PREFETCH", "NO prefetch request of any kind was observed for the five links in this run: the link may not have been prefetched at all (inert by design for a dynamic route without a loading boundary) or the browser did not prefetch. Walk starts below are the evidence that matters.");
  } else if (noPrefetchLinks.length > 0) {
    note("N-PREFETCH", `no prefetch request seen for: ${noPrefetchLinks.join(", ")} (a dynamic route without a loading boundary is not prefetched)`);
  }
  const allFast = Object.values(seen).flat().every((r) => r.finishedWall && r.finishedWall - r.wall < 5000);
  const walksAfterB1 = allWalkStarts();
  const walkDelta = walksBeforeB1 === null || walksAfterB1 === null ? null : walksAfterB1 - walksBeforeB1;
  const subjectWalks = stubWalkStarts("subject", "verify-iota"); const hubWalks = stubWalkStarts("program", "verify-slow-program");
  record("B1", "viewport prefetch of real links starts no catalog walk (subject listings and exam hubs), and every prefetch request completes fast",
    linksOk !== null && allFast && (walkDelta === null || walkDelta === 0),
    `links rendered ${linksOk !== null}; prefetch requests seen ${JSON.stringify(Object.fromEntries(Object.entries(seen).map(([k, a]) => [k, a.length])))}; every prefetch request finished under 5 s ${allFast}; total stub walk starts during the window ${walkDelta ?? "n/a (check runtime logs)"}; stub walk starts for the slow subject ${subjectWalks ?? "n/a (check runtime logs)"} and the slow hub ${hubWalks ?? "n/a (check runtime logs)"} over ${((Date.now() - prefetchWindowStart) / 1000).toFixed(0)} s of viewport time`);
  const prefetchWindow = { from: prefetchWindowStart, to: Date.now() };
  results.evidence.prefetchWindowUtc = { from: new Date(prefetchWindow.from).toISOString(), to: new Date(prefetchWindow.to).toISOString() };

  // ---- B1c (informational tripwire): the SAME kind of subject link with the DEFAULT prefetch. On Next 16.1.6 it starts a walk from the
  // viewport, which is why the app's subject links set prefetch={false}. If an upgrade stops this, the NOTE flips and tells us.
  const controlBefore = stubWalkStarts("subject", "verify-delta");
  await goto("/preview-verify-links?control=1"); await sleep(6000);
  const controlAfter = stubWalkStarts("subject", "verify-delta");
  note("B1c", `control link with the DEFAULT prefetch to a subject: walk starts from the viewport ${controlBefore === null ? "n/a (check runtime logs)" : controlAfter - controlBefore} (expected 1 on Next 16.1.6; 0 would mean the framework stopped prefetch-walking and the prefetch={false} rule can be revisited)`);
  results.evidence.controlDefaultPrefetchWalks = controlBefore === null ? null : controlAfter - controlBefore;

  // ---- B7: the REAL public note page. Its subject links (breadcrumb and "See all") must start no walk from the viewport, and still navigate.
  const subjectLinkSel = 'a[href="/public/library/verify-beta"]';
  const walksB7a = allWalkStarts(); const betaBefore = stubWalkStarts("subject", "verify-beta"); const netB7 = net.size;
  await goto("/public/library/verify-beta/anchor-note-stable"); await sleep(6000);
  const noteLinks = await evalJs(`document.querySelectorAll('${subjectLinkSel}').length`);
  const noteDelta = walksB7a === null ? null : allWalkStarts() - walksB7a;
  const notePrefetchToSubject = [...net.values()].slice(netB7).filter((r) => r.path === "/public/library/verify-beta" && r.headers["next-router-prefetch"]).length;
  record("B7a", "real note page: its subject links (breadcrumb, See all) start no catalog walk from the viewport and trigger no prefetch request",
    noteLinks >= 1 && notePrefetchToSubject === 0 && (noteDelta === null || noteDelta === 0),
    `subject links rendered ${noteLinks}; prefetch requests to the subject listing ${notePrefetchToSubject}; catalog walks started while the note page sat in the viewport ${noteDelta ?? "n/a (check runtime logs)"}`);
  await evalJs(`window.__pvMarker = 'client-side-navigation-marker'; document.querySelector('${subjectLinkSel}').click(); true`);
  const betaContent = await waitFor(hasText("Subject Library"), 20000); await sleep(700);
  const betaDelta = betaBefore === null ? null : stubWalkStarts("subject", "verify-beta") - betaBefore;
  record("B7b", "clicking the real note page's subject link navigates client-side to complete content with exactly one catalog walk (metadata and page share it)",
    betaContent !== null && !(await evalJs(SKEL)) && (await evalJs("window.__pvMarker === 'client-side-navigation-marker'")) && (betaDelta === null || betaDelta === 1),
    `content after ${betaContent ?? "never"} ms; client-side ${await evalJs("window.__pvMarker === 'client-side-navigation-marker'")}; walks started by this one navigation ${betaDelta ?? "n/a (check runtime logs)"}`);

  // ---- B8: the REAL exam hub page. Its subject chips must start no subject walk from the viewport; the hub's own load is one program walk.
  const programBefore = stubWalkStarts("program", "verify-program-a"); const subjectsBefore = allWalkStarts() - (programBefore ?? 0);
  const netB8 = net.size;
  await goto("/exam/ale"); await sleep(6000);
  const chips = await evalJs(`document.querySelectorAll('main a[href^="/public/library/verify-"]').length`);
  const programWalks = programBefore === null ? null : stubWalkStarts("program", "verify-program-a") - programBefore;
  const subjectWalksAfterHub = allWalkStarts() === null ? null : (allWalkStarts() - (stubWalkStarts("program", "verify-program-a") ?? 0)) - subjectsBefore;
  const chipPrefetches = [...net.values()].slice(netB8).filter((r) => /^\/public\/library\/verify-[a-z-]+$/.test(r.path) && r.headers["next-router-prefetch"]).length;
  const notePrefetches = [...net.values()].slice(netB8).filter((r) => /^\/public\/library\/verify-[a-z-]+\/[^/]+$/.test(r.path) && r.headers["next-router-prefetch"]).length;
  note("N-NOTE-PREFETCH", `the hub page's note cards (default prefetch, unchanged) produced ${notePrefetches} prefetch requests to NOTE pages in 6 s; each runs a note page's metadata, which makes one single-note backend fetch (not a catalog walk); this exists today and is outside B4 scope`);
  results.evidence.hubNotePrefetchRequests = notePrefetches;
  record("B8", "real exam hub page: its subject chips start no subject walk and trigger no prefetch; the hub's own load is one program walk",
    chips >= 1 && chipPrefetches === 0 && (subjectWalksAfterHub === null || subjectWalksAfterHub === 0) && (programWalks === null || programWalks === 1),
    `subject chips rendered ${chips}; prefetch requests to subject listings ${chipPrefetches}; subject walks started while the hub sat in the viewport ${subjectWalksAfterHub ?? "n/a (check runtime logs)"}; program walks for the hub's own load ${programWalks ?? "n/a (check runtime logs)"}`);

  if (only === "b1") { writeFileSync(outFile, JSON.stringify(results, null, 2)); cdp.close(); proc.kill(); process.exit(0); }
  // ---------------------------------------------------------------- B2: real click, fast subject (prefetched ~6 s earlier)
  await goto("/preview-verify-links"); await sleep(3000);
  await evalJs("window.__pvMarker = 'client-side-navigation-marker'; true");
  const alphaBefore = stubWalkStarts("subject", "verify-alpha");
  const clickStart = Date.now(); await click("link-fast-subject");
  const subjectContent = await waitFor(hasText("Subject Library"), 20000);
  const stillMarker = await evalJs("window.__pvMarker === 'client-side-navigation-marker'");
  await sleep(1000);
  const skelLingers = await evalJs(SKEL);
  const alphaAfter = stubWalkStarts("subject", "verify-alpha");
  const alphaDelta = alphaBefore === null || alphaAfter === null ? null : alphaAfter - alphaBefore;
  record("B2", "click through to a fast subject after its prefetch: complete content replaces the skeleton, client-side, and it does not stay a skeleton",
    subjectContent !== null && !skelLingers && stillMarker && (await evalJs("location.pathname")) === "/public/library/verify-alpha" && (alphaDelta === null || alphaDelta === 1),
    `content visible after ${subjectContent ?? "never"} ms; skeleton still in the DOM 1 s later: ${skelLingers}; client-side navigation (page state survived): ${stillMarker}; click-to-content ${Date.now() - clickStart} ms; catalog walks started by this ONE navigation (metadata and page share one) ${alphaDelta ?? "n/a (check runtime logs)"}`);

  // ---------------------------------------------------------------- B3: slow subject, real click: prompt skeleton, then the approved failure state
  await goto("/preview-verify-links"); await sleep(3000);
  await evalJs("window.__pvMarker = 'client-side-navigation-marker'; true");
  const slowClick = Date.now(); await click("link-slow-subject");
  const skelAt = await waitFor(SKEL, 5000);
  const navAt = await evalJs("document.querySelector('nav') !== null");
  const strictEarly = await evalJs(hasText("Strict Probe Note"));
  await sleep(700); // let the click's walk begin before counting
  const walksAfterClick = stubWalkStarts("subject", "verify-iota");
  const gone = await waitFor(`!(${SKEL})`, DEADLINE + 20000, 250);
  const strictLate = await evalJs(hasText("Strict Probe Note")); const subjLate = await evalJs(hasText("Subject Library"));
  record("B3", "slow subject, real click: the skeleton and the site nav appear promptly, the click (not the prefetch) starts the walk, and the page ends in the existing failure state at the deadline, never indefinitely",
    skelAt !== null && skelAt < 3000 && navAt && !strictEarly && (walksAfterClick === null || walksAfterClick === 1) && gone !== null && !strictLate && !subjLate,
    `skeleton after ${skelAt ?? "never"} ms; nav present ${navAt}; catalog data in the skeleton ${strictEarly}; stub walk starts after the click ${walksAfterClick ?? "n/a"}; skeleton gone after ${gone === null ? "NEVER" : `${((Date.now() - slowClick) / 1000).toFixed(1)} s`} (deadline ${DEADLINE / 1000} s); strict notes shown at the end ${strictLate}; 'Subject Library' shown at the end ${subjLate}`);

  // ---------------------------------------------------------------- B4: hubs (fast one clicked ~45 s after its prefetch, to cover a stale prefetch cache)
  await goto("/preview-verify-links"); await sleep(6000);
  await sleep(40000); // total about 46 s since this page's prefetch: past a 30 s dynamic prefetch window
  const hubStart = Date.now(); await click("link-fast-hub");
  const hubContent = await waitFor(`location.pathname === '/exam/ale' && !(${SKEL}) && document.querySelector('main h1') !== null && document.querySelector('main h1').innerText.trim().length > 0`, 20000);
  await sleep(1000); const hubSkel = await evalJs(SKEL); const hubPath = await evalJs("location.pathname");
  const hubText = await evalJs("document.body.innerText.slice(0, 200)");
  record("B4a", "fast exam hub clicked about 46 s after its prefetch: complete content, no lingering skeleton",
    hubPath === "/exam/ale" && hubContent !== null && !hubSkel, `path ${hubPath}; content after ${hubContent ?? "never"} ms; skeleton lingering ${hubSkel}; click-to-content ${Date.now() - hubStart} ms; text starts: ${JSON.stringify(hubText.replace(/\s+/g, " ").slice(0, 90))}`);
  await goto("/preview-verify-links"); await sleep(3000);
  const slowHubClick = Date.now(); await click("link-slow-hub");
  const hubSkelAt = await waitFor(SKEL, 5000);
  await sleep(700); // let the click's walk begin before counting
  const hubWalksAfter = stubWalkStarts("program", "verify-slow-program");
  const hubGone = await waitFor(`!(${SKEL})`, DEADLINE + 20000, 250);
  const hubStrict = await evalJs(hasText("Strict Probe Note"));
  record("B4b", "slow exam hub, real click: prompt skeleton, the click starts the walk, the failure state replaces the skeleton at the deadline",
    hubSkelAt !== null && hubSkelAt < 3000 && (hubWalksAfter === null || hubWalksAfter === 1) && hubGone !== null && !hubStrict,
    `skeleton after ${hubSkelAt ?? "never"} ms; stub walk starts after the click ${hubWalksAfter ?? "n/a"}; skeleton gone after ${hubGone === null ? "NEVER" : `${((Date.now() - slowHubClick) / 1000).toFixed(1)} s`}; strict notes shown ${hubStrict}`);

  // ---------------------------------------------------------------- B5: direct URL, refresh, and navigation after the cache expires
  await goto("/public/library/verify-alpha"); const direct = await waitFor(hasText("Subject Library"), 20000);
  await cdp.send("Page.reload"); await waitFor("document.readyState === 'complete'", 30000); const refreshed = await waitFor(hasText("Subject Library"), 20000);
  record("B5a", "direct URL navigation and a refresh of an affected page render complete content", direct !== null && refreshed !== null && !(await evalJs(SKEL)),
    `direct: content after ${direct ?? "never"} ms; refresh: content after ${refreshed ?? "never"} ms`);
  await goto("/preview-verify-links"); await sleep(2000);
  await sleep(TTL + 3000); // let the page cache for the fast subject expire
  const alphaPre = stubWalkStarts("subject", "verify-alpha");
  await click("link-fast-subject"); const afterExpiry = await waitFor(hasText("Subject Library"), 30000);
  await sleep(500); const alphaPost = stubWalkStarts("subject", "verify-alpha"); const expiryDelta = alphaPre === null || alphaPost === null ? null : alphaPost - alphaPre;
  record("B5b", "navigation after the cache has expired still loads fresh complete content (no stale data, no stuck skeleton)", afterExpiry !== null && !(await evalJs(SKEL)) && (expiryDelta === null || expiryDelta === 1),
    `waited ${((TTL + 3000) / 1000).toFixed(0)} s; content after ${afterExpiry ?? "never"} ms; walks started by this one post-expiry navigation ${expiryDelta ?? "n/a (check runtime logs)"}`);

  // ---------------------------------------------------------------- B6: navigation following a failed catalog load
  await goto("/preview-verify-links"); await sleep(2000);
  await click("link-failing-subject"); await sleep(3000);
  const failShown = !(await evalJs(SKEL)) && !(await evalJs(hasText("Subject Library"))) && !(await evalJs(hasText("Strict Probe Note")));
  await goto("/preview-verify-links"); await sleep(1500);
  await click("link-failing-subject"); await sleep(3000); // inside the 15 s failure backoff on the same instance
  const failAgain = !(await evalJs(SKEL)) && !(await evalJs(hasText("Subject Library"))) && !(await evalJs(hasText("Strict Probe Note")));
  record("B6", "navigation following a failed catalog load ends in the existing failure state (no data, no stuck skeleton), also inside the failure backoff", failShown && failAgain,
    `first click: failure state ${failShown}; second click inside the backoff: failure state ${failAgain}`);

  results.evidence.requestsSample = [...net.values()].filter((r) => r.headers["rsc"]).slice(0, 40).map((r) => ({ path: r.path, prefetch: r.headers["next-router-prefetch"] ?? null, status: r.status ?? null, ms: r.finishedWall ? r.finishedWall - r.wall : null, bytes: r.bytes ?? null }));
  writeFileSync(outFile, JSON.stringify(results, null, 2));
  cdp.close(); proc.kill();
  const failed = results.tests.filter((t) => !t.pass);
  console.log(`\n${failed.length === 0 ? "ALL PASS" : `${failed.length} FAILED: ${failed.map((t) => t.id).join(", ")}`}  (details in ${outFile}; ran ${((Date.now() - t0) / 1000).toFixed(0)} s)`);
  process.exit(failed.length === 0 ? 0 : 1);
})().catch((error) => { console.error("driver error:", error); process.exit(2); });
