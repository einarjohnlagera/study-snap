import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

export function DashboardEmpty() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <h2 className="text-lg font-semibold sm:text-xl">Start your first note</h2>
      <p className="max-w-2xl text-sm text-foreground/75">
        Write, paste, or upload notes, then turn them into a Study Pack.
      </p>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <Link href="/study?editor=note" className="w-full sm:w-auto">
          <Button type="button" className="w-full sm:w-auto">Create Note</Button>
        </Link>
        <Link href="/study?editor=note&focus=upload" className="w-full sm:w-auto">
          <Button type="button" variant="outline" className="w-full sm:w-auto">Upload Notes</Button>
        </Link>
      </div>
      <Link href="/demo" className="inline-block text-sm text-blue-600 hover:underline dark:text-blue-400">
        Try with sample notes
      </Link>
    </Card>
  );
}
