import { Suspense } from "react";
import StudyPageClient from "./study-page-client";

export default function StudyPage() {
  return (
    <Suspense fallback={null}>
      <StudyPageClient />
    </Suspense>
  );
}

