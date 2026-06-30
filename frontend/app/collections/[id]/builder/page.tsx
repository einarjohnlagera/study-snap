import { StudyPlanBuilderPageClient } from "./study-plan-builder-page-client";

export const metadata = {
  title: "Study Plan Builder | NoteLib",
};

export default async function StudyPlanBuilderPage({ params }: Readonly<{ params: Promise<{ id: string }> }>) {
  const { id } = await params;
  return <StudyPlanBuilderPageClient collectionId={id} />;
}
