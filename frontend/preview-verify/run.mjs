#!/usr/bin/env node
// THROW-AWAY verification driver (branch verify/v0179-cache only; never merged).
// Usage: node preview-verify/run.mjs --base https://<preview-host> [--bypass <vercel automation bypass secret>] [--out results.json]
// It reads the stub schedule from /preview-verify-state, waits for the next schedule period, then runs the
// tests below against the real deployment. All times are SERVER times (skew-corrected). u = the page-class TTL.
import { writeFileSync } from "node:fs";

const argv = process.argv.slice(2);
const arg = (name, fallback) => { const i = argv.indexOf(`--${name}`); return i >= 0 ? argv[i + 1] : fallback; };
const base = (arg("base", "") || "").replace(/\/$/, "");
if (!base) { console.error("--base is required"); process.exit(2); }
const bypass = arg("bypass", "");
const outFile = arg("out", `preview-verify-results-${Date.now()}.json`);
// Alternative to --bypass: an auth cookie obtained from a Vercel shareable link, passed via the environment so it stays
// out of shell history and `ps`: VERIFY_COOKIE='_vercel_jwt=...' node preview-verify/run.mjs --base ...
const cookie = process.env.VERIFY_COOKIE || "";
const reqHeaders = { ...(bypass ? { "x-vercel-protection-bypass": bypass } : {}), ...(cookie ? { cookie } : {}) };

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
let skew = 0;
const serverNow = () => Date.now() + skew;
const sleepUntil = async (t) => { const d = t - serverNow(); if (d > 0) await sleep(d); };

async function get(path) {
  const t = serverNow();
  try {
    const r = await fetch(`${base}${path}`, { headers: reqHeaders, redirect: "manual", cache: "no-store" });
    const body = await r.text();
    const h = {};
    r.headers.forEach((v, k) => { h[k.toLowerCase()] = v; });
    return { path, t, status: r.status, headers: h, body };
  } catch (error) {
    return { path, t, status: 0, headers: {}, body: "", error: String(error) };
  }
}
async function state() {
  const r = await get("/preview-verify-state");
  if (r.status !== 200) throw new Error(`state route returned ${r.status}: ${r.body.slice(0, 200)}`);
  return JSON.parse(r.body);
}

const PAGES = {
  s1: "/public/library/verify-alpha", s2: "/public/library/verify-beta", s3: "/public/library/verify-gamma",
  s4: "/public/library/verify-delta", hub: "/exam/ale", note: "/public/library/verify-beta/anchor-note-stable",
  sitemap: "/sitemap.xml",
};
const MARKER = /Withdrawal Marker (Alpha|Beta)|withdrawal-marker-(alpha|beta)|MARKER-WITHDRAWABLE-TEXT/i;
const PLAN_MARKER = /Marker Plan/;
const EDIT_OLD = /Edit Probe Old Title|edit-probe-old-title|EDIT-OLD-TEXT/i;
const EDIT_NEW = /Edit Probe New Title|edit-probe-new-title|EDIT-NEW-TEXT/i;
const tags = (body) => {
  const out = new Set();
  for (const m of body.matchAll(/Probe ([a-f0-9]{6}) (\d+)/g)) out.add(`${m[1]}:${m[2]}`);
  for (const m of body.matchAll(/probe-([a-f0-9]{6})-(\d+)-/g)) out.add(`${m[1]}:${m[2]}`);
  return [...out];
};

const results = { tests: [], notes: [], raw: {} };
const record = (id, name, pass, detail) => {
  results.tests.push({ id, name, pass, detail });
  console.log(`${pass ? "PASS" : "FAIL"}  ${id}  ${name}\n      ${detail}`);
};

