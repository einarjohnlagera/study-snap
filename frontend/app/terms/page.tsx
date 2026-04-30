import type { Metadata } from "next";
import { LegalPageLayout } from "@/components/public/legal-page-layout";
import { buildPageMetadata } from "@/lib/site-metadata";

const lastUpdated = "March 25, 2026";

export const metadata: Metadata = buildPageMetadata({
  title: "NoteLib Terms of Service",
  description: "Read the terms that apply to using NoteLib, paid plans, and acceptable use.",
  path: "/terms",
});

export default function TermsPage() {
  return (
    <LegalPageLayout
      title="Terms of Service"
      lastUpdated={lastUpdated}
      intro={(
        <p>
          These terms describe the rules for using NoteLib. By using the service, you agree to follow these terms and
          use the platform responsibly.
        </p>
      )}
      sections={[
        {
          title: "1. Use of Service",
          content: (
            <>
              <p>NoteLib is a study tool.</p>
              <p>Users are responsible for the content they upload and must not upload illegal content.</p>
            </>
          ),
        },
        {
          title: "2. Accounts",
          content: (
            <>
              <p>Users are responsible for keeping their account secure.</p>
              <p>We may suspend accounts for abuse or violations of these terms.</p>
            </>
          ),
        },
        {
          title: "3. Acceptable Use",
          content: (
            <>
              <p>Users must not:</p>
              <ul className="list-disc space-y-1 pl-5">
                <li>Abuse AI generation.</li>
                <li>Attempt to hack the system.</li>
                <li>Upload harmful or illegal content.</li>
                <li>Use the service for non-study-related abusive automation.</li>
              </ul>
            </>
          ),
        },
        {
          title: "4. Subscription",
          content: (
            <>
              <p>NoteLib offers manual-renewal paid plans.</p>
              <p>Users can cancel anytime, and paid access remains active until the end of the billing period.</p>
              <p>No refunds are provided for partial billing periods unless required by law.</p>
            </>
          ),
        },
        {
          title: "5. Service Availability",
          content: (
            <p>We try to keep NoteLib available, but we do not guarantee uninterrupted service.</p>
          ),
        },
        {
          title: "6. Changes to Service",
          content: (
            <p>Features, pricing, and limits may change over time as the service evolves.</p>
          ),
        },
        {
          title: "7. Contact",
          content: (
            <p>
              For questions about these terms, contact{" "}
              <a href="mailto:support@mail.notelib.app" className="text-blue-600 underline underline-offset-4 dark:text-blue-400">
                support@mail.notelib.app
              </a>
              .
            </p>
          ),
        },
      ]}
    />
  );
}
