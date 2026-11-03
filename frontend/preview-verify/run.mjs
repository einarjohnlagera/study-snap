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
const reqHeaders = bypass ? { "x-vercel-protection-bypass": bypass } : {};

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
  console.log(`waiting for the next period start in ${(Math.max(0, T(0) - serverNow()) / 1000).toFixed(0)} s; the run then takes about ${(11.6 * u / 60000).toFixed(1)} min`);
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
    await sleepUntil(T(11.3));
    const after = {};
    for (const k of ["s4", "hub", "sitemap", "note", "s2"]) { after[k] = keep(await get(PAGES[k])); addTags(after[k]); }
    return { warm4, during, after };
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
  const recovered = ["s4", "hub", "sitemap", "note", "s2"].every((k) => a[k].status === 200);
  const railsBack = /Edit Probe New Title|EDIT-NEW-TEXT|Probe [a-f0-9]{6}/.test(a.note.body);
  record("T5c", "recovery after the failure window: every page returns 200 with fresh data, rails back",
    recovered && railsBack && !MARKER.test(a.s2.body) && !MARKER.test(a.hub.body),
    `statuses after: ${["s4", "hub", "sitemap", "note", "s2"].map((k) => `${k}=${a[k].status}`).join(" ")}; rails back ${railsBack}; marker absent on s2 ${!MARKER.test(a.s2.body)} and hub ${!MARKER.test(a.hub.body)}`);

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
