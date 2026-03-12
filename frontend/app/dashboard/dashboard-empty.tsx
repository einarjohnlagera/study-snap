import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

export function DashboardEmpty() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <h2 className="text-lg font-semibold sm:text-xl">No Study Packs yet</h2>
      <p className="max-w-2xl text-sm text-foreground/75">
        Study Packs are your saved summaries, key concepts, and quiz reviewers. Create your first one to start building
        your personal Study Library.
      </p>
      <Link href="/study" className="w-full sm:w-auto">
        <Button type="button" className="w-full sm:w-auto">Create your first Study Pack</Button>
      </Link>
    </Card>
  );
}
