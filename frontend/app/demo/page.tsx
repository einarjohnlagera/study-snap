import { Suspense } from "react";
import StudyPageClient from "@/app/study/study-page-client";

export default function DemoPage() {
  return (
    <Suspense fallback={null}>
      <StudyPageClient forcedDemoMode />
    </Suspense>
  );
}