(async () => {
  const st0 = await state();
  skew = st0.now - Date.now();
  const u = st0.u;
  console.log(`stub: u=${u} ms, period=${st0.periodU}u, withdraw at ${st0.withdrawU}u, failure ${st0.failStartU}u to ${st0.failEndU}u; skew ${skew} ms; installed=${st0.stubInstalled}`);
  let ps = st0.periodStart;
  if (serverNow() > ps + 0.05 * u) ps += st0.periodMs; // start at the next period boundary
  const T = (x) => ps + x * u;
  const tWithdraw = T(st0.withdrawU);
  console.log(`waiting for the next period start in ${(Math.max(0, T(0) - serverNow()) / 1000).toFixed(0)} s; the run then takes about ${(11.6 * u / 60000 + 5).toFixed(1)} min (timeline plus about 5 minutes of strictness and slow-walk phases)`);
  while (T(0) - serverNow() > 60_000) { await sleep(60_000); console.log(`  ...${((T(0) - serverNow()) / 1000).toFixed(0)} s to start`); }
  await sleepUntil(T(0));

  const all = []; // every page response
  const keep = (r) => { all.push(r); return r; };
  const P = { s2: [], hub: [], note: [], sitemap: [] }; // polls
  const instances = new Set();
  const addTags = (r) => tags(r.body).forEach((t) => instances.add(t.split(":")[0]));

  // ---- precondition warm (T0) ----
  const warm = {};
  for (const k of ["s1", "s2", "hub", "note", "sitemap"]) { warm[k] = keep(await get(PAGES[k])); addTags(warm[k]); }
  const pre = ["s1", "s2", "hub", "note", "sitemap"].map((k) => `${k}:${warm[k].status}${MARKER.test(warm[k].body) ? "+marker" : ""}`).join(" ");
  const preOk = warm.s1.status === 200 && MARKER.test(warm.s1.body) && warm.s2.status === 200 && MARKER.test(warm.s2.body)
    && warm.hub.status === 200 && MARKER.test(warm.hub.body) && warm.note.status === 200 && MARKER.test(warm.note.body)
    && warm.sitemap.status === 200 && MARKER.test(warm.sitemap.body);
  record("T0", "preconditions: every page renders with the marker before the withdrawal", preOk, pre);
  if (!preOk) { console.error("Preconditions failed: pages did not render the stub marker. Aborting."); writeFileSync(outFile, JSON.stringify(results, null, 2)); process.exit(1); }

  // ---- timelines ----
  const concurrency = (async () => {
    await sleepUntil(T(0.5));
    const w = keep(await get(PAGES.s3)); addTags(w);
    await sleepUntil(T(2.7));
    const burst = await Promise.all(Array.from({ length: 20 }, () => get(PAGES.s3)));
    burst.forEach((r) => { keep(r); addTags(r); });
    return { warmTags: tags(w.body), burst };
  })();
  const idle = (async () => {
    await sleepUntil(T(3.0));
    const before = keep(await get(PAGES.s1));
    await sleepUntil(T(9.0)); // 6u idle (12 minutes at scale 1), spanning the withdrawal
    const after = keep(await get(PAGES.s1));
    return { before, after };
  })();
  // Each polled page starts at a different phase so cache entries are created at different ages relative to the withdrawal
  // (hub at 3.95u is the worst case: its entry is created just before the withdrawal and may legitimately live almost one TTL after it).
  const PHASES = { s2: 3.0, note: 3.5, sitemap: 3.9, hub: 3.95 };
  const pollPage = async (k) => {
    const step = u / 12;
    for (let t = T(PHASES[k]); t <= T(8.0); t += step) {
      await sleepUntil(t);
      const r = await get(PAGES[k]);
      P[k].push(r); keep(r); addTags(r);
    }
  };
  const polls = Promise.all(["s2", "note", "sitemap", "hub"].map(pollPage));
  const failure = (async () => {
    await sleepUntil(T(9.3));
    const warm4 = keep(await get(PAGES.s4)); addTags(warm4);
    await sleepUntil(T(10.6));
    const during = {};
    for (const k of ["s4", "hub", "sitemap", "note", "s2"]) { during[k] = keep(await get(PAGES[k])); }
    // Recovery is polled: after a failure the cache backs off for 15 s per key per instance (B3b), so the first rounds
    // after the window may still show the error shell. The bound is the backoff plus a margin, NOT scaled with u.
    const failEndT = T(st0.failEndU);
    let after = null; let recoveredAfterMs = null;
    for (let t = failEndT + 1000; t <= failEndT + 40_000; t += 2000) {
      await sleepUntil(t);
      const cur = {};
      for (const k of ["s4", "hub", "sitemap", "note", "s2"]) { cur[k] = keep(await get(PAGES[k])); addTags(cur[k]); }
      after = cur;
      const ok = ["s4", "hub", "sitemap", "note", "s2"].every((k) => cur[k].status === 200)
        && /Edit Probe New Title|EDIT-NEW-TEXT|Probe [a-f0-9]{6}/.test(cur.note.body) && !MARKER.test(cur.s2.body) && !MARKER.test(cur.hub.body);
      if (ok) { recoveredAfterMs = serverNow() - failEndT; break; }
    }
    return { warm4, during, after, recoveredAfterMs };
  })();
  const [conc, idl, , fail] = await Promise.all([concurrency, idle, polls, failure]);

  // ---- evaluations ----
  const rel = (t) => `${((t - tWithdraw) / 1000).toFixed(1)}s`;
  const fmtDeadline = (bound) => `${((bound) / 1000).toFixed(1)}s`;
  const render = 0.05 * u; // allowance for one cached render and network time
  const exposure = (id, name, rs, test, bound) => {
    const seenWith = rs.filter((r) => r.status === 200 && test(r.body));
    const lastSeen = seenWith.length ? Math.max(...seenWith.map((r) => r.t)) : null;
    const afterDeadline = rs.filter((r) => r.t > tWithdraw + bound + render && test(r.body));
    const newState = rs.filter((r) => r.t > tWithdraw + bound + render && r.status === 200 && !test(r.body));
    const pass = lastSeen !== null && afterDeadline.length === 0 && newState.length > 0;
    record(id, name, pass,
      `bound ${fmtDeadline(bound + render)} after the withdrawal; last response still showing it at ${lastSeen === null ? "never" : rel(lastSeen)}; ` +
      `${afterDeadline.length} response(s) after the deadline still showed it; ${newState.length} later response(s) showed the new state`);
  };
  exposure("T1a", "subject page: withdrawn note title/excerpt gone within the page bound", P.s2, (b) => MARKER.test(b), u);
  exposure("T1b", "exam hub: withdrawn note gone within the page bound", P.hub, (b) => MARKER.test(b), u);
  exposure("T1c", "exam hub: withdrawn study plan gone within the page bound", P.hub, (b) => PLAN_MARKER.test(b), u);
  exposure("T9", "note-page rails: withdrawn note gone within the page bound", P.note, (b) => MARKER.test(b), u);
  exposure("T6", "subject page: edited-out text gone within the page bound", P.s2, (b) => EDIT_OLD.test(b), u);
  exposure("T7", "sitemap: withdrawn slug gone within the (shortened) sitemap bound", P.sitemap, (b) => MARKER.test(b), u / 2);

  record("T2", "first request after 6u idle (spanning the withdrawal) shows no withdrawn content",
    idl.after.status === 200 && !MARKER.test(idl.after.body) && MARKER.test(idl.before.body),
    `before-idle status ${idl.before.status} marker=${MARKER.test(idl.before.body)}; after-idle status ${idl.after.status} marker=${MARKER.test(idl.after.body)}`);

  const burstOk = conc.burst.filter((r) => r.status === 200);
  const perInstance = {};
  conc.burst.forEach((r) => tags(r.body).filter((t) => t.includes(":")).forEach((t) => {
    const [i, s] = t.split(":"); (perInstance[i] ??= new Set()).add(s);
  }));
  const warmByInstance = Object.fromEntries(conc.warmTags.map((t) => t.split(":")));
  const stale = Object.entries(perInstance).filter(([i, seqs]) => warmByInstance[i] !== undefined && seqs.has(warmByInstance[i]));
  const singleFlight = Object.values(perInstance).every((s) => s.size <= 1);
  record("T3", "20 parallel requests after expiry: no expired data, at most one rebuild per instance",
    burstOk.length === 20 && singleFlight && stale.length === 0 && Object.keys(perInstance).length > 0,
    `${burstOk.length}/20 returned 200; instances seen ${Object.keys(perInstance).length}; distinct rebuild ids per instance ${JSON.stringify(Object.fromEntries(Object.entries(perInstance).map(([i, s]) => [i, [...s]])))}; ` +
    `expired id served: ${stale.length ? JSON.stringify(stale.map(([i]) => i)) : "none"}`);

  const bad = [];
  const seen = { cc: new Set(), vc: new Set() };
  all.filter((r) => r.path !== PAGES.sitemap || true).forEach((r) => {
    if (r.status === 0) return;
    const cc = r.headers["cache-control"] ?? ""; const vc = (r.headers["x-vercel-cache"] ?? "").toUpperCase();
    seen.cc.add(cc || "(none)"); seen.vc.add(vc || "(none)");
    if (/s-maxage|stale-while-revalidate/i.test(cc)) bad.push(`${r.path}: cache-control ${cc}`);
    if (["HIT", "STALE", "PRERENDER"].includes(vc)) bad.push(`${r.path}: x-vercel-cache ${vc}`);
  });
  record("T4", "no CDN or page-cache layer: no s-maxage or stale-while-revalidate, x-vercel-cache never HIT or STALE",
    bad.length === 0, `${all.length} responses; cache-control values ${JSON.stringify([...seen.cc])}; x-vercel-cache values ${JSON.stringify([...seen.vc])}; violations ${bad.length ? bad.slice(0, 3).join("; ") : "none"}`);

  const d = fail.during; const a = fail.after;
  const is5xx = (r) => r.status >= 500 && r.status < 600;
  const leaksData = (r) => /Probe [a-f0-9]{6} \d+|probe-[a-f0-9]{6}-\d+-|Verify (Alpha|Beta|Gamma|Delta) Note \d|Edit Probe/.test(r.body);
  const pageKeys = ["s4", "hub", "s2"];
  record("T5a", "upstream failure after the TTL: pages show NO expired data (privacy requirement); the sitemap fails with a 5xx",
    pageKeys.every((k) => !leaksData(d[k])) && is5xx(d.sitemap),
    `expired data present: ${pageKeys.map((k) => `${k}=${leaksData(d[k])}`).join(" ")}; sitemap status ${d.sitemap.status}; page statuses ${pageKeys.map((k) => `${k}=${d[k].status}`).join(" ")}`);
  const pageStatuses = pageKeys.map((k) => d[k].status);
  const real5xx = pageStatuses.every((c) => c >= 500 && c < 600);
  results.notes.push({ id: "L1", name: "KNOWN LIMITATION: pages return a real 5xx on upstream failure", observed: real5xx ? "yes" : `no, statuses ${pageStatuses.join(",")} (an error shell streamed after a committed 200)` });
  console.log(`NOTE  L1  real 5xx from pages on upstream failure: ${real5xx ? "yes" : `NO, statuses ${pageStatuses.join(",")}`} (informational, not counted as a failure; the root layout wraps pages in a Suspense boundary so the status commits before the data resolves)`);
  const railsGone = d.note.status === 200 && !/Verify Beta Note \d|Edit Probe|Probe [a-f0-9]{6}/.test(d.note.body);
  record("T5b", "upstream failure: the note page still renders (200) with EMPTY rails, no expired list",
    railsGone, `note page status ${d.note.status}; rails content present: ${d.note.status === 200 ? !railsGone : "n/a"}`);
  const RECOVERY_BOUND_MS = 25_000; // 15 s failure backoff plus polling and render margin
  const recoveredMs = fail.recoveredAfterMs;
  record("T5c", "recovery after the failure window: every page returns 200 with fresh data and rails back within the backoff bound",
    recoveredMs !== null && recoveredMs <= RECOVERY_BOUND_MS,
    `recovered ${recoveredMs === null ? "NEVER within 40 s" : `${(recoveredMs / 1000).toFixed(1)} s`} after the failure window ended (bound ${RECOVERY_BOUND_MS / 1000} s); last statuses ${["s4", "hub", "sitemap", "note", "s2"].map((k) => `${k}=${a?.[k]?.status}`).join(" ")}`);

  // ---- sitemap completeness and canonical host (from the warm response taken before the withdrawal) ----
  const locs = [...warm.sitemap.body.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]);
  const wrongHost = locs.filter((l) => !l.startsWith("https://www.notelib.app/"));
  const wantAll = ["/public/library/verify-alpha", "/public/library/verify-beta", "/public/library/verify-gamma", "/public/library/verify-delta", "anchor-note-stable"];
  const missing = wantAll.filter((w) => !locs.some((l) => l.includes(w)));
  record("T17", "sitemap is complete for the stub catalog and every URL uses the www host",
    locs.length > 0 && wrongHost.length === 0 && missing.length === 0,
    `${locs.length} <loc> entries; non-www entries ${wrongHost.length ? wrongHost.slice(0, 2).join(", ") : "none"}; missing ${missing.length ? missing.join(", ") : "none"}`);

  // ---- B3 phases (not clock-driven; the stub is outside its failure window here) ----
  const SUBJ = (slug) => `/public/library/${slug}`;
  const strictLeak = (b) => /Strict Probe Note/.test(b);
  const rendersSubject = (b) => /Subject Library/.test(b);
  const timed = async (path) => { const t0 = Date.now(); const r = await get(path); keep(r); return { ...r, ms: Date.now() - t0 }; };
  const failsClosed = (r) => r.status === 200 && !strictLeak(r.body) && !rendersSubject(r.body);
  const describeR = (r) => `status ${r.status}, ${(r.ms / 1000).toFixed(1)} s, subject page rendered ${rendersSubject(r.body)}, strict notes leaked ${strictLeak(r.body)}`;

  const noFlag = await timed(SUBJ("verify-nohasmore"));
  record("T10", "walk page without hasMore: fails closed (error shell, no partial list rendered)", failsClosed(noFlag), describeR(noFlag));
  const emptyMore = await timed(SUBJ("verify-emptymore"));
  record("T11", "empty page that still says hasMore=true: fails closed", failsClosed(emptyMore), describeR(emptyMore));
  const endless = await timed(SUBJ("verify-endless"));
  record("T12", "endless catalog: the 400-page guard (or the deadline) fails the walk closed; check runtime logs for CATALOG_WALK_GUARD_BREACH",
    failsClosed(endless), `${describeR(endless)} (a guard breach takes about 400 stub pages; near 90 s means the deadline fired first)`);
  const validEmpty = await timed(SUBJ("verify-empty"));
  record("T13", "valid empty catalog (hasMore=false): renders the normal (empty) subject page, no error",
    validEmpty.status === 200 && rendersSubject(validEmpty.body) && !strictLeak(validEmpty.body), describeR(validEmpty));
  const junk = [];
  for (let i = 0; i < 40; i += 1) junk.push(await timed(SUBJ(`verify-junk-${i}`)));
  for (let i = 0; i < 20; i += 1) junk.push(await timed(SUBJ("verify-junk-repeat")));
  const junkOk = junk.every((r) => r.status === 200 && rendersSubject(r.body) && !strictLeak(r.body));
  record("T14", "junk subject slugs: every request renders the existing empty page (HTTP 200); check runtime logs that empty walks are not cached",
    junkOk, `60 requests (40 distinct, 20 repeating one slug); all rendered the normal empty page: ${junkOk}; slowest ${(Math.max(...junk.map((r) => r.ms)) / 1000).toFixed(1)} s (logs should show about 60 or more CATALOG_WALK ... notes=0 lines, not about 40)`);
  console.log("starting the slow-walk phase (about 90 s)...");
  const slowWalk = await timed(SUBJ("verify-epsilon"));
  // The effective deadline is min(90 s, page TTL): at scale 1 the TTL is 120 s so 90 s governs; in a scaled dry run the TTL
  // override (u) is lower and the clamp makes the deadline equal to u. Both are the production behavior.
  const expectedMs = Math.min(90_000, u);
  const deadlineGoverned = failsClosed(slowWalk) && slowWalk.ms >= 0.85 * expectedMs && slowWalk.ms <= expectedMs + 25_000;
  record("T15", "slow walk (20 pages x 6 s, each under the 15 s request timeout): the whole-walk deadline governs and fails the page closed",
    deadlineGoverned, `${describeR(slowWalk)}; expected an error shell at about ${(expectedMs / 1000).toFixed(0)} s (min of the 90 s deadline and the page TTL). A 504 or an earlier end at scale 1 means the PLATFORM function limit is below the 90 s deadline (record the elapsed time); check runtime logs for CATALOG_CACHE_DEADLINE class=walk:page deadlineMs=${expectedMs}`);

  // ---- B4 phases: the loading skeleton, prefetch, the note route and limited bots ----
  const BROWSER_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36";
  const SKEL = 'data-public-page-skeleton="listing"';
  // Reads the response as a stream for up to `ms`, timestamping every chunk (an aborted read is expected for slow pages).
  const streamProbe = async (path, { ua = BROWSER_UA, ms = 6000 } = {}) => {
    const ctrl = new AbortController(); const t0 = Date.now(); const timer = setTimeout(() => ctrl.abort(), ms);
    const chunks = []; let status = 0;
    try {
      const r = await fetch(`${base}${path}`, { headers: { ...reqHeaders, "user-agent": ua }, redirect: "manual", cache: "no-store", signal: ctrl.signal });
      status = r.status; const reader = r.body.getReader(); const dec = new TextDecoder();
      for (;;) { const { done, value } = await reader.read(); if (done) break; chunks.push({ t: Date.now() - t0, text: dec.decode(value, { stream: true }) }); }
    } catch { /* aborted at the cap */ }
    clearTimeout(timer);
    return { status, chunks, body: chunks.map((c) => c.text).join(""), ms: Date.now() - t0 };
  };

  const early = await streamProbe(SUBJ("verify-zeta"), { ms: 6000 });
  const firstSkel = early.chunks.find((c) => c.text.includes(SKEL));
  record("T18", "slow subject: the text-free skeleton and the site chrome stream within seconds, with no catalog data in the early bytes",
    early.status === 200 && !!firstSkel && firstSkel.t <= 5000 && !strictLeak(early.body) && !rendersSubject(early.body) && /<nav/.test(early.body),
    `status ${early.status}; skeleton marker first seen at ${firstSkel ? `${(firstSkel.t / 1000).toFixed(1)} s` : "never"} of a 6 s probe (${early.chunks.length} chunks, ${early.body.length} bytes); nav present ${/<nav/.test(early.body)}; strict notes leaked ${strictLeak(early.body)}; 'Subject Library' present ${rendersSubject(early.body)}`);

  // A prefetch-style request (what <Link> sends) to a loading-bounded slow subject must NOT start a walk. Baseline on the B3 Preview
  // (no loading.tsx): 1.2 s, 251 bytes. A walk would take about 90 s.
  const pfStart = Date.now(); let pf;
  try {
    const r = await fetch(`${base}${SUBJ("verify-theta")}`, { headers: { ...reqHeaders, "user-agent": BROWSER_UA, RSC: "1", "Next-Router-Prefetch": "1" }, redirect: "manual", cache: "no-store", signal: AbortSignal.timeout(12000) });
    const b = await r.text(); pf = { status: r.status, ms: Date.now() - pfStart, bytes: b.length, body: b };
  } catch (error) { pf = { status: 0, ms: Date.now() - pfStart, bytes: 0, body: "", error: String(error) }; }
  record("T19", "prefetch-style request to a loading-bounded slow subject returns fast with no catalog data (a prefetch must not start a walk)",
    pf.status === 200 && pf.ms < 5000 && pf.bytes < 200_000 && !strictLeak(pf.body),
    `status ${pf.status}${pf.error ? ` (${pf.error})` : ""}; ${(pf.ms / 1000).toFixed(1)} s; ${pf.bytes} bytes (baseline on B3: 1.2 s, 251 bytes); strict notes leaked ${strictLeak(pf.body)}. Also confirm in the runtime logs that no walk for this subject started`);

  const noteFull = await timed("/public/library/verify-beta/anchor-note-stable");
  record("T20", "note page: renders the note and carries NO loading skeleton marker (the note route has no loading boundary)",
    noteFull.status === 200 && /Anchor Note Stable/.test(noteFull.body) && !noteFull.body.includes(SKEL),
    `status ${noteFull.status}, ${(noteFull.ms / 1000).toFixed(1)} s; note title present ${/Anchor Note Stable/.test(noteFull.body)}; skeleton marker present ${noteFull.body.includes(SKEL)}`);

  const BOTS = ["facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)", "Twitterbot/1.0"];
  const headOf = (body) => { const i = body.indexOf("<body"); return i > 0 ? body.slice(0, i) : body.slice(0, 20000); };
  const botRes = []; for (const ua of BOTS) botRes.push(await streamProbe(SUBJ("verify-beta"), { ua, ms: 30000 }));
  // The raw streamed HTML legitimately contains the text-free fallback markup as well as the content (observed in the dry run),
  // so the skeleton marker is REPORTED here, not required to be absent.
  const botOk = botRes.every((r) => r.status === 200 && rendersSubject(r.body) && /<title>[^<]*Verify Beta/.test(headOf(r.body)));
  record("T21", "limited-bot user agents get complete HTML: the page content is present and the title is in the head (the text-free skeleton marker is reported, not forbidden)",
    botOk, botRes.map((r, i) => `${BOTS[i].split("/")[0]}: status ${r.status}, content ${rendersSubject(r.body)}, skeleton ${r.body.includes(SKEL)}, title in head ${/<title>[^<]*Verify Beta/.test(headOf(r.body))}`).join("; "));
  const normalRes = await streamProbe(SUBJ("verify-beta"), { ms: 30000 });
  results.notes.push({ id: "N1", name: "INFORMATIONAL: browser user agent on a fast subject", observed: `title in head ${/<title>[^<]*Verify Beta/.test(headOf(normalRes.body))}; skeleton marker in the raw stream ${normalRes.body.includes(SKEL)}; ${normalRes.chunks.length} chunks` });
  console.log(`NOTE  N1  browser UA on a fast subject: title in head ${/<title>[^<]*Verify Beta/.test(headOf(normalRes.body))}; skeleton marker in the raw stream ${normalRes.body.includes(SKEL)}; ${normalRes.chunks.length} chunks`);

  const finalState = await state().catch(() => null);
  record("T8", "instances and safety: instances observed; the real API was never contacted",
    instances.size > 0 && !!finalState && finalState.stubInstalled && (finalState.blockedApiRequests ?? []).length === 0,
    `distinct instance ids seen in pages ${instances.size} (${[...instances].join(",")}); state route (one instance): stubInstalled=${finalState?.stubInstalled}, blockedApiRequests=${JSON.stringify(finalState?.blockedApiRequests ?? null)}`);

  results.raw = { ps, u, tWithdraw, instances: [...instances], polls: Object.fromEntries(Object.entries(P).map(([k, v]) => [k, v.map((r) => ({ t: r.t - tWithdraw, status: r.status, marker: MARKER.test(r.body), plan: PLAN_MARKER.test(r.body), editOld: EDIT_OLD.test(r.body), xvc: r.headers["x-vercel-cache"] ?? null }))])) };
  writeFileSync(outFile, JSON.stringify(results, null, 2));
  const failed = results.tests.filter((t) => !t.pass);
  console.log(`\n${failed.length === 0 ? "ALL PASS" : `${failed.length} FAILED: ${failed.map((t) => t.id).join(", ")}`}  (details in ${outFile})`);
  process.exit(failed.length === 0 ? 0 : 1);
})().catch((error) => { console.error("driver error:", error); process.exit(2); });
