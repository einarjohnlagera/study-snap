"use client";

import { useEffect } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { requireAdminUser } from "@/lib/route-guards";
import { AdminCourseProgramCatalogSection } from "@/components/admin/admin-course-program-catalog-section";
import { AdminProgramFamiliesSection } from "@/components/admin/admin-program-families-section";
import { AdminApplicableProgramsSection } from "@/components/admin/admin-applicable-programs-section";

export default function AdminCourseProgramsPage() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const viewParam = searchParams.get("view");
  const activeView = viewParam === "programs" ? "programs" : "families";

  // Redirect-only guard, matching the other admin sub-pages. Gating the render on a state flag set
  // inside the effect trips the cascading-render lint rule, and it is unnecessary here: both sections
  // fetch from ADMIN-only endpoints, so a non-admin who briefly renders gets no data either way.
  useEffect(() => {
    requireAdminUser(router);
  }, [router]);

  useEffect(() => {
    if (viewParam !== "families" && viewParam !== "programs") {
      const params = new URLSearchParams(searchParams.toString());
      params.set("view", "families");
      router.replace(`${pathname}?${params.toString()}`);
    }
  }, [pathname, router, searchParams, viewParam]);

  const selectView = (view: "families" | "programs") => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("view", view);
    router.push(`${pathname}?${params.toString()}`);
  };

  const handleTabKeyDown = (event: React.KeyboardEvent<HTMLButtonElement>) => {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    const next = activeView === "families" ? "programs" : "families";
    selectView(next);
    globalThis.setTimeout(() => document.getElementById(`catalog-tab-${next}`)?.focus(), 0);
  };

  return (
    <div className="mx-auto w-full max-w-7xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      <header className="space-y-2">
        <div className="flex items-center gap-3">
          <Link href="/admin" className="text-sm text-foreground/55 hover:text-foreground/80">
            ← Admin
          </Link>
        </div>
        <h1 className="text-3xl font-semibold text-foreground">Course / Program Catalog</h1>
        <p className="max-w-3xl text-sm leading-relaxed text-foreground/70">
          Manage the shared catalog: the programs notes can be curated against, and the families that group them.
        </p>
      </header>
      <div className="border-b border-border" role="tablist" aria-label="Catalog management views">
        <div className="flex items-center gap-5 sm:gap-6">
          {(["families", "programs"] as const).map((view) => (
            <button key={view} id={`catalog-tab-${view}`} type="button" role="tab"
              aria-selected={activeView === view} aria-controls={`catalog-panel-${view}`}
              tabIndex={activeView === view ? 0 : -1} onKeyDown={handleTabKeyDown}
              onClick={() => selectView(view)}
              className={`border-b-2 px-1 py-3 text-sm font-medium ${activeView === view ? "border-primary text-foreground" : "border-transparent text-foreground/60"}`}>
              {view === "families" ? "Program Families" : "Course / Programs"}
            </button>
          ))}
        </div>
      </div>
      <div id={`catalog-panel-${activeView}`} role="tabpanel"
        aria-labelledby={`catalog-tab-${activeView}`}>
        {activeView === "families" ? <AdminProgramFamiliesSection /> : <AdminCourseProgramCatalogSection />}
      </div>
      <AdminApplicableProgramsSection />
    </div>
  );
}
