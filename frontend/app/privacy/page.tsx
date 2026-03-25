import type { Metadata } from "next";
import { LegalPageLayout } from "@/components/public/legal-page-layout";
import { buildPageMetadata } from "@/lib/site-metadata";

const lastUpdated = "March 25, 2026";

export const metadata: Metadata = buildPageMetadata({
  title: "NoteLib Privacy Policy",
  description: "Read how NoteLib collects, uses, and protects account, study, and billing information.",
  path: "/privacy",
});

export default function PrivacyPage() {
  return (
    <LegalPageLayout
      title="Privacy Policy"
      lastUpdated={lastUpdated}
      intro={(
        <p>
          NoteLib is a study tool that helps users turn notes into study materials such as summaries, key concepts,
          and quizzes. This policy explains what information we collect and how we use it.
        </p>
      )}
      sections={[
        {
          title: "1. Introduction",
          content: (
            <p>
              NoteLib is designed to support studying, review, and exam preparation. We collect limited information so
              the product can create, save, and improve study workflows for users.
            </p>
          ),
        },
        {
          title: "2. Information We Collect",
          content: (
            <>
              <p>We may collect the following categories of information:</p>
              <ul className="list-disc space-y-1 pl-5">
                <li>Account information such as your name and email address.</li>
                <li>Notes content and study materials uploaded or created by you.</li>
                <li>Study activity such as quiz results and Study Packs generated.</li>
                <li>Usage data such as pages visited and features used.</li>
              </ul>
            </>
          ),
        },
        {
          title: "3. How We Use Information",
          content: (
            <ul className="list-disc space-y-1 pl-5">
              <li>To provide and improve the service.</li>
              <li>To generate study packs and quizzes.</li>
              <li>To track usage and improve features.</li>
              <li>To send important emails such as verification, billing, and study reminders.</li>
            </ul>
          ),
        },
        {
          title: "4. AI Processing",
          content: (
            <>
              <p>
                Notes and uploaded images may be processed by AI services to generate summaries, key concepts, and
                quizzes.
              </p>
              <p>Uploaded images are processed for OCR text extraction.</p>
              <p>We do not sell user data.</p>
            </>
          ),
        },
        {
          title: "5. Data Storage",
          content: (
            <>
              <p>User data is stored securely using reasonable safeguards.</p>
              <p>We take reasonable measures to protect account data, notes content, and study activity.</p>
            </>
          ),
        },
        {
          title: "6. Emails",
          content: (
            <>
              <p>We may send account emails, study reminders, and billing emails when relevant.</p>
              <p>Users can manage reminder-related preferences in Settings.</p>
            </>
          ),
        },
        {
          title: "7. Subscription and Payments",
          content: (
            <>
              <p>Payments are processed via PayMongo.</p>
              <p>We do not store full credit card information.</p>
            </>
          ),
        },
        {
          title: "8. Contact",
          content: (
            <p>
              For privacy questions, contact{" "}
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
