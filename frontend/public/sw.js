const CACHE_VERSION = "notelib-v1";
const PRECACHE = ["/offline.html", "/favicon-192x192.png"];
const STATIC_ASSET_DESTINATIONS = ["style", "script", "image", "font"];
const OFFLINE_FALLBACK_SERVED_EVENT_TYPE = "OFFLINE_FALLBACK_SERVED";
// sw.js is served as a static, unbundled file, so it has no access to
// NEXT_PUBLIC_API_BASE_URL at build time. The registering page passes the
// real backend origin via a query param on the registration URL instead
// (see ServiceWorkerRegistration) — frontend and backend run on different
// origins in every real environment (see docker-compose CORS config).
const API_BASE_URL = new URL(self.location.href).searchParams.get("apiBase") ?? "/api";
const ANALYTICS_EVENTS_PATH = `${API_BASE_URL}/analytics/events`;

function trackOfflineFallback(path) {
  try {
    void fetch(ANALYTICS_EVENTS_PATH, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        eventType: OFFLINE_FALLBACK_SERVED_EVENT_TYPE,
        entityId: null,
        metadata: { path },
      }),
    }).catch(() => undefined);
  } catch {
    // Offline telemetry must never interfere with serving the fallback page.
  }
}

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_VERSION).then((cache) => cache.addAll(PRECACHE)),
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys
          .filter((key) => key !== CACHE_VERSION)
          .map((key) => caches.delete(key)),
      ),
    ),
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  const { request } = event;
  const url = new URL(request.url);

  if (url.pathname.startsWith("/api/")) {
    return;
  }

  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request).catch(() => {
        trackOfflineFallback(url.pathname);
        return caches.match("/offline.html");
      }),
    );
    return;
  }

  if (STATIC_ASSET_DESTINATIONS.includes(request.destination)) {
    event.respondWith(
      caches.open(CACHE_VERSION).then(async (cache) => {
        const cached = await cache.match(request);
        const networkFetch = fetch(request)
          .then((response) => {
            if (response.ok) {
              cache.put(request, response.clone());
            }
            return response;
          })
          .catch(() => cached);

        return cached ?? networkFetch;
      }),
    );
  }
});
