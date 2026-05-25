import type { Metadata } from "next";
import { LegalPageLayout } from "@/components/public/legal-page-layout";

const lastUpdated = "May 25, 2026";
const canonicalUrl = "https://www.notelib.app/refund";

export const metadata: Metadata = {
  title: "Refund & Cancellation Policy — NoteLib",
  description: "Read NoteLib's refund and cancellation policy, including how manual renewal, cancellation, and billing-error refunds work.",
  alternates: {
    canonical: canonicalUrl,
  },
  openGraph: {
    title: "Refund & Cancellation Policy — NoteLib",
    description: "Read NoteLib's refund and cancellation policy, including how manual renewal, cancellation, and billing-error refunds work.",
    type: "website",
    url: canonicalUrl,
    siteName: "NoteLib",
  },
};

export default function RefundPage() {
  return (
    <LegalPageLayout
      title="Refund & Cancellation Policy"
      lastUpdated={lastUpdated}
      intro={(
        <p>
          This policy explains how cancellations and refund requests work for NoteLib paid plans.
        </p>
      )}
      sections={[
        {
          title: "1. Cancellation",
          content: (
            <p>
              You can cancel your plan anytime from Settings → Plan &amp; Billing. After cancelling, your paid access
              remains active until the end of your current billing period. Your notes and Study Packs are never
              deleted. NoteLib uses manual renewal — there are no automatic charges.
            </p>
          ),
        },
        {
          title: "2. Refund Policy",
          content: (
            <p>
              Payments are non-refundable for partial billing periods. We issue refunds only for billing errors:
              duplicate charges or payments made due to a technical failure on our end.
            </p>
          ),
        },
        {
          title: "3. How to Request a Refund",
          content: (
            <ol className="list-decimal space-y-1 pl-5">
              <li>Email support@mail.notelib.app with your account email address and a description of the issue.</li>
              <li>We will review your request within 2 business days.</li>
              <li>If approved, we will process the refund through Xendit.</li>
              <li>You will receive a confirmation email once the refund is submitted.</li>
              <li>The refund will appear on your original payment method within 5–10 business days, depending on your bank or e-wallet.</li>
            </ol>
          ),
        },
        {
          title: "4. How to Cancel",
          content: (
            <ol className="list-decimal space-y-1 pl-5">
              <li>Go to Settings.</li>
              <li>Open Plan &amp; Billing.</li>
              <li>Click &quot;Cancel plan&quot; under your current plan.</li>
              <li>Confirm. Access continues until your billing period ends.</li>
            </ol>
          ),
        },
        {
          title: "5. Contact",
          content: (
            <p>
              For refund requests or billing questions, email{" "}
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
