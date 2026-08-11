"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { requireAdminUser } from "@/lib/route-guards";
import { AdminCourseProgramCatalogSection } from "@/components/admin/admin-course-program-catalog-section";
import { AdminApplicableProgramsSection } from "@/components/admin/admin-applicable-programs-section";

export default function AdminCourseProgramsPage() {
  const router = useRouter();

  // Redirect-only guard, matching the other admin sub-pages. Gating the render on a state flag set
  // inside the effect trips the cascading-render lint rule, and it is unnecessary here: both sections
  // fetch from ADMIN-only endpoints, so a non-admin who briefly renders gets no data either way.
  useEffect(() => {
    requireAdminUser(router);
  }, [router]);

  return (
    <div className="mx-auto w-full max-w-7xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      <header className="space-y-2">
        <div className="flex items-center gap-3">
          <Link href="/admin" className="text-sm text-foreground/55 hover:text-foreground/80">
            ← Admin
          </Link>
        </div>
        <h1 className="text-3xl font-semibold text-foreground">Course / Programs</h1>
        <p className="max-w-3xl text-sm leading-relaxed text-foreground/70">
          Curation surfaces for the shared Course / Program catalog and for which programs a note is
          discoverable under. Both are authoring tools rather than operational checks, which is why they
          live here rather than on the Admin dashboard.
        </p>
      </header>

      <AdminCourseProgramCatalogSection />
      <AdminApplicableProgramsSection />
    </div>
  );
}
