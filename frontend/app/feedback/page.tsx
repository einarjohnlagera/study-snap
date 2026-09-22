import { CampaignFeedbackPageClient } from "@/components/feedback/campaign-feedback-page-client";
import { PageHeader } from "@/components/page-header";
import { BackLink } from "@/components/ui/back-link";

export const metadata = {
  title: "Help us improve NoteLib | NoteLib",
};

export default function CampaignFeedbackPage() {
  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-8">
      <BackLink href="/dashboard" label="Dashboard" />
      <PageHeader
        eyebrow="FEEDBACK"
        title="Help us improve NoteLib"
        description="Tell us what gets in the way, and we'll use your feedback to decide what to improve next."
      />
      <CampaignFeedbackPageClient />
    </main>
  );
}
