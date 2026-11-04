// THROW-AWAY harness page (branch verify/v0179-cache only; never merged): real next/link anchors to the loading-bounded routes,
// so a real browser exercises Next's own viewport prefetch and client-side navigation (preview-verify/browser.mjs).
// Subject-listing links mirror the application (prefetch={false}); exam-hub links use the default prefetch, as the app does.
// `?control=1` adds a subject link with the DEFAULT prefetch: a tripwire that documents the framework behavior (on Next 16.1.6 it
// starts a walk from the viewport), so an upgrade that changes it is noticed.
import Link from "next/link";

export const dynamic = "force-dynamic";

const LINKS: Array<[string, string, string, boolean?]> = [
  ["fast-subject", "/public/library/verify-alpha", "Fast subject", false],
  ["slow-subject", "/public/library/verify-iota", "Slow subject", false],
  ["failing-subject", "/public/library/verify-nohasmore", "Failing subject", false],
  ["fast-hub", "/exam/ale", "Fast exam hub"],
  ["slow-hub", "/exam/ce", "Slow exam hub"],
];

export default async function PreviewVerifyLinksPage({ searchParams }: { searchParams: Promise<{ control?: string }> }) {
  const control = (await searchParams).control === "1";
  const links = control ? [...LINKS, ["control-default-prefetch", "/public/library/verify-delta", "Control: default prefetch"] as [string, string, string]] : LINKS;
  return (
    <main className="mx-auto max-w-3xl space-y-4 p-6">
      <h1 className="text-2xl font-semibold">Verification links</h1>
      <ul className="space-y-2">
        {links.map(([id, href, label, prefetch]) => (
          <li key={id}>
            <Link data-testid={`link-${id}`} href={href} prefetch={prefetch} className="text-blue-700 underline">
              {label}
            </Link>
          </li>
        ))}
      </ul>
    </main>
  );
}
